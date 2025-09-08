import at.asitplus.*
import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpNetwork
import at.asitplus.cidre.byteops.toNetmask
import kotlinx.serialization.json.Json
import kotlin.test.*

private val json = Json

class TestAgainstPython {

    val parsing = json.decodeFromString<ParsingFixture>(resourceText("pythontest/parsing.json"))
    val normalization = json.decodeFromString<NormalizationFixture>(resourceText("pythontest/normalization.json"))
    val overlaps_containment =
        json.decodeFromString<OverlapsContainmentFixture>(resourceText("pythontest/overlaps_containment.json"))
    val set_operations = json.decodeFromString<SetOperationsFixture>(resourceText("pythontest/set_operations.json"))
    val subnetting = json.decodeFromString<SubnettingFixture>(resourceText("pythontest/subnetting.json"))
    val supernetting = json.decodeFromString<SupernettingFixture>(resourceText("pythontest/supernetting.json"))
    val net_containment = json.decodeFromString<NetContainmentFixture>(resourceText("pythontest/net_containment.json"))
    val addr_membership = json.decodeFromString<MembershipFixture>(resourceText("pythontest/addr_membership.json"))
    val ip_sort = json.decodeFromString<IpComparisonFixture>(resourceText("pythontest/ip_sort.json"))
    val netmask = json.decodeFromString<NetmaskFixture>(resourceText("pythontest/netmask.json"))


    @Test
    fun validIpParsing() = parsing.validAddresses.forEach { (str, hex) ->
        val parsed = IpAddress(str)
        if (str.contains(IpAddress.V6.segmentSeparator)) assertIs<IpAddress.V6>(parsed)
        else assertIs<IpAddress.V4>(parsed)
        assertContentEquals(parsed.octets, hex.hexToByteArray())

    }

    @Test
    fun invalidIpParsing() = parsing.invalidAddresses.forEach {
        assertFailsWith(IllegalArgumentException::class) {
            IpAddress(it)

        }
    }

    @Test
    //norm uses canonicalized form
    fun canonicalization() = normalization.cases.forEach { case ->
        val ip = case.input.split('/').first()
        assertEquals(ip, IpAddress(ip).toString())
    }


    @Test
    fun netmask() = netmask.cases.forEach { case ->
        assertContentEquals(case.netmaskHex.hexToByteArray(), case.prefix.toUInt().toNetmask(case.octetCount))
    }

    @Test
    fun normalization() =   normalization.cases.forEach { case ->
            val (netAddr, prefix) = case.expectNetwork.split('/').let { IpAddress(it.first()) to it.last().toUInt() }
            val input = IpAddress(case.input.split('/').first())

            //generic
            if (case.expectNetwork == case.input) {
                val net = IpNetwork(case.input)
                assertEquals(netAddr, net.address)

            } else assertFailsWith(IllegalArgumentException::class) {
                IpNetwork(case.input)
            }.message.let {
                assertContains(it!!, "$input is not an actual network address. Should be:")
            }.also {
                //now we normalize
                IpNetwork(case.input, strict = false)
                val actual = IpNetwork(input, prefix, strict = false).address
                assertEquals(netAddr, actual)
                assertNotSame(input, actual)
            }

            //case split
            when (input) {
                is IpAddress.V4 -> {
                    if (case.expectNetwork == case.input) {
                        val net = IpNetwork.V4(case.input)
                        assertEquals(netAddr, net.address)
                    } else assertFailsWith(IllegalArgumentException::class) {
                        IpNetwork.V4(case.input)

                    }.message.let {
                        assertContains(it!!, "$input is not an actual network address. Should be:")
                    }.also {
                        //now we normalize
                        val net = IpNetwork.V4(case.input, strict = false)
                        /*containment test*/ assertTrue(net.contains(input))
                        val actual = IpNetwork.V4(input, prefix, strict = false).address
                        assertEquals(netAddr, actual)
                        assertNotSame(input, actual)
                    }

                }

                is IpAddress.V6 -> {
                    if (case.expectNetwork == case.input) {
                        val net = IpNetwork.V6(case.input)
                        assertEquals(netAddr, net.address)
                    } else assertFailsWith(IllegalArgumentException::class) {
                        IpNetwork.V6(case.input)

                    }.message.let {
                        assertContains(it!!, "$input is not an actual network address. Should be:")
                    }.also {
                        //now we normalize
                        val net = IpNetwork.V6(case.input, strict = false)
                        /*containment test*/ assertTrue(net.contains(input))
                        val actual = IpNetwork.V6(input, prefix, strict = false).address
                        assertEquals(netAddr, actual)
                        assertNotSame(input, actual)
                    }

                }
            }

            //in-place wrapping
            assertSame(netAddr, IpNetwork.forAddress(netAddr, prefix).address)

        }


    @Test
    fun ipSortingTest()= ip_sort.cases.forEach { case ->
            val (a, b) = when (case.version) {
                "V4" -> IpAddress.V4(case.a) to IpAddress.V4(case.b)
                "V6" -> IpAddress.V6(case.a) to IpAddress.V6(case.b)
                else -> throw AssertionError()
            }
            val cmp = when (a) {
                is IpAddress.V4 -> a.compareTo(b as IpAddress.V4)
                is IpAddress.V6 -> a.compareTo(b as IpAddress.V6)
            }
            assertEquals(case.cmp, cmp)
        }


    @Test
    fun netContainment() = net_containment.cases.forEach { case ->
        val inner = IpNetwork(case.inner)
        val outer = IpNetwork(case.outer)

        when(inner) {
            is IpNetwork.V4 -> {
                assertIs<IpNetwork.V4>(outer)
                assertEquals(case.expect, outer.contains(inner))
            }
            is IpNetwork.V6 -> {
                assertIs<IpNetwork.V6>(outer)
                assertEquals(case.expect, outer.contains(inner))
            }
        }
    }

    private fun resourceText(path: String): String =
        this::class.java.classLoader.getResourceAsStream(path).reader(Charsets.UTF_8).readText()

}

