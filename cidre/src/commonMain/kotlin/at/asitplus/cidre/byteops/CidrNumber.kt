package at.asitplus.cidre.byteops

import at.asitplus.cidre.byteops.CidrNumber.V4.Companion.MAX_VALUE
import at.asitplus.cidre.byteops.CidrNumber.V6.Companion.MAX_VALUE
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Number with a byte layout optimized for CIDR/IP operations.
 * The maximum number of addresses inside an IP address space is 2^32 for IPv4 and 2^128 for IPv6. Hence,
 * 32/128-bit numbers are not enough to represent the size of /0 networks, and an additional bit is needed.
 * At the same time, the *actual* number of bytes used is 4/16, which is why (by default) a CidrNumber's
 * byte-representation is truncated to 4/16 bytes, to make it efficiently usable for CIDR operations.
 *
 * Its String representation is the hex-encoded byte-representation.
 *
 * @see toByteArray
 */
interface CidrNumber<TSelf : CidrNumber<TSelf>> : Comparable<TSelf> {
    operator fun plus(other: TSelf): TSelf
    operator fun minus(other: TSelf): TSelf
    operator fun plus(other: UInt): TSelf
    operator fun minus(other: UInt): TSelf
    infix fun and(other: TSelf): TSelf
    infix fun or(other: TSelf): TSelf
    infix fun xor(other: TSelf): TSelf
    infix fun shl(bits: Int): TSelf
    infix fun shr(bits: Int): TSelf
    operator fun inv(): TSelf
    operator fun inc(): TSelf
    operator fun dec(): TSelf

    /**
     * Converts the internal representation of the object into a ByteArray optimized for CIDR/IP operations.
     * The maximum number of addresses inside an IP address space is 2^32 for IPv4 and 2^128 for IPv6. Hence,
     * 32/128-bit numbers are not enough to represent the size of /0 networks, and an additional bit is needed.
     *
     * However, this function by default truncates to 4/16 bytes, as useful for CIDR operations.
     * To write the number of addresses correctly, set [truncate]` = false`.
     */
    fun toByteArray(truncate: Boolean = true): ByteArray

    /**
     * Minimal, fixed-width 3**3**-bit unsigned integer for CIDR/IP operations,
     * meaning its [MAX_VALUE] is 2^3**3**-1 (and not 2^32-1). This is required to represent the maximum number of addresses inside
     * an IPv4 network.
     *
     * * Always exactly 3**3** bits wide for arithmetic.
     * * Network byte order (big-endian) for serialization.
     * * Tailored for IPv4 arithmetic: compare, +/-, bitwise ops, shifts.
     * * Implemented as a value class on [ULong] with clamping to 33 bit.
     */


    companion object {
        /**
         * Parse from
         * * 4 or 5 bytes (IPv4)
         * * 16 or 17 bytes (IPv6)
         *
         * @throws IllegalArgumentException for invalid lengths
         */
        @OptIn(ExperimentalObjCName::class)
        @JvmStatic
        @JvmName("fromBytes")
        @ObjCName("fromBytes")
        operator fun invoke(bytes: ByteArray): CidrNumber<*> =
            when (bytes.size) {
                4, 5 -> V4(bytes)
                16, 17 -> V6(bytes)
                else -> throw IllegalArgumentException("invalid byte length: ${bytes.size}")
            }

    }

