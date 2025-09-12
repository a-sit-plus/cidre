package at.asitplus

// CidrFixtures.kt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Common "meta" block (accommodates both packs)
@Serializable
data class Meta(
    val version: String? = null,
    val generated: String? = null,
    val notes: String? = null,
    val source_file: String? = null,
    val source_ref: String? = null,
    val tool: String? = null
)

/* ---------- parsing.json ---------- */
@Serializable
data class ParsingFixture(
    val meta: Meta,
    @SerialName("valid_addresses") val validAddresses: Map<String,String> = emptyMap(),
    @SerialName("invalid_addresses") val invalidAddresses: List<String> = emptyList(),
    @SerialName("valid_networks") val validNetworks: List<String> = emptyList(),
    @SerialName("loose_networks") val looseNetworks: List<String> = emptyList(),
    val interfaces: List<String> = emptyList(),
    @SerialName("invalid_network_like") val invalidNetworkLike: List<String> = emptyList()
)

/* ---------- normalization.json ---------- */
@Serializable
data class NormalizationFixture(
    val meta: Meta,
    val cases: List<NormalizationCase> = emptyList()
)
@Serializable
data class NormalizationCase(
    val input: String,
    @SerialName("expect_network") val expectNetwork: String
)

/* ---------- subnetting.json ---------- */
@Serializable
data class SubnettingFixture(
    val meta: Meta,
    val cases: List<SubnetCase> = emptyList(),
    @SerialName("error_cases") val errorCases: List<SubnetErrorCase> = emptyList()
)
@Serializable
data class SubnetCase(
    val parent: String,
    @SerialName("new_prefix") val newPrefix: Int? = null,
    @SerialName("prefixlen_diff") val prefixlenDiff: Int? = null,
    val expect: List<String> = emptyList(),
    val note: String? = null
)
@Serializable
data class SubnetErrorCase(
    val parent: String,
    @SerialName("new_prefix") val newPrefix: Int? = null,
    @SerialName("prefixlen_diff") val prefixlenDiff: Int? = null,
    @SerialName("expect_error") val expectError: String
)

/* ---------- supernetting.json ---------- */
@Serializable
data class SupernettingFixture(
    val meta: Meta,
    val cases: List<SupernetCase> = emptyList(),
    @SerialName("error_cases") val errorCases: List<SupernetErrorCase> = emptyList()
)
@Serializable
data class SupernetCase(
    val child: String,
    @SerialName("new_prefix") val newPrefix: Int? = null,
    @SerialName("prefixlen_diff") val prefixlenDiff: Int? = null,
    val expect: String
)
@Serializable
data class SupernetErrorCase(
    val child: String,
    @SerialName("new_prefix") val newPrefix: Int? = null,
    @SerialName("prefixlen_diff") val prefixlenDiff: Int? = null,
    @SerialName("expect_error") val expectError: String,
    val reason: String? = null
)

/* ---------- overlaps_containment.json ---------- */
@Serializable
data class OverlapsContainmentFixture(
    val meta: Meta,
    val pairs: List<OverlapPair> = emptyList()
)
@Serializable
data class OverlapPair(
    val a: String,
    val b: String,
    val overlaps: Boolean,
    @SerialName("a_subnet_of_b") val aSubnetOfB: Boolean? = null,
    @SerialName("b_subnet_of_a") val bSubnetOfA: Boolean? = null,
    @SerialName("a_supernet_of_b") val aSupernetOfB: Boolean? = null,
    @SerialName("b_supernet_of_a") val bSupernetOfA: Boolean? = null
)

/* ---------- set_operations.json ---------- */
@Serializable
data class SetOperationsFixture(
    val meta: Meta,
    val union: List<UnionCase> = emptyList(),
    val intersection: List<IntersectionCase> = emptyList(),
    val difference: List<DifferenceCase> = emptyList()
)
@Serializable
data class UnionCase(
    val inputs: List<String> = emptyList(),
    val collapse: List<String> = emptyList(),
    val covering: List<String> = emptyList()
)
@Serializable
data class IntersectionCase(
    val a: String,
    val b: String,
    val expect: List<String> = emptyList()
)
@Serializable
data class DifferenceCase(
    val a: String,
    val b: String,
    val expect: List<String> = emptyList()
)

