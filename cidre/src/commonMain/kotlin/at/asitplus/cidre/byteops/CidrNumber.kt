package at.asitplus.cidre.byteops

import at.asitplus.cidre.byteops.CidrNumber.V4.Companion.fromUnpadded
import at.asitplus.cidre.byteops.CidrNumber.V6.Companion.fromUnpadded
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Number with a byte layout and semantics optimized for CIDR/IP operations.
 * The maximum number of addresses inside an IP address space is 2^32 for IPv4 and 2^128 for IPv6. Hence,
 * 32/128-bit unsigned integers are not enough to represent the size of /0 networks, and an additional bit is needed.
 * At the same time, the actual number of bytes used for addresses and netmasks is 4/16, which is why (by default) a CidrNumber's
 * byte representation is truncated to 4/16 bytes to keep it efficient for CIDR operations.
 *
 * Ranges:
 * - IPv4 ([CidrNumber.V4]): valid values are in [0, 2^32]. Creating a [CidrNumber.V4] from an [ULong] truncates it to the modeled IPv4 width (2^32). It does not signal overflow
 * - IPv6 ([CidrNumber.V6]): valid values are in [0, 2^128].
 *
 * Overflow/underflow semantics:
 * - Arithmetic operations (+ and -) return null on overflow/underflow, ensuring values stay within the valid ranges above.
 * - Bitwise operations (and, or, xor), shifts (shl, shr), and inversion (inv) operate within the modeled bit-width and
 *   always yield a representable value in the respective range.
 *
 * Its String representation is the hex-encoded byte-representation of the number produced by
 * [toByteArray]`(truncate = false)`.
 * I.e., 5 bytes for IPv4 when equal to 2^32 and 17 bytes for IPv6 when equal to 2^128; otherwise 4/16 bytes.
 *
 * @see toByteArray
 */

sealed interface CidrNumber<TSelf : CidrNumber<TSelf>> : Comparable<TSelf> {
    /**
     * Adds [other] to this CIDR number.
     *
     * Nullability semantics:
     * - Returns `null` if the mathematical sum exceeds the maximum valid value
     *   (i.e., > 2^32 for IPv4 or > 2^128 for IPv6).
     * - Otherwise returns the exact sum.
     */
    operator fun plus(other: TSelf): TSelf?

    /**
     * Subtracts [other] from this CIDR number.
     *
     * Nullability semantics:
     * - Returns `null` if the mathematical result would be negative (underflow).
     * - Otherwise returns the exact difference.
     */
    operator fun minus(other: TSelf): TSelf?

    /**
     * Adds an unsigned 32-bit integer to this CIDR number.
     *
     * Nullability semantics:
     * - Returns `null` if the mathematical sum exceeds the maximum valid value
     *   (i.e., > 2^32 for IPv4 or > 2^128 for IPv6).
     * - Otherwise returns the exact sum.
     */
    operator fun plus(other: UInt): TSelf?

    /**
     * Subtracts an unsigned 32-bit integer from this CIDR number.
     *
     * Nullability semantics:
     * - Returns null if the mathematical result would be negative (underflow).
     * - Otherwise returns the exact difference.
     */
    operator fun minus(other: UInt): TSelf?

    /**
     * Bitwise AND within the modeled bit width.
     *
     * Behavior:
     * - Always succeeds
     * - Never returns `null`.
     */
    infix fun and(other: TSelf): TSelf

    /**
     * Bitwise OR within the modeled bit width.
     *
     * Behavior:
     * - Always succeeds
     * - Never returns `null`.
     */
    infix fun or(other: TSelf): TSelf

    /**
     * Bitwise XOR within the modeled bit width.
     *
     * Behavior:
     * - Always succeeds
     * - Never returns `null`.
     */
    infix fun xor(other: TSelf): TSelf