    @JvmInline
    value class V4 private constructor(val raw: ULong) : CidrNumber<CidrNumber.V4> {


        override fun toString(): String = toByteArray().toHexString()

        companion object Companion {

            @OptIn(ExperimentalObjCName::class)
            @JvmStatic
            @JvmName("fromLong")
            @ObjCName("fromLong")
            operator fun invoke(raw: ULong) = CidrNumber.V4(raw and MAX_VALUE.raw)
            val ZERO = CidrNumber.V4(0u)
            val ONE = CidrNumber.V4(1u)
            val MAX_VALUE = CidrNumber.V4((2uL shl 33) - 1uL)

            /**
             * Parse from 4 or 5 bytes (big-endian).
             * This supports the full 33-bit range [0, 2^33-1].
             *
             * @throws IllegalArgumentException for invalid lengths
             */
            @OptIn(ExperimentalObjCName::class)
            @JvmStatic
            @JvmName("fromBytes")
            @ObjCName("fromBytes")
            operator fun invoke(bytes: ByteArray): CidrNumber.V4 {
                require(bytes.size == 4 || bytes.size == 5) { "ByteArray must contain 4 or 5 bytes. Has: ${bytes.size}" }
                var acc = 0uL
                var i = 0
                while (i < bytes.size) {
                    acc = (acc shl 8) or (bytes[i].toUByte().toULong() and 0xFFuL)
                    i++
                }
                return CidrNumber.V4(acc)
            }

            fun fromUnpadded(bytes: ByteArray): CidrNumber.V4 = CidrNumber.V4(
                if (bytes.size < 4) ByteArray(5).apply {
                    bytes.indices.forEach { this[5 - bytes.size + it] = bytes[it] }
                } else bytes
            )
        }


        override fun plus(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw + other.raw)
        override fun plus(other: UInt): CidrNumber.V4 = CidrNumber.V4(raw + other)

        override fun minus(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw - other.raw)
        override fun minus(other: UInt): CidrNumber.V4 = CidrNumber.V4(raw - other)

        override fun and(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw.and(other.raw))

        override fun or(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw.or(other.raw))

        override fun xor(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw.xor(other.raw))

        override fun shl(bits: Int): CidrNumber.V4 = CidrNumber.V4(raw.shl(bits))

        override fun shr(bits: Int): CidrNumber.V4 = CidrNumber.V4(raw.shr(bits))

        override fun inv(): CidrNumber.V4 = CidrNumber.V4(raw.inv())

        override fun inc(): CidrNumber.V4 = CidrNumber.V4(raw.inc())

        override fun dec(): CidrNumber.V4 = CidrNumber.V4(raw.dec())


        override fun toByteArray(truncate: Boolean): ByteArray {
            val out = ByteArray(if (truncate || raw <= UInt.MAX_VALUE) 4 else 5)
            var v = raw
            for (i in 0 until out.size) {
                out[out.size - 1 - i] = (v and 0xFFuL).toUByte().toByte()
                v = v shr 8
            }
            return out
        }

        override fun compareTo(other: CidrNumber.V4): Int = raw.compareTo(other.raw)

    }

    /**
     * Minimal, fixed-width 12**9**-bit unsigned integer for CIDR/IP operations,
     * meaning its [MAX_VALUE] is 2^12**9**-1 (and not 2^128-1). This is required to represent the maximum number of addresses inside
     * an IPv6 network.
     *
     * * Always exactly 12**9** bits wide for arithmetic.
     * * Network byte order (big-endian) for serialization.
     * * Tailored for IPv6 arithmetic: compare, +/-, bitwise ops, shifts.
     * * Implemented via two 64-bit limbs + an extra Boolean.
     *
     * Storage layout (big-endian semantics):
     *  hi: most-significant 64 bits (bits 127..64)
     *  lo: least-significant 64 bits (bits 63..0)
     *  extraBit: bit 65
     */
    class V6 internal constructor(hi: ULong, lo: ULong, extraBit: Boolean = false) : CidrNumber<V6> {

        var hi: ULong = hi
            private set
        var lo: ULong = lo
            private set
        var extraBit: Boolean = extraBit
            private set

        override fun toString(): String = toByteArray().toHexString()

        override fun compareTo(other: V6): Int = when {
            this.extraBit != other.extraBit -> if (this.extraBit) 1 else -1
            this.hi != other.hi -> if (this.hi < other.hi) -1 else 1
            this.lo != other.lo -> if (this.lo < other.lo) -1 else 1
            else -> 0
        }

        override operator fun plus(other: V6): V6 {
            // 64-bit low limb
            val loSum = this.lo + other.lo
            val carryLo = if (loSum < this.lo) 1uL else 0uL

            // 64-bit high limb with carry propagation in two steps to detect carry-out
            val hiSum0 = this.hi + other.hi
            val carryHi0 = if (hiSum0 < this.hi) 1 else 0
            val hiSum = hiSum0 + carryLo
            val carryHi1 = if (hiSum < hiSum0) 1 else 0
            val carryOutHi = carryHi0 or carryHi1 // 0 or 1

            // 129th bit is the sum of: a.extra, b.extra, and carry-out from hi, modulo 2
            val aE = if (this.extraBit) 1 else 0
            val bE = if (other.extraBit) 1 else 0
            val newExtra = ((aE + bE + carryOutHi) and 1) == 1

            return V6(hiSum, loSum, newExtra)
        }

