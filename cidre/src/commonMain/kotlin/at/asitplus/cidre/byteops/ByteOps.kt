package at.asitplus.cidre.byteops

import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpFamily
import at.asitplus.cidre.Netmask
import at.asitplus.cidre.Prefix
import kotlin.experimental.inv

/**
 * logical `AND` operation returning a newly-allocated [ByteArray].
 * @throws IllegalArgumentException if bytes are of different size
 */
@Throws(IllegalArgumentException::class)
infix fun ByteArray.and(other: ByteArray): ByteArray {
    require(size == other.size) { "Mismatched sizes: $size vs ${other.size}" }
    val r = ByteArray(size)
    for (i in indices) r[i] = (this[i].toInt() and other[i].toInt()).toUByte().toByte()
    return r
}

/**
 * logical `OR` operation returning a newly-allocated [ByteArray].
 * @throws IllegalArgumentException if bytes are of different size
 */
@Throws(IllegalArgumentException::class)
infix fun ByteArray.or(other: ByteArray): ByteArray {
    require(size == other.size) { "Mismatched sizes: $size vs ${other.size}" }
    val r = ByteArray(size)
    for (i in indices) r[i] = (this[i].toInt() or other[i].toInt()).toUByte().toByte()
    return r
}

/**
 * In-place logical `AND` operation, modifying the receiver ByteArray. Returns the number of modified bits
 * @throws IllegalArgumentException if bytes are of different size
 */
@Throws(IllegalArgumentException::class)
fun ByteArray.andInplace(other: ByteArray): Int {
    require(size == other.size) { "Mismatched sizes: $size vs ${other.size}" }
    var changedBits = 0
    var i = 0
    while (i < size) {
        val m = other[i].toInt() and 0xFF
        if (m != 0xFF) { // fast path: 0xFF would leave the byte unchanged
            val old = this[i].toInt() and 0xFF
            val new = old and m
            if (new != old) {
                changedBits += (old xor new).countOneBits()
                this[i] = new.toByte()
            }
        }
        i++
    }
    return changedBits
}

/**
 * @throws IllegalArgumentException if an odd number of bytes is passed
 */
@Throws(IllegalArgumentException::class)
fun ByteArray.toShortArray(bigEndian: Boolean = true): ShortArray {
    require(size % 2 == 0) { "ByteArray size must be even, was $size" }
    val out = ShortArray(size / 2)
    var bi = 0
    var si = 0
    while (bi < size) {
        val b0 = this[bi++].toInt() and 0xFF
        val b1 = this[bi++].toInt() and 0xFF
        val value = if (bigEndian) {
            (b0 shl 8) or b1
        } else {
            (b1 shl 8) or b0
        }
        out[si++] = value.toShort()
    }
    return out
}

/**
 * Compares this ByteArray to [other] interpreted as an unsigned BE (network order)
 *
 * @throws IllegalArgumentException in case the arrays have different sizes
 */
@Throws(IllegalArgumentException::class)
fun ByteArray.compareUnsignedBE(other: ByteArray): Int {
    val a = this
    val b = other
    require(a.size == b.size) { "ByteArrays must be same size. this.size: ${a.size}, other.size: ${b.size}" }
    // Skip leading zeros
    var ai = 0
    while (ai < a.size && a[ai] == 0.toByte()) ai++
    var bi = 0
    while (bi < b.size && b[bi] == 0.toByte()) bi++

    val aLen = a.size - ai
    val bLen = b.size - bi
    if (aLen != bLen) return if (aLen > bLen) 1 else -1

    // Same significant length: compare lexicographically as unsigned
    var i = 0
    while (i < aLen) {
        val av = a[ai + i].toUByte()
        val bv = b[bi + i].toUByte()
        if (av != bv) return if (av > bv) 1 else -1
        i++
    }
    return 0
}


/**
 * Creates a netmask from this prefix. Arbitrary [octetCount]s can be specified.
 * @throws IllegalArgumentException in case the prefix exceeds the specified [octetCount]
 */
@Throws(IllegalArgumentException::class)
fun Prefix.toNetmask(octetCount: Int): Netmask {
    val prefix = this.toInt()
    require(prefix in 0..(octetCount * 8)) { "prefix out of range for $octetCount-octet address" }

    val mask = ByteArray(octetCount) { 0 }
    val fullBytes = prefix / 8
    val remainingBits = prefix % 8

    // Set full bytes to 0xFF
    for (i in 0 until fullBytes) {
        mask[i] = 0xFF.toByte()
    }

    // Set the remaining bits in the last byte
    if (remainingBits > 0) {
        mask[fullBytes] = (0xFF shl (8 - remainingBits)).toByte()
    }

    return mask
}

/**
 * **IN-PLACE** inversion
 * @see [kotlin.experimental.inv]*/
fun ByteArray.invInPlace() = forEachIndexed { i, byte -> this[i] = byte.inv() }

operator fun ByteArray.inv() = copyOf().apply { invInPlace() }

/**
 * Creates a netmask from this prefix. The resulting number of octets depends on the specified [family].
 *
 * @throws IllegalArgumentException in case the prefix is too long
 */
@Throws(IllegalArgumentException::class)
fun Prefix.toNetmask(family: IpFamily): Netmask = toNetmask(family.numberOfOctets)

/**
 * Converts a netmask into its CIDR prefix length.
 * Validates that the mask's length and that it is contiguous (all 1-bits followed by 0-bits).
 *
 * @throws IllegalArgumentException if the netmask is not contiguous
 * @throws IllegalArgumentException if the netmask size matches neither [IpAddress.Companion.numberOfOctets] nor [IpAddress.Companion.numberOfOctets]
 */
@Throws(IllegalArgumentException::class)
fun Netmask.toPrefix(): Prefix {
    require(size == IpAddress.V4.numberOfOctets || size == IpAddress.V6.numberOfOctets) { "Netmask size neither ${IpAddress.V4.numberOfOctets} nor ${IpAddress.V6.numberOfOctets}. Was $size." }
    var prefix = 0u
    var sawZero = false
    for (b in this) {
        val ub = b.toInt() and 0xFF
        var mask = 0x80
        var i = 0
        while (i < 8) {
            val isOne = (ub and mask) != 0
            if (isOne) {
                if (sawZero) {
                    throw IllegalArgumentException("non-contiguous netmask")
                }
                prefix++
            } else {
                sawZero = true
            }
            mask = mask ushr 1
            i++
        }
    }
    return prefix
}

/**
 * Converts the UInt value to its IPv4 address representation in the form of a 4-byte-long ByteArray.
 * Each byte in the array corresponds to one octet of the IPv4 address, arranged in network byte order (big-endian).
 *
 * @return A ByteArray containing 4 bytes representing the IPv4 address.
 */
fun UInt.toIPv4Bytes(): ByteArray {
    val out = ByteArray(4)
    var v = this
    for (i in 0 until out.size) {
        out[out.size - 1 - i] = (v and 0xFFu).toUByte().toByte()
        v = v shr 8
    }
    return out
}

/**
 * Converts the UInt value to its IPv4 address representation in the form of a 4-byte-long ByteArray.
 * Each byte in the array corresponds to one octet of the IPv4 address, arranged in network byte order (big-endian).
 * Any values larger than [UInt.MAX_VALUE] will be truncated to four bytes
 *
 * @return A ByteArray containing 4 bytes representing the IPv4 address.
 */
fun ULong.toIPv4Bytes(): ByteArray {
    val out = ByteArray(if (this > UInt.MAX_VALUE) 5 else 4)
    var v = this
    for (i in 0 until out.size) {
        out[out.size - 1 - i] = (v and 0xFFuL).toUByte().toByte()
        v = v shr 8
    }
    return out
}