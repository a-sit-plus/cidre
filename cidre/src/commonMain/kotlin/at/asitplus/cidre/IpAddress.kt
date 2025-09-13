package at.asitplus.cidre

import at.asitplus.cidre.byteops.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.jvm.JvmName

/**
 * An IP address consisting of [IpFamily.numberOfOctets] many octets, as defined by [Specification.numberOfOctets]
 * * [N] indicates the type of [segments]. For IPv4 those are [Byte]s, for IPv6 they are [Short]s laid out in network order (BE).
 * * [octets] contains the byte-representation of this IP address in network order (BE)
 */
sealed class IpAddress<N : Number, S : CidrNumber<S>>(val octets: ByteArray, disambiguation: Unit) :
    Comparable<IpAddress<N, S>> {

    /** IP address family ([IpFamily.V4], [IpFamily.V6]*/
    abstract val family: IpFamily

    init {
        require(octets.size == family.numberOfOctets) { "Illegal number of octets specified for ${this::class.simpleName}: ${octets.size}. Expected: ${family.numberOfOctets}." }
    }

    abstract fun toCidrNumber(): S

    /**
     * The address's segments. For IPv4 those are [Byte]s, for IPv6 they are [Short]s laid out in network order (BE).
     * The string representation separates segments by [IpFamily.segmentSeparator]
     */
    abstract val segments: List<N>

    /**
     * Compares the IP addresses' octets interpreted as unsigned BE (network order) integer
     */
    override fun compareTo(other: IpAddress<N, S>): Int = octets.compareUnsignedBE(other.octets)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IpAddress<*, *>) return false

        if (!octets.contentEquals(other.octets)) return false

        return true
    }

    override fun hashCode(): Int {
        return octets.contentHashCode()
    }


    /**
     * Masks this IP address *in-place* (i.e. without copying) according to [prefix].
     *
     * @return the number of bits modified
     */
    fun mask(prefix: Prefix): Int = octets.andInplace(prefix.toNetmask(octets.size))

    /**
     * Masks this IP address in-place (i.e. without copying) according to [netmask].
     *
     * @return the number of bits modified
     */
    fun mask(netmask: Netmask): Int = octets.andInplace(netmask)

    /**Deep-copies an IP address*/
    @Suppress("UNCHECKED_CAST")
    fun copy(): IpAddress<N, S> = IpAddress(octets.copyOf()) as IpAddress<N, S>

    /**
     * unspecified addresses are:
     * * `0.0.0.0` (IPv4)
     * * `::` (IPv6)
     */
    val isSpecified: Boolean get() = !octets.all { it == 0.toByte() }

    @Suppress("UNCHECKED_CAST")
    operator fun plus(number: S): IpAddress<N, S> = IpAddress(toCidrNumber() + number) as IpAddress<N, S>

    @Suppress("UNCHECKED_CAST")
    operator fun minus(number: S): IpAddress<N, S> = IpAddress(toCidrNumber() - number) as IpAddress<N, S>

    inline infix fun shl(bits: Int): IpAddress<N, S> = IpAddress(octets shl bits) as IpAddress<N, S>
    inline infix fun shr(bits: Int): IpAddress<N, S> = IpAddress(octets shr bits) as IpAddress<N, S>

    //@formatter:off
    inline infix fun or(address: IpAddress<N, S>): IpAddress<N, S> =
    IpAddress(octets or address.octets) as IpAddress<N, S>

    inline infix fun and(address: IpAddress<N, S>): IpAddress<N, S> =
    IpAddress(octets and address.octets) as IpAddress<N, S>

    inline infix fun xor(address: IpAddress<N, S>): IpAddress<N, S> =
    IpAddress(octets xor address.octets) as IpAddress<N, S>
    //@formatter:on

    inline infix fun or(netmask: Netmask): IpAddress<N, S> = IpAddress(octets or netmask) as IpAddress<N, S>
    inline infix fun xor(netmask: Netmask): IpAddress<N, S> = IpAddress(octets xor netmask) as IpAddress<N, S>
    inline infix fun and(netmask: Netmask): IpAddress<N, S> = IpAddress(octets and netmask) as IpAddress<N, S>

    inline operator fun inv(): IpAddress<N, S> = IpAddress(octets.inv()) as IpAddress<N, S>

    /**
     * Internet Protocol (IpV4), originally defined by [RFC 791](https://www.rfc-editor.org/rfc/rfc791.html)
     */
    class V4

    /**
     * Creates an IPv4 address from octets containing an address in byte representation (BE/network oder)
     *
     * @throws IllegalArgumentException if invalid [octets] are provided
     */
    @Throws(IllegalArgumentException::class)
    constructor(octets: ByteArray) : IpAddress<Byte, CidrNumber.V4>(octets, Unit) {

        override val family: Companion get() = IpFamily.V4

        override val segments: List<Byte> by lazy { octets.toList() }

        override fun toCidrNumber(): CidrNumber.V4 = CidrNumber.V4(octets)

        /**
         * String representation as per [RFC 1123, Section 2](https://www.rfc-editor.org/rfc/rfc1123.html#section-2):
         * `#.#.#.#`, where `#` is the unsigned byte representation of an octet, without leading zeros.
         */
        override fun toString(): String =
            octets.joinToString(separator = segmentSeparator.toString()) {
                it.toUByte().toString()
            }


        /**
         * Historical IPv4 address classes as per [RFC 791, Section 3.2](https://www.rfc-editor.org/rfc/rfc791.html#section-3.2),
         * designating [Class.E] to the address range then defined as "reserved":
         *
         * | Class | First bits | First octet range | Purpose     |
         * |-------|:----------:|-------------------|-------------|
         * | A     | `0xxx`     | 0–127             | Large nets  |
         * | B     | `10xx`     | 128–191           | Medium nets |
         * | C     | `110x`     | 192–223           | Small nets  |
         * | D     | `1110`     | 224–239           | Multicast   |
         * | E     | `1111`     | 240–255           | Reserved    |
         */
        @get:JvmName("getAddressClass")
        @Deprecated("CIDR is the way to go!")
        val `class`: Class?
            get() = when (octets[0].toUByte().toInt()) {
                in 0..127 -> Class.A
                in 128..191 -> Class.B
                in 192..223 -> Class.C
                in 224..239 -> Class.D
                else -> Class.E
            }

        /**
         * Historical IPv4 address classes as per [RFC 791, Section 3.2](https://www.rfc-editor.org/rfc/rfc791.html#section-3.2),
         * designating [Class.E] to the address range then defined as "reserved":
         *
         * | Class | First bits | First octet range | Purpose     |
         * |-------|:----------:|-------------------|-------------|
         * | A     | `0xxx`     | 0–127             | Large nets  |
         * | B     | `10xx`     | 128–191           | Medium nets |
         * | C     | `110x`     | 192–223           | Small nets  |
         * | D     | `1110`     | 224–239           | Multicast   |
         * | E     | `1111`     | 240–255           | Reserved    |
         */
        @Deprecated("CIDR is the way to go!")
        enum class Class(/*TODO later: properties like prefix length*/) {
            A,
            B,
            C,
            D,
            E
        }

        companion object : IpFamily {
            override val numberOfOctets: Int = 4
            override val segmentSeparator: Char = '.'
            override val regex: IpFamily.RegexSpec = object : IpFamily.RegexSpec() {
                override val segment = Regex("(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)")
                override val address = Regex("${segment.pattern}(?:\\.${segment.pattern}){3}")
            }

            /**
             * Creates an IP address from its [stringRepresentation].
             *
             * @throws IllegalArgumentException if an invalid string is provided
             */
            @Throws(IllegalArgumentException::class)
            operator fun invoke(stringRepresentation: String): V4 {
                require(stringRepresentation.matches(Regex("^[0-9${segmentSeparator}]+$"))) { "Invalid IPv4 address '$stringRepresentation': contains invalid characters" }
                val parts = stringRepresentation.split(segmentSeparator)
                require(parts.size == 4) { "Invalid number of IPv4 segments: ${parts.size} in address: $stringRepresentation" }

                return V4(ByteArray(4) { index ->
                    require(parts[index].matches(regex.segment)) { "Invalid IPv4 address '$stringRepresentation'" }
                    val value = parts[index].toIntOrNull()
                    require(value != null && value in 0..255) { "Invalid IPv4 byte '${parts[index]}' in address $stringRepresentation" }
                    value.toUByte().toByte()
                })
            }

            /**
             * Creates an IP address from the passed [numeric] representation.
             * Note that if the numeric representation exceeds the address space (i.e., if the size of a /0 network is passed),
             * it simply overflows and never throws.
             */
            operator fun invoke(numeric: CidrNumber.V4): IpAddress.V4 = IpAddress.V4(numeric.toByteArray())

        }
    }

    /**
     * Internet Protocol, Version 6 (IPv6), originally defined by [RFC 8200](https://www.rfc-editor.org/rfc/rfc8200.html)
     */
    class V6

    /**
     * Creates an IPv6 address from octets containing an address in byte representation (BE/network oder)
     *
     * @throws IllegalArgumentException if invalid [octets] are provided
     */
    @Throws(IllegalArgumentException::class)
    constructor(octets: ByteArray) : IpAddress<Short, CidrNumber.V6>(octets, Unit) {

        override val segments: List<Short> by lazy { octets.toShortArray().asList() }

        override val family: Companion get() = IpFamily.V6

        override fun toCidrNumber(): CidrNumber.V6 = CidrNumber.V6(octets)

        /**
         * *Has an impact on the string representation of this address!*
         *
         * Indicates whether this IPv6 address contains a mapped IPv4 address in accordance with
         * [RFC 4291, Section 2.5.5.2](https://www.rfc-editor.org/rfc/rfc4291.html#section-2.5.5.2):
         *
         *| 80 bits                             | 16  | 32 bits         |
         * |:-----------------------------------:|:---:|:----------------:|
         * | `0000..............................0000` | `FFFF` | IPv4 address |
         */
        val isIpv4Mapped: Boolean by lazy {
            PREFIX_IPV4_MAPPED.indices.firstOrNull { PREFIX_IPV4_MAPPED[it] != octets[it] } == null
        }

        /**
         * Returns the IPv4 address embedded in this IPv6 address, in case one is contained (either [isIpv4Compatible] or [isIpv4Mapped] being `true`)
         */
        val embeddedIpV4Address: V4?
            get() =
                if (isIpv4Mapped || isIpv4Compatible) V4(segments.takeLast(2).map {
                    listOf(((it.toInt() and 0xFFFF) shr 8 and 0xFF).toByte(), it.toByte())
                }.flatten().toByteArray())
                else null

        /**
         * Deprecated by [RFC 4291](https://www.rfc-editor.org/rfc/rfc4291) and must not be used anymore. Has no impact on String representation.
         *
         * Indicates whether this IPv6 address is IPv4-*compatible* and contains  IPv4 address in accordance with
         * [RFC 4291, Section 2.5.5.1](https://www.rfc-editor.org/rfc/rfc4291.html#section-2.5.5.1):
         *
         *| 80 bits                             | 16  | 32 bits         |
         * |:-----------------------------------:|:---:|:----------------:|
         * | `0000..............................0000` | `0000` | IPv4 address |
         */

        @Deprecated("Deprecated by RFC 4291")
        val isIpv4Compatible: Boolean by lazy {
            PREFIX_IPV4_COMPAT.indices.firstOrNull { PREFIX_IPV4_COMPAT[it] != octets[it] } == null
        }


        /**
         * Returns a string representation of this IP address in [RFC 5952](https://www.rfc-editor.org/rfc/rfc5952.html) canonical form:
         * - lowercase hex
         * - omit leading zeros
         * - use '::' once for the longest run of zero hextets (length >= 2), leftmost on tie
         * - for IPv4-mapped, render last 32 bits in dotted-quad, and apply '::' to the hex part only
         */
        override fun toString() = toString(expanded = false)


        /**
         * Returns a string representation of this IP address
         * if [expanded] is set to `true` all hextets are printed in full length.  <br>
         * Otherwise, the resulting string conforms to [RFC 5952](https://www.rfc-editor.org/rfc/rfc5952.html) canonical form:
         * - lowercase hex
         * - omit leading zeros
         * - use '::' once for the longest run of zero hextets (length >= 2), leftmost on tie
         * - for IPv4-mapped, render last 32 bits in dotted-quad, and apply '::' to the hex part only
         */
        fun toString(expanded: Boolean): String {
            if (expanded) {
                return if (isIpv4Mapped) {
                    (segments.take(segments.size - 2)
                        .joinToString(separator = segmentSeparator.toString()) {
                            it.toUShort().toString(16).lowercase()
                        }) + segmentSeparator + embeddedIpV4Address!!.toString()
                } else segments.joinToString(separator = segmentSeparator.toString()) {
                    it.toUShort().toString(16).lowercase()
                }

            } else {
                if (!isSpecified) return "::"

                // Use existing hextet view
                val hextets = segments.map { it.toInt() and 0xFFFF }

                val ipv4Tail = isIpv4Mapped
                val hexLen = if (ipv4Tail) 6 else 8

                // Find the longest run of zeros within the hex portion
                var bestStart = -1
                var bestLen = 0
                var i = 0
                while (i < hexLen) {
                    if (hextets[i] == 0) {
                        var j = i
                        while (j < hexLen && hextets[j] == 0) j++
                        val len = j - i
                        if (len >= 2 && len > bestLen) {
                            bestStart = i
                            bestLen = len
                        }
                        i = j
                    } else {
                        i++
                    }
                }

                val sb = StringBuilder()
                var idx = 0
                var compressedUsed = false
                while (idx < hexLen) {
                    if (!compressedUsed && bestLen >= 2 && idx == bestStart) {
                        // '::' compression
                        if (sb.isEmpty()) sb.append("::") else sb.append("::")
                        idx += bestLen
                        compressedUsed = true
                        continue
                    }
                    if (sb.isNotEmpty() && sb.last() != ':') sb.append(':')
                    sb.append(hextets[idx].toString(16))
                    idx++
                }

                if (ipv4Tail) {
                    // Append IPv4 dotted-quad for last 32 bits
                    if (sb.isNotEmpty() && sb.last() != ':') sb.append(':')
                    val s6 = hextets[6]
                    val s7 = hextets[7]
                    val a = (s6 ushr 8) and 0xFF
                    val b = s6 and 0xFF
                    val c = (s7 ushr 8) and 0xFF
                    val d = s7 and 0xFF
                    sb.append("$a.$b.$c.$d")
                }

                return sb.toString().lowercase()
            }
        }

        companion object : IpFamily {


            override val numberOfOctets: Int = 16
            override val segmentSeparator: Char = ':'

            val PREFIX_IPV4_COMPAT = ByteArray(12)
            val PREFIX_IPV4_MAPPED = ByteArray(10) + ByteArray(2) { -1 }

            override val regex: IpFamily.RegexSpec = object : IpFamily.RegexSpec() {
                override val segment = Regex("[0-9A-Fa-f]{1,4}")
                private val H = segment.pattern
                private val V4 = IpFamily.V4.regex.address.pattern
                private fun exactHextets(n: Int): String = when {
                    n <= 0 -> ""
                    n == 1 -> H
                    else -> "(?:$H:){${n - 1}}$H"
                }

                private fun hextetsWithTrailingColon(n: Int): String = if (n <= 0) "" else "(?:$H:){$n}"
                private val V6_FULL_8 = "^${exactHextets(8)}$"
                private val V6_COMP_HEX_VARIANTS: List<String> = buildList {
                    for (before in 0..8) {
                        val maxAfter = 7 - before
                        if (maxAfter < 0) continue
                        for (after in 0..maxAfter) {
                            val lhs = exactHextets(before)
                            val rhs = exactHextets(after)
                            add("^$lhs::$rhs$")
                        }
                    }
                }
                private val V6_V4_TAIL_NO_COMP = "^(?:$H:){6}$V4$"
                private val V6_V4_TAIL_COMP_VARIANTS: List<Regex> = buildList {
                    for (before in 0..5) {
                        val maxAfter = 5 - before
                        for (after in 0..maxAfter) {
                            val lhs = exactHextets(before)
                            val rhs = hextetsWithTrailingColon(after)
                            add(Regex("^$lhs::$rhs$V4$", RegexOption.IGNORE_CASE))
                        }
                    }
                }
                override val address = Regex(
                    (listOf(V6_FULL_8) + V6_COMP_HEX_VARIANTS + listOf(V6_V4_TAIL_NO_COMP) + V6_V4_TAIL_COMP_VARIANTS).joinToString(
                        "|"
                    ),
                    RegexOption.IGNORE_CASE
                )
            }


            private val V6_UNSPEC = Regex("^::$")
            private val v4EmbeddedRegex = Regex("^[0-9A-Fa-f:]+:${V4.regex.address.pattern}$$", RegexOption.IGNORE_CASE)
            private val segmentIpv4Regex = Regex(".*${V4.regex.address.pattern}$", RegexOption.IGNORE_CASE)

            @Throws(IllegalArgumentException::class)
            operator fun invoke(stringRepresentation: String): V6 {
                if (stringRepresentation.matches(V6_UNSPEC)) return V6(ByteArray(numberOfOctets))

                require(stringRepresentation.matches(regex.address)) { "Invalid IPv6 address '$stringRepresentation': contains invalid characters" }
                val parts = stringRepresentation.split("${segmentSeparator}${segmentSeparator}")
                require(parts.size <= 2) { "Invalid IPv6 address: too many '::'" }

                val containsIpv4 = stringRepresentation.matches(v4EmbeddedRegex)

                val delimiters = if (containsIpv4) charArrayOf(segmentSeparator, V4.segmentSeparator)
                else charArrayOf(segmentSeparator)

                val headSegments = if (parts[0].isNotEmpty()) parts[0].split(*delimiters) else emptyList()
                val headIpv4 = parts.first().matches(segmentIpv4Regex)

                val tailSegments =
                    if (parts.size == 2 && parts[1].isNotEmpty()) parts[1].split(*delimiters) else emptyList()
                val tailIpv4 = parts.last().matches(segmentIpv4Regex)

                val totalSegments = headSegments.size + tailSegments.size

                val compressed =
                    stringRepresentation.contains("${segmentSeparator}${segmentSeparator}")

                if (containsIpv4) require(if (compressed) totalSegments <= 9 else totalSegments == 10) { "Invalid IPv6 address: too many segments: $totalSegments" }
                else require(if (compressed) totalSegments <= 7 else totalSegments == 8) { "Invalid IPv6 address: too many segments: $totalSegments" }

                val result = ByteArray(numberOfOctets)
                var byteIndex = 0

                var index = 0
                for (segment in headSegments) {
                    index++
                    if (headIpv4 && headSegments.size - index < 4) {
                        result[byteIndex++] = segment.toUByte().toByte()
                    } else {
                        val value = segment.toUShort(16)
                        result[byteIndex++] = (value.toInt() shr 8).toByte()
                        result[byteIndex++] = value.toByte()
                    }
                }
                if (compressed) {
                    val zerosToInsert = (if (containsIpv4) 10 else 8) - totalSegments
                    byteIndex += zerosToInsert * 2
                }

                index = 0
                for (segment in tailSegments) {
                    index++
                    if (tailIpv4 && tailSegments.size - index < 4) {
                        result[byteIndex++] = segment.toUByte().toByte()
                    } else {
                        val value = segment.toUShort(16)
                        result[byteIndex++] = (value.toInt() shr 8).toUByte().toByte()
                        result[byteIndex++] = value.toUByte().toByte()
                    }
                }

                return V6(result)
            }

            /**
             * Creates an IP address from the passed [numeric] representation.
             * Note that if the numeric representation exceeds the address space (i.e., if the size of a /0 network is passed),
             * it simply overflows and never throws.
             */
            operator fun invoke(numeric: CidrNumber.V6): IpAddress.V6 = IpAddress.V6(numeric.toByteArray())

        }
    }


    companion object {

        /**
         * Creates an IP address from its [stringRepresentation].
         *
         * @throws IllegalArgumentException if an invalid string is provided
         */
        @Throws(IllegalArgumentException::class)
        operator fun invoke(stringRepresentation: String): IpAddress<*, *> = when {
            V6.segmentSeparator in stringRepresentation -> V6(stringRepresentation)
            V4.segmentSeparator in stringRepresentation -> V4(stringRepresentation)
            else -> throw IllegalArgumentException("Invalid address '$stringRepresentation'")
        }

        /**
         * Creates an IP address from octets containing an address in byte representation (BE/network oder)
         *
         * @throws IllegalArgumentException if invalid [octets] are provided
         */
        @Throws(IllegalArgumentException::class)
        operator fun invoke(octets: ByteArray): IpAddress<*, *> = when (octets.size) {
            V6.numberOfOctets -> V6(octets)
            V4.numberOfOctets -> V4(octets)
            else -> throw IllegalArgumentException("Invalid number of octets: ${octets.size}")
        }

        /**
         * Creates an IP address from the passed [numeric] representation.
         * Note that if the numeric representation exceeds the address space (i.e., if the size of a /0 network is passed),
         * it simply overflows and never throws.
         */
        operator fun invoke(numeric: CidrNumber<*>): IpAddress<*, *> = IpAddress(numeric.toByteArray())

    }

}

@OptIn(ExperimentalContracts::class)
fun IpAddress<*, *>.isV4(): Boolean {
    contract {
        returns(true) implies (this@isV4 is IpAddress.V4)
        returns(false) implies (this@isV4 is IpAddress.V6)
    }

    return this is IpAddressAndPrefix.V4
}


@OptIn(ExperimentalContracts::class)
fun IpAddress<*, *>.isV6(): Boolean {
    contract {
        returns(true) implies (this@isV6 is IpAddress.V6)
        returns(false) implies (this@isV6 is IpAddress.V4)
    }
    return this is IpAddressAndPrefix.V6
}

@OptIn(ExperimentalContracts::class)
fun IpAddress.V4.isSameFamily(other: IpAddress<*, *>): Boolean {
    contract {
        returns(true) implies (other is IpAddress.V4)
    }
    return other is IpAddress.V4
}

@OptIn(ExperimentalContracts::class)
fun IpAddress.V6.isSameFamily(other: IpAddress<*, *>): Boolean {
    contract {
        returns(true) implies (other is IpAddress.V6)
    }
    return other is IpAddress.V6
}