        operator fun plusAssign(other: V6) {
            val oldLo = this.lo
            val loSum = oldLo + other.lo
            val carryLo = if (loSum < oldLo) 1uL else 0uL

            val oldHi = this.hi
            val hiSum1 = oldHi + other.hi
            val carryHi0 = if (hiSum1 < oldHi) 1 else 0
            val hiSum = hiSum1 + carryLo
            val carryHi1 = if (hiSum < hiSum1) 1 else 0
            val carryToExtra = carryHi0 or carryHi1 // 0 or 1

            val aE = if (this.extraBit) 1 else 0
            val bE = if (other.extraBit) 1 else 0
            val newExtra = ((aE xor bE) xor (carryToExtra and 1)) == 1

            this.lo = loSum
            this.hi = hiSum
            this.extraBit = newExtra
        }


        // In-place add: this += number (lower 64-bits only)
        operator fun plusAssign(number: ULong) {
            val oldLo = this.lo
            val loSum = oldLo + number
            val carryLo = if (loSum < oldLo) 1uL else 0uL

            val oldHi = this.hi
            val hiSum = oldHi + carryLo
            val carryToExtra = if (hiSum < oldHi) 1 else 0

            val aE = if (this.extraBit) 1 else 0
            val newExtra = ((aE) xor (carryToExtra and 1)) == 1

            this.lo = loSum
            this.hi = hiSum
            this.extraBit = newExtra
        }


        override operator fun minus(other: V6): V6 {
            // 64-bit low limb
            val borrowLo = if (this.lo < other.lo) 1uL else 0uL
            val loDiff = this.lo - other.lo

            // 64-bit high limb with borrow propagation in two steps to detect borrow-out to the 129th bit
            val hiSub1 = this.hi - other.hi
            val borrowHi0 = if (this.hi < other.hi) 1 else 0
            val hiDiff = hiSub1 - borrowLo
            val borrowHi1 = if (hiSub1 < borrowLo) 1 else 0
            val borrowFromExtra = borrowHi0 or borrowHi1 // 0 or 1

            // 129th bit subtraction: a.extra - b.extra - borrowFromExtra (mod 2)
            val aE = if (this.extraBit) 1 else 0
            val bE = if (other.extraBit) 1 else 0
            val newExtra = ((aE xor bE) xor (borrowFromExtra and 1)) == 1

            return V6(hiDiff, loDiff, newExtra)
        }

        operator fun minusAssign(other: V6) {
            // 64-bit low limb
            val borrowLo = if (this.lo < other.lo) 1uL else 0uL
            val loDiff = this.lo - other.lo

            // 64-bit high limb with borrow propagation
            val hiSub1 = this.hi - other.hi
            val borrowHi0 = if (this.hi < other.hi) 1 else 0
            val hiDiff = hiSub1 - borrowLo
            val borrowHi1 = if (hiSub1 < borrowLo) 1 else 0
            val borrowFromExtra = borrowHi0 or borrowHi1 // 0 or 1

            // 129th bit subtraction: a.extra - b.extra - borrowFromExtra (mod 2)
            val aE = if (this.extraBit) 1 else 0
            val bE = if (other.extraBit) 1 else 0
            val newExtra = ((aE xor bE) xor (borrowFromExtra and 1)) == 1

            this.hi = hiDiff
            this.lo = loDiff
            this.extraBit = newExtra
        }

        operator fun minusAssign(number: ULong) {
            val borrowLo = if (this.lo < number) 1uL else 0uL
            val loDiff = this.lo - number

            val oldHi = this.hi
            val hiDiff = oldHi - borrowLo
            val borrowFromExtra = if (oldHi < borrowLo) 1 else 0

            val aE = if (this.extraBit) 1 else 0
            val newExtra = ((aE) xor (borrowFromExtra and 1)) == 1

            this.hi = hiDiff
            this.lo = loDiff
            this.extraBit = newExtra
        }

        override fun plus(number: UInt): V6 = this + number.toULong()

        // Add ULong without creating a temporary V6
        operator fun plus(number: ULong): V6 {
            val oldLo = this.lo
            val loSum = oldLo + number
            val carryLo = if (loSum < oldLo) 1uL else 0uL

            val oldHi = this.hi
            val hiSum = oldHi + carryLo
            val carryToExtra = if (hiSum < oldHi) 1 else 0

            val newExtra = this.extraBit.xor(carryToExtra == 1)
            return V6(hiSum, loSum, newExtra)
        }

