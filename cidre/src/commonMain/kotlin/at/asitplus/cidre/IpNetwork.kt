package at.asitplus.cidre

import at.asitplus.cidre.byteops.*


sealed class IpNetwork<N : Number, Size>
@Throws(IllegalArgumentException::class)
constructor(address: IpAddress<N, Size>, override val prefix: Prefix, strict: Boolean, deepCopy: Boolean) :
    IpAddressAndPrefix<N, Size>,
    Comparable<IpNetwork<N, Size>> {

    init {
        require(prefix <= address.octets.size.toUInt() * 8u) { "Prefix $prefix too long for IP address ${address.family}. Max length: ${address.octets.size * 8}" }
    }

    override val netmask: Netmask = prefix.toNetmask(address.family)

    override val isLinkLocal: Boolean get() = this == specialRanges.linkLocal

    override val isLoopback: Boolean get() = this == specialRanges.loopback

    override val isMulticast: Boolean get() = this == specialRanges.multicast

    override fun toString(): String = "$address/$prefix"

    //Ordering by network address, then by netmask length (shorter prefixes come first if addresses equal).
    override fun compareTo(other: IpNetwork<N, Size>): Int {
        //ip addresses are network address, so we can compare those and be good
        val byIp = address.compareTo(other.address)
        if (byIp != 0) return byIp
        return prefix.compareTo(other.prefix)
    }

    protected val lastOctetInBlock get() = (address.octets or hostMask)

    /**
     * The very last address inside this network's [addressSpace].
     * Lazily computed once. Do not mess with its octets!
     */
    val lastAddress: IpAddress<N, Size> by lazy { @Suppress("UNCHECKED_CAST") IpAddress(lastOctetInBlock) as IpAddress<N, Size> }

    /**
     * Creates an [IpInterface] associated with this exact IpNetwork instance, avoiding the creation of new [IpNetwork] instances
     */
    fun interfaceFor(address: IpAddress<N, Size>): IpInterface<N, Size> = IpInterface.unsafe(this, address, prefix)

    /**
     * Two networks `A` and `B` are adjacent iff either:
     * * `A.lastAddress + 1 == B.address`
     * * `B.lastAddress + 1 == A.address`
     */
    fun isAdjacentTo(other: IpNetwork<N, Size>): Boolean = if (overlaps(other)) false else {
        val a = if (this < other) this else other
        val b = if (this > other) this else other
        when (this) {
            is IpNetwork.V4 -> IpAddress.V4((ULong(a.lastAddress.octets) + 1u).toByteArray(4))
            is IpNetwork.V6 -> IpAddress.V6((Overlong(a.lastAddress.octets) + 1uL).toByteArray())
        } == b.address
    }

    /**
     * For two networks
     * * *A* = *a* / *pA*
     * * *B* = *b* / *pB*
     *
     * *A* is a subnet of *B* iff:
     * * *pA* ≥ *pB*
     * * *a* masked with *pB* == *b*
     */
    fun isSubnetOf(other: IpNetwork<N, Size>): Boolean =
        ((prefix >= other.prefix) && address.copy().apply { mask(other.prefix) } == other.address)

    /**
     * For two networks
     * * *A* = *a* / *pA*
     * * *B* = *b* / *pB*
     *
     * *A* is a supernet of *B* iff:
     * * *pA* ≤ *pB*
     * * b masked with *pA* == *a*
     */
    fun isSupernetOf(other: IpNetwork<N, Size>): Boolean =
        ((prefix <= other.prefix) && other.address.copy().apply { mask(prefix) } == address)


    /**
     * Two networks overlap if either contains the other
     */
    fun overlaps(other: IpNetwork<N, Size>): Boolean = other.contains(this) or contains(other)

    fun subnet(newPrefix: UInt): Sequence<IpNetwork<N, Size>> = TODO("maybe implement separately for V4 and V6?")
    fun subnetRelative(prefixDiff: UInt): Sequence<IpNetwork<N, Size>> =
        TODO("maybe implement separately for V4 and V6?")

    /*
    TODO later
    fun supernet(newPrefix: UInt): IpNetwork<N, T>? = TODO("maybe implement separately for V4 and V6?")
    fun supernetRelative(prefixDiff: UInt): IpNetwork<N, T>? = TODO("maybe implement separately for V4 and V6?")
    */

    /**
     * Tries to merge this network with an[other]. This will fail and return `null` unless the following conditions are met:
     * * Same [prefix]
     * * The [other] network is adjacent to this network
     * * Masking the lower network’s [address] with the [prefix] of what would be the merged supernet must yield itself
     *
     * @return the resulting supernet with [prefix]` - 1` if merging is possible, or `null` otherwise.
     */
    operator fun plus(other: IpNetwork<N, Size>): IpNetwork<N, Size>? {
        if (!canMergeWith(other)) return null
        val lowerNetwork = if (this < other) this else other
        val newPrefix = this.prefix - 1u
        @Suppress("UNCHECKED_CAST")
        return IpNetwork(lowerNetwork.address.copy(), newPrefix)
    }

    /**
     * Checks if this network can be merged with an[other]. Merging is possible, iff the following conditions are met:
     * * Same [prefix]
     * * The [other] network is adjacent to this network
     * * Masking the lower network’s [address] with the [prefix] of what would be the merged supernet must yield itself
     */
    fun canMergeWith(other: IpNetwork<N, Size>): Boolean {
        if (prefix == 0u) return false
        if (this.prefix != other.prefix) return false
        if (!this.isAdjacentTo(other)) return false
        val lowerNetwork = if (this < other) this else other
        val newPrefix = prefix - 1u
        val maskedLower = lowerNetwork.address.copy().apply { mask(newPrefix) }
        return maskedLower == lowerNetwork.address
    }


    /**
     * Assignable range of hosts for this network. For IPv6 this includes the network's router-subnet anycast [address].
     * **For IPv4, this is generally NOT the full address range, but excludes this network's [address] and the last address in the block**,
     * which can instead be obtained through [addressSpace] and contains only bare addresses without a netmask.
     *
     * @return a fresh sequence of newly allocated [IpInterface]s every access
     */
    val assignableHostRange: Sequence<IpInterface<N, Size>>
        get() = sequence {
            when (prefix) {
                address.family.numberOfBits.toUInt() -> yield(interfaceFor(address.copy()))
                address.family.numberOfBits.toUInt() - 1u -> {
                    yield(interfaceFor(address.copy()))
                    yield(interfaceFor(IpAddress(lastOctetInBlock) as IpAddress<N, Size>))
                }

                else -> {
                    val seq = addressSpaceUntil(if (family == IpAddress.V4) 1 else 0)
                    seq.drop(1).forEach {
                        yield(interfaceFor(it as IpAddress<N, Size>))
                    }
                }
            }
        }

    /**
     * The **whole** range of this network, including the network [address] itself and the last address in the block.
     * **For IPv4, this is generally NOT the range of assignable addresses**, which can be obtained through [assignableHostRange]!
     *
     * @return a fresh sequence og newly allocated [IpAddress]es on every access
     */
    val addressSpace: Sequence<IpAddress<N, Size>> = addressSpaceUntil(0)

    protected abstract fun addressSpaceUntil(excludingLastN: Int): Sequence<IpAddress<N, Size>>

    /**
     * First assignable host (this [address]/[prefix] for a full netmask and point-to-point).
     */
    val firstAssignableHost: IpInterface<N, Size> by lazy {
        when (prefix) {
            address.family.numberOfBits.toUInt(), address.family.numberOfBits.toUInt() - 1u -> address.copy()
            else -> when (this) {
                is IpNetwork.V4 -> IpAddress.V4((UInt(address.octets) + 1u).toFourBytes())
                is IpNetwork.V6 -> address.copy()
            }
        }.run { interfaceFor(this as IpAddress<N, Size>) }
    }

    /**
     * Last assignable host (this corresponds to the last address in the [addressSpace] for a full netmask and point-to-point).
     */
    val lastAssignableHost: IpInterface<N, Size> by lazy {
        when (prefix) {
            address.family.numberOfBits.toUInt() -> address.copy()
            address.family.numberOfBits.toUInt() - 1u -> IpAddress(lastOctetInBlock)
            else -> when (this) {
                is IpNetwork.V4 -> IpAddress.V4((UInt(lastOctetInBlock) - 1u).toFourBytes())
                is IpNetwork.V6 -> IpAddress.V6(lastOctetInBlock)
            }
        }.run { interfaceFor(this as IpAddress<N, Size>) }
    }
    abstract val size: Size


    /** Tests if [address] is inside this network. This network's address is, by definition, inside the network, as is the broadcast address.*/
    fun contains(address: IpAddress<N, Size>): Boolean = (address.octets and netmask) contentEquals this.address.octets

    /** Tests if [ipInterface] belongs this network. This network's address is, by definition, inside the network, as is the broadcast address.*/
    fun contains(ipInterface: IpInterface<N, Size>): Boolean = ipInterface.network == this

    /**Tests if [network] is fully contained inside this network.*/
    fun contains(network: IpNetwork<N, Size>): Boolean {
        if (prefix > network.prefix) return false
        return address.octets contentEquals (network.address.octets and netmask)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IpNetwork<*, *>) return false

        if (prefix != other.prefix) return false
        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int {
        var result = prefix.hashCode()
        result = 31 * result + address.hashCode()
        return result
    }

    //lazy breaks inti cycle
    @Suppress("UNCHECKED_CAST")
    val specialRanges: SpecialRanges<N, Size> by lazy {
        when (address) {
            is IpAddress.V4 -> V4.specialRanges
            is IpAddress.V6 -> V6.specialRanges
        } as SpecialRanges<N, Size>
    }

    class V4 internal constructor(address: IpAddress.V4, prefix: Prefix, strict: Boolean, deepCopy: Boolean) :

        IpNetwork<Byte, ULong>(address, prefix, strict, deepCopy), IpAddressAndPrefix.V4 {
        /**
         * Note that [address] will be deep-copied into [IpNetwork.address], so the passed reference won't be touched
         */
        constructor(address: IpAddress.V4, prefix: Prefix, strict: Boolean = true) : this(
            address,
            prefix,
            strict,
            deepCopy = true
        )

        override val address: IpAddress.V4 = address.toNetWorkAddress(deepCopy, netmask, strict) as IpAddress.V4

        override val size: ULong by lazy { 1uL shl (address.family.numberOfBits - prefix.toInt()) }

        /**
         * IPv4 broadcast address. `null` for RFC 3021 point-to-point networks (i.e, `/31`) and single-address ( `/32`) networks.
         */
        val broadcastAddress: IpInterface.V4?
            get() = (if (prefix < 31u) interfaceFor(
                IpAddress.V4(
                    lastOctetInBlock
                )
            ) else null) as IpInterface.V4?

        override fun addressSpaceUntil(excludingLastN: Int): Sequence<IpAddress.V4> = sequence {
            if (prefix == family.numberOfBits.toUInt()) yield(address.copy() as IpAddress.V4)
            var current = ULong(address.octets)
            val last = ULong(lastOctetInBlock) - excludingLastN.toUInt()
            while (current <= last) {
                yield(IpAddress.V4(current.toByteArray(4)))
                current++
            }
        }

        companion object : Specification<Byte, ULong> {
            @Throws(IllegalArgumentException::class)
            operator fun invoke(stringRepresentation: String, strict: Boolean = true): V4 {
                val network = IpNetwork(stringRepresentation, strict)
                require(network is V4) { "Network is not a V6 address: $stringRepresentation" }
                return network
            }

            /**
             * Creates a network **without** deep-copying the passed [address]. Note that this always normalizes the network address in-place. I.e., it directly modifies the address's octets.
             * Hence, it is always legal to pass an address that is **not** the network address but an address within the network, as it is transformed into the resulting network's address based the specified [prefix].
             *
             * @throws IllegalArgumentException in case the specified [prefix] is too long
             */
            @Throws(IllegalArgumentException::class)
            fun forAddress(address: IpAddress.V4, prefix: Prefix): V4 {
                val network = IpNetwork(address, prefix)
                require(network is V4) { "Network is not a V6 address: $address" }
                return network
            }

            //lazy prevents initializationexception when constructor throws
            override val specialRanges: IpNetwork.SpecialRanges<Byte, ULong> by lazy { IpNetwork.V4.SpecialRanges }
        }

        override val isPrivate: Boolean get() = IpNetwork.V4.SpecialRanges.private.contains(this)

        override val isPublic: Boolean get() = !(isPrivate || isLinkLocal || isMulticast || isLoopback)

        object SpecialRanges : IpNetwork.SpecialRanges<Byte, ULong> {
            /**`127.0.0.0/8`*/
            override val loopback = V4("127.0.0.0/8")

            /**`169.254.0.0/16`*/
            override val linkLocal = V4("169.254.0.0/16")

            /**`224.0.0.0/4`*/
            override val multicast = V4("224.0.0.0/4")

            /**
             * * `10.0.0.0/8`
             * * `172.16.0.0/12`
             * * `192.168.0.0/16`
             */
            val private: List<IpNetwork.V4> = listOf(
                IpNetwork.V4("10.0.0.0/8"),
                IpNetwork.V4("172.16.0.0/12"),
                IpNetwork.V4("192.168.0.0/16"),
            )
        }

    }

    class V6 internal constructor(address: IpAddress.V6, prefix: Prefix, strict: Boolean, deepCopy: Boolean) :
        IpNetwork<Short, Overlong>(address, prefix, strict, deepCopy), IpAddressAndPrefix.V6 {

        /**
         * Note that [address] will be deep-copied into [at.asitplus.cidre.IpNetwork.address], so the passed reference won't be touched
         */
        constructor(address: IpAddress.V6, prefix: Prefix, strict: Boolean = true) : this(
            address,
            prefix,
            strict,
            deepCopy = true
        )

        override val address: IpAddress.V6 = address.toNetWorkAddress(deepCopy, netmask, strict) as IpAddress.V6

        override fun addressSpaceUntil(excludingLastN: Int): Sequence<IpAddress.V6> = sequence {
            if (prefix == family.numberOfBits.toUInt()) yield(address.copy() as IpAddress.V6)
            var current = Overlong(address.octets)
            val toExclude = excludingLastN.toULong()
            val last = Overlong(lastOctetInBlock) - toExclude
            while (current <= last) {
                yield(IpAddress.V6(current.toByteArray()))
                current++
            }
        }

        override val size: Overlong by lazy { Overlong(1uL) shl address.family.numberOfBits - prefix.toInt() }

        companion object : Specification<Short, Overlong> {
            @Throws(IllegalArgumentException::class)
            operator fun invoke(stringRepresentation: String, strict: Boolean = true): V6 {
                val network = IpNetwork(stringRepresentation, strict)
                require(network is V6) { "Network is not a V6 address: $stringRepresentation" }
                return network
            }

            /**
             * Creates a network **without** deep-copying the passed [address]. Note that this always normalizes the network address in-place. I.e., it directly modifies the address's octets.
             * Hence, it is always legal to pass an address that is **not** the network address but an address within the network, as it is transformed into the resulting network's address based the specified [prefix].
             *
             * @throws IllegalArgumentException in case the specified [prefix] is too long
             */
            @Throws(IllegalArgumentException::class)
            fun forAddress(address: IpAddress.V6, prefix: Prefix): V6 {
                val network = IpNetwork(address, prefix)
                require(network is V6) { "Network is not a V6 address: $address" }
                return network
            }

            //lazy prevents initializationexception when constructor throws
            override val specialRanges: IpNetwork.SpecialRanges<Short, Overlong> by lazy { IpNetwork.V6.SpecialRanges }
        }


        override val isGlobalUnicast: Boolean get() = this == IpNetwork.V6.SpecialRanges.globalUnicast

        override val isUniqueLocal: Boolean get() = this == IpNetwork.V6.SpecialRanges.uniqueLocal

        override val isUniqueLocalLocallyAssigned: Boolean get() = this == IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned

        override val isIpV4Mapped: Boolean get() = this == IpNetwork.V6.SpecialRanges.ipV4Mapped

        @Deprecated("Originally meant to embed IPv4, now obsolete")
        override val isIpV4Compatible: Boolean get() = this == IpNetwork.V6.SpecialRanges.ipV4Compatible

        override val isDocumentation: Boolean get() = this == IpNetwork.V6.SpecialRanges.documentation

        override val isDiscardOnly: Boolean get() = this == IpNetwork.V6.SpecialRanges.discardOnly

        override val isReserved: Boolean get() = this == IpNetwork.V6.SpecialRanges.reserved

        object SpecialRanges : IpNetwork.SpecialRanges<Short, Overlong> {

            /**`::1/128`*/
            override val loopback = V6("::1/128")

            /**`fe80::/10`*/
            override val linkLocal = V6("fe80::/10")

            /**`ff00::/8`*/
            override val multicast = V6("ff00::/8")

            /**`2000::/3`*/
            val globalUnicast = V6("2000::/3")

            /**`fc00::/7`*/
            val uniqueLocal = V6("fc00::/7")

            /**`fd00::/8`*/
            val uniqueLocalLocallyAssigned = V6("fd00::/8")

            /**`::ffff:0:0/96`*/
            val ipV4Mapped = V6("::ffff:0:0/96")

            /**`::/96`*/
            @Deprecated("Originally meant to embed IPv4, now obsolete")
            val ipV4Compatible = V6("::/96")

            /**
             * `2001:db8::/32`.
             * This address range is reserved for documentation illustrations and examples.
             * */
            val documentation = V6("2001:db8::/32")

            /**
             * `100::/64`.
             * Packets to this address range are dropped.
             * */
            val discardOnly = V6("100::/64")

            /**Reserved for future use*/
            val reserved: List<IpNetwork.V6> = IntRange(0x4000, 0x7fff).map { V6(it.toString(16) + "::/3") }
        }
    }

    sealed interface SpecialRanges<N : Number, Size> {
        val loopback: IpNetwork<N, Size>
        val linkLocal: IpNetwork<N, Size>
        val multicast: IpNetwork<N, Size>
    }

    interface Specification<N : Number, Size> {
        val specialRanges: SpecialRanges<N, Size>
    }

    companion object {

        @Suppress("UNCHECKED_CAST")
        private fun <N : Number, Size> IpAddress<N, Size>.toNetWorkAddress(
            deepCopy: Boolean,
            netmask: Netmask,
            strict: Boolean
        ): IpAddress<N, Size> = if (deepCopy) IpAddress(octets.copyOf()).apply { mask(netmask) }.also {
            if (strict) require(it == this) { "$this is not an actual network address. Should be: $it" }
        } as IpAddress<N, Size>
        else {
            val changedBits = mask(netmask)
            if (strict) require(changedBits == 0) { "Implementation error in address-into-network wrapping. Report this bug here: https://github.com/a-sit-plus/cidre/issues/new" }
            this
        }

        /**
         * Note that [address] will be deep-copied into [at.asitplus.cidre.IpNetwork.address], so the passed reference won't be touched
         * @throws IllegalArgumentException in case the specified [prefix] is too long or if [strict] = `true` and the passed address is not the designated network's address.
         */
        @Throws(IllegalArgumentException::class)
        operator fun invoke(stringRepresentation: String, strict: Boolean = true): IpNetwork<*, *> {
            val (addr, prefix) = parseIpAndPrefix(stringRepresentation)

            return when (addr) {
                is IpAddress.V4 -> V4(addr, prefix, strict)
                is IpAddress.V6 -> V6(addr, prefix, strict)
            }
        }

        /**
         * Note that [address] will be deep-copied into [at.asitplus.cidre.IpNetwork.address], so the passed reference won't be touched
         * @throws IllegalArgumentException in case the specified [prefix] is too long
         */
        @Throws(IllegalArgumentException::class)
        @Suppress("UNCHECKED_CAST")
        operator fun <N : Number, Size> invoke(
            address: IpAddress<N, Size>,
            prefix: Prefix,
            strict: Boolean = true
        ): IpNetwork<N, Size> = when (address) {
            is IpAddress.V4 -> V4(address, prefix, strict) as IpNetwork<N, Size>
            is IpAddress.V6 -> V6(address, prefix, strict) as IpNetwork<N, Size>
        }

        /**
         * Creates a network **without** deep-copying the passed [address]. Note that this always normalizes the network address in-place. I.e., it directly modifies the address's octets.
         * Hence, it is always legal to pass an address that is **not** the network address but an address within the network, as it is transformed into the resulting network's address based the specified [prefix].
         *
         * @throws IllegalArgumentException in case the specified [prefix] is too long
         */
        @Throws(IllegalArgumentException::class)
        fun <N : Number, Size> forAddress(
            address: IpAddress<N, Size>,
            prefix: Prefix
        ): IpNetwork<N, Size> =
            when (address) {
                is IpAddress.V4 -> IpNetwork.V4(
                    address,
                    prefix,
                    strict = false,
                    deepCopy = false
                ) as IpNetwork<N, Size>

                is IpAddress.V6 -> IpNetwork.V6(
                    address,
                    prefix,
                    strict = false,
                    deepCopy = false
                ) as IpNetwork<N, Size>
            }

    }


}