    /**
     * Logical left shift within the modeled bit width.
     *
     * Behavior:
     * - Always succeeds; bits shifted out are discarded, zeros shifted in.
     * - Never returns `null`.
     *
     * @throws IllegalArgumentException if the shift count is outside the supported range for the family.
     */
    @Throws(IllegalArgumentException::class)
    infix fun shl(bits: Int): TSelf

    /**
     * Logical right shift within the modeled bit width.
     *
     * Behavior:
     * - Always succeeds; bits shifted out are discarded, zeros shifted in.
     * - Never returns `null`.
     *
     * @throws IllegalArgumentException if the shift count is outside the supported range for the family.
     */
    @Throws(IllegalArgumentException::class)
    infix fun shr(bits: Int): TSelf

    /**
     * Bitwise inversion within the modeled bit width.
     *
     * Behavior:
     * - Always succeeds; flips all bits (33 bits for IPv4, 129 bits for IPv6).
     * - Never returns `null`.
     */
    operator fun inv(): TSelf

    /**
     * Converts the internal representation of the object into a ByteArray optimized for CIDR/IP operations.
     * The maximum number of addresses inside an IP address space is 2^32 for IPv4 and 2^128 for IPv6. Hence,
     * 32/128-bit numbers are not enough to represent the size of /0 networks, and an additional bit is needed.
     *
     * By default, this function truncates to 4/16 bytes (sufficient for most CIDR operations).
     * To serialize the full range (including 2^32 for IPv4 and 2^128 for IPv6), set [truncate] = false.
     */
    fun toByteArray(truncate: Boolean = true): ByteArray

    companion object {

        /**
         * Parse from 4 or 5 bytes (IPv4) and 16 or 17 bytes (IPv6).
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


        override fun toString(): String = toByteArray(truncate = false).toHexString()

        companion object Companion {
            /**
             * Creates a [CidrNumber.V4] from a raw unsigned 64-bit value by truncating it to the modeled IPv4 width (2^32).
             *
             * Truncation semantics:
             * - The input [raw] is masked to the V4 modeling width (33 bits). Any bits above that width are discarded.
             * - It does not signal overflow; it intentionally truncates to fit the internal representation.
             *
             * - Use when you need to coerce a value into the V4 modeling range (e.g., from a larger integer)
             *   and you accept truncation.
             * - If you need overflow detection (i.e., to ensure results stay within [0, 2^32] without silent truncation),
             *   use the arithmetic APIs (plus/minus) which return null on overflow/underflow, or construct from bytes
             *   using the documented 4/5-byte value-preserving encodings.
             */
            @OptIn(ExperimentalObjCName::class)
            @JvmStatic
            @JvmName("fromLong")
            @ObjCName("fromLong")
            operator fun invoke(raw: ULong) = CidrNumber.V4(raw and 0x1FFFFFFFFuL)
            val ZERO = CidrNumber.V4(0u)
            val ONE = CidrNumber.V4(1u)
            val MAX_VALUE = CidrNumber.V4((1uL shl 32))

            /**
             * Parse from exactly 4 or 5 bytes. To parse unpadded byte arrays use [fromUnpadded].
             * Valid big-endian encoded values are in [0, 2^32].
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

            /**
             * Parse unpadded byte arrays.
             * Valid big-endian encoded values are in [0, 2^32].
             *
             * @throws IllegalArgumentException for invalid lengths
             */
            fun fromUnpadded(bytes: ByteArray): CidrNumber.V4 = CidrNumber.V4(
                if (bytes.size < 4) ByteArray(5).apply {
                    bytes.indices.forEach { this[5 - bytes.size + it] = bytes[it] }
                } else bytes
            )
        }


        override fun plus(other: CidrNumber.V4): CidrNumber.V4? {
            val max = MAX_VALUE.raw
            val b = other.raw
            return if (raw > max - b) null else CidrNumber.V4(raw + b)
        }

        override fun plus(other: UInt): CidrNumber.V4? {
            val b = other.toULong()
            val max = MAX_VALUE.raw
            return if (raw > max - b) null else CidrNumber.V4(raw + b)
        }

