import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpInterface
import at.asitplus.cidre.IpNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegressionFindingsTest {

    @Test
    fun specialRangeFlagsShouldUseContainmentNotEquality() {
        assertTrue(IpNetwork.V4("10.0.0.0/9").isPrivate)
        assertTrue(IpNetwork.V4("127.1.2.0/24").isLoopback)
        assertTrue(IpNetwork.V4("224.1.2.0/24").isMulticast)
        assertTrue(IpNetwork.V4("169.254.42.0/24").isLinkLocal)
        assertFalse(IpNetwork.V4("10.0.0.0/9").isPublic)

        assertTrue(IpNetwork.V6("2001:db8:1::/48").isDocumentation)
        assertTrue(IpNetwork.V6("fc00:1::/32").isUniqueLocal)
        assertTrue(IpNetwork.V6("fd00:1::/32").isUniqueLocalLocallyAssigned)
        assertTrue(IpNetwork.V6("ff01::/16").isMulticast)
        assertTrue(IpNetwork.V6("fe80::1/128").isLinkLocal)
        assertTrue(IpNetwork.V6("4000::/3").isReserved)
    }

    @Test
    fun containsInterfaceShouldValidateAddressMembership() {
        val net = IpNetwork.V4("10.0.0.0/24")
        val outside = IpAddress.V4("11.0.0.1")
        val iface = net.interfaceFor(outside)

        assertFalse(net.contains(iface))
    }

    @Test
    fun addressSpaceShouldNotDuplicateSingleAddressNetworks() {
        assertEquals(1, IpNetwork.V4("1.2.3.4/32").addressSpace.count())
        assertEquals(1, IpNetwork.V6("2001:db8::1/128").addressSpace.count())
    }

    @Test
    fun ipv6ExpandedToStringShouldPadHextetsToFourDigits() {
        val ip = IpAddress.V6("2001:db8::1")
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", ip.toString(expanded = true))
    }

    @Test
    fun leadingPrefixSignedConstructorShouldRejectValuesOutsideUnsignedByte() {
        assertFailsWith<IllegalArgumentException> {
            IpAddress.V4.LeadingPrefix(8, 300)
        }
    }
}