/* ---------- ipv6_canonical.json ---------- */
@Serializable
data class Ipv6CanonicalFixture(
    val meta: Meta,
    val cases: List<Ipv6CanonicalCase> = emptyList()
)
@Serializable
data class Ipv6CanonicalCase(
    val input: String,
    val expect: String
)

/* ---------- lpm.json ---------- */
@Serializable
data class LpmFixture(
    val meta: Meta,
    val cases: List<LpmCase> = emptyList()
)
@Serializable
data class LpmCase(
    val routes: List<String>,
    val addr: String,
    val expect: String
)

@Serializable
data class MembershipFixture(
    val meta: Meta? = null,
    val cases: List<AddrMembershipCase> = emptyList()
)

@Serializable
data class AddrMembershipCase(
    val addr: String,
    val network: String,
    val expect: Boolean
)

@Serializable
data class NetContainmentFixture(
    val meta: Meta? = null,
    val cases: List<NetContainmentCase> = emptyList()
)

@Serializable
data class NetContainmentCase(
    val inner: String,
    val outer: String,
    val expect: Boolean
)

@Serializable
data class IpComparisonCase(
    val version: String, // "V4" or "V6"
    val a: String,
    val b: String,
    val cmp: Int // -1 if a<b, 0 if a==b, 1 if a>b
)

@Serializable
data class IpComparisonFixture(
    val cases: List<IpComparisonCase>
)



@Serializable
enum class Version { V4, V6 }

@Serializable
data class NetmaskCase(
    val version: Version,
    val prefix: Int,
    @SerialName("octet_count") val octetCount: Int,
    @SerialName("netmask_hex") val netmaskHex: String
)

@Serializable
data class NetmaskMeta(
    val tool: String? = null,
    val generated: String? = null,
    val source: String? = null
)

@Serializable
data class NetmaskFixture(
    val meta: NetmaskMeta? = null,
    val cases: List<NetmaskCase>
)


@Serializable
data class NetworkPropsFixture(
    val generated_at: String,
    val counts: Counts,
    val test_networks: List<TestNetwork>,
    val adjacency_cases: List<AdjacencyCase>
)

@Serializable
data class Counts(
    val test_networks: Int,
    val adjacency_cases: Int
)


@Serializable
data class TestNetwork(
    val family: String,           // "IPv4" or "IPv6"
    val cidr: String,             // e.g., "192.0.2.0/24"
    val address: String,          // network address string
    val prefix: Int,
    val network_address: String,
    val last_address: String,     // top of block (broadcast for IPv4)
    val first_assignable: String,
    val last_assignable: String,
    val broadcast: String?,       // null for IPv6
    val num_addresses: String,    // keep as string; huge for IPv6
    val size_be_hex: String       // big-endian byte encoding (hex) of num_addresses
)
@Serializable
data class AdjacencyCase(
    val family: String,           // "IPv4" or "IPv6"
    val a_cidr: String,
    val b_cidr: String,
    val are_adjacent: Boolean,
    val overlaps: Boolean,
    val relation: String          // "equal" | "A_contains_B" | "B_contains_A" | "adjacent" | "disjoint" | "relation_check"
)

@Serializable
data class MergeCase(
    @SerialName("a_cidr") val aCidr: String,
    @SerialName("b_cidr") val bCidr: String,
    @SerialName("can_merge") val canMerge: Boolean,
    val expect: String? = null,
    val note: String? = null
)


@Serializable
data class OverlongFixture(
    val generated_at: String,
    val tests: List<OverlongOp>
)

@Serializable
data class OverlongOp(
    val input: String,        // hex, 16 or 17 bytes BE (lowercase)
    val operation: String,    // "INV" | "AND" | "OR" | "XOR" | "SHL" | "SHR"
    val argument: String? = null, // null for INV; decimal shift for SHL/SHR; hex-be for AND/OR/XOR
    val output: String        // expected hex-be
)