        override operator fun minus(number: UInt): V6 = this - number.toULong()

        // Subtract ULong without creating a temporary V6
        operator fun minus(number: ULong): V6 {
            val borrowLo = if (this.lo < number) 1uL else 0uL
            val loDiff = this.lo - number

            val oldHi = this.hi
            val hiDiff = oldHi - borrowLo
            val borrowFromExtra = if (oldHi < borrowLo) 1 else 0

            val newExtra = this.extraBit.xor(borrowFromExtra == 1)
            return V6(hiDiff, loDiff, newExtra)
        }

        override infix fun and(other: V6): V6 = V6(
            this.hi and other.hi,
            this.lo and other.lo,
            extraBit = this.extraBit && other.extraBit
        )

        override infix fun or(other: V6): V6 = V6(
            this.hi or other.hi,
            this.lo or other.lo,
            extraBit = this.extraBit || other.extraBit
        )

        override infix fun xor(other: V6): V6 = V6(
            this.hi xor other.hi,
            this.lo xor other.lo,
            extraBit = this.extraBit.xor(other.extraBit)
        )

        override operator fun inv(): V6 = V6(
            this.hi.inv(),
            this.lo.inv(),
            extraBit = !this.extraBit
        )

        fun andInPlace(other: V6): V6 {
            this.hi = this.hi and other.hi
            this.lo = this.lo and other.lo
            this.extraBit = this.extraBit && other.extraBit
            return this
        }

        // In-place OR: this = this | other
        fun orInPlace(other: V6): V6 {
            this.hi = this.hi or other.hi
            this.lo = this.lo or other.lo
            this.extraBit = this.extraBit || other.extraBit
            return this
        }

        fun xorInPlace(other: V6): V6 {
            this.hi = this.hi xor other.hi
            this.lo = this.lo xor other.lo
            this.extraBit = this.extraBit xor other.extraBit
            return this
        }

        fun invInPlace(): V6 {
            this.hi = this.hi.inv()
            this.lo = this.lo.inv()
            this.extraBit = !this.extraBit
            return this
        }

        // ----- Shifts (logical) -----
        override infix fun shl(bits: Int): V6 {
            require(bits in 0..128) { "shift must be in [0,128]" }
            if (bits == 0) return this
            var newHi: ULong
            var newLo: ULong
            val newExtra: Boolean
            when {
                bits < 64 -> {
                    // carry lo -> hi, compute extra from hi bit at index (64 - n)
                    newHi = (hi shl bits) or (lo shr (64 - bits))
                    newLo = (lo shl bits)
                    val t = 64 - bits // 0..63
                    newExtra = ((hi shr t) and 1uL) == 1uL
                }

                bits == 64 -> {
                    newHi = lo
                    newLo = 0uL
                    val t = 0
                    newExtra = ((hi shr t) and 1uL) == 1uL
                }

                bits < 128 -> {
                    val k = bits - 64 // 1..63
                    newHi = (lo shl k)
                    newLo = 0uL
                    val t = 128 - bits // 1..63 maps to lo bit index
                    newExtra = ((lo shr t) and 1uL) == 1uL
                }

                else -> { // n == 128
                    newHi = 0uL
                    newLo = 0uL
                    newExtra = (lo and 1uL) == 1uL
                }
            }
            return V6(newHi, newLo, newExtra)
        }

        override infix fun shr(bits: Int): V6 {
            require(bits in 0..128) { "shift must be in [0,128]" }
            if (bits == 0) return this
            var newHi: ULong
            var newLo: ULong
            // After any right shift (n>0), the new extra (bit 128) is always zero.
            val newExtra = false
            when {
                bits < 64 -> {
                    newLo = (lo shr bits) or (hi shl (64 - bits))
                    newHi = (hi shr bits)
                    // Inject previous extraBit into hi at bit index (64 - n)
                    if (extraBit) {
                        val t = 64 - bits // 1..63 (or 0 when n=64, but this branch is n<64)
                        newHi = newHi or (1uL shl t)
                    }
                }

                bits == 64 -> {
                    newLo = hi
                    newHi = if (extraBit) 1uL else 0uL // extraBit moves to hi bit-0
                }

                bits < 128 -> {
                    val k = bits - 64 // 1..63
                    newLo = (hi shr k)
                    if (extraBit) {
                        val t = 128 - bits // 1..63 -> lo bit index
                        newLo = newLo or (1uL shl t)
                    }
                    newHi = 0uL
                }

                else -> { // n == 128
                    newLo = if (extraBit) 1uL else 0uL // extraBit into lo bit-0
                    newHi = 0uL
                }
            }
            return V6(newHi, newLo, newExtra)
        }


