
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
Currently, CIDRE can
* parse and encode IPv4 and IPv6 addresses from/to String and ByteArray representations
* iterate over, slice, split, and merge networks,
* convert prefixes from/to netmasks
* subnet and supernet
* sorting of networks and addresses


In general, CIDRE's data model has semantics influenced by [netaddr](https://github.com/netaddr/netaddr/?tab=readme-ov-file): An `IpNetwork` covers a range of `IpInterface`s, both of which consist of an `IpAddress` and a `prefix`.   
In contrast to a network, an `IpInterface` has only a single `IpAddress` (although no validation is performed whether it is distinct from the associated network's address).  
In more technical terms, CIDRE introduces three main classes:
- `IpAddress` - a sealed class, specialised as
  - `IpAddress.V4` representing IPv4 addresses
  - `IpAddress.V6` representing IPv6 addresses
- `IpNetwork` - a sealed class, following the same hierarchy:
  - `IpNetwork.V4` representing an IPv4 network consisting of an `IpAddress.V4` network address and a prefix/netmask
  - `IpNetwork.V6` representing an IPv4 network consisting of an `IpAddress.V6` network address and a prefix/netmask
- `IpInterface` a concrete IP address belonging to a network. This is also a combination of IP address and a prefix/netmask but with distinctly different semantics.  
  - `IpInterface.V4` consisting of an `IpAddress.V4` network address and a prefix/netmask
  - `IpInterface.V6` consisting of an `IpAddress.V6` network address and a prefix/netmask

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


### Working with Networks

#### Netmasks and Prefixes

#### Host Ranges and Address Spaces

#### Containment, Overlap Checks, and Adjacency

To check whether an address, a network, or an `IpInterface` falls inside a target network, call `targetNetwork.contanins(addrNwOrIf)`.



#### Subnetting and Supernetting


### Low-Level Utilities
The `at.asitplus.cidre.byteops` package provides low-level helper functions

## Contributing
External contributions are greatly appreciated!
Just be sure to observe the contribution guidelines (see [CONTRIBUTING.md](CONTRIBUTING.md)).

<br>

---

<p align="center">
The Apache License does not apply to the logos, (including the A-SIT logo) and the project/module name(s), as these are the sole property of
A-SIT/A-SIT Plus GmbH and may not be used in derivative works without explicit permission!
</p>
