#!/usr/bin/env python3
import argparse
import ipaddress
import json
import random
from typing import List, Dict, Any, Tuple


def cmp_int(a: int, b: int) -> int:
    if a < b:
        return -1
    if a > b:
        return 1
    return 0


def gen_ipv4_pairs() -> List[Tuple[ipaddress.IPv4Address, ipaddress.IPv4Address]]:
    pairs = []

    # Curated edge/equality cases
    curated = [
        ("0.0.0.0", "0.0.0.0"),
        ("0.0.0.0", "0.0.0.1"),
        ("0.0.0.1", "0.0.0.0"),
        ("0.0.0.255", "0.0.1.0"),
        ("255.255.255.254", "255.255.255.255"),
        ("1.2.3.4", "1.2.3.5"),
        ("1.2.3.5", "1.2.3.4"),
        ("10.0.0.1", "10.0.0.1"),
        ("192.168.0.1", "192.168.0.2"),
        ("127.0.0.1", "127.0.0.1"),
    ]
    for a, b in curated:
        pairs.append((ipaddress.IPv4Address(a), ipaddress.IPv4Address(b)))

    # Deterministic random cases
    rng = random.Random(1337)
    for _ in range(200):
        a = ipaddress.IPv4Address(rng.randrange(0, 2**32))
        b = ipaddress.IPv4Address(rng.randrange(0, 2**32))
        pairs.append((a, b))

    return pairs


def gen_ipv6_pairs() -> List[Tuple[ipaddress.IPv6Address, ipaddress.IPv6Address]]:
    pairs = []

    # Curated edge/equality cases (cover compression, v4-mapped, loopback, etc.)
    curated = [
        ("::", "::"),
        ("::", "::1"),
        ("::1", "::"),
        ("::ffff", "::1:0"),                 # compare short hextets
        ("::ffff:0:0", "::ffff:0:1"),
        ("2001:db8::", "2001:db8::1"),
        ("2001:db8::1", "2001:db8::"),
        ("2001:db8:0:0:0:0:0:1", "2001:db8::1"),  # equality under different spellings
        ("ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"),
        ("::ffff:192.0.2.1", "::ffff:192.0.2.2"),  # IPv4-mapped tail
        ("fe80::1", "fe80::1"),                    # equal link-local
        ("2001:db8::a", "2001:db8::b"),
    ]
    for a, b in curated:
        pairs.append((ipaddress.IPv6Address(a), ipaddress.IPv6Address(b)))

    # Deterministic random cases
    rng = random.Random(2025)
    for _ in range(200):
        # Build IPv6 address from two 64-bit ints to keep it simple and deterministic
        hi = rng.getrandbits(64)
        lo = rng.getrandbits(64)
        a = ipaddress.IPv6Address((hi << 64) | lo)

        hi = rng.getrandbits(64)
        lo = rng.getrandbits(64)
        b = ipaddress.IPv6Address((hi << 64) | lo)

        pairs.append((a, b))

    return pairs


def to_case(version: str, a_str: str, b_str: str, cmp_val: int) -> Dict[str, Any]:
    return {
        "version": version,
        "a": a_str,
        "b": b_str,
        "cmp": cmp_val
    }


def main():
    parser = argparse.ArgumentParser(description="Generate IP address comparison cases as JSON.")
    parser.add_argument("-o", "--output", help="Output JSON file (default: ./cidre/src/jvmTest/resources/pythontest/ip_sort.json)", default="./cidre/src/jvmTest/resources/pythontest/ip_sort.json")
    args = parser.parse_args()

    cases: List[Dict[str, Any]] = []

    # IPv4
    for a, b in gen_ipv4_pairs():
        cmpv = cmp_int(int(a), int(b))
        cases.append(to_case("V4", str(a), str(b), cmpv))

    # IPv6
    for a, b in gen_ipv6_pairs():
        cmpv = cmp_int(int(a), int(b))
        cases.append(to_case("V6", str(a), str(b), cmpv))

    payload = {
        "cases": cases
    }

    text = json.dumps(payload, indent=2, sort_keys=False)

    if args.output == "-" or not args.output:
        print(text)
    else:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(text)


if __name__ == "__main__":
    main()