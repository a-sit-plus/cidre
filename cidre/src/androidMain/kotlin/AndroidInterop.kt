package at.asitplus.cidre

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

@Throws(IllegalArgumentException::class)
operator fun IpAddress.Companion.invoke(inetAddress: InetAddress) = IpAddress.Companion(inetAddress.address)

//this must never throw if our implementation is correct
operator fun IpAddress.V4.Companion.invoke(inetAddress: Inet4Address) = IpAddress.V4(inetAddress.address)

//this must never throw if our implementation is correct
operator fun IpAddress.V6.Companion.invoke(inetAddress: Inet6Address) = IpAddress.V6(inetAddress.address)

//this must never throw if our implementation is correct
fun IpAddress<*>.toInetAddress() = InetAddress.getByAddress(octets)

//this must never throw if our implementation is correct
fun IpAddress.V4.toInetAddress() = Inet4Address.getByAddress(octets)

//this must never throw if our implementation is correct
fun IpAddress.V6.toInetAddress() = Inet6Address.getByAddress(octets)
