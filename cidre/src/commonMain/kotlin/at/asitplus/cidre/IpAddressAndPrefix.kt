package at.asitplus.cidre

import at.asitplus.cidre.byteops.CidrNumber
import at.asitplus.cidre.byteops.invInPlace
import at.asitplus.cidre.byteops.toPrefix
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


/**CIDR prefix length*/
typealias Prefix = UInt

/**Network-order (BE) layout of a CIDR prefix*/
typealias Netmask = ByteArray

/**
 * Sealed base interface of [IpAddress] and [IpInterface], attaching common semantics and functionality to the combination of an [address] and [prefix].
 */
sealed interface IpAddressAndPrefix<N : Number, S : CidrNumber<S>> {

    /** Computed once. Do not mess with its octets!*/
    val address: IpAddress<N, S>

    val family: IpFamily get() = address.family

    /** CIDR prefix length */
    val prefix: Prefix

    /** Network-order (BE) layout of a CIDR prefix */
    val netmask: Netmask

    /**
     * The complement of [netmask]
     */
    val hostMask: ByteArray get() = netmask.copyOf().apply { invInPlace() }

    val numberOfHostBits get(): UInt = family.numberOfBits.toUInt() - prefix

    /**`true` if this is (part of) the [IpNetwork.SpecialRanges.linkLocal] network */
    val isLinkLocal: Boolean

    /**`true` if this is (part of)  the [IpNetwork.SpecialRanges.loopback] network */
    val isLoopback: Boolean

    /**`true` if this is (part of)  the [IpNetwork.SpecialRanges.multicast] network */
    val isMulticast: Boolean

    /**
     * Encodes this network into X.509 iPAddressName ByteArray (RFC 5280).
     * IPv4 byte layout: `AAAANNNN`, where `A` is an address octet and `N` is a netmask octet (8 bytes total)
     * IPv6 byte layout:  `AAAAAAAAAAAAAAAANNNNNNNNNNNNNNNN`, where `A` is an address octet and `N` is a netmask octet(32 bytes total)
     */
    fun toX509Octets(): ByteArray = address.octets + netmask

    companion object {

        /**
         * Low-level helper used by [IpNetwork.fromX509Octets] and [IpInterface.fromX509Octets]
         * to extract base address and CIDR prefix
         * IPv4 byte layout: `AAAANNNN`, where `A` is an address octet and `N` is a netmask octet (8 bytes total)
         * IPv6 byte layout:  `AAAAAAAAAAAAAAAANNNNNNNNNNNNNNNN`, where `A` is an address octet and `N` is a netmask octet(32 bytes total)
         */
        internal fun parseX509Octets(bytes: ByteArray): Pair<IpAddress<*, *>, Prefix> =
            when (bytes.size) {
                2 * IpFamily.V4.numberOfOctets -> {
                    val address = bytes.copyOfRange(0, 4)
                    val mask = bytes.copyOfRange(4, 8)
                    IpAddress.V4(address) to mask.toPrefix()
                }

                2 * IpFamily.V6.numberOfOctets -> {
                    val address = bytes.copyOfRange(0, 16)
                    val mask = bytes.copyOfRange(16, 32)
                    IpAddress.V6(address) to mask.toPrefix()
                }

                else -> throw IllegalArgumentException("Invalid iPAddress length: ${bytes.size}")
            }
    }

    /**
     * Sealed base interface of [IpAddress.V4] and [IpInterface.V4] with IPv4-specific add-ons.
     *
     * @see IpAddressAndPrefix
     */
    sealed interface V4 : IpAddressAndPrefix<Byte, CidrNumber.V4> {

        override val address: IpAddress.V4

        /**`true` if this is private, i.e. (part of) any [IpNetwork.V4.SpecialRanges.private] network */
        val isPrivate: Boolean

        /**
         * `true` if this is public, i.e. *NOT* (part of) any [IpNetwork.V4.SpecialRanges.private] network.
         * *This is NOT the opposite of [isPrivate]!*
         * */
        val isPublic: Boolean

        /** Dotted-quad representation of this IPv4 netmask (`#.#.#.#`) */
        fun netmaskToString() = netmask.joinToString(".") { it.toUByte().toString() }

        /**
         * Useful for debugging. Allows toggling between
         * [preferNetmaskOverPrefix]` = true` for default CIDR notation (`#.#.#.#/prefix`)
         * [preferNetmaskOverPrefix]` = false` legacy segmented notation (`A.A.A.A / N.N.N.N`, where `A` represents an address quad and `N` a netmask quad)
         */
        fun toString(preferNetmaskOverPrefix: Boolean): String = if (preferNetmaskOverPrefix) {
            "$address ${netmaskToString()}"
        } else "$address/$prefix"
    }

    /**
     * Sealed base interface of [IpAddress.V6] and [IpInterface.V6] with IPv6-specific add-ons.
     *
     * @see IpAddressAndPrefix
     */
    sealed interface V6 : IpAddressAndPrefix<Short, CidrNumber.V6> {

        /**`true` if this is (part of) part of the [IpNetwork.V6.SpecialRanges.globalUnicast] address range. This is the equivalent of an IPv4 public address.
         * This is the IPv6 equivalent of [IpInterface.V4.isPublic] */
        val isGlobalUnicast: Boolean

        override val address: IpAddress.V6

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


@OptIn(ExperimentalContracts::class)
fun IpAddressAndPrefix<*, *>.isV4(): Boolean {
    contract { returns(true) implies (this@isV4 is IpAddressAndPrefix.V4) }
    return this is IpAddressAndPrefix.V4
}

@OptIn(ExperimentalContracts::class)
fun IpAddressAndPrefix<*, *>.isV6(): Boolean {
    contract { returns(true) implies (this@isV6 is IpAddressAndPrefix.V6) }
    return this is IpAddressAndPrefix.V6
}

@OptIn(ExperimentalContracts::class)
fun IpAddressAndPrefix.V4.isSameFamily(other: IpAddressAndPrefix<*, *>): Boolean {
    contract {
        returns(true) implies (other is IpAddressAndPrefix.V4)
    }
    return other is IpAddressAndPrefix.V4
}

@OptIn(ExperimentalContracts::class)
fun IpAddressAndPrefix.V6.isSameFamily(other: IpAddressAndPrefix<*, *>): Boolean {
    contract {
        returns(true) implies (other is IpAddressAndPrefix.V6)
    }
    return other is IpAddressAndPrefix.V6
}
