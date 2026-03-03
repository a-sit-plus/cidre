# Changelog

## NEXT
* Add subnetting and supernetting helpers on `IpNetwork`
  * `subnet(newPrefix: UInt)`
  * `subnetRelative(prefixDiff: UInt)`
  * `supernet(newPrefix: UInt)`
  * `supernetRelative(prefixDiff: UInt)`
* Add fixture-backed JVM tests for subnetting and supernetting (`subnetting.json`, `supernetting.json`)
* Add `IpAddress.V4.LeadingPrefix` for explicit bit-prefix modeling with:
  * `UByte` constructor (`leadingPrefixLength`, `leadingPrefixValue`)
  * signed-number constructor (`Int`, `Int`)
  * bit-string constructor (`String`, e.g. `"110"`)
  * canonical bit-string rendering via `toString()`

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
