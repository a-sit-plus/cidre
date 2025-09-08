
<div align="center">

<img src="cidre.png" alt="CIDRE logo" width="444" height="88" />


# CIDRE&hairsp;&mdash;&hairsp;100% Pure KMP IPv4/IPv6 Repre&shy;sentation with Complementary CIDR Math

[![A-SIT Plus Official](https://raw.githubusercontent.com/a-sit-plus/a-sit-plus.github.io/709e802b3e00cb57916cbb254ca5e1a5756ad2a8/A-SIT%20Plus_%20official_opt.svg)](https://plus.a-sit.at/open-source.html)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-orange.svg?logo=kotlin)](http://kotlinlang.org)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Java](https://img.shields.io/badge/java-17-blue.svg?logo=OPENJDK)](https://www.oracle.com/java/technologies/downloads/#java17)
[![Android](https://img.shields.io/badge/Android-SDK--21-37AA55?logo=android)](https://developer.android.com/tools/releases/platforms#5.0)
[![Maven Central](https://img.shields.io/maven-central/v/at.asitplus/cidre)](https://mvnrepository.com/artifact/at.asitplus.maven/coordinates)

</div>


> From orchard to endpoint: _CIDRE_ delivers a smooth, dry balance of IP handling and subnet math, served consistently
> sparkling across **all** KMP targets. Unchaptalized&hairsp;&mdash;&hairsp;zero added dependencies; just natural, refreshing clarity.

&mdash; _Sir Evander Marchbank_, self-proclaimed cider cartographer who insists that every orchard has its own “gravitational pull” affecting the bubbles.

---

CIDRE focuses on parsing and representing IP addresses, IP networks and providing CIDR math. On the JVM and Android it maps from/to `InetAddress`/`Inet4Address`/`Inet6Address`. On native targets, it maps from/to `in_addr`/`in6_addr`.
It is not a full IP networking implementation, but you can use it to implement IP routing.
It has a total of zero external dependencies.  
Currently, CIDRE provides the following functionality:
* parsing and encoding IPv4 and IPv6 addresses from/to String and ByteArray representations
* converting CIDR prefixes from/to netmasks
* checking whether addresses or networks are are fully contained within a network
* comparability of networks and addresses inside a family (IPv4/IPv6)

Planned features are:
* iterating over, slicing, splitting, and merging networks
* subnetting and supernetting


In general, CIDRE's data model has semantics influenced by [netaddr](https://github.com/netaddr/netaddr/?tab=readme-ov-file): An `IpNetwork` covers a range of `IpInterface`s, both of which consist of an `IpAddress` and a `prefix`.   
Semantically, an `IpInterface` has only a single `IpAddress` (although no validation is performed whether it is distinct from the associated network's address), while a network spans a range.  
In more technical terms, CIDRE introduces three main classes:
- `IpAddress` - a sealed class, specialised as
  - `IpAddress.V4` representing IPv4 addresses
  - `IpAddress.V6` representing IPv6 addresses
- `IpNetwork` - a sealed class, following the same hierarchy:
  - `IpNetwork.V4` representing an IPv4 network consisting of an `IpAddress.V4` network address and a prefix/netmask
  - `IpNetwork.V6` representing an IPv4 network consisting of an `IpAddress.V6` network address and a prefix/netmask
- `IpInterface` a concrete IP address belonging to a network. Like a network, this is also a combination of IP address and prefix/netmask but with distinctly different semantics.  
  - `IpInterface.V4` consisting of an `IpAddress.V4` network address and a prefix/netmask
  - `IpInterface.V6` consisting of an `IpAddress.V6` network address and a prefix/netmask

`IpNetwork`, `IpAddress` and their IPv4 and IPv6 specializations share the `IpAddressAndPrefix` interface hierarchy, which groups common semantics and functionality.
However, addresses and networks are not comparable, so this is mainly an application of DRY.  
In addition, two typealiases are used:
* `typealias Prefix = UInt`
* `typealias Netmask = ByteArray`.

## Using in your Projects

This library is available at maven central.

### Gradle

```kotlin
dependencies {
    api("at.asitplus:cidre:$version")
}
```

### Working with IP Addresses

#### Parsing and Encoding
```kotlin
val ip4          = IpAddress("128.65.88.6") //returns an IpAddress.V4
val ip6          = IpAddress("2002:ac1d:2d64::1") //returns an IpAddress.V6
val ip4mappedIp6 = IpAddress("0000:0000:0000:0000:0000:FFFF:192.168.255.255") // returns an IPv4-mapped IpAddress.V6
```

Simply `toString()` any IP address to get its string representation, or access `octets` to get its network-oder byte representation.
An `IpAddress`'s companion object also provides helpful properties such as segment separator, number of octets, and readily usable `Regex` instances to check whether a string is a valid representation of
an IP address or a single address segment.

#### Ordering

IP addresses are `Comparable` inside a family (IPv4, IPv6) and are ordered by comparing their octets interpreted as a BE-encoded unsigned integer.

#### Platform Interop

CIDRE's `IpAddress` classes conveniently map from/to platform types.
Except for JavaScript and Wasm targets (which lack a native non-string IP address representation), creating addresses is as easy as passing a platform-native address into a CIDRE IP address constructor:

| Runtime                  | JVM/Android                                  | Mac/Linux/AndroidNative/MinGW                    |
|--------------------------|----------------------------------------------|--------------------------------------------------|
| Generic creation         | `IpAddress(InetAddress)`                     | not possible                                     |
| Type-safe IPv4 creation  | `IpAddress.V4(InetAddress)`                  | `IpAddress(in_addr)` / `IpAddress.V4(in_addr)`   |
| Type-safe IPv6 creation  | `IpAddress.V6(InetAddress)`                  | `IpAddress(in6_addr)` / `IpAddress.V6(in6_addr)` |
| To generic platform type | `IpAddress.toInetAddress(): InetAddress`     | `IpAddress.toInAddr(): CValue<out CStructVar>`   |
| To IPv4 platform type    | `IpAddress.V4.toInetAddress(): Inet4Address` | `IpAddress.V4.toInAddr(): CValue<in_addr>`       |
| To IPv6 platform type    | `IpAddress.V6.toInetAddress(): Inet6Address` | `IpAddress.V6.toInAddr(): CValue<in6_addr>`      |


#### IPv4 Specifics
Though it has long since been superseded by CIDR, `IpAddress.V4` still features a `class` property (albeit marked as deprecated), that indicates its pre-CIDR
address class.

#### IPv6 Specifics
IPv6 addresses can embed IPv4 addresses in two ways:
* IPv4 _mapped_  addresses: `0000:0000:0000:0000:0000:FFFF:<IPv4 Address in IPv4 Notation>`
* IPv4 _compatible_ addresses: `0000:0000:0000:0000:0000:0000:<IPv4 Address in IPv4 Notation>`

While the former is still very much a thing (and exposed through the `isIpv4Mapped` flag), the latter has been deprecated.
Still, the flag `isIpv4Compatible` indicates whether an IPv6 address conforms to the _compatible_ schema.

It is possible to extract the contained IPv4 address from an IPv4-mapped or IPv4-compatible address by accessing the
`embeddedIpV4Address` property. It returns null if no IPv4 address is contained.

### Working with Networks and IpInterfaces

CIDRE models two closely related concepts:
- `IpNetwork`: a contiguous address range, defined by a network address and prefix.  
The network’s address itself is part of the network (and for IPv4, the broadcast address is also considered inside for membership checks).
- `IpInterface`: a single address bound to a prefix and associated with a network, and therefore carry a reference to their associated `IpNetwork`.

Both share the `IpAddressAndPrefix` interface and its respective IPv4 and IPv6 specializations and therefore expose:
- `address` and prefix (CIDR prefix length)
- `netmask` (network-order ByteArray)
- common flags (e.g., `isLinkLocal`, `isLoopback`, `isMulticast`). IPv4- and IPv6-specific flags are available on their
respective interfaces (IpAddressAndPrefix.V4 / V6).
- consistent `toString()` behavior with address/prefix; IPv4 variants also support netmask printing helpers.


#### Creating IpInterfaces from Networks

Given an `IpAddress` and a prefix, it is possible to get the corresponding network in two ways:
- `IpNetwork(address strict = false)` to create a new `IpNetwork` and deep-copy the ip address into the network's `address` property.
    - If `strict = true` the passed address must already be the network address (i.e., correctly masked), according to the specified prefix
    - If `strict = false` the passed address will be masked to the network address, according to the specified prefix
- `IpNetwork.forAddress(address, prefix)` creates a new network, referencing and masking the passed `address`. This avoids copying, but modifies any not-correctly-masked address in-place, according to the given `prefix`.

#### Netmasks and Prefixes

CIDRE uses type aliases
- Prefix is a UInt (`typealias Prefix = UInt`)
- Netmask is a network-order byte array (`typealias Netmask = ByteArray`)

Round-tripping between prefixes and netmasks is straightforward:
- Create a netmask from a prefix:
    - For a specific IP family: `prefix.toNetmask(IpAddress.Family.V4)` or `prefix.toNetmask(IpAddress.Family.V6)`
    - For an arbitrary octet count: `prefix.toNetmask(octetCount)`
- Convert a netmask back to a prefix and validate contiguity: `netmask.toPrefix()`

IP addresses can be masked in-place by calling either `mask(prefix)` or `mask(netmask)`.
To create a deep-copied masked version of an address, manually `copy()` it before masking.

For IPv4, it is also possible to get a dotted-quad representation and choose a preferred textual form when working with `IpAddressAndPrefix`:
- `netmaskToString()` yields a `#.#.#.#` string
- `toString(preferNetmaskOverPrefix = true)` prints `A.A.A.A N.N.N.N`, where `A` is an IP address quad and `N` is a netmask quad.
- `toString(preferNetmaskOverPrefix = false)` prints standard `#.#.#.#/prefix`

#### Host Ranges and Address Spaces

Conceptually:
- An `IpNetwork` represents a contiguous range of addresses.
- An `IpInterface` is a single address bound to a prefix.
- The network address is part of the network; for IPv4, the broadcast address (when applicable) is also inside.

Coming soon:
- Iteration over the full address space of a network
- Convenience accessors for first/last interface and broadcast (where applicable)
- Efficient network size computations


#### Containment

Containment checks are explicit (and fast!):
- Address in network: `network.contains(ipAddress)`
- Interface in network: `network.contains(ipInterface)`
- Network fully contained in another network: `anotherNetwork.contains(network)`



### Low-Level Utilities
The `at.asitplus.cidre.byteops` package provides low-level helper functions:

* `infix fun ByteArray.and(other: ByteArray): ByteArray` performing a logical `AND` operation, returning a fresh ByteArray.
* `fun ByteArray.andInplace(other: ByteArray): Int` performing an in-place logical `AND` operation, modifying the receiver ByteArray. Returns the number of modified bits.
* `fun ByteArray.compareUnsignedBE(other: ByteArray): Int` comparing two same-sized byte arrays by interpreting their contents as unsigned BE integers
* `fun Prefix.toNetmask(family: IpAddress.Family): Netmask` converting an `UInt` CIDR prefix to its byte representation
* `fun Netmask.toPrefix(): Prefix` converting a netmask into its CIDR prefix length
* `ByteArray.toShortArray(bigEndian: Boolean = true): ShortArray` grouping pairs of bytes into a short. Useful to get IPv6 hextets from octets.


## Roadmap:
- More comprehensive tests
- Address range enumeration
- Subnet enumeration (absolute and relative, e.g., `/24` or “+2 bits”)
- Supernetting helpers (absolute and relative)
- Overlap and adjacency checks
- Safe aggregation of adjacent/overlapping ranges where possible and merging of networks

## Contributing
External contributions are greatly appreciated!
Just be sure to observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).

<br>

---

<p align="center">
The Apache License does not apply to the logos, (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