        override fun toByteArray(truncate: Boolean): ByteArray {
            if (truncate || !extraBit) {
                val b = ByteArray(16)
                writeULongBE(b, 0, hi)
                writeULongBE(b, 8, lo)
                return b
            }
            val b = ByteArray(17)
            b[0] = if (extraBit) 0x01 else 0x00
            writeULongBE(b, 1, hi)
            writeULongBE(b, 9, lo)
            return b
        }


        override operator fun inc(): V6 {
            this += 1uL
            return this
        }


        override operator fun dec(): V6 {
            this -= 1uL
            return this
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is V6) return false

            if (extraBit != other.extraBit) return false
            if (hi != other.hi) return false
            if (lo != other.lo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = extraBit.hashCode()
            result = 31 * result + hi.hashCode()
            result = 31 * result + lo.hashCode()
            return result
        }

        // ----- Companion utilities for CIDR math -----
        companion object Companion {
            val ZERO = V6(0uL, 0uL)
            val ONE = V6(0uL, 1uL)
            val MAX_VALUE = V6(ULong.MAX_VALUE, ULong.MAX_VALUE, extraBit = true)

            @OptIn(ExperimentalObjCName::class)
            @JvmStatic
            @JvmName("fromLong")
            @ObjCName("fromLong")
            operator fun invoke(number: ULong) = V6(0uL, number)

            /**
             * Parse from 16 or 17 bytes (big-endian).
             * * 16-byte form: standard 128-bit value (extraBit = false).
             * * 17-byte form: the first byte is a header (0x00 or 0x01) representing the 129th MSB,
             *   followed by a 16-byte 128-bit value (hi||lo). Header 0x01 sets extraBit = true.
             *
             * This supports the full 129-bit range [0, 2^129-1].
             *
             * @throws IllegalArgumentException for invalid lengths or invalid header values.
             */
            @Throws(IllegalArgumentException::class)
            @OptIn(ExperimentalObjCName::class)
            @JvmStatic
            @JvmName("fromBytes")
            @ObjCName("fromBytes")
            // ---- V6 factory from BE bytes (canonical: 16 or 17)
            // 17-byte form: first byte is 0x01 (since values < 2^129 ⇒ top byte ∈ {0x00,0x01})
            operator fun invoke(bytes: ByteArray): V6 {
                return when (bytes.size) {
                    16 -> {
                        val hi = readULongBE(bytes, 0)
                        val lo = readULongBE(bytes, 8)
                        V6(hi, lo, extraBit = false)
                    }

                    17 -> {
                        val header = bytes[0].toInt() and 0xFF
                        // For values in [2^128, 2^129-1], header must be 0x01 in canonical encoding.
                        require(header == 0x00 || header == 0x01) {
                            "Invalid first byte for 17-byte 129-bit value: 0x${
                                header.toString(
                                    16
                                )
                            }"
                        }
                        val hi = readULongBE(bytes, 1)
                        val lo = readULongBE(bytes, 9)
                        V6(hi, lo, extraBit = (header == 0x01))
                    }

                    else -> error("Expected 16 or 17 bytes, got ${bytes.size}")
                }
            }

            fun fromUnpadded(bytes: ByteArray): CidrNumber.V6 = CidrNumber.V6(
                if (bytes.size < 16) ByteArray(17).apply {
                    bytes.indices.forEach { this[17 - bytes.size + it] = bytes[it] }
                } else bytes
            )

            // ---- BE limb helpers (your read is fine; write fixed to use 0xFFuL)
            private fun readULongBE(src: ByteArray, offset: Int): ULong {
                var v = 0uL
                for (i in 0 until 8) {
                    v = (v shl 8) or src[offset + i].toUByte().toULong()
                }
                return v
            }

            private fun writeULongBE(out: ByteArray, offset: Int, v: ULong) {
                for (i in 7 downTo 0) {
                    out[offset + (7 - i)] = (v shr (i * 8) and 0xFFuL).toUByte().toByte()
                }
            }
        }
    }
}