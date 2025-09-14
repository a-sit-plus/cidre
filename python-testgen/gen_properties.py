#!/usr/bin/env python3
"""
Generate CIDR test cases (IPv4/IPv6) using only Python's stdlib 'ipaddress'.

Emits:
- test_networks[] with:
    family, cidr, address (network), prefix,
    network_address, last_address (top of block),
    first_assignable, last_assignable,
    broadcast (IPv4 only, None for IPv6),
    num_addresses (as string),
    size_be_hex (block size encoded as big-endian bytes, hex string)
- adjacency_cases[] with known relations (adjacent / contains / disjoint).

Notes:
- 'size_be_hex' is the big-endian byte encoding of net.num_addresses, hex-encoded
  with minimal length (no leading 00 bytes, no "0x" prefix).
- Relies solely on ipaddress methods/properties.
"""

import argparse
import ipaddress
import json
import random
from datetime import datetime


def ipv4_prefixes():
    return [0, 8, 16, 24, 25, 26, 27, 28, 29, 30, 31, 32]


def ipv6_prefixes():
    return [0, 32, 48, 56, 60, 64, 96, 112, 126, 127, 128]


def rand_net(version: int, prefix: int):
    if version == 4:
        return ipaddress.ip_network((ipaddress.IPv4Address(random.getrandbits(32)), prefix), strict=False)
    else:
        return ipaddress.ip_network((ipaddress.IPv6Address(random.getrandbits(128)), prefix), strict=False)


def broadcast_or_none(net):
    # IPv4 has a directed broadcast only when prefix <= 30.
    # /31 and /32 have no subnet-directed broadcast.
    if isinstance(net, ipaddress.IPv4Network):
        return str(net.broadcast_address) if net.prefixlen <= 30 else None
    return None


def first_last_assignable(net: ipaddress._BaseNetwork):
    """
    Use only ipaddress:
      IPv4: /32 single; /31 use hosts(); else first=next(hosts()), last=broadcast-1
      IPv6: /128 single; /127 use hosts(); else treat whole block (no broadcast in IPv6)
    """
    if isinstance(net, ipaddress.IPv4Network):
        p = net.prefixlen
        if p == 32:
            return str(net.network_address), str(net.network_address)
        if p == 31:
            hs = list(net.hosts())  # exactly two
            return str(hs[0]), str(hs[-1])
        first = next(net.hosts(), None)
        last = net.broadcast_address - 1
        return str(first), str(last)
    else:
        p = net.prefixlen
        if p == 128:
            return str(net.network_address), str(net.network_address)
        if p == 127:
            hs = list(net.hosts())  # exactly two
            return str(hs[0]), str(hs[-1])
        return str(net.network_address), str(net.broadcast_address)


def int_to_be_hex(n: int) -> str:
    """Minimal-length big-endian bytes for n, hex-encoded (lowercase, no '0x')."""
    if n == 0:
        return "00"
    length = (n.bit_length() + 7) // 8
    return n.to_bytes(length, "big").hex()


