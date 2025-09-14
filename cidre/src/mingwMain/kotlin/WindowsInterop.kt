package at.asitplus.cidre

import kotlinx.cinterop.*
import platform.posix.in_addr
import platform.posix.memcpy
import platform.windows.in6_addr

@OptIn(ExperimentalForeignApi::class)
fun IpAddress.V4.toInAddr(): CValue<in_addr> =
    cValue {
        // Interpret 4 octets as UInt32 in network byte order
        val value = ((octets[0].toUInt() and 0xFFu) shl 24) or
                ((octets[1].toUInt() and 0xFFu) shl 16) or
                ((octets[2].toUInt() and 0xFFu) shl 8) or
                (octets[3].toUInt() and 0xFFu)
        S_un.S_addr = value
    }


@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
fun IpAddress.V6.toInAddr(): CValue<in6_addr> =
    memScoped {
        val v6 = alloc<in6_addr>()
        octets.usePinned { src -> memcpy(v6.ptr, src.addressOf(0), 16.convert()) }
        v6.readValue()
    }

/**@return `CValue<in6_addr>` for IPv6 and `CValue<in_addr>` for IPv4 */
@OptIn(ExperimentalForeignApi::class)
fun IpAddress<*, *>.toInAddr(): CValue<out CStructVar> = when (this) {
    is IpAddress.V4 -> toInAddr()
    is IpAddress.V6 -> toInAddr()
}


@OptIn(ExperimentalForeignApi::class)
operator fun IpAddress.Companion.invoke(addr: CValue<in_addr>): IpAddress.V4 = IpAddress.V4(addr)

@OptIn(ExperimentalForeignApi::class)
operator fun IpAddress.Companion.invoke(addr: CPointer<in_addr>): IpAddress.V4 = IpAddress.V4(addr)

@OptIn(ExperimentalForeignApi::class)
operator fun IpAddress.Companion.invoke(addr: CValue<in6_addr>): IpAddress.V6 = IpAddress.V6(addr)

@OptIn(ExperimentalForeignApi::class)
operator fun IpAddress.Companion.invoke(addr: CPointer<in6_addr>): IpAddress.V6 = IpAddress.V6(addr)


// IPv4: CValue<in_addr> -> IpAddress.V4
@OptIn(ExperimentalForeignApi::class)
operator fun IpAddress.V4.Companion.invoke(addr: CValue<in_addr>): IpAddress.V4 = addr.useContents {
    val v: UInt = S_un.S_addr
    val b0 = ((v shr 24) and 0xFFu).toUByte().toByte()
    val b1 = ((v shr 16) and 0xFFu).toUByte().toByte()
    val b2 = ((v shr 8) and 0xFFu).toUByte().toByte()
    val b3 = (v and 0xFFu).toUByte().toByte()
    IpAddress.V4(byteArrayOf(b0, b1, b2, b3))
}

// Optional convenience overload (pointer)
@OptIn(ExperimentalForeignApi::class)
operator fun IpAddress.V4.Companion.invoke(addr: CPointer<in_addr>): IpAddress.V4 = addr.pointed.run {
    val v: UInt = S_un.S_addr
    val b0 = ((v shr 24) and 0xFFu).toUByte().toByte()
    val b1 = ((v shr 16) and 0xFFu).toUByte().toByte()
    val b2 = ((v shr 8) and 0xFFu).toUByte().toByte()
    val b3 = (v and 0xFFu).toUByte().toByte()
    IpAddress.V4(byteArrayOf(b0, b1, b2, b3))
}

// IPv6: CValue<in6_addr> -> IpAddress.V6
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
operator fun IpAddress.V6.Companion.invoke(addr: CValue<in6_addr>): IpAddress.V6 = memScoped {
    val out = ByteArray(16)
    out.usePinned { dst ->
        // Copy raw 16 bytes from the C struct into the ByteArray
        memcpy(dst.addressOf(0), addr.ptr, 16.convert())
    }
    IpAddress.V6(out)
}

// Optional convenience overload (pointer)
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
operator fun IpAddress.V6.Companion.invoke(addr: CPointer<in6_addr>): IpAddress.V6 = memScoped {
    val out = ByteArray(16)
    out.usePinned { dst ->
        memcpy(dst.addressOf(0), addr, 16.convert())
    }
    IpAddress.V6(out)
}


