import at.asitplus.cidre.IpInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IpInterfaceEqualsTest {

    @Test
    fun `IPv4 equals and hashCode`() {
        val ip1 = IpInterface.V4("192.168.1.1/24")
        val ip2 = IpInterface.V4("192.168.1.1/24")
        val ip3 = IpInterface.V4("192.168.1.2/24")
        val ip4 = IpInterface.V4("192.168.1.1/16")

        assertEquals(ip1, ip2, "Should be equal")
        assertNotEquals(ip1, ip3, "Different addresses should not be equal")
        assertNotEquals(ip1, ip4, "Different prefixes should not be equal")

        assertEquals(ip1.hashCode(), ip2.hashCode(), "Equal objects must have equal hashCodes")
        assertNotEquals(ip1.hashCode(), ip3.hashCode(), "Should have different hashCodes")
        assertNotEquals(ip1.hashCode(), ip4.hashCode(), "Should have different hashCodes")
    }

    @Test
    fun `IPv6 equals and hashCode`() {
        val ip1 = IpInterface.V6("2001:db8::1/64")
        val ip2 = IpInterface.V6("2001:db8::1/64")
        val ip3 = IpInterface.V6("2001:db8::2/64")
        val ip4 = IpInterface.V6("2001:db8::1/48")

        assertEquals(ip1, ip2, "Should be equal")
        assertNotEquals(ip1, ip3, "Different addresses should not be equal")
        assertNotEquals(ip1, ip4, "Different prefixes should not be equal")

        assertEquals(ip1.hashCode(), ip2.hashCode(), "Equal objects must have equal hashCodes")
        assertNotEquals(ip1.hashCode(), ip3.hashCode(), "Should have different hashCodes")
        assertNotEquals(ip1.hashCode(), ip4.hashCode(), "Should have different hashCodes")
    }

    @Test
    fun `IPv4 and IPv6 are not equal`() {
        val ipv4 = IpInterface.V4("192.168.1.1/24")
        val ipv6 = IpInterface.V6("2001:db8::1/64")

        assertNotEquals<IpInterface<*, *>>(ipv4, ipv6, "IPv4 and IPv6 should never be equal")
    }
}