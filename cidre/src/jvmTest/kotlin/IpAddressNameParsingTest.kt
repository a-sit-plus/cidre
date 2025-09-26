import at.asitplus.cidre.IpInterface
import at.asitplus.cidre.IpNetwork
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IpAddressNameParsingTest {

    // IPv4 raw bytes
    private val ipv4WithMask1 = byteArrayOf(0x0a, 0x09, 0x08, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00)
    private val ipv4WithMask2 = byteArrayOf(0x0a, 0x09, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(), 0x80.toByte(), 0x00)
    private val ipv4WithMask3 = byteArrayOf(0x0a, 0x09, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(), 0xc0.toByte(), 0x00)

    private val ipv4WithMask1Str = "10.9.8.0/24"
    private val ipv4WithMask2Str = "10.9.0.0/17"
    private val ipv4WithMask3Str = "10.9.0.0/18"

    // IPv6 raw bytes
    private val ipv6a = byteArrayOf(
        0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x8a.toByte(), 0x2e, 0x0a, 0x09, 0x08, 0x00,
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    private val ipv6b = byteArrayOf(
        0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x8a.toByte(), 0x2e, 0x0a, 0x09, 0x08, 0x00,
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()
    )

    private val ipv6c = byteArrayOf(
        0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    private val ipv6d = byteArrayOf(
        0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xfe.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    private val ipv6e = byteArrayOf(
        0x20, 0x01, 0x0d, 0xb8.toByte(), 0x85.toByte(), 0xa3.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0x80.toByte(), 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    private val ipv6aStr = "2001:db8:85a3::8a2e:a09:800/48"
    private val ipv6bStr = "2001:db8:85a3::8a2e:a09:800/128"
    private val ipv6cStr = "2001:db8:85a3::/48"
    private val ipv6dStr = "2001:db8:85a3::/47"
    private val ipv6eStr = "2001:db8:85a3::/49"

    @Test fun parseIPv6a() = parseCheck(ipv6a, IpInterface.V6::class, ipv6aStr)
    @Test fun parseIPv6b() = parseCheck(ipv6b, IpInterface.V6::class, ipv6bStr)
    @Test fun parseIPv6c() = parseCheck(ipv6c, IpInterface.V6::class, ipv6cStr)
    @Test fun parseIPv6d() = parseCheck(ipv6d, IpInterface.V6::class, ipv6dStr)
    @Test fun parseIPv6e() = parseCheck(ipv6e, IpInterface.V6::class, ipv6eStr)

    @Test fun parseIPv4mask24() = parseCheck(ipv4WithMask1, IpInterface.V4::class, ipv4WithMask1Str)
    @Test fun parseIPv4mask17() = parseCheck(ipv4WithMask2, IpInterface.V4::class, ipv4WithMask2Str)
    @Test fun parseIPv4mask18() = parseCheck(ipv4WithMask3, IpInterface.V4::class, ipv4WithMask3Str)

    private fun parseCheck(bytes: ByteArray, expectedType: KClass<out IpInterface<*, *>>, expectedString: String) {
        val addressAndPrefix = IpInterface.fromX509Octets(bytes)
        assertTrue(expectedType.isInstance(addressAndPrefix))
        assertContentEquals(bytes, addressAndPrefix.toX509Octets())
        assertEquals(expectedString, addressAndPrefix.toString())
    }
}
