import at.asitplus.*
import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpNetwork
import at.asitplus.cidre.byteops.CidrNumber
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
    val overlongs = json.decodeFromString<OverlongFixture>(resourceText("pythontest/overlongs.json"))
    val merge_cases = json.decodeFromString<List<MergeCase>>(resourceText("pythontest/merge_cases.json"))
    val python_oracle = json.decodeFromString<PythonOracleFixture>(resourceText("pythontest/python_oracle.json"))

    @Test
    fun overlongs() = overlongs.tests.forEach { case ->
        when (case.operation) {
            "AND" -> assertEquals(
                CidrNumber.V6(case.output.hexToByteArray()),
                CidrNumber.V6(case.input.hexToByteArray()) and CidrNumber.V6(case.argument!!.hexToByteArray())
            )

            "OR" -> assertEquals(
                CidrNumber.V6(case.output.hexToByteArray()),
                CidrNumber.V6(case.input.hexToByteArray()) or CidrNumber.V6(case.argument!!.hexToByteArray())
            )

            "XOR" -> assertEquals(
                CidrNumber.V6(case.output.hexToByteArray()),
                CidrNumber.V6(case.input.hexToByteArray()) xor CidrNumber.V6(case.argument!!.hexToByteArray())
            )

            "SHR" -> assertEquals(
                CidrNumber.V6(case.output.hexToByteArray()),
                CidrNumber.V6(case.input.hexToByteArray()) shr case.argument!!.toInt()
            )

            "SHL" -> assertEquals(
                CidrNumber.V6(case.output.hexToByteArray()),
                CidrNumber.V6(case.input.hexToByteArray()) shl case.argument!!.toInt()
            )

            "INV" -> assertEquals(
                CidrNumber.V6(case.output.hexToByteArray()),
                CidrNumber.V6(case.input.hexToByteArray()).inv()
            )
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
        val cmp = when (case.version) {
            "V4" -> IpAddress.V4(case.a).compareTo(IpAddress.V4(case.b))
            "V6" -> IpAddress.V6(case.a).compareTo(IpAddress.V6(case.b))
            else -> throw AssertionError()
        }
        assertEquals(case.cmp, cmp)
    }


    @Test
    fun addrMembership() = addr_membership.cases.forEach { case ->
        val inner = IpAddress(case.addr)
        val outer = IpNetwork(case.network)
        val actual = when {
            inner is IpAddress.V4 && outer is IpNetwork.V4 -> outer.contains(inner)
            inner is IpAddress.V6 && outer is IpNetwork.V6 -> outer.contains(inner)
            else -> fail("IP family mismatch in fixture: ${case.addr} vs ${case.network}")
        }
        assertEquals(case.expect, actual)
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
        val a = IpNetwork(case.a_cidr)
        val b = IpNetwork(case.b_cidr)
        withSameFamilyNetworks(
            a,
            b,
            blockV4 = { a4, b4 ->
                assertEquals(case.are_adjacent, a4.isAdjacentTo(b4))
                assertEquals(case.are_adjacent, b4.isAdjacentTo(a4))
                assertEquals(case.overlaps, a4.overlaps(b4))
                assertEquals(case.overlaps, b4.overlaps(a4))
                when (case.relation) {
                    "A_contains_B" -> {
                        assertTrue(a4.contains(b4))
                        assertFalse(b4.contains(a4))
                        assertNotEquals(b4, a4)
                    }

                    "B_contains_A" -> {
                        assertTrue(b4.contains(a4))
                        assertFalse(a4.contains(b4))
                        assertNotEquals(b4, a4)
                    }

                    "equal" -> {
                        assertEquals(b4, a4)
                        assertTrue(a4.contains(b4))
                        assertTrue(b4.contains(a4))
                    }

                    "disjoint" -> {
                        assertNotEquals(b4, a4)
                        assertFalse(a4.contains(b4))
                        assertFalse(b4.contains(a4))
                        assertFalse(b4.overlaps(a4))
                        assertFalse(b4.isAdjacentTo(a4))
                    }
                }
            },
            blockV6 = { a6, b6 ->
                assertEquals(case.are_adjacent, a6.isAdjacentTo(b6))
                assertEquals(case.are_adjacent, b6.isAdjacentTo(a6))
                assertEquals(case.overlaps, a6.overlaps(b6))
                assertEquals(case.overlaps, b6.overlaps(a6))
                when (case.relation) {
                    "A_contains_B" -> {
                        assertTrue(a6.contains(b6))
                        assertFalse(b6.contains(a6))
                        assertNotEquals(b6, a6)
                    }

                    "B_contains_A" -> {
                        assertTrue(b6.contains(a6))
                        assertFalse(a6.contains(b6))
                        assertNotEquals(b6, a6)
                    }

                    "equal" -> {
                        assertEquals(b6, a6)
                        assertTrue(a6.contains(b6))
                        assertTrue(b6.contains(a6))
                    }

                    "disjoint" -> {
                        assertNotEquals(b6, a6)
                        assertFalse(a6.contains(b6))
                        assertFalse(b6.contains(a6))
                        assertFalse(b6.overlaps(a6))
                        assertFalse(b6.isAdjacentTo(a6))
                    }
                }
            }
        )
    }

    @Test
    fun overlapsContainmentFixture() = overlaps_containment.pairs.forEach { case ->
        val a = IpNetwork(case.a)
        val b = IpNetwork(case.b)
        withSameFamilyNetworks(
            a,
            b,
            blockV4 = { a4, b4 ->
                assertEquals(case.overlaps, a4.overlaps(b4), "a=${case.a}, b=${case.b}")
                assertEquals(case.overlaps, b4.overlaps(a4), "a=${case.a}, b=${case.b}")
                case.aSubnetOfB?.let { assertEquals(it, a4.isSubnetOf(b4), "a_subnet_of_b for ${case.a} vs ${case.b}") }
                case.bSubnetOfA?.let { assertEquals(it, b4.isSubnetOf(a4), "b_subnet_of_a for ${case.a} vs ${case.b}") }
                case.aSupernetOfB?.let { assertEquals(it, a4.isSupernetOf(b4), "a_supernet_of_b for ${case.a} vs ${case.b}") }
                case.bSupernetOfA?.let { assertEquals(it, b4.isSupernetOf(a4), "b_supernet_of_a for ${case.a} vs ${case.b}") }
            },
            blockV6 = { a6, b6 ->
                assertEquals(case.overlaps, a6.overlaps(b6), "a=${case.a}, b=${case.b}")
                assertEquals(case.overlaps, b6.overlaps(a6), "a=${case.a}, b=${case.b}")
                case.aSubnetOfB?.let { assertEquals(it, a6.isSubnetOf(b6), "a_subnet_of_b for ${case.a} vs ${case.b}") }
                case.bSubnetOfA?.let { assertEquals(it, b6.isSubnetOf(a6), "b_subnet_of_a for ${case.a} vs ${case.b}") }
                case.aSupernetOfB?.let { assertEquals(it, a6.isSupernetOf(b6), "a_supernet_of_b for ${case.a} vs ${case.b}") }
                case.bSupernetOfA?.let { assertEquals(it, b6.isSupernetOf(a6), "b_supernet_of_a for ${case.a} vs ${case.b}") }
            }
        )
    }

    @Test
    fun setOperationsFixture() {
        set_operations.union.forEach { case ->
            assertEquals(2, case.inputs.size, "union fixture currently expects pairs")
            val a = IpNetwork(case.inputs[0])
            val b = IpNetwork(case.inputs[1])
            withSameFamilyNetworks(
                a,
                b,
                blockV4 = { a4, b4 ->
                    val collapse = a4.unionCollapse(b4).map { it.toString() }
                    val covering = a4.unionCovering(b4).map { it.toString() }
                    assertContentEquals(case.collapse, collapse, "collapse for ${case.inputs}")
                    assertContentEquals(case.covering, covering, "covering for ${case.inputs}")
                },
                blockV6 = { a6, b6 ->
                    val collapse = a6.unionCollapse(b6).map { it.toString() }
                    val covering = a6.unionCovering(b6).map { it.toString() }
                    assertContentEquals(case.collapse, collapse, "collapse for ${case.inputs}")
                    assertContentEquals(case.covering, covering, "covering for ${case.inputs}")
                }
            )
        }

        set_operations.intersection.forEach { case ->
            val a = IpNetwork(case.a)
            val b = IpNetwork(case.b)
            withSameFamilyNetworks(
                a,
                b,
                blockV4 = { a4, b4 ->
                    val actual = a4.intersection(b4).map { it.toString() }
                    assertContentEquals(case.expect, actual, "intersection for ${case.a} vs ${case.b}")
                },
                blockV6 = { a6, b6 ->
                    val actual = a6.intersection(b6).map { it.toString() }
                    assertContentEquals(case.expect, actual, "intersection for ${case.a} vs ${case.b}")
                }
            )
        }

        set_operations.difference.forEach { case ->
            val a = IpNetwork(case.a)
            val b = IpNetwork(case.b)
            withSameFamilyNetworks(
                a,
                b,
                blockV4 = { a4, b4 ->
                    val actual = a4.difference(b4).map { it.toString() }
                    assertContentEquals(case.expect, actual, "difference for ${case.a} - ${case.b}")
                },
                blockV6 = { a6, b6 ->
                    val actual = a6.difference(b6).map { it.toString() }
                    assertContentEquals(case.expect, actual, "difference for ${case.a} - ${case.b}")
                }
            )
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
        val size_bytes = it.size_be_hex.hexToByteArray()
        val size = CidrNumber.V6.fromUnpadded(size_bytes)
        if (net is IpNetwork.V4) {
            if (net.size < CidrNumber.V4(100000000u)) {
                val sp = net.addressSpace
                assertEquals(net.address, sp.first())
                assertEquals(net.lastAddress, sp.last())
            }
        }

        when (net) {
            is IpNetwork.V4 -> {
                val expected = CidrNumber.V4.fromUnpadded(size_bytes)
                assertEquals(expected, net.size)
            }

            is IpNetwork.V6 -> assertEquals(size, net.size)
        }
    }


    @Test
    fun merge_cases() = merge_cases.forEach { case ->
        val a = IpNetwork(case.aCidr)
        val b = IpNetwork(case.bCidr)
        withSameFamilyNetworks(
            a,
            b,
            blockV4 = { a4, b4 ->
                val canMerge = case.canMerge
                assertEquals(canMerge, a4.canMergeWith(b4))
                if (canMerge) {
                    val expected = IpNetwork(case.expect!!)
                    assertIs<IpNetwork.V4>(expected)
                    assertEquals(expected, a4 + b4)
                } else assertNull(a4 + b4)
            },
            blockV6 = { a6, b6 ->
                val canMerge = case.canMerge
                assertEquals(canMerge, a6.canMergeWith(b6))
                if (canMerge) {
                    val expected = IpNetwork(case.expect!!)
                    assertIs<IpNetwork.V6>(expected)
                    assertEquals(expected, a6 + b6)
                } else assertNull(a6 + b6)
            }
        )

    }

    @Test
    fun pythonOracleNetworkFlags() = python_oracle.networkFlags.forEach { case ->
        val net = IpNetwork(case.cidr)
        when (net) {
            is IpNetwork.V4 -> {
                assertEquals(case.isLoopback, net.isLoopback, "isLoopback for ${case.cidr}")
                assertEquals(case.isLinkLocal, net.isLinkLocal, "isLinkLocal for ${case.cidr}")
                assertEquals(case.isMulticast, net.isMulticast, "isMulticast for ${case.cidr}")
            }

            is IpNetwork.V6 -> {
                assertEquals(case.isLoopback, net.isLoopback, "isLoopback for ${case.cidr}")
                assertEquals(case.isLinkLocal, net.isLinkLocal, "isLinkLocal for ${case.cidr}")
                assertEquals(case.isMulticast, net.isMulticast, "isMulticast for ${case.cidr}")
            }
        }
    }

    @Test
    fun pythonOracleIpv6Expanded() = python_oracle.ipv6Expanded.forEach { case ->
        val actual = IpAddress.V6(case.input).toString(expanded = true)
        assertEquals(case.exploded, actual, "expanded for ${case.input}")
    }

    @Test
    fun subnetting() {
        subnetting.cases.forEach { case ->
            val parent = IpNetwork(case.parent)
            val actual = when {
                case.newPrefix != null -> parent.subnet(case.newPrefix.toUInt())
                case.prefixlenDiff != null -> parent.subnetRelative(case.prefixlenDiff.toUInt())
                else -> fail("Neither new_prefix nor prefixlen_diff provided for subnet case: $case")
            }.map { it.toString() }.toList()

            assertContentEquals(case.expect, actual, "parent=${case.parent}, case=$case")
        }

        subnetting.errorCases.forEach { case ->
            val parent = IpNetwork(case.parent)
            assertFailsWith<IllegalArgumentException>("parent=${case.parent}, case=$case") {
                when {
                    case.newPrefix != null -> parent.subnet(case.newPrefix.toUInt())
                    case.prefixlenDiff != null -> parent.subnetRelative(case.prefixlenDiff.toUInt())
                    else -> fail("Neither new_prefix nor prefixlen_diff provided for subnet error case: $case")
                }
            }
        }
    }

    @Test
    fun supernetting() {
        supernetting.cases.forEach { case ->
            val child = IpNetwork(case.child)
            val actual = when {
                case.newPrefix != null -> child.supernet(case.newPrefix.toUInt())
                case.prefixlenDiff != null -> child.supernetRelative(case.prefixlenDiff.toUInt())
                else -> fail("Neither new_prefix nor prefixlen_diff provided for supernet case: $case")
            }.toString()

            assertEquals(case.expect, actual, "child=${case.child}, case=$case")
        }

        supernetting.errorCases.forEach { case ->
            val child = IpNetwork(case.child)
            assertFailsWith<IllegalArgumentException>("child=${case.child}, case=$case") {
                when {
                    case.newPrefix != null -> child.supernet(case.newPrefix.toUInt())
                    case.prefixlenDiff != null -> child.supernetRelative(case.prefixlenDiff.toUInt())
                    else -> fail("Neither new_prefix nor prefixlen_diff provided for supernet error case: $case")
                }
            }
        }
    }

    private fun resourceText(path: String): String =
        this::class.java.classLoader.getResourceAsStream(path).reader(Charsets.UTF_8).readText()

    private inline fun <T> withSameFamilyNetworks(
        a: IpNetwork<*, *>,
        b: IpNetwork<*, *>,
        blockV4: (IpNetwork.V4, IpNetwork.V4) -> T,
        blockV6: (IpNetwork.V6, IpNetwork.V6) -> T
    ): T {
        return when {
            a is IpNetwork.V4 && b is IpNetwork.V4 -> blockV4(a, b)
            a is IpNetwork.V6 && b is IpNetwork.V6 -> blockV6(a, b)
            else -> fail("Network family mismatch: $a vs $b")
        }
    }

}
