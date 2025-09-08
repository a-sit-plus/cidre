package at.asitplus.cidre

/**
 * Sealed base interface of [IpAddress] and [IpInterface], attaching common semantics and functionality to the combination of an [address] and [prefix].
 */
sealed interface IpAddressAndPrefix<N : Number, T : IpAddress<N>> {

    val address: T

    /** CIDR prefix length */
    val prefix: Prefix
    /** Network-order (BE) layout of a CIDR prefix */
    val netmask: Netmask

    /**`true` if this is (part of) the [IpNetwork.SpecialRanges.linkLocal] network */
    val isLinkLocal: Boolean

    /**`true` if this is (part of)  the [IpNetwork.SpecialRanges.loopback] network */
    val isLoopback: Boolean

    /**`true` if this is (part of)  the [IpNetwork.SpecialRanges.multicast] network */
    val isMulticast: Boolean

    /**
     * Sealed base interface of [IpAddress.V4] and [IpInterface.V4] with IPv4-specific add-ons.
     *
     * @see IpAddressAndPrefix
     */
    sealed interface V4 : IpAddressAndPrefix<Byte, IpAddress.V4> {

        /**`true` if this is private, i.e. (part of) any [IpNetwork.V4.SpecialRanges.private] network */
        val isPrivate: Boolean

        /**
         * `true` if this is public, i.e. *NOT* (part of) any [IpNetwork.V4.SpecialRanges.private] network.
         * *This is NOT the opposite of [isPrivate]!*
         * */
        val isPublic: Boolean

        /** String representation of this IPv4 netmask (`#.#.#.#`) */
        fun netmaskToString() = netmask.joinToString(".") { it.toUByte().toString() }

        /**
         * Useful for debugging. Allows toggling between
         * [preferNetmaskOverPrefix]` = true` for default CIDR notation (`#.#.#.#/prefix`)
         * [preferNetmaskOverPrefix]` = false` legacy segmented notation (`A.A.A.A / N.N.N.N`, where `A` represents an address segment and `N` a netmask segment)
         * */
        fun toString(preferNetmaskOverPrefix: Boolean): String = if(preferNetmaskOverPrefix) {"$address ${netmaskToString()}"} else "$address/$prefix"
    }

    /**
     * Sealed base interface of [IpAddress.V6] and [IpInterface.V6] with IPv6-specific add-ons.
     *
     * @see IpAddressAndPrefix
     */
    sealed interface V6 : IpAddressAndPrefix<Short, IpAddress.V6> {

        /**`true` if this is (part of) part of the [IpNetwork.V6.SpecialRanges.globalUnicast] address range. This is the equivalent of an IPv4 public address.
         * This is the IPv6 equivalent of [IpInterface.V4.isPublic] */
        val isGlobalUnicast: Boolean

        /**
         * `true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.uniqueLocal] address range.
         * This range contains both [IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned] and the currently unused `fc00::/8` address range.
         * */
        val isUniqueLocal: Boolean

        /**`true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.uniqueLocalLocallyAssigned] address range. This is the equivalent of an IPv4 private address. */
        val isUniqueLocalLocallyAssigned: Boolean

        /**`true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.ipV4Mapped] address range. */
        val isIpV4Mapped: Boolean

        /**`true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.ipV4Compatible] address range. */
        @Deprecated("Originally meant to embed IPv4, now obsolete")
        val isIpV4Compatible: Boolean

        /**`true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.documentation] address range. Such addresses are reserved for documentation illustrations and examples. */
        val isDocumentation: Boolean

        /**`true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.discardOnly] address range. Packets to such a destination address are dropped. */
        val isDiscardOnly: Boolean

        /**`true` if this is (part of)  the [IpNetwork.V6.SpecialRanges.reserved] address range. */
        val isReserved: Boolean


        /**
         * Returns a string representation in the form of `address/prefix`
         * if [expanded] is set to `true` all hextets are printed in full length.  <br>
         * Otherwise, the address string conforms to [RFC 5952](https://www.rfc-editor.org/rfc/rfc5952.html) canonical form:
         * - lowercase hex
         * - omit leading zeros
         * - use '::' once for the longest run of zero hextets (length >= 2), leftmost on tie
         * - for IPv4-mapped, render last 32 bits in dotted-quad, and apply '::' to the hex part only
         */
        fun toString(expanded: Boolean): String = "${address.toString(expanded)}/$prefix"
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
