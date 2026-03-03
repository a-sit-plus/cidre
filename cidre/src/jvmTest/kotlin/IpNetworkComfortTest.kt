import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpNetwork
import at.asitplus.cidre.byteops.CidrNumber
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpNetworkComfortTest {

    @Test
    fun relationToClassification() {
        val a = IpNetwork.V4("10.0.0.0/24")
        val same = IpNetwork.V4("10.0.0.0/24")
        val child = IpNetwork.V4("10.0.0.0/25")
        val adjacent = IpNetwork.V4("10.0.1.0/24")
        val disjoint = IpNetwork.V4("10.0.3.0/24")

        assertEquals(IpNetwork.Relation.EQUAL, a.relationTo(same))
        assertEquals(IpNetwork.Relation.CONTAINS, a.relationTo(child))
        assertEquals(IpNetwork.Relation.WITHIN, child.relationTo(a))
        assertEquals(IpNetwork.Relation.ADJACENT, a.relationTo(adjacent))
        assertEquals(IpNetwork.Relation.DISJOINT, a.relationTo(disjoint))
    }

    @Test
    fun rangeApisRoundtrip() {
        val v4Start = IpAddress.V4("10.0.0.5")
        val v4End = IpAddress.V4("10.0.0.130")
        val v4Summary = IpNetwork.fromRange(v4Start, v4End)
        assertCanonical(v4Summary)
        assertEquals(v4Start, v4Summary.first().toRange().first)
        assertEquals(v4End, v4Summary.last().toRange().second)
        assertEquals(rangeAddressSet(v4Start, v4End), addressSet(v4Summary))
        v4Summary.forEach { assertEquals(listOf(it), IpNetwork.fromRange(it.address, it.lastAddress)) }

        val v6Start = IpAddress.V6("2001:db8::5")
        val v6End = IpAddress.V6("2001:db8::a")
        val v6Summary = IpNetwork.fromRange(v6Start, v6End)
        assertCanonical(v6Summary)
        assertEquals(v6Start, v6Summary.first().toRange().first)
        assertEquals(v6End, v6Summary.last().toRange().second)
        assertEquals(rangeAddressSet(v6Start, v6End), addressSet(v6Summary))
        v6Summary.forEach { assertEquals(listOf(it), IpNetwork.fromRange(it.address, it.lastAddress)) }
    }

    @Test
    fun setOperationOutputsAreCanonical() {
        val a = IpNetwork.V4("10.0.0.0/24")
        val b = IpNetwork.V4("10.0.0.64/26")
        val c = IpNetwork.V4("10.0.1.0/24")

        assertCanonical(a.unionCollapse(c))
        assertCanonical(a.unionCovering(c))
        assertCanonical(a.intersection(b))
        assertCanonical(a.difference(b))
    }

    @Test
    fun mixedFamilyOperationsFailFast() {
        val v4 = IpNetwork.V4("10.0.0.0/24")
        val v6 = IpNetwork.V6("2001:db8::/64")
        val v6Address = IpAddress.V6("2001:db8::1")

        @Suppress("UNCHECKED_CAST")
        val v6AsV4Net = v6 as IpNetwork<Byte, CidrNumber.V4>
        @Suppress("UNCHECKED_CAST")
        val v6AsV4Addr = v6Address as IpAddress<Byte, CidrNumber.V4>

        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).overlaps(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).unionCollapse(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).unionCovering(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).intersection(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).difference(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).relationTo(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).contains(v6AsV4Net) }
        assertFailsWith<IllegalArgumentException> { (v4 as IpNetwork<Byte, CidrNumber.V4>).contains(v6AsV4Addr) }
    }

    @Test
    fun randomizedInvariantsV4() {
        val rnd = Random(0xC1D0Eu.toInt())
        repeat(250) {
            val a = randomV4Network(rnd)
            val b = randomV4Network(rnd)
            val i = a.intersection(b)
            val d = a.difference(b)

            assertCanonical(i)
            assertCanonical(d)

            val aSet = addressSet(listOf(a))
            val iSet = addressSet(i)
            val dSet = addressSet(d)
            assertTrue((iSet intersect dSet).isEmpty(), "intersection and difference must be disjoint")
            assertEquals(aSet, iSet + dSet, "(A ∩ B) U (A - B) must reconstruct A")

            assertRelationSymmetry(a, b)

            val base = IpNetwork.V4(randomV4Address(rnd), 24u, strict = false)
            val startOffset = rnd.nextInt(0, 256)
            val endOffset = rnd.nextInt(startOffset, 256)
            val start = (base.address + startOffset.toUInt())!!
            val end = (base.address + endOffset.toUInt())!!
            val summary = IpNetwork.fromRange(start, end)
            assertCanonical(summary)
            assertEquals(rangeAddressSet(start, end), addressSet(summary))
        }
    }

    @Test
    fun randomizedInvariantsV6() {
        val rnd = Random(0x6C1D0Eu.toInt())
        repeat(250) {
            val a = randomV6Network(rnd)
            val b = randomV6Network(rnd)
            val i = a.intersection(b)
            val d = a.difference(b)

            assertCanonical(i)
            assertCanonical(d)

            val aSet = addressSet(listOf(a))
            val iSet = addressSet(i)
            val dSet = addressSet(d)
            assertTrue((iSet intersect dSet).isEmpty(), "intersection and difference must be disjoint")
            assertEquals(aSet, iSet + dSet, "(A ∩ B) U (A - B) must reconstruct A")

            assertRelationSymmetry(a, b)

            val base = IpNetwork.V6(randomV6Address(rnd), 124u, strict = false)
            val startOffset = rnd.nextInt(0, 16)
            val endOffset = rnd.nextInt(startOffset, 16)
            val start = (base.address + startOffset.toUInt())!!
            val end = (base.address + endOffset.toUInt())!!
            val summary = IpNetwork.fromRange(start, end)
            assertCanonical(summary)
            assertEquals(rangeAddressSet(start, end), addressSet(summary))
        }
    }

    private fun randomV4Network(rnd: Random): IpNetwork.V4 {
        val address = randomV4Address(rnd)
        val prefix = rnd.nextInt(24, 33).toUInt()
        return IpNetwork.V4(address, prefix, strict = false)
    }

    private fun randomV4Address(rnd: Random): IpAddress.V4 {
        val bytes = ByteArray(4) { rnd.nextInt(0, 256).toByte() }
        return IpAddress.V4(bytes)
    }

    private fun randomV6Address(rnd: Random): IpAddress.V6 {
        val bytes = ByteArray(16) { rnd.nextInt(0, 256).toByte() }
        return IpAddress.V6(bytes)
    }

    private fun randomV6Network(rnd: Random): IpNetwork.V6 {
        val prefix = rnd.nextInt(124, 129).toUInt()
        return IpNetwork.V6(randomV6Address(rnd), prefix, strict = false)
    }

    private fun <N : Number, S : CidrNumber<S>> addressSet(networks: List<IpNetwork<N, S>>): Set<String> =
        networks.flatMap { net -> net.addressSpace.map { it.toString() }.toList() }.toSet()

    private fun <N : Number, S : CidrNumber<S>> rangeAddressSet(start: IpAddress<N, S>, end: IpAddress<N, S>): Set<String> {
        val out = mutableSetOf<String>()
        var current = start.toCidrNumber()
        val endNumber = end.toCidrNumber()
        while (current <= endNumber) {
            out += IpAddress(current).toString()
            current = (current + 1u) ?: break
        }
        return out
    }

    private fun <N : Number, S : CidrNumber<S>> assertCanonical(networks: List<IpNetwork<N, S>>) {
        for (index in 1 until networks.size) {
            assertTrue(networks[index - 1] <= networks[index], "output must be sorted")
            assertFalse(networks[index - 1].canMergeWith(networks[index]), "adjacent mergeable networks must be collapsed")
        }
        for (i in networks.indices) {
            for (j in i + 1 until networks.size) {
                assertFalse(networks[i].contains(networks[j]), "output must not contain redundant contained networks")
                assertFalse(networks[j].contains(networks[i]), "output must not contain redundant contained networks")
                assertFalse(networks[i].overlaps(networks[j]), "output must be non-overlapping")
            }
        }
    }

    private fun <N : Number, S : CidrNumber<S>> assertRelationSymmetry(a: IpNetwork<N, S>, b: IpNetwork<N, S>) {
        val forward = a.relationTo(b)
        val backward = b.relationTo(a)
        val expectedBackward = when (forward) {
            IpNetwork.Relation.EQUAL -> IpNetwork.Relation.EQUAL
            IpNetwork.Relation.CONTAINS -> IpNetwork.Relation.WITHIN
            IpNetwork.Relation.WITHIN -> IpNetwork.Relation.CONTAINS
            IpNetwork.Relation.ADJACENT -> IpNetwork.Relation.ADJACENT
            IpNetwork.Relation.DISJOINT -> IpNetwork.Relation.DISJOINT
        }
        assertEquals(expectedBackward, backward)
    }
}
