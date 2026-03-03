# Changelog

## NEXT
* Add subnetting and supernetting helpers on `IpNetwork`
  * `subnet(newPrefix: UInt)`
  * `subnetRelative(prefixDiff: UInt)`
  * `supernet(newPrefix: UInt)`
  * `supernetRelative(prefixDiff: UInt)`
* Add fixture-backed JVM tests for subnetting and supernetting (`subnetting.json`, `supernetting.json`)
* Add additional fixture-backed JVM coverage:
  * `overlaps_containment.json`
  * `python_oracle.json` generated from Python stdlib `ipaddress`
  * `set_operations.json` (union collapse/covering, intersection, difference)
* Add Python-oracle fixture generator:
  * `python-testgen/gen_python_oracle.py`
* Add network set-operations on `IpNetwork`:
  * `unionCollapse`
  * `unionCovering`
  * `intersection`
  * `difference`
* Keep set-operation API surface explicit and unambiguous; no alias variants (`union`, `spanningUnion`, `intersect`, `minus`)
* Add `IpAddress.V4.LeadingPrefix` for explicit bit-prefix modeling with:
  * `UByte` constructor (`leadingPrefixLength`, `leadingPrefixValue`)
  * signed-number constructor (`Int`, `Int`)
  * bit-string constructor (`String`, e.g. `"110"`)
  * canonical bit-string rendering via `toString()`
* Fix network flag semantics to use containment instead of exact-network equality:
  * `isLoopback`
  * `isLinkLocal`
  * `isMulticast`
  * `isPrivate`
  * IPv6 range flags (`isGlobalUnicast`, `isUniqueLocal`, `isUniqueLocalLocallyAssigned`, `isIpV4Mapped`, `isIpV4Compatible`, `isDocumentation`, `isDiscardOnly`, `isReserved`)
* Fix IPv6 reserved special ranges initialization (`4000::/3`, `6000::/3`) to avoid invalid-network initialization errors
* Fix `IpNetwork.contains(IpInterface)` to also validate that the interface address is inside the network
* Fix `addressSpace` for `/32` and `/128` to avoid duplicate single-address emission
* Fix `IpAddress.V6.toString(expanded = true)` to emit fully padded 4-digit hextets
* Tighten `IpAddress.V4.LeadingPrefix(Int, Int)` validation to reject values outside `0..255` before conversion

## 0.3.1
* Add `hashCode()` and `equals` in `IpInterface` 

## 0.3.0
* Add methods in `IpAddressAndPrefix` interface for parsing `ByteArray` representing address and subnet mask in X509 `IpAddressName`
  * `fromX509Octets`
  * `toX509Octets`

## 0.2.0
* Revised generic type arguments
* Introduce `CidrNumber` optimized for CIDR operations
  * `CidrNumber.V4` for IPv4
  * `CidrNumber.V6` for IPv6
* CIDR math helpers on IP Addresses:
  * `toCidrNumber` to get numeric representation
  * `plus`
  * `minus`
  * `shl`
  * `shr`
  * `and`
  * `or`
  * `xor`
  * `inv`
* More properties:
  * `hostMask`
  * `numberOfHostBits`
  * `lastAddress`
  * `firstAssignableHost`
  * `lastAssignableHost`
  * `assignableHostRange`
  * `addressSpace`
  * `lastAddress`
  * `overlaps`
  * `isSubnetOf`
  * `isSupernetOf`
  * `isAdjacentTo`
  * `boradcastAddress` (IPv4 only)
* Fix native interop package

## 0.1.0
First public release
