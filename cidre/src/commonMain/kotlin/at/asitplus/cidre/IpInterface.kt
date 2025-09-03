package at.asitplus.cidre

/**
 * An [address] with a [prefix] belonging to a [network]
 */
sealed class IpInterface<N : Number, T : IpAddress<N>>
@Throws(IllegalArgumentException::class)
constructor(val address: T, val prefix: Prefix, val network: IpNetwork<N, T>) {

    /**`true` if this IP interface is part of the [IpNetwork.SpecialRanges.linkLocal] network */
    val isLinkLocal: Boolean get() = network == network.specialRanges.linkLocal

    /**`true` if this IP interface is part of the [IpNetwork.SpecialRanges.loopback] network */
    val isLoopback: Boolean get() = network == network.specialRanges.loopback

    /**`true` if this IP interface is part of the [IpNetwork.SpecialRanges.multicast] network */
    val isMulticast: Boolean get() = network == network.specialRanges.multicast


    override fun toString(): String = "$address/$prefix"

    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun <N : Number, T : IpAddress<N>> unsafe(
            network: IpNetwork<N, T>,
            address: T,
            prefix: Prefix
        ): IpInterface<N, T> = when (address) {
            is IpAddress.V4 -> V4(address, prefix, network as IpNetwork.V4) as IpInterface<N, T>
            is IpAddress.V6 -> V6(address, prefix, network as IpNetwork.V6) as IpInterface<N, T>
        }

        @Suppress("UNCHECKED_CAST")
        operator fun <T : Number> invoke(address: IpAddress<T>, prefix: Prefix): IpInterface<T, IpAddress<T>> =
            when (address) {
                is IpAddress.V4 -> V4(address, prefix) as IpInterface<T, IpAddress<T>>
                is IpAddress.V6 -> V6(address, prefix) as IpInterface<T, IpAddress<T>>
            }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(stringRepresentation: String): IpInterface<*, *> {
            val (addr, prefix) = parseIpAndPrefix(stringRepresentation)
            return IpInterface(addr, prefix)
        }
    }

    class V4 internal constructor(address: IpAddress.V4, prefix: Prefix, network: IpNetwork<Byte, IpAddress.V4>) :
        IpInterface<Byte, IpAddress.V4>(address, prefix, network) {

        constructor(address: IpAddress.V4, prefix: Prefix) : this(
            address,
            prefix,
            IpNetwork.V4(address, prefix, strict = false)
        )

        /**`true` if this IP interface is private, i.e. part of any [IpNetwork.V4.SpecialRanges.private] networks */
        val isPrivate: Boolean get() = IpNetwork.V4.SpecialRanges.private.contains(network)

        /**
         * `true` if this IP interface is public, i.e. *NOT* part of any [IpNetwork.V4.SpecialRanges.private] networks.
         * *This is NOT the opposite of [isPrivate]!*
         * */
        val isPublic: Boolean get() = !(isPrivate || isLinkLocal || isMulticast ||isLoopback)
    }

    class V6(address: IpAddress.V6, prefix: Prefix, network: IpNetwork<Short, IpAddress.V6>) :
        IpInterface<Short, IpAddress.V6>(address, prefix, network) {

        constructor(address: IpAddress.V6, prefix: Prefix) : this(
            address,
            prefix,
            IpNetwork.V6(address, prefix, strict = false)
        )

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.globalUnicast] address range. This is the equivalent of an IPv4 public address.
         * This is the IPv6 equivalent of [IpInterface.V4.isPublic] */
        val isGlobalUnicast: Boolean get() = network == IpNetwork.V6.SpecialRanges.globalUnicast

        /**
         * `true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.uniqueLocal] address range.
         * This range contains both [IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned] and the currently unused `fc00::/8` address range.
         * */
        val isUniqueLocal: Boolean get() = network == IpNetwork.V6.SpecialRanges.uniqueLocal

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned] address range. This is the equivalent of an IPv4 private address. */
        val isUniqueLocalLocallyAssigned: Boolean get() = network == IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.ipV4Mapped] address range. */
        val isIpV4Mapped: Boolean get() = network == IpNetwork.V6.SpecialRanges.ipV4Mapped

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.ipV4Compatible] address range. */
        @Deprecated("Originally meant to embed IPv4, now obsolete")
        val isIpV4Compatible: Boolean get() = network == IpNetwork.V6.SpecialRanges.ipV4Compatible

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.documentation] address range. Such addresses are reserved for documentation illustrations and examples. */
        val isDocumentation: Boolean get() = network == IpNetwork.V6.SpecialRanges.documentation

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.discardOnly] address range. Packets to such a destination address are dropped. */
        val isDiscardOnly: Boolean get() = network == IpNetwork.V6.SpecialRanges.discardOnly

        /**`true` if this IP interface is part of the [IpNetwork.V6.SpecialRanges.reserved] address range. */
        val isReserved: Boolean get() = network == IpNetwork.V6.SpecialRanges.reserved
    }

}

@Throws(IllegalArgumentException::class)
internal fun parseIpAndPrefix(stringRepresentation: String) = try {
    val parts = stringRepresentation.split('/')
    require(parts.size == 2) { "could not split string $stringRepresentation into address and prefix part" }

    IpAddress(parts.first()) to parts.last().toUInt()
} catch (e: Throwable) {
    if (e is IllegalArgumentException) throw e
    else throw IllegalArgumentException("$stringRepresentation is not a valid IP address", e)
}
