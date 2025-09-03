package at.asitplus.cidre

import at.asitplus.cidre.byteops.and
import at.asitplus.cidre.byteops.andInplace
import at.asitplus.cidre.byteops.toNetmask

/**CIDR prefix length*/
typealias Prefix = UInt

/**Network-order (BE) layout of a CIDR prefix*/
typealias Netmask = ByteArray

sealed class IpNetwork<N : Number, T : IpAddress<N>>
@Throws(IllegalArgumentException::class)
constructor(address: T, val prefix: Prefix, strict: Boolean, deepCopy: Boolean) : Comparable<IpNetwork<N, T>> {

    init {
        require(prefix <= address.octets.size.toUInt() * 8u) { "Prefix $prefix too long for IP address ${address.version}. Max length: ${address.octets.size * 8}" }
    }

    val netMask: Netmask = prefix.toNetmask(address.version)

    @Suppress("UNCHECKED_CAST")
    val address: T = if (deepCopy) IpAddress(address.octets and netMask).also {
        if (strict) require(it == address) { "$address is not an actual network address. Should be: $it" }
    } as T
    else {
        val changedBits = address.octets.andInplace(netMask)
        if (strict) require(changedBits == 0) { "Implementation error in address-into-network wrapping. Report this bug here: https://github.com/a-sit-plus/cidre/issues/new" }
        address
    }

    val networkPart: ByteArray by lazy { TODO("the network slice of the address octets") }
    val hostPart: ByteArray by lazy { TODO("the host slice of the address octets") }


    override fun toString(): String = "$address/$prefix"

    //Ordering by network address, then by netmask length (shorter prefixes come first if addresses equal).
    override fun compareTo(other: IpNetwork<N, T>): Int {
        //ip addresses are network address, so we can compare those and be good
        val byIp = address.compareTo(other.address)
        if (byIp != 0) return byIp
        return prefix.compareTo(other.prefix)
    }

    /**
     * Creates an [IpInterface] associated with this exact IpNetwork instance, avoiding the creation of new [IpNetwork] instances
     */
    fun interfaceFor(address: T): IpInterface<N, T> = IpInterface.unsafe(this, address, prefix)

    fun subnet(newPrefix: UInt): Sequence<IpNetwork<N, T>> = TODO("maybe implement separately for V4 and V6?")
    fun subnetRelative(prefixDiff: UInt): Sequence<IpNetwork<N, T>> = TODO("maybe implement separately for V4 and V6?")

    fun isAdjacentTo(other: IpNetwork<N, T>): Boolean = TODO("maybe implement separately for V4 and V6?")

    /*
    TODO later
    fun supernet(newPrefix: UInt): IpNetwork<N, T>? = TODO("maybe implement separately for V4 and V6?")
    fun supernetRelative(prefixDiff: UInt): IpNetwork<N, T>? = TODO("maybe implement separately for V4 and V6?")
    */

    //Aggregation; may fail if disjoint
    abstract operator fun plus(other: T): T?

    val hostRange: Sequence<IpInterface<N, T>> get() = addressSpace.map { IpInterface.unsafe(this, it, prefix) }
    abstract val addressSpace: Sequence<T>

    fun first(): IpInterface<N, T> = TODO()
    fun last(): IpInterface<N, T> = TODO()
    val size: Long get() = TODO()

    fun isSubnetOf(other: IpNetwork<N, T>): Boolean = TODO("maybe implement separately for V4 and V6?")
    fun overlapsWith(other: IpNetwork<N, T>): Boolean = TODO("maybe implement separately for V4 and V6?")

    /** Tests if [address] is inside this network. This network's address is, by definition, inside the network, as is the broadcast address.*/
    fun contains(address: T): Boolean = (address.octets and netMask) contentEquals this.address.octets

    /** Tests if [ipInterface] belongs this network. This network's address is, by definition, inside the network, as is the broadcast address.*/
    fun contains(ipInterface: IpInterface<N, T>): Boolean = ipInterface.network == this

    /**Tests if [network] is fully contained inside this network.*/
    fun contains(network: IpNetwork<N, T>): Boolean {
        if (prefix > network.prefix) return false
        return address.octets contentEquals (network.address.octets and netMask)
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
    val specialRanges: SpecialRanges<N, T> by lazy {
        when (address) {
            is IpAddress.V4 -> V4.specialRanges
            is IpAddress.V6 -> V6.specialRanges
        } as SpecialRanges<N, T>
    }

    class V4 internal constructor(address: IpAddress.V4, prefix: Prefix, strict: Boolean, deepCopy: Boolean) :

        IpNetwork<Byte, IpAddress.V4>(address, prefix, strict, deepCopy) {
        /**
         * Note that [address] will be deep-copied into [at.asitplus.cidre.IpNetwork.address], so the passed reference won't be touched
         */
        constructor(address: IpAddress.V4, prefix: Prefix, strict: Boolean = true) : this(
            address,
            prefix,
            strict,
            deepCopy = true
        )

        val broadcastAddress: IpInterface<Byte, IpAddress.V4> get() = TODO()

        override fun plus(other: IpAddress.V4): IpAddress.V4? = TODO("Not yet implemented")
        override val addressSpace: Sequence<IpAddress.V4> get() = TODO("Not yet implemented")

        companion object : Specification<Byte, IpAddress.V4> {
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
            override val specialRanges: IpNetwork.SpecialRanges<Byte, IpAddress.V4> by lazy { IpNetwork.V4.SpecialRanges }
        }

        object SpecialRanges : IpNetwork.SpecialRanges<Byte, IpAddress.V4> {
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
        IpNetwork<Short, IpAddress.V6>(address, prefix, strict, deepCopy) {

        /**
         * Note that [address] will be deep-copied into [at.asitplus.cidre.IpNetwork.address], so the passed reference won't be touched
         */
        constructor(address: IpAddress.V6, prefix: Prefix, strict: Boolean = true) : this(
            address,
            prefix,
            strict,
            deepCopy = true
        )

        val isGlobalUnicast: Boolean get() = TODO()

        @Deprecated(
            "This is Ipv4 terminology and present here for convenience. If you know, you ware working with IPv6, prefer isUniqueLocal.",
            ReplaceWith("isUniqueLocal")
        )

        override fun plus(other: IpAddress.V6): IpAddress.V6? = TODO("Not yet implemented")
        override val addressSpace: Sequence<IpAddress.V6> get() = TODO("Not yet implemented")

        companion object : Specification<Short, IpAddress.V6> {
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
            override val specialRanges: IpNetwork.SpecialRanges<Short, IpAddress.V6> by lazy { IpNetwork.V6.SpecialRanges }
        }

        object SpecialRanges : IpNetwork.SpecialRanges<Short, IpAddress.V6> {

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

    sealed interface SpecialRanges<N : Number, T : IpAddress<N>> {
        val loopback: IpNetwork<N, T>
        val linkLocal: IpNetwork<N, T>
        val multicast: IpNetwork<N, T>
    }

    interface Specification<N : Number, T : IpAddress<N>> {
        val specialRanges: SpecialRanges<N, T>
    }

    companion object {
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
        operator fun <N : Number> invoke(
            address: IpAddress<N>,
            prefix: Prefix,
            strict: Boolean = true
        ): IpNetwork<N, IpAddress<N>> = when (address) {
            is IpAddress.V4 -> V4(address, prefix, strict) as IpNetwork<N, IpAddress<N>>
            is IpAddress.V6 -> V6(address, prefix, strict) as IpNetwork<N, IpAddress<N>>
        }

        /**
         * Creates a network **without** deep-copying the passed [address]. Note that this always normalizes the network address in-place. I.e., it directly modifies the address's octets.
         * Hence, it is always legal to pass an address that is **not** the network address but an address within the network, as it is transformed into the resulting network's address based the specified [prefix].
         *
         * @throws IllegalArgumentException in case the specified [prefix] is too long
         */
        @Throws(IllegalArgumentException::class)
        fun <N : Number> forAddress(address: IpAddress<N>, prefix: Prefix): IpNetwork<N, IpAddress<N>> =
            when (address) {
                is IpAddress.V4 -> IpNetwork.V4(
                    address,
                    prefix,
                    strict = false,
                    deepCopy = false
                ) as IpNetwork<N, IpAddress<N>>

                is IpAddress.V6 -> IpNetwork.V6(
                    address,
                    prefix,
                    strict = false,
                    deepCopy = false
                ) as IpNetwork<N, IpAddress<N>>
            }

    }


}