def build_suite(n_v4=800, n_v6=800, n_pairs=800, rng_seed=42):
    random.seed(rng_seed)

    nets = []

    # Deterministic seeds in documentation ranges
    for p in ipv4_prefixes():
        nets.append(ipaddress.ip_network(("192.0.2.1", p), strict=False))     # TEST-NET-1
    for p in ipv6_prefixes():
        nets.append(ipaddress.ip_network(("2001:db8::1", p), strict=False))   # 2001:db8::/32

    # Random fill
    while sum(isinstance(n, ipaddress.IPv4Network) for n in nets) < n_v4:
        nets.append(rand_net(4, random.choice(ipv4_prefixes())))
    while sum(isinstance(n, ipaddress.IPv6Network) for n in nets) < n_v6:
        nets.append(rand_net(6, random.choice(ipv6_prefixes())))

    # Deduplicate while preserving order
    nets = list(dict.fromkeys(nets))

    # test_networks
    test_networks = []
    for net in nets:
        fa, la = first_last_assignable(net)
        size_hex = int_to_be_hex(net.num_addresses)
        test_networks.append({
            "family": "IPv4" if isinstance(net, ipaddress.IPv4Network) else "IPv6",
            "cidr": str(net),
            "address": str(net.network_address),
            "prefix": net.prefixlen,
            "network_address": str(net.network_address),
            # 'broadcast_address' is "top of block" for both IPv4/IPv6 in ipaddress
            "last_address": str(net.broadcast_address),
            "first_assignable": fa,
            "last_assignable": la,
            "broadcast": broadcast_or_none(net),
            "num_addresses": str(net.num_addresses),  # string to avoid overflow elsewhere
            "size_be_hex": size_hex
        })

    # adjacency_cases using subnets()/supernet() to avoid custom math
    adjacency_cases = []

    # Adjacent siblings: split a known supernet into two children
    for _ in range(n_pairs // 3):
        base = random.choice(nets)
        if base.prefixlen == 0:
            continue
        supernet = base.supernet(new_prefix=base.prefixlen - 1)
        kids = list(supernet.subnets(new_prefix=base.prefixlen))
        if len(kids) >= 2:
            a, b = kids[0], kids[1]
            adjacency_cases.append({
                "family": "IPv4" if isinstance(a, ipaddress.IPv4Network) else "IPv6",
                "a_cidr": str(a),
                "b_cidr": str(b),
                "are_adjacent": True,
                "overlaps": a.overlaps(b),
                "relation": "adjacent"
            })

    # Containment: pair a net with its supernet
    for _ in range(n_pairs // 3):
        base = random.choice(nets)
        if base.prefixlen == 0:
            continue
        supernet = base.supernet(new_prefix=base.prefixlen - 1)
        adjacency_cases.append({
            "family": "IPv4" if isinstance(base, ipaddress.IPv4Network) else "IPv6",
            "a_cidr": str(supernet),
            "b_cidr": str(base),
            "are_adjacent": False,
            "overlaps": supernet.overlaps(base),
            "relation": "A_contains_B" if supernet.supernet_of(base) else "relation_check"
        })

    # Disjoint non-adjacent: pick two subnets with a gap between them
    for _ in range(n_pairs // 3):
        base = random.choice(nets)
        deepest = 127 if isinstance(base, ipaddress.IPv6Network) else 31
        if base.prefixlen >= deepest:
            continue
        sibs = list(base.subnets(new_prefix=base.prefixlen + 1))
        if len(sibs) >= 3:
            a, c = sibs[0], sibs[2]
            adjacency_cases.append({
                "family": "IPv4" if isinstance(a, ipaddress.IPv4Network) else "IPv6",
                "a_cidr": str(a),
                "b_cidr": str(c),
                "are_adjacent": False,
                "overlaps": a.overlaps(c),
                "relation": "disjoint"
            })

    suite = {
        "generated_at": datetime.utcnow().isoformat() + "Z",
        "counts": {
            "test_networks": len(test_networks),
            "adjacency_cases": len(adjacency_cases)
        },
        "test_networks": test_networks,
        "adjacency_cases": adjacency_cases
    }
    return suite


def main():
    ap = argparse.ArgumentParser(description="Generate CIDR test cases with size as big-endian hex.")
    ap.add_argument("--out", default="../cidre/src/jvmTest/resources/pythontest/net_props.json", help="Output JSON path")
    ap.add_argument("--seed", type=int, default=42, help="Random seed")
    ap.add_argument("--v4", type=int, default=800, help="Approx number of IPv4 networks")
    ap.add_argument("--v6", type=int, default=800, help="Approx number of IPv6 networks")
    ap.add_argument("--pairs", type=int, default=800, help="Approx number of relationship pairs")
    args = ap.parse_args()

    suite = build_suite(n_v4=args.v4, n_v6=args.v6, n_pairs=args.pairs, rng_seed=args.seed)

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(suite, f, indent=2)
    print(f"Wrote {args.out} with {suite['counts']['test_networks']} test networks "
          f"and {suite['counts']['adjacency_cases']} adjacency cases (seed={args.seed}).")


if __name__ == "__main__":
    main()
