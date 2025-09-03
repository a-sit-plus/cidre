#!/usr/bin/env python3
"""
generate_large_cidr_fixtures.py  —  scalable, non-hanging generator

Produces LARGE JSON test suites for:
  • Address-in-network membership  -> addr_membership.json
  • Network-in-network containment -> net_containment.json

Key features:
  • --scale {tiny,small,medium,large,huge} to control explosion level
  • Never materializes massive IPv6 subnet lists; uses islice caps
  • Deterministic (no randomness); uses strides & fixed ordering
  • Covers lots of RFC special ranges & edge cases

Usage:
  python generate_large_cidr_fixtures.py --out ./fixtures --scale huge
"""

import argparse, json, ipaddress as ip, itertools, datetime, os
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

def take(iterable, n: int):
    return list(itertools.islice(iterable, n))

def write_json(path: str, obj: Any):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, sort_keys=True)

def addr(a: str) -> ip._BaseAddress:
    return ip.ip_address(a)

def net(n: str, strict=True) -> ip._BaseNetwork:
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
        # NAT64 well-known IPv4 side (to pair with v6 64:ff9b::/96)
        "192.0.2.0/24",  # already included
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
    hi = int(n.broadcast_address)
    fam_bits = 32 if n.version == 4 else 128
    minv, maxv = 0, (1 << fam_bits) - 1

    out.add(str(n.network_address))
    out.add(str(n.broadcast_address))
    if n.version == 4 and n.prefixlen <= 30 and n.num_addresses >= 4:
        out.add(str(ip.ip_address(lo + 1)))
        out.add(str(ip.ip_address(hi - 1)))

    if lo - 1 >= minv:
        out.add(str(ip.ip_address(lo - 1)))
    if hi + 1 <= maxv:
        out.add(str(ip.ip_address(hi + 1)))
    return sorted(out)

def sample_addresses_for_network(n: ip._BaseNetwork, addr_steps_cap: int) -> List[str]:
    """
    Build up to addr_steps_cap interior samples + boundaries, at deterministic stride.
    """
    out = set(derive_boundary_addresses(n))
    lo = int(n.network_address)
    hi = int(n.broadcast_address)
    size = hi - lo + 1

    if size <= 32:
        stride = 1
    elif size <= 256:
        stride = max(1, size // 16)
    elif size <= 65536:
        stride = max(1, size // 64)
    else:
        stride = max(1, size // 256)

    cursor = lo
    steps = 0
    while cursor <= hi and steps < addr_steps_cap:
        out.add(str(ip.ip_address(cursor)))
        cursor += stride
        steps += 1
    return sorted(out)

def bounded_children(n: ip._BaseNetwork, new_prefix: int, max_children: int) -> List[ip._BaseNetwork]:
    """Safely take only the first N children from subnets(new_prefix=...)."""
    if new_prefix < n.prefixlen:
        return []
    return take(n.subnets(new_prefix=new_prefix), max_children)

# -----------------------------
# Generators
# -----------------------------

def generate_addr_membership_cases(scale: str) -> Dict[str, Any]:
    caps = SCALES[scale]
    addr_steps = caps["addr_steps"]
    cross_per = caps["cross_per_net"]

    cases: List[Dict[str, Any]] = []

    # IPv4 networks with bounded sampling
    for nstr in ipv4_canonical_networks(scale):
        n = net(nstr)
        for a in sample_addresses_for_network(n, addr_steps):
            cases.append({"addr": a, "network": nstr, "expect": (addr(a) in n)})

        # cross-tests vs notable prefixes
        for other in ["10.0.0.0/8", "192.168.0.0/16", "203.0.113.0/24", "224.0.0.0/4", "198.18.0.0/15"]:
            o = net(other)
            for a in take(iter(sample_addresses_for_network(n, addr_steps)), cross_per):
                cases.append({"addr": a, "network": other, "expect": (addr(a) in o)})

    # Extra IPv4 addresses vs many networks
    v4_extra_addrs = more_ipv4_addresses()
    v4_compare_nets = ipv4_canonical_networks(scale)
    for a in v4_extra_addrs:
        for nstr in take(iter(v4_compare_nets), min(len(v4_compare_nets), 24 if scale in ("large","huge") else 18)):
            n = net(nstr)
            cases.append({"addr": a, "network": nstr, "expect": (addr(a) in n)})

    # IPv6 networks with bounded sampling
    for nstr in ipv6_canonical_networks(scale):
        n = net(nstr)
        for a in sample_addresses_for_network(n, addr_steps):
            cases.append({"addr": a, "network": nstr, "expect": (addr(a) in n)})
        for other in ["2001:db8::/32", "fc00::/7", "fe80::/10", "ff00::/8", "::ffff:0:0/96", "64:ff9b::/96"]:
            o = net(other)
            for a in take(iter(sample_addresses_for_network(n, addr_steps)), cross_per):
                cases.append({"addr": a, "network": other, "expect": (addr(a) in o)})

    # Extra IPv6 addresses vs many nets
    v6_extra_addrs = more_ipv6_addresses()
    v6_compare_nets = ipv6_canonical_networks(scale)
    for a in v6_extra_addrs:
        for nstr in take(iter(v6_compare_nets), min(len(v6_compare_nets), 28 if scale in ("large","huge") else 20)):
            n = net(nstr)
            cases.append({"addr": a, "network": nstr, "expect": (addr(a) in n)})

    # Deduplicate
    unique, seen = [], set()
    for c in cases:
        key = (c["addr"], c["network"])
        if key not in seen:
            seen.add(key)
            unique.append(c)

    return {
        "meta": {
            "version": f"1.2.0-{scale}",
            "generated": datetime.datetime.utcnow().isoformat() + "Z",
            "notes": f"Address membership suite; scale={scale}; caps={caps}"
        },
        "cases": unique
    }

def generate_net_containment_cases(scale: str) -> Dict[str, Any]:
    caps = SCALES[scale]
    v4c25, v4c26, v4c27 = caps["v4_child25"], caps["v4_child26"], caps["v4_child27"]
    v6c48, v6c64 = caps["v6_child48"], caps["v6_child64"]

    cases: List[Dict[str, Any]] = []

    def add(inner: str, outer: str):
        i, o = net(inner), net(outer)
        cases.append({"inner": inner, "outer": outer, "expect": i.subnet_of(o)})

    # IPv4 ladders (bounded kids)
    for base in ["203.0.113.0/24", "198.51.100.0/24", "192.0.2.0/24"]:
        n = net(base)
        kids25 = bounded_children(n, n.prefixlen + 1, v4c25)
        kids26 = bounded_children(n, n.prefixlen + 2, v4c26)
        kids27 = bounded_children(n, n.prefixlen + 3, v4c27)

        for ch in itertools.chain(kids25, kids26, kids27):
            add(str(ch), base)
        add(base, base)  # equality

        # Sibling falses
        if len(kids25) >= 2:
            add(str(kids25[0]), str(kids25[1]))
        if len(kids26) >= 4:
            add(str(kids26[1]), str(kids26[2]))
        if len(kids27) >= 4:
            add(str(kids27[0]), str(kids27[3]))

        # Parent multi-level sample
        if kids26:
            p = kids26[0].supernet(new_prefix=n.prefixlen)
            add(str(kids26[0]), str(p))

    # IPv4 edge prefixes
    add("198.51.100.0/31", "198.51.100.0/30")   # True
    add("198.51.100.0/31", "198.51.100.2/31")   # False (siblings)
    add("198.51.100.42/32", "198.51.100.42/32") # equality
    add("198.51.100.42/32", "198.51.100.0/24")  # True
    add("198.51.100.43/32", "198.51.100.0/30")  # False

    # Private within broader (and false cross-private)
    for inner, outer in [
        ("192.168.1.0/24", "192.168.0.0/16"),
        ("172.20.0.0/16",  "172.16.0.0/12"),
        ("10.0.0.0/9",     "10.0.0.0/8"),
        ("10.128.0.0/9",   "10.0.0.0/8"),
        ("10.64.0.0/10",   "10.0.0.0/8"),
        ("192.168.0.0/16", "172.16.0.0/12"),
        ("192.168.0.0/16", "10.0.0.0/8"),
    ]:
        add(inner, outer)

    # Special-use vs not
    add("224.0.0.0/24", "224.0.0.0/4")
    add("224.0.1.0/24", "224.0.0.0/4")
    add("192.0.2.0/24", "224.0.0.0/4")   # False

    # IPv6 ladders (bounded)
    for base in ["2001:db8::/32", "2001:db8:0:1::/64"]:
        n = net(base)

        if n.prefixlen <= 48:
            kids48 = bounded_children(n, 48, v6c48)
            for ch in kids48:
                add(str(ch), base)
            if len(kids48) >= 2:
                add(str(kids48[0]), str(kids48[1]))  # sibling false

        if n.prefixlen <= 64:
            kids64 = bounded_children(n, 64, v6c64)
            for ch in take(iter(kids64), max(32, min(256, v6c64))):
                add(str(ch), base)
            if len(kids64) >= 8:
                add(str(kids64[5]), str(kids64[6]))  # sibling false

        add(base, base)  # equality

    # ULA & related falses
    add("fd12:3456:789a::/64", "fd12:3456:789a::/48")
    add("fd12:3456:789a::/48", "fc00::/7")
    add("fd00::/8",            "fc00::/7")
    add("fe80::/64",           "fc00::/7")          # False

    # IPv4-mapped, 6to4, Teredo, NAT64 WKP
    add("::ffff:192.0.2.128/128", "::ffff:0:0/96")
    add("::ffff:0:0/96",          "2001:db8::/32")  # False
    add("::ffff:0:0/96",          "::ffff:0:0/96")  # equality
    add("2002:c000:0201::/48",    "2002::/16")
    add("2001:0:53aa:64c::/64",   "2001::/32")
    add("2002::/16",              "2001:db8::/32")  # False
    add("64:ff9b::/96",           "2001:db8::/32")  # False
    add("64:ff9b::192.0.2.33/128","64:ff9b::/96")   # True (embedded IPv4)

    # Link-local containment
    add("fe80::1/128", "fe80::/10")
    add("fe80:1234::/64", "fe80::/10")

    # /127 and /128 edges
    add("2001:db8::/127", "2001:db8::/64")  # True
    add("2001:db8::/128", "2001:db8::/64")  # True
    add("2001:db8::/64",  "2001:db8::/127") # False

    # Deduplicate
    unique, seen = [], set()
    for c in cases:
        key = (c["inner"], c["outer"])
        if key not in seen:
            seen.add(key)
            unique.append(c)

    return {
        "meta": {
            "version": f"1.2.0-{scale}",
            "generated": datetime.datetime.utcnow().isoformat() + "Z",
            "notes": f"Network containment suite; scale={scale}; caps={SCALES[scale]}"
        },
        "cases": unique
    }

# -----------------------------
# Main
# -----------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="./cidre/src/jvmTest/resources/pythontest", help="Output directory")
    ap.add_argument("--scale", choices=list(SCALES.keys()), default="medium", help="Explosion level")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    addr_fx = generate_addr_membership_cases(args.scale)
    net_fx  = generate_net_containment_cases(args.scale)

    write_json(os.path.join(args.out, "addr_membership.json"), addr_fx)
    write_json(os.path.join(args.out, "net_containment.json"), net_fx)
    write_json(os.path.join(args.out, "INDEX.json"), {
        "meta": {
            "generated": datetime.datetime.utcnow().isoformat() + "Z",
            "tool": "generate_large_cidr_fixtures.py",
            "scale": args.scale
        },
        "files": ["addr_membership.json", "net_containment.json"]
    })

    print(f"[{args.scale}] Wrote {len(addr_fx['cases'])} address-membership cases")
    print(f"[{args.scale}] Wrote {len(net_fx['cases'])} network-containment cases")
    print(f"Output → {os.path.abspath(args.out)}")

if __name__ == "__main__":
    main()
