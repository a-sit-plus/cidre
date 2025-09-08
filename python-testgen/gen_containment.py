#!/usr/bin/env python3
"""
generate_large_cidr_fixtures.py — scalable, deterministic generator

Produces JSON test suites for:
  • Address-in-network membership  -> addr_membership.json
  • Network-in-network containment -> net_containment.json

Key features:
  • --scale {tiny,small,medium,large,huge} to control size
  • Deterministic (no randomness)
  • RFC-driven canonical ranges plus boundary/sibling/parent sampling
  • More "False" net-containment cases without reducing "True" cases

Usage:
  python generate_large_cidr_fixtures.py --out ./fixtures --scale medium
"""

from __future__ import annotations

import argparse
import datetime
import ipaddress as ip
import itertools
import json
import os
from typing import Iterable, List, Dict, Any


# -----------------------------
# Scale presets (tune safely)
# -----------------------------
SCALES = {
    # conservative smoke
    "tiny":   dict(addr_steps=12,  cross_per_net=4,  v4_child25=4,  v4_child26=8,  v4_child27=8,
                   v6_child48=8,  v6_child64=8),
    # small CI job
    "small":  dict(addr_steps=24,  cross_per_net=6,  v4_child25=8,  v4_child26=12, v4_child27=16,
                   v6_child48=16, v6_child64=16),
    # default
    "medium": dict(addr_steps=64,  cross_per_net=10, v4_child25=8,  v4_child26=16, v4_child27=16,
                   v6_child48=64, v6_child64=64),
    # big but sane
    "large":  dict(addr_steps=256, cross_per_net=24, v4_child25=32, v4_child26=64, v4_child27=128,
                   v6_child48=256, v6_child64=512),
    # aggressively big while bounded
    "huge":   dict(addr_steps=512, cross_per_net=64, v4_child25=128, v4_child26=256, v4_child27=512,
                   v6_child48=1024, v6_child64=2048),
}


# -----------------------------
# Helpers
# -----------------------------

def take(iterable: Iterable[Any], n: int) -> List[Any]:
    return list(itertools.islice(iterable, n))


def write_json(path: str, obj: Any):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, sort_keys=True)


def addr(a: str) -> ip._BaseAddress:
    return ip.ip_address(a)


def net(n: str, strict: bool = True) -> ip._BaseNetwork:
    return ip.ip_network(n, strict=strict)


# -----------------------------
# Canonical datasets (RFC-driven)
# -----------------------------

def ipv4_canonical_networks(scale: str) -> List[str]:
    base = [
        # RFC 5737 documentation
        "192.0.2.0/24", "198.51.100.0/24", "203.0.113.0/24",
        # RFC 1918 private
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
        # RFC 6598 CGNAT
        "100.64.0.0/10",
        # RFC 3927 link-local
        "169.254.0.0/16",
        # Loopback, multicast, class E/reserved, benchmark, 6to4 relay (deprecated)
        "127.0.0.0/8", "224.0.0.0/4", "240.0.0.0/4",
        "198.18.0.0/15",    # benchmarking
        "192.88.99.0/24",   # 6to4 relay anycast (deprecated)
        "0.0.0.0/8",        # special
        "255.255.255.255/32",
        # Handy split grids inside doc space
        "203.0.113.0/25", "203.0.113.128/25",
        "203.0.113.0/26", "203.0.113.64/26", "203.0.113.128/26", "203.0.113.192/26",
        # /31 (RFC 3021) and /32
        "198.51.100.0/31", "198.51.100.2/31", "198.51.100.4/31",
        "198.51.100.42/32",
    ]
    if scale in ("large", "huge"):
        # add more grids inside 192.0.2.0/24 and 198.51.100.0/24
        base += [
            "192.0.2.0/25", "192.0.2.128/25",
            "192.0.2.0/27", "192.0.2.32/27", "192.0.2.64/27", "192.0.2.96/27",
            "192.0.2.128/27", "192.0.2.160/27", "192.0.2.192/27", "192.0.2.224/27",
            "198.51.100.0/25", "198.51.100.128/25",
            "198.51.100.0/26", "198.51.100.64/26", "198.51.100.128/26", "198.51.100.192/26",
        ]
    return base


def ipv6_canonical_networks(scale: str) -> List[str]:
    base = [
        # RFC 3849 documentation
        "2001:db8::/32",
        # additional doc subspaces
        "2001:db8:1::/48", "2001:db8:2::/48", "2001:db8:ffff::/48",
        # ULA/link-local/multicast
        "fc00::/7", "fd00::/8", "fd12:3456:789a::/48",
        "fe80::/10", "ff00::/8", "ff02::/16",
        # IPv4-mapped, 6to4, Teredo, NAT64 well-known prefix
        "::ffff:0:0/96", "2002::/16", "2001::/32", "64:ff9b::/96",
        # Edge subnets
        "2001:db8::/64", "2001:db8::/127", "2001:db8::/128",
        "2001:db8:0:1::/64",
        # ORCHIDv2 (RFC 7343)
        "2001:20::/28",
    ]
    if scale in ("large", "huge"):
        # more doc-space /64s for sibling/ladder tests
        base += [
            "2001:db8:0:2::/64", "2001:db8:0:3::/64", "2001:db8:0:4::/64",
            "2001:db8:0:5::/64", "2001:db8:0:6::/64", "2001:db8:0:7::/64",
        ]
    return base


def more_ipv4_addresses() -> List[str]:
    return [
        "192.0.2.0", "192.0.2.1", "192.0.2.254", "192.0.2.255",
        "198.51.100.1", "203.0.113.200",
        "10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.255", "192.168.1.1",
        "169.254.1.1", "127.0.0.1", "224.0.0.1", "255.255.255.255",
        "198.18.0.1", "192.88.99.1", "0.0.0.0",
    ]


def more_ipv6_addresses() -> List[str]:
    return [
        "2001:db8::", "2001:db8::1", "2001:db8::ffff", "2001:db8:1::1",
        "fe80::1", "fe80::abcd",
        "fd00::1", "fd12:3456:789a::1",
        "::1", "::",
        "ff02::1", "ff05::2",
        "::ffff:0:0", "::ffff:192.0.2.128",
        "2002:c000:0201::", "2001:0:53aa:64c::1",
        "64:ff9b::192.0.2.33",
    ]


# -----------------------------
# Sampling & bounded enumeration
# -----------------------------

def derive_boundary_addresses(n: ip._BaseNetwork) -> List[str]:
    out = set()
    lo = int(n.network_address)
    hi = int(getattr(n, "broadcast_address", n.network_address))  # IPv6 has no broadcast in lib, but equal for range
    fam_bits = 32 if n.version == 4 else 128
    minv, maxv = 0, (1 << fam_bits) - 1

    out.add(str(n.network_address))
    if n.version == 4:
        out.add(str(n.broadcast_address))
        if n.prefixlen <= 30 and n.num_addresses >= 4:
            out.add(str(ip.ip_address(lo + 1)))
            out.add(str(ip.ip_address(hi - 1)))
    else:
        # For IPv6, synthesize "hi" as last address of the range
        last = int(n.network_address) + (n.num_addresses - 1)
        out.add(str(ip.ip_address(last)))
        # Pick neighbors if in range
        if n.num_addresses >= 4:
            out.add(str(ip.ip_address(int(n.network_address) + 1)))
            out.add(str(ip.ip_address(last - 1)))

    if lo - 1 >= minv:
        out.add(str(ip.ip_address(lo - 1)))
    if hi + 1 <= maxv:
        out.add(str(ip.ip_address(hi + 1)))
    return sorted(out)


def sample_addresses_for_network(n: ip._BaseNetwork, addr_steps_cap: int) -> List[str]:
    # Combine boundary sampling with coarse stepping through the space
    out = list(derive_boundary_addresses(n))
    if addr_steps_cap <= 0:
        return sorted(set(out))

    # step over host-space in a deterministic way
    total = n.num_addresses
    steps = min(addr_steps_cap, max(0, total - 1))
    if steps > 0:
        stride = max(1, total // steps)
        base = int(n.network_address)
        for k in range(steps):
            a = base + min(total - 1, k * stride)
            out.append(str(ip.ip_address(a)))
    return sorted(set(out))


# -----------------------------
# Net-containment extra False-case builders
# -----------------------------

def sibling_of(n: ip._BaseNetwork) -> ip._BaseNetwork | None:
    """
    Returns a sibling network with the same prefix length as n, if it exists.
    """
    try:
        parent = n.supernet()
    except ValueError:
        return None
    subs = list(parent.subnets(new_prefix=n.prefixlen))
    if len(subs) < 2:
        return None
    return subs[1] if subs[0] == n else subs[0]


def bounded_child(n: ip._BaseNetwork, new_prefix: int) -> ip._BaseNetwork | None:
    """
    Returns the first child at new_prefix if it is longer than n.prefixlen, else None.
    """
    if new_prefix <= n.prefixlen:
        return None
    # Guard against exceeding family max (/32 for IPv4, /128 for IPv6)
    maxp = getattr(n, "max_prefixlen", 32 if n.version == 4 else 128)
    if new_prefix > maxp:
        return None
    try:
        return next(n.subnets(new_prefix=new_prefix))
    except (StopIteration, ValueError):
        # ValueError can happen if new_prefix is invalid for this netblock
        return None



def expand_false_pairs_for_outer(outer: ip._BaseNetwork, budget: int) -> list[dict[str, object]]:
    """
    Produce multiple not-contained cases for a given outer, within a deterministic budget.
    Strategies (in order, until budget is exhausted):
      1) Parent-vs-child: inner = parent(outer), expect False
      2) Sibling: inner = sibling(outer), expect False
      3) Sibling's child: inner = first-child(sibling, outer.prefixlen+1), expect False
      4) Wider parent ladders: move up more parent levels
      5) Additional sibling children with deeper prefixes for variety
    """
    out: list[dict[str, object]] = []
    remaining = budget

    # 1) Parent vs child (shorter prefix cannot be contained in the more specific outer)
    try:
        parent = outer.supernet()
    except ValueError:
        parent = None
    if parent and remaining > 0:
        out.append(dict(inner=str(parent), outer=str(outer), expect=False))
        remaining -= 1

    # 2) Sibling at same prefix (disjoint)
    sib = sibling_of(outer)
    if sib and remaining > 0:
        out.append(dict(inner=str(sib), outer=str(outer), expect=False))
        remaining -= 1

    # 3) A subnet of the sibling (also disjoint)
    if sib and remaining > 0:
        sib_child = bounded_child(sib, outer.prefixlen + 1)
        if sib_child:
            out.append(dict(inner=str(sib_child), outer=str(outer), expect=False))
            remaining -= 1

    # 4) Walk further up the ladder a bit
    up = parent
    steps = 0
    while up and remaining > 0 and steps < 2:
        try:
            up2 = up.supernet()
        except ValueError:
            up2 = None
        if up2:
            out.append(dict(inner=str(up2), outer=str(outer), expect=False))
            remaining -= 1
        up = up2
        steps += 1

    # 5) If budget remains, make deeper sibling children
    if sib and remaining > 0:
        maxp = getattr(outer, "max_prefixlen", 32 if outer.version == 4 else 128)
        depth = 2
        while remaining > 0 and depth <= 4 and (outer.prefixlen + depth) <= maxp:
            sib_deeper = bounded_child(sib, outer.prefixlen + depth)
            if sib_deeper:
                out.append(dict(inner=str(sib_deeper), outer=str(outer), expect=False))
                remaining -= 1
            depth += 1

    return out



# -----------------------------
# Case assembly
# -----------------------------

def assemble_addr_membership_cases(scale: str) -> list[dict[str, object]]:
    params = SCALES[scale]
    addr_steps = int(params.get("addr_steps", 64))

    v4_nets = [net(n) for n in ipv4_canonical_networks(scale)]
    v6_nets = [net(n) for n in ipv6_canonical_networks(scale)]

    cases: list[dict[str, object]] = []

    # IPv4: sample addresses per network
    for n4 in v4_nets:
        for a in sample_addresses_for_network(n4, addr_steps):
            a4 = addr(a)
            expect = a4 in n4
            cases.append(dict(address=str(a4), network=str(n4), expect=bool(expect)))

    # Add more IPv4 addresses against multiple nets
    for a in more_ipv4_addresses():
        a4 = addr(a)
        # Test against all canonical v4 nets (bounded by scale through size of canonical list)
        for n4 in v4_nets:
            cases.append(dict(address=str(a4), network=str(n4), expect=bool(a4 in n4)))

    # IPv6: sample addresses per network
    for n6 in v6_nets:
        for a in sample_addresses_for_network(n6, addr_steps):
            a6 = addr(a)
            expect = a6 in n6
            cases.append(dict(address=str(a6), network=str(n6), expect=bool(expect)))

    # Add more IPv6 addresses against multiple nets
    for a in more_ipv6_addresses():
        a6 = addr(a)
        for n6 in v6_nets:
            cases.append(dict(address=str(a6), network=str(n6), expect=bool(a6 in n6)))

    return cases


def true_net_containment_pairs_for_outer(outer: ip._BaseNetwork, params: Dict[str, Any]) -> list[dict[str, object]]:
    """
    Build 'True' containment cases for a given outer network:
      - identical network
      - a few children at selected deeper prefixes
      - ladder of descendants
    """
    out: list[dict[str, object]] = []

    # identical
    out.append(dict(inner=str(outer), outer=str(outer), expect=True))

    # children at specific depths depending on family and scale knobs
    if outer.version == 4:
        for dp_key in ("v4_child25", "v4_child26", "v4_child27"):
            new_prefix_count = params.get(dp_key, 0)
            target_prefix = min(32, max(0, int(outer.prefixlen) + int(dp_key.split("_child")[-1])))
            if target_prefix <= 32 and target_prefix > outer.prefixlen and new_prefix_count > 0:
                # take a bounded number of children at target_prefix
                try:
                    children = take(outer.subnets(new_prefix=target_prefix), min(new_prefix_count, 8))
                    for ch in children:
                        out.append(dict(inner=str(ch), outer=str(outer), expect=True))
                except ValueError:
                    pass
    else:
        # IPv6 knobs
        targets = [(48, "v6_child48"), (64, "v6_child64")]
        for target, key in targets:
            if outer.prefixlen < target:
                count = params.get(key, 0)
                if count > 0:
                    try:
                        children = take(outer.subnets(new_prefix=target), min(count, 32))
                        for ch in children:
                            out.append(dict(inner=str(ch), outer=str(outer), expect=True))
                    except ValueError:
                        pass

    # descendant ladder: go deeper a few steps if possible
    cursor = outer
    steps = 0
    while steps < 3 and cursor.prefixlen < (32 if outer.version == 4 else 128):
        newp = cursor.prefixlen + 1
        try:
            nxt = next(cursor.subnets(new_prefix=newp))
        except StopIteration:
            break
        out.append(dict(inner=str(nxt), outer=str(outer), expect=True))
        cursor = nxt
        steps += 1

    return out


def assemble_net_containment_cases(scale: str) -> list[dict[str, object]]:
    params = SCALES[scale]
    cross_budget = int(params.get("cross_per_net", 6))

    v4_nets = [net(n) for n in ipv4_canonical_networks(scale)]
    v6_nets = [net(n) for n in ipv6_canonical_networks(scale)]

    cases: list[dict[str, object]] = []

    # TRUE cases first (kept intact)
    for n4 in v4_nets:
        cases.extend(true_net_containment_pairs_for_outer(n4, params))
    for n6 in v6_nets:
        cases.extend(true_net_containment_pairs_for_outer(n6, params))

    # Additional FALSE cases (append-only, does not reduce TRUE)
    for outer in v4_nets:
        cases.extend(expand_false_pairs_for_outer(outer, cross_budget))
    for outer in v6_nets:
        cases.extend(expand_false_pairs_for_outer(outer, cross_budget))

    # Cross-family sanity: ensure no mixing (inner/outer must be same IP version)
    # The above builders only operate within a family; nothing to do here.

    return cases


# -----------------------------
# CLI and main
# -----------------------------

def main():
    parser = argparse.ArgumentParser(description="Generate scalable CIDR fixtures")
    parser.add_argument("--out", default="../cidre/src/jvmTest/resources/pythontest", help="Output directory for JSON fixtures")
    parser.add_argument("--scale", choices=list(SCALES.keys()), default="medium", help="Fixture size preset")
    parser.add_argument("--no-membership", action="store_true", help="Skip addr_membership.json")
    parser.add_argument("--no-containment", action="store_true", help="Skip net_containment.json")
    args = parser.parse_args()

    ts = datetime.datetime.utcnow().replace(microsecond=0).isoformat() + "Z"
    meta = {
        "generated": ts,
        "notes": args.scale,
        "version": "0.2"
    }

    if not args.no_membership:
        addr_cases = assemble_addr_membership_cases(args.scale)
        write_json(os.path.join(args.out, "addr_membership.json"), {"meta": meta, "cases": addr_cases})

    if not args.no_containment:
        net_cases = assemble_net_containment_cases(args.scale)
        write_json(os.path.join(args.out, "net_containment.json"), {"meta": meta, "cases": net_cases})

    print(f"[OK] Wrote fixtures to: {args.out} (scale={args.scale})")


if __name__ == "__main__":
    main()