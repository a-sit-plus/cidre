package at.asitplus.cidre

import at.asitplus.cidre.IpAddressAndPrefix.Companion.parseX509Octets
import at.asitplus.cidre.byteops.CidrNumber
import at.asitplus.cidre.byteops.and
import at.asitplus.cidre.byteops.or
import at.asitplus.cidre.byteops.toNetmask


sealed class IpNetwork<N : Number, S : CidrNumber<S>>
@Throws(IllegalArgumentException::class)
constructor(address: IpAddress<N, S>, override val prefix: Prefix, strict: Boolean, deepCopy: Boolean) :
    IpAddressAndPrefix<N, S>,
    Comparable<IpNetwork<N, S>> {

    init {
        require(prefix <= address.octets.size.toUInt() * 8u) { "Prefix $prefix too long for IP address ${address.family}. Max length: ${address.octets.size * 8}" }
    }

    override val netmask: Netmask = prefix.toNetmask(address.family)

    override val isLinkLocal: Boolean get() = specialRanges.linkLocal.contains(this)

    override val isLoopback: Boolean get() = specialRanges.loopback.contains(this)

    override val isMulticast: Boolean get() = specialRanges.multicast.contains(this)

    enum class Relation {
        EQUAL,
        CONTAINS,
        WITHIN,
        ADJACENT,
        DISJOINT
    }

    override fun toString(): String = "$address/$prefix"

    //Ordering by network address, then by netmask length (shorter prefixes come first if addresses equal).
    override fun compareTo(other: IpNetwork<N, S>): Int {
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
    val lastAddress: IpAddress<N, S> by lazy { @Suppress("UNCHECKED_CAST") IpAddress(lastOctetInBlock) as IpAddress<N, S> }

    /**
     * Inclusive range boundaries (first and last address) covered by this network.
     */
    fun toRange(): Pair<IpAddress<N, S>, IpAddress<N, S>> = address.copy() to lastAddress.copy()

    /**
     * Creates an [IpInterface] associated with this exact IpNetwork instance, avoiding the creation of new [IpNetwork] instances
     */
    fun interfaceFor(address: IpAddress<N, S>): IpInterface<N, S> = IpInterface.unsafe(this, address, prefix)

    /**
     * Two networks `A` and `B` are adjacent iff either:
     * * `A.lastAddress + 1 == B.address`
     * * `B.lastAddress + 1 == A.address`
     */
    fun isAdjacentTo(other: IpNetwork<N, S>): Boolean = if (overlaps(other)) false else {
        val a = if (this < other) this else other
        val b = if (this > other) this else other
        ((a.lastAddress.toCidrNumber() + 1u) ?: false) == b.address.toCidrNumber()
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
    fun isSubnetOf(other: IpNetwork<N, S>): Boolean =
        ((requireSameFamily(other).let { prefix >= other.prefix }) && address.copy().apply { mask(other.prefix) } == other.address)

    /**
     * For two networks
     * * *A* = *a* / *pA*
     * * *B* = *b* / *pB*
     *
     * *A* is a supernet of *B* iff:
     * * *pA* ≤ *pB*
     * * b masked with *pA* == *a*
     */
    fun isSupernetOf(other: IpNetwork<N, S>): Boolean =
        ((requireSameFamily(other).let { prefix <= other.prefix }) && other.address.copy().apply { mask(prefix) } == address)


    /**
     * Two networks overlap if either contains the other
     */
    fun overlaps(other: IpNetwork<N, S>): Boolean {
        requireSameFamily(other)
        return other.contains(this) or contains(other)
    }

    /**
     * Union reduced by containment/adjacency collapse.
     * Returns one or two CIDRs.
     */
    fun unionCollapse(other: IpNetwork<N, S>): List<IpNetwork<N, S>> {
        requireSameFamily(other)
        return when {
        contains(other) -> listOf(this)
        other.contains(this) -> listOf(other)
        canMergeWith(other) -> listOf((this + other)!!)
        else -> listOf(this, other).sorted()
    }
    }

    /**
     * Minimal CIDR covering of the full address interval from the lower network start to the higher network end.
     * This may include addresses not present in either input network.
     */
    fun unionCovering(other: IpNetwork<N, S>): List<IpNetwork<N, S>> {
        requireSameFamily(other)
        if (contains(other)) return listOf(this)
        if (other.contains(this)) return listOf(other)
        val lower = if (this <= other) this else other
        val upper = if (this >= other) this else other
        return canonicalizeNetworks(summarizeAddressInterval(lower.address.toCidrNumber(), upper.lastAddress.toCidrNumber()))
    }

    /**
     * Intersection of two networks (0 or 1 CIDR for proper CIDR-aligned inputs).
     */
    fun intersection(other: IpNetwork<N, S>): List<IpNetwork<N, S>> {
        requireSameFamily(other)
        return canonicalizeNetworks(when {
        !overlaps(other) -> emptyList()
        contains(other) -> listOf(other)
        other.contains(this) -> listOf(this)
        else -> emptyList()
    })
    }

    /**
     * CIDR difference (this minus [other]).
     */
    fun difference(other: IpNetwork<N, S>): List<IpNetwork<N, S>> {
        requireSameFamily(other)
        if (!overlaps(other)) return listOf(this)
        if (other.contains(this)) return emptyList()
        if (contains(other).not()) return listOf(this)

        // this contains other: recursively split this until overlapping portions are isolated.
        val children = subnetRelative(1u).toList()
        val out = mutableListOf<IpNetwork<N, S>>()
        children.forEach { child ->
            if (child.overlaps(other)) out += child.difference(other)
            else out += child
        }
        return canonicalizeNetworks(out)
    }

    /**
     * Classification of this network's relation to [other].
     */
    fun relationTo(other: IpNetwork<N, S>): Relation {
        requireSameFamily(other)
        return when {
        this == other -> Relation.EQUAL
        contains(other) -> Relation.CONTAINS
        other.contains(this) -> Relation.WITHIN
        isAdjacentTo(other) -> Relation.ADJACENT
        else -> Relation.DISJOINT
    }
    }

    @Suppress("UNCHECKED_CAST")
    private fun summarizeAddressInterval(start: S, end: S): List<IpNetwork<N, S>> {
        val result = mutableListOf<IpNetwork<N, S>>()
        val maxPrefix = family.numberOfBits
        var current = start

        while (current <= end) {
            val currentAddress = IpAddress(current) as IpAddress<N, S>
            var bestPrefix = maxPrefix.toUInt()
            var p = maxPrefix - 1
            while (p >= 0) {
                val candidatePrefix = p.toUInt()
                val candidate = IpNetwork(currentAddress.copy(), candidatePrefix, strict = false)
                val aligned = candidate.address == currentAddress
                if (!aligned) break
                if (candidate.lastAddress.toCidrNumber() > end) break
                bestPrefix = candidatePrefix
                p--
            }

            val chosen = IpNetwork(currentAddress.copy(), bestPrefix, strict = false)
            result += chosen
            val next = chosen.lastAddress.toCidrNumber() + 1u
            if (next == null) break
            current = next
        }
        return result
    }

    /**
     * Enumerates subnets of this network at [newPrefix].
     *
     * @throws IllegalArgumentException if [newPrefix] is not strictly longer than [prefix]
     * or exceeds the address family's max prefix length.
     */
    @Suppress("UNCHECKED_CAST")
    fun subnet(newPrefix: UInt): Sequence<IpNetwork<N, S>> {
        val maxPrefix = family.numberOfBits.toUInt()
        require(newPrefix > prefix) {
            "newPrefix ($newPrefix) must be greater than current prefix ($prefix)"
        }
        require(newPrefix <= maxPrefix) {
            "newPrefix ($newPrefix) exceeds max prefix length ($maxPrefix) for $family"
        }

        val step = when (this) {
            is IpNetwork.V4 -> (CidrNumber.V4.ONE shl (IpAddress.V4.numberOfBits - newPrefix.toInt())) as S
            is IpNetwork.V6 -> (CidrNumber.V6.ONE shl (IpAddress.V6.numberOfBits - newPrefix.toInt())) as S
        }

        return sequence {
            var current = address.toCidrNumber()
            val end = lastAddress.toCidrNumber()
            while (current <= end) {
                val childAddress = IpAddress(current) as IpAddress<N, S>
                yield(IpNetwork(childAddress, newPrefix))
                current = (current + step) ?: break
            }
        }
    }

    /**
     * Enumerates subnets of this network by extending [prefix] by [prefixDiff] bits.
     *
     * @throws IllegalArgumentException if [prefixDiff] is zero or results in an invalid prefix.
     */
    fun subnetRelative(prefixDiff: UInt): Sequence<IpNetwork<N, S>> {
        require(prefixDiff > 0u) { "prefixDiff must be > 0" }
        val maxPrefix = family.numberOfBits.toUInt()
        val newPrefix = prefix + prefixDiff
        require(newPrefix <= maxPrefix) {
            "Resulting prefix ($newPrefix) exceeds max prefix length ($maxPrefix) for $family"
        }
        return subnet(newPrefix)
    }

    /**
     * Computes the supernet containing this network at [newPrefix].
     *
     * @throws IllegalArgumentException if [newPrefix] is not strictly shorter than [prefix].
     */
    fun supernet(newPrefix: UInt): IpNetwork<N, S> {
        require(newPrefix < prefix) {
            "newPrefix ($newPrefix) must be less than current prefix ($prefix)"
        }
        return IpNetwork(address.copy(), newPrefix, strict = false)
    }

    /**
     * Computes the supernet containing this network by shortening [prefix] by [prefixDiff] bits.
     *
     * @throws IllegalArgumentException if [prefixDiff] is zero or larger than [prefix].
     */
    fun supernetRelative(prefixDiff: UInt): IpNetwork<N, S> {
        require(prefixDiff > 0u) { "prefixDiff must be > 0" }
        require(prefixDiff <= prefix) {
            "prefixDiff ($prefixDiff) is larger than current prefix ($prefix)"
        }
        return supernet(prefix - prefixDiff)
    }

    /**
     * Tries to merge this network with an[other]. This will fail and return `null` unless the following conditions are met:
     * * Same [prefix]
     * * The [other] network is adjacent to this network
     * * Masking the lower network’s [address] with the [prefix] of what would be the merged supernet must yield itself
     *
     * @return the resulting supernet with [prefix]` - 1` if merging is possible, or `null` otherwise.
     */
    operator fun plus(other: IpNetwork<N, S>): IpNetwork<N, S>? {
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
    fun canMergeWith(other: IpNetwork<N, S>): Boolean {
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
    val assignableHostRange: Sequence<IpInterface<N, S>>
        get() = sequence {
            when (prefix) {
                address.family.numberOfBits.toUInt() -> yield(interfaceFor(address.copy()))
                address.family.numberOfBits.toUInt() - 1u -> {
                    yield(interfaceFor(address.copy()))
                    yield(interfaceFor(IpAddress(lastOctetInBlock) as IpAddress<N, S>))
                }

                else -> {
                    val seq = addressSpaceUntil(if (family == IpAddress.V4) 1u else 0u)
                    seq.drop(1).forEach {
                        yield(interfaceFor(it as IpAddress<N, S>))
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
    val addressSpace: Sequence<IpAddress<N, S>> = addressSpaceUntil(0u)

    protected abstract fun addressSpaceUntil(excludingLastN: UInt): Sequence<IpAddress<N, S>>

    /**
     * First assignable host (this [address]/[prefix] for a full netmask and point-to-point).
     */
    val firstAssignableHost: IpInterface<N, S> by lazy {
        when (prefix) {
            address.family.numberOfBits.toUInt(), address.family.numberOfBits.toUInt() - 1u -> address.copy()
            else -> when (this) {
                is IpNetwork.V4 -> address + 1u
                is IpNetwork.V6 -> address.copy()
            }
        }.run { interfaceFor(this as IpAddress<N, S>) }
    }

    /**
     * Last assignable host (this corresponds to the last address in the [addressSpace] for a full netmask and point-to-point).
     */
    val lastAssignableHost: IpInterface<N, S> by lazy {
        when (prefix) {
            address.family.numberOfBits.toUInt() -> address.copy()
            address.family.numberOfBits.toUInt() - 1u -> IpAddress(lastOctetInBlock)
            else -> when (this) {
                is IpNetwork.V4 -> (CidrNumber(lastOctetInBlock) - 1u).let {
                    require( it !=null){"$address should be in range of 0-$it, $prefix"}
                    IpAddress(it)
                }
                is IpNetwork.V6 -> IpAddress.V6(lastOctetInBlock)
            }
        }.run { interfaceFor(this as IpAddress<N, S>) }
    }
    abstract val size: S


    /** Tests if [address] is inside this network. This network's address is, by definition, inside the network, as is the broadcast address.*/
    operator fun contains(address: IpAddress<N, S>): Boolean {
        require(family == address.family) { "IP family mismatch: $family vs ${address.family}" }
        return (address.octets and netmask) contentEquals this.address.octets
    }

    /** Tests if [ipInterface] belongs this network. This network's address is, by definition, inside the network, as is the broadcast address.*/
    operator fun contains(ipInterface: IpInterface<N, S>): Boolean =
        ipInterface.network.family == family && ipInterface.address.family == family && ipInterface.network == this && contains(ipInterface.address)

    /**Tests if [network] is fully contained inside this network.*/
    operator fun contains(network: IpNetwork<N, S>): Boolean {
        requireSameFamily(network)
        if (prefix > network.prefix) return false
        return address.octets contentEquals (network.address.octets and netmask)
    }

    private fun requireSameFamily(other: IpNetwork<*, *>) {
        require(family == other.family) { "IP family mismatch: $family vs ${other.family}" }
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
    val specialRanges: SpecialRanges<N, S> by lazy {
        when (address) {
            is IpAddress.V4 -> V4.specialRanges
            is IpAddress.V6 -> V6.specialRanges
        } as SpecialRanges<N, S>
    }

    class V4 internal constructor(address: IpAddress.V4, prefix: Prefix, strict: Boolean, deepCopy: Boolean) :

        IpNetwork<Byte, CidrNumber.V4>(address, prefix, strict, deepCopy), IpAddressAndPrefix.V4 {
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

        override val size: CidrNumber.V4 by lazy { CidrNumber.V4(1uL shl (address.family.numberOfBits - prefix.toInt())) }

        /**
         * IPv4 broadcast address. `null` for RFC 3021 point-to-point networks (i.e, `/31`) and single-address ( `/32`) networks.
         */
        val broadcastAddress: IpInterface.V4?
            get() = (if (prefix < 31u) interfaceFor(
                IpAddress.V4(
                    lastOctetInBlock
                )
            ) else null) as IpInterface.V4?

        override fun addressSpaceUntil(excludingLastN: UInt): Sequence<IpAddress.V4> = sequence {
            if (prefix == family.numberOfBits.toUInt()) {
                yield(address.copy() as IpAddress.V4)
                return@sequence
            }
            var current = CidrNumber.V4(address.octets)
            val last = CidrNumber.V4(lastOctetInBlock) - excludingLastN
            assert(last != null, "0xBADCAB")
            while (current <= last) {
                yield(IpAddress.V4(current))
                current = (current + 1u) ?: throw ImplementationError("0x5ADCAB")
            }
        }

        companion object : Specification<Byte, CidrNumber.V4> {
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
            override val specialRanges: IpNetwork.SpecialRanges<Byte, CidrNumber.V4> get() = IpNetwork.V4.SpecialRanges
            override val family: IpFamily get() = IpFamily.V4
        }

        override val isPrivate: Boolean get() = IpNetwork.V4.SpecialRanges.private.any { it.contains(this) }

        override val isPublic: Boolean get() = !(isPrivate || isLinkLocal || isMulticast || isLoopback)

        object SpecialRanges : IpNetwork.SpecialRanges<Byte, CidrNumber.V4> {
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
        IpNetwork<Short, CidrNumber.V6>(address, prefix, strict, deepCopy), IpAddressAndPrefix.V6 {

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

        override fun addressSpaceUntil(excludingLastN: UInt): Sequence<IpAddress.V6> = sequence {
            if (prefix == family.numberOfBits.toUInt()) {
                yield(address.copy() as IpAddress.V6)
                return@sequence
            }
            var current = CidrNumber.V6(address.octets)
            val toExclude = excludingLastN.toULong()
            val last = CidrNumber.V6(lastOctetInBlock) - toExclude
            assert(last != null, "0xBADCAB1E")
            while (current <= last) {
                yield(IpAddress.V6(current))
                current = (current + 1u) ?: throw ImplementationError("0x5ADCAB1E")
            }
        }

        override val size: CidrNumber.V6 by lazy { CidrNumber.V6(1uL) shl address.family.numberOfBits - prefix.toInt() }

        companion object : Specification<Short, CidrNumber.V6> {
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
            override val specialRanges: IpNetwork.V6.SpecialRanges get() = IpNetwork.V6.SpecialRanges

            override val family: IpFamily get() = IpFamily.V6
        }


        override val isGlobalUnicast: Boolean get() = IpNetwork.V6.SpecialRanges.globalUnicast.contains(this) && !isDocumentation

        override val isUniqueLocal: Boolean get() = IpNetwork.V6.SpecialRanges.uniqueLocal.contains(this)

        override val isUniqueLocalLocallyAssigned: Boolean
            get() = IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned.contains(this)

        override val isIpV4Mapped: Boolean get() = IpNetwork.V6.SpecialRanges.ipV4Mapped.contains(this)

        @Deprecated("Originally meant to embed IPv4, now obsolete")
        override val isIpV4Compatible: Boolean get() = IpNetwork.V6.SpecialRanges.ipV4Compatible.contains(this)

        override val isDocumentation: Boolean get() = IpNetwork.V6.SpecialRanges.documentation.contains(this)

        override val isDiscardOnly: Boolean get() = IpNetwork.V6.SpecialRanges.discardOnly.contains(this)

        override val isReserved: Boolean get() = IpNetwork.V6.SpecialRanges.reserved.any { it.contains(this) }

        object SpecialRanges : IpNetwork.SpecialRanges<Short, CidrNumber.V6> {

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
            val reserved: List<IpNetwork.V6> = listOf(
                V6("4000::/3"),
                V6("6000::/3"),
            )
        }
    }

    sealed interface SpecialRanges<N : Number, S : CidrNumber<S>> {
        val loopback: IpNetwork<N, S>
        val linkLocal: IpNetwork<N, S>
        val multicast: IpNetwork<N, S>
    }

    interface Specification<N : Number, S : CidrNumber<S>> {
        val specialRanges: SpecialRanges<N, S>
        val family: IpFamily
    }

    companion object {
        /**
         * Produces a canonical CIDR summary that exactly covers the inclusive address range [start]..[end].
         */
        fun <N : Number, S : CidrNumber<S>> fromRange(start: IpAddress<N, S>, end: IpAddress<N, S>): List<IpNetwork<N, S>> {
            require(start.family == end.family) {
                "Range endpoints must have same IP family: ${start.family} vs ${end.family}"
            }
            require(start <= end) { "Range start must be <= end: $start > $end" }
            val seed = IpNetwork(start.copy(), start.family.numberOfBits.toUInt(), strict = false)
            return canonicalizeNetworks(seed.summarizeAddressInterval(start.toCidrNumber(), end.toCidrNumber()))
        }

        private fun <N : Number, S : CidrNumber<S>> canonicalizeNetworks(networks: List<IpNetwork<N, S>>): List<IpNetwork<N, S>> {
            if (networks.size < 2) return networks
            val sorted = networks.sorted()
            val out = mutableListOf<IpNetwork<N, S>>()
            sorted.forEach { candidate ->
                out += candidate
                while (out.size >= 2) {
                    val right = out.removeAt(out.lastIndex)
                    val left = out.removeAt(out.lastIndex)
                    val merged: IpNetwork<N, S>? = when {
                        left.contains(right) -> left
                        right.contains(left) -> right
                        left.canMergeWith(right) -> left + right
                        else -> null
                    }
                    if (merged != null) out += merged
                    else {
                        out += left
                        out += right
                        break
                    }
                }
            }
            return out
        }

        @Suppress("UNCHECKED_CAST")
        private fun <N : Number, S : CidrNumber<S>> IpAddress<N, S>.toNetWorkAddress(
            deepCopy: Boolean,
            netmask: Netmask,
            strict: Boolean
        ): IpAddress<N, S> = if (deepCopy) IpAddress(octets.copyOf()).apply { mask(netmask) }.also {
            if (strict) require(it == this) { "$this is not an actual network address. Should be: $it" }
        } as IpAddress<N, S>
        else {
            val changedBits = mask(netmask)
            if (strict) assert(changedBits == 0, "0xBADC0DE")
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
        operator fun <N : Number, S : CidrNumber<S>> invoke(
            address: IpAddress<N, S>,
            prefix: Prefix,
            strict: Boolean = true
        ): IpNetwork<N, S> = when (address) {
            is IpAddress.V4 -> V4(address, prefix, strict) as IpNetwork<N, S>
            is IpAddress.V6 -> V6(address, prefix, strict) as IpNetwork<N, S>
        }

        /**
         * Creates a network **without** deep-copying the passed [address]. Note that this always normalizes the network address in-place. I.e., it directly modifies the address's octets.
         * Hence, it is always legal to pass an address that is **not** the network address but an address within the network, as it is transformed into the resulting network's address based the specified [prefix].
         *
         * @throws IllegalArgumentException in case the specified [prefix] is too long
         */
        @Throws(IllegalArgumentException::class)
        fun <N : Number, S : CidrNumber<S>> forAddress(
            address: IpAddress<N, S>,
            prefix: Prefix
        ): IpNetwork<N, S> =
            when (address) {
                is IpAddress.V4 -> IpNetwork.V4(
                    address,
                    prefix,
                    strict = false,
                    deepCopy = false
                ) as IpNetwork<N, S>

                is IpAddress.V6 -> IpNetwork.V6(
                    address,
                    prefix,
                    strict = false,
                    deepCopy = false
                ) as IpNetwork<N, S>
            }

        /**
         * Decodes an IpNetwork from X.509 iPAddressName ByteArray (RFC 5280).
         * IPv4 byte layout: `AAAANNNN`, where `A` is an address octet and `N` is a netmask octet (8 bytes total)
         * IPv6 byte layout:  `AAAAAAAAAAAAAAAANNNNNNNNNNNNNNNN`, where `A` is an address octet and `N` is a netmask octet(32 bytes total)
         */
        fun fromX509Octets(bytes: ByteArray): IpNetwork<*, *> {
            val (addr, prefix) = parseX509Octets(bytes)
            return when (addr) {
                is IpAddress.V4 -> V4(addr, prefix)
                is IpAddress.V6 -> V6(addr, prefix)
            }
        }

    }


}
