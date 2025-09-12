import at.asitplus.*
import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpNetwork
import at.asitplus.cidre.byteops.Size
import at.asitplus.cidre.byteops.toNetmask
import kotlinx.serialization.json.Json
import kotlin.test.*

private val json = Json { ignoreUnknownKeys = true }

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
    val net_props = json.decodeFromString<NetworkPropsFixture>(resourceText("pythontest/net_props.json"))
    val overlogs = json.decodeFromString<OverlongFixture>(resourceText("pythontest/overlongs.json"))
    val merge_cases = json.decodeFromString<List<MergeCase>>(resourceText("pythontest/merge_cases.json"))

    @Test
    fun overlogs() = overlogs.tests.forEach { case ->
        println(case.input + " ${case.operation} " + case.argument + " = " + case.output)
        when (case.operation) {
            "AND" -> assertEquals(
                Size.V6(case.output.hexToByteArray()),
                Size.V6(case.input.hexToByteArray()) and Size.V6(case.argument!!.hexToByteArray())
            )

            "OR" -> assertEquals(
                Size.V6(case.output.hexToByteArray()),
                Size.V6(case.input.hexToByteArray()) or Size.V6(case.argument!!.hexToByteArray())
            )

            "XOR" -> assertEquals(
                Size.V6(case.output.hexToByteArray()),
                Size.V6(case.input.hexToByteArray()) xor Size.V6(case.argument!!.hexToByteArray())
            )

            "SHR" -> assertEquals(
                Size.V6(case.output.hexToByteArray()),
                Size.V6(case.input.hexToByteArray()) shr case.argument!!.toInt()
            )

            "SHL" -> assertEquals(
                Size.V6(case.output.hexToByteArray()),
                Size.V6(case.input.hexToByteArray()) shl case.argument!!.toInt()
            )

            "INV" -> assertEquals(Size.V6(case.output.hexToByteArray()), Size.V6(case.input.hexToByteArray()).inv())
        }
    }

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
    fun normalization() = normalization.cases.forEach { case ->
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
    fun ipSortingTest() = ip_sort.cases.forEach { case ->
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
    fun addrMembership() = addr_membership.cases.forEach { case ->
        val inner = IpAddress(case.addr) as IpAddress<Number, Any>
        val outer = IpNetwork(case.network) as IpNetwork<Number, Any>

        assertEquals(case.expect, outer.contains(inner))
    }


    @Test
    fun netContainment() = net_containment.cases.forEach { case ->
        val inner = IpNetwork(case.inner)
        val outer = IpNetwork(case.outer)

        when (inner) {
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

    @Test
    fun testRelations() = net_props.adjacency_cases.forEach { case ->
        val a = IpNetwork(case.a_cidr) as IpNetwork<Number, Any>
        val b = IpNetwork(case.b_cidr) as IpNetwork<Number, Any>
        assertEquals(case.are_adjacent, a.isAdjacentTo(b))
        assertEquals(case.are_adjacent, b.isAdjacentTo(a))

        assertEquals(case.overlaps, a.overlaps(b))
        assertEquals(case.overlaps, b.overlaps(a))

        if (case.relation == "A_contains_B") {
            assertTrue(a.contains(b))
            assertFalse(b.contains(a))
            assertNotEquals(b, a)
        } else if (case.relation == "B_contains_A") {
            assertTrue(b.contains(a))
            assertFalse(a.contains(b))
            assertNotEquals(b, a)
        } else if (case.relation == "equal") {
            assertEquals(b, a)
            assertTrue(a.contains(b))
            assertTrue(b.contains(a))
        } else if (case.relation == "disjoint") {
            assertNotEquals(b, a)
            assertFalse(a.contains(b))
            assertFalse(b.contains(a))
            assertFalse(b.overlaps(a))
            assertFalse(b.isAdjacentTo(a))
        }
    }

    @Test
    fun netProps() = net_props.test_networks.forEach {
        val nwAddr = IpAddress(it.address)
        val lastAddr = IpAddress(it.last_address)
        val lastAssigneable = IpAddress(it.last_assignable)
        val firstAssigneable = IpAddress(it.first_assignable)
        val net = IpNetwork(it.cidr)
        assertEquals(nwAddr, net.address)
        assertEquals(lastAddr, net.lastAddress)
        assertEquals(lastAssigneable, net.lastAssignableHost.address)
        assertEquals(firstAssigneable, net.firstAssignableHost.address)

        if (net is IpNetwork.V4) {
            if (it.broadcast == null) assertNull(net.broadcastAddress)
            else assertEquals(IpAddress.V4(it.broadcast), net.broadcastAddress!!.address)
        }
        var size_bytes = it.size_be_hex.hexToByteArray()
        val size = Size.V6(ByteArray(17).apply {
            size_bytes.indices.forEach {
                this[17 - size_bytes.size + it] = size_bytes[it]

            }
        })
        if (net is IpNetwork.V4) {
            if (net.size < Size.V4(100000000u)) {
                println(net.size)
                val sp = net.addressSpace
                assertEquals(net.address, sp.first())
                assertEquals(net.lastAddress, sp.last())
            }
        }

        when (net) {
            is IpNetwork.V4 -> assertEquals(Size.V4(size_bytes.let {
                if (it.size < 4)
                    ByteArray(5).apply {
                        size_bytes.indices.forEach {
                            this[5 - size_bytes.size + it] = size_bytes[it]
                        }
                    }
                else it
            }), net.size)

            is IpNetwork.V6 -> assertEquals(size, net.size)
        }
    }


    @Test
    fun merge_cases() = merge_cases.forEach { case ->
        val a = IpNetwork(case.aCidr) as IpNetwork<Number, Any>
        val b = IpNetwork(case.bCidr) as IpNetwork<Number, Any>

        val canMerge = case.canMerge
        assertEquals(canMerge, a.canMergeWith(b))
        if (canMerge) assertEquals(IpNetwork(case.expect!!) as IpNetwork<Number, Any>, a + b)
        else assertNull(a + b)

    }

    private fun resourceText(path: String): String =
        this::class.java.classLoader.getResourceAsStream(path).reader(Charsets.UTF_8).readText()

}