        override fun minus(other: CidrNumber.V4): CidrNumber.V4? {
            val b = other.raw
            return if (raw < b) null else CidrNumber.V4(raw - b)
        }

        override fun minus(other: UInt): CidrNumber.V4? {
            val b = other.toULong()
            return if (raw < b) null else CidrNumber.V4(raw - b)
        }

        override fun and(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw.and(other.raw))

        override fun or(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw.or(other.raw))

        override fun xor(other: CidrNumber.V4): CidrNumber.V4 = CidrNumber.V4(raw.xor(other.raw))

        override fun shl(bits: Int): CidrNumber.V4 = CidrNumber.V4(raw.shl(bits))

        override fun shr(bits: Int): CidrNumber.V4 = CidrNumber.V4(raw.shr(bits))

        override fun inv(): CidrNumber.V4 = CidrNumber.V4(raw.inv())

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

    class V6 internal constructor(private var hi: ULong, private var lo: ULong, private var extraBit: Boolean = false) :
        CidrNumber<V6> {

        override fun toString(): String = toByteArray(truncate = false).toHexString()

        override fun compareTo(other: V6): Int = when {
            this.extraBit != other.extraBit -> if (this.extraBit) 1 else -1
            this.hi != other.hi -> if (this.hi < other.hi) -1 else 1
            this.lo != other.lo -> if (this.lo < other.lo) -1 else 1
            else -> 0
        }

        override operator fun plus(other: V6): V6? {
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
            // overflow to 130th bit if the sum into the 129th bit >= 2
            val overflow = (aE + bE + carryOutHi) >= 2
            if (overflow) return null

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


        override operator fun minus(other: V6): V6? {
            // underflow if this < other
            if (this < other) return null
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

        override operator fun plus(number: UInt): V6? = this + number.toULong()

        operator fun plus(number: ULong): V6? {
            val oldLo = this.lo
            val loSum = oldLo + number
            val carryLo = if (loSum < oldLo) 1uL else 0uL

            val oldHi = this.hi
            val hiSum = oldHi + carryLo
            val carryToExtra = if (hiSum < oldHi) 1 else 0

            val aE = if (this.extraBit) 1 else 0
            val newExtra = ((aE) xor (carryToExtra and 1)) == 1
            // overflow if extraBit (1) + carryToExtra (1) would carry out beyond 129th bit
            if (aE + carryToExtra >= 2) return null

            return V6(hiSum, loSum, newExtra)
        }

        override operator fun minus(number: UInt): V6? = this - number.toULong()

        operator fun minus(number: ULong): V6? {
            // underflow if value < number (since number is <= 2^64-1 and has extraBit=false, hi=0)
            if (!this.extraBit && this.hi == 0uL && this.lo < number) return null
            val borrowLo = if (this.lo < number) 1uL else 0uL
            val loDiff = this.lo - number

            val oldHi = this.hi
            val hiDiff = oldHi - borrowLo
            val borrowFromExtra = if (oldHi < borrowLo) 1 else 0

            val aE = if (this.extraBit) 1 else 0
            val newExtra = ((aE) xor (borrowFromExtra and 1)) == 1

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
            val MAX_VALUE = V6(0uL, 0uL, extraBit = true)

            @OptIn(ExperimentalObjCName::class)
            @JvmStatic
            @JvmName("fromLong")
            @ObjCName("fromLong")
            operator fun invoke(number: ULong) = V6(0uL, number)

            /**
             * Parse from exactly 16 or 17 bytes. To parse unpadded byte arrays use [fromUnpadded].
             * Valid big-endian encoded values are in [0, 2^128].
             *
             * @throws IllegalArgumentException for invalid lengths
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

            /**
             * Parse from unpadded byte arrays.
             * Valid big-endian encoded values are in [0, 2^32].
             *
             * @throws IllegalArgumentException for invalid lengths
             */
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