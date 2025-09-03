package at.asitplus.cidre.byteops

import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.Netmask
import at.asitplus.cidre.Prefix

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
fun UInt.toNetmask(octetCount: Int): Netmask {
    val prefix = this.toInt()
    require(prefix in 0..(octetCount * 8)) { "prefix out of range for $octetCount-octet address" }

    val out = ByteArray(octetCount)
    val full = prefix / 8
    val rem = prefix % 8

    // Full 0xFF bytes
    for (i in 0 until full) out[i] = 0xFF.toByte()

    // Partial byte with leading ones
    if (rem > 0) out[full] = ((0xFF shl (8 - rem)) and 0xFF).toByte()

    return out
}

/**
 * Creates a netmask from this prefix. The resulting number of octets depends on the specified [version].
 *
 * @throws IllegalArgumentException in case the prefix is too long
 */
@Throws(IllegalArgumentException::class)
fun Prefix.toNetmask(version: IpAddress.Version): Netmask = toNetmask(version.numberOfOctets)

/**
 * Converts a network-order netmask into its CIDR prefix length.
 * Validates that the mask's length and that it is contiguous (all 1-bits followed by 0-bits).
 *
 * @throws IllegalArgumentException if the netmask is not contiguous
 * @throws IllegalArgumentException if the netmask size matches neither [IpAddress.V4.numberOfOctets] nor [IpAddress.V6.numberOfOctets]
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

