package at.asitplus

import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpNetwork
import at.asitplus.cidre.byteops.CidrNumber
import at.asitplus.cidre.withSameFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SameFamilyScopeTest {

    @Test
    fun addressScopeWorksForV4() {
        val lower: IpAddress<*, *> = IpAddress("192.168.0.1")
        val higher: IpAddress<*, *> = IpAddress("192.168.0.99")

        val result = higher.withSameFamily(lower) {
            assertEquals(CidrNumber.V4(98u), left - right)
            left and right
        }

        assertEquals(IpAddress("192.168.0.1"), result)
    }

    @Test
    fun addressScopeWorksForV6() {
        val lower: IpAddress<*, *> = IpAddress("2001:db8::1")
        val higher: IpAddress<*, *> = IpAddress("2001:db8::2")

        val result = higher.withSameFamily(lower) {
            assertEquals(CidrNumber.V6(1u), left - right)
            left xor right
        }

        assertEquals(IpAddress("::3"), result)
    }

    @Test
    fun addressScopeSkipsMixedFamilies() {
        val blockResult = IpAddress("192.168.0.1").withSameFamily(IpAddress("2001:db8::1")) {
            left - right
        }

        assertNull(blockResult)
    }

    @Test
    fun addressScopeNarrowsBothSidesFromOneFamilyCheck() {
        val lower: IpAddress<*, *> = IpAddress("192.168.0.1")
        val higher: IpAddress<*, *> = IpAddress("192.168.0.99")

        val distance = higher.withSameFamily(lower) {
            whenFamily(
                v4 = {
                    val knownLeft: IpAddress.V4 = left
                    val knownRight: IpAddress.V4 = right
                    knownLeft - knownRight
                },
                v6 = {
                    null
                }
            )
        }

        assertEquals(CidrNumber.V4(98u), distance)
    }

    @Test
    fun addressScopeCanNarrowOtherSideWhenOneSideIsKnown() {
        val lower: IpAddress<*, *> = IpAddress("192.168.0.1")
        val higher: IpAddress.V4 = IpAddress.V4("192.168.0.99")

        val distance = higher.withSameFamily(lower) {
            whenFamily(
                v4 = {
                    val knownRight: IpAddress.V4 = right
                    higher - knownRight
                },
                v6 = {
                    null
                }
            )
        }

        assertEquals(CidrNumber.V4(98u), distance)
    }

    @Test
    fun networkScopeWorksForV4() {
        val left: IpNetwork<*, *> = IpNetwork("192.168.0.0/25")
        val right: IpNetwork<*, *> = IpNetwork("192.168.0.128/25")

        val merged = left.withSameFamily(right) {
            assertFalse(left overlaps right)
            assertTrue(left isAdjacentTo right)
            assertTrue(left canMergeWith right)
            left + right
        }

        assertEquals(IpNetwork("192.168.0.0/24"), merged)
    }

    @Test
    fun networkScopeWorksForV6() {
        val outer: IpNetwork<*, *> = IpNetwork("2001:db8::/32")
        val inner: IpNetwork<*, *> = IpNetwork("2001:db8:1::/48")

        val contains = outer.withSameFamily(inner) {
            assertTrue(inner isSubnetOf outer)
            assertTrue(outer isSupernetOf inner)
            assertTrue(outer overlaps inner)
            inner in outer
        }

        assertTrue(assertNotNull(contains))
    }

    @Test
    fun networkScopeSkipsMixedFamilies() {
        val blockResult = IpNetwork("192.168.0.0/24").withSameFamily(IpNetwork("2001:db8::/32")) {
            left overlaps right
        }

        assertNull(blockResult)
    }

    @Test
    fun networkAddressScopeWorksBothWays() {
        val network: IpNetwork<*, *> = IpNetwork("192.168.0.0/24")
        val address: IpAddress<*, *> = IpAddress("192.168.0.42")

        val fromNetwork = network.withSameFamily(address) {
            assertTrue(contains())
            address in network
        }
        val fromAddress = address.withSameFamily(network) {
            assertTrue(isInNetwork())
            address in network
        }

        assertTrue(assertNotNull(fromNetwork))
        assertTrue(assertNotNull(fromAddress))
    }

    @Test
    fun networkAddressScopeNarrowsBothSides() {
        val network: IpNetwork<*, *> = IpNetwork("2001:db8::/32")
        val address: IpAddress<*, *> = IpAddress("2001:db8::42")

        val contains = network.withSameFamily(address) {
            whenFamily(
                v4 = {
                    false
                },
                v6 = {
                    val knownNetwork: IpNetwork.V6 = network
                    val knownAddress: IpAddress.V6 = address
                    knownAddress in knownNetwork
                }
            )
        }

        assertTrue(assertNotNull(contains))
    }

    @Test
    fun networkAddressScopeSkipsMixedFamiliesBothWays() {
        val network: IpNetwork<*, *> = IpNetwork("192.168.0.0/24")
        val address: IpAddress<*, *> = IpAddress("2001:db8::42")

        assertNull(network.withSameFamily(address) { contains() })
        assertNull(address.withSameFamily(network) { isInNetwork() })
    }
}
