import argparse
import ipaddress
import json
import random
from typing import Any, Dict, List, Optional, Tuple


def as_net(cidr: str):
    return ipaddress.ip_network(cidr, strict=True)


def collapse_pair(a, b):
    # Returns list from ipaddress.collapse_addresses
    return list(ipaddress.collapse_addresses([a, b]))


def mergeable_and_supernet(a, b) -> Tuple[bool, Optional[ipaddress._BaseNetwork], Optional[str]]:
    # Use stdlib collapse + subnets to determine mergeability (no custom predicate).
    if a.version != b.version:
        return False, None, "different families"
    if a.prefixlen != b.prefixlen:
        return False, None, "different prefix lengths"
    if a.prefixlen == 0:
        return False, None, "cannot supernet /0"

    collapsed = collapse_pair(a, b)
    if len(collapsed) != 1:
        return False, None, "does not collapse to single supernet"
    sup = collapsed[0]
    if sup.prefixlen != a.prefixlen - 1:
        return False, None, "collapsed supernet is not one bit shorter"

    # Check that sup’s subnets at the original prefix split exactly into {a, b}
    parts = list(sup.subnets(new_prefix=a.prefixlen))
    if len(parts) != 2:
        return False, None, "unexpected subdivision count"
    expect_set = {n.with_prefixlen for n in parts}
    got_set = {a.with_prefixlen, b.with_prefixlen}
    if expect_set != got_set:
        return False, None, "collapsed supernet does not split back into the two inputs"

    return True, sup, None


def make_case(a, b, note: Optional[str] = None) -> Dict[str, Any]:
    can, sup, reason = mergeable_and_supernet(a, b)
    out = {
        "a_cidr": a.with_prefixlen,
        "b_cidr": b.with_prefixlen,
        "can_merge": can,
        "expect": sup.with_prefixlen if can and sup else None,
    }
    if note:
        out["note"] = note
    elif not can and reason:
        out["note"] = reason
    return out


def edge_cases() -> List[Dict[str, Any]]:
    cases: List[Dict[str, Any]] = []

    # IPv4: mergeable siblings (/25 + /25 => /24)
    cases.append(make_case(as_net("192.168.10.0/25"), as_net("192.168.10.128/25"), "mergeable siblings"))
    # IPv4: smallest mergeable (/32 + /32 => /31)
    cases.append(make_case(as_net("0.0.0.0/32"), as_net("0.0.0.1/32"), "mergeable /31 low end"))
    cases.append(make_case(as_net("10.0.0.254/32"), as_net("10.0.0.255/32"), "mergeable /31 high end"))

    # IPv4: adjacent but different prefixes (not mergeable)
    cases.append(make_case(as_net("10.0.0.0/25"), as_net("10.0.0.128/26"), "adjacent, different prefix"))

    # IPv4: containment/overlap (not mergeable)
    cases.append(make_case(as_net("10.0.0.0/24"), as_net("10.0.0.128/25"), "containment"))
    cases.append(make_case(as_net("172.16.0.0/23"), as_net("172.16.1.0/24"), "containment"))

    # IPv4: disjoint
    cases.append(make_case(as_net("192.168.0.0/24"), as_net("192.168.2.0/24"), "disjoint"))

    # IPv6: mergeable siblings (/65 + /65 => /64)
    cases.append(make_case(as_net("2001:db8::/65"), as_net("2001:db8:0:0:8000::/65"), "mergeable siblings /64"))

    # IPv6: different prefixes adjacent (not mergeable)
    cases.append(make_case(as_net("2001:db8::/65"), as_net("2001:db8:0:0:8000::/66"), "adjacent, different prefix"))

    # IPv6: containment (not mergeable)
    cases.append(make_case(as_net("2001:db8::/64"), as_net("2001:db8::/65"), "containment"))

    # IPv6: disjoint
    cases.append(make_case(as_net("2001:db8::/64"), as_net("2001:db8:1::/64"), "disjoint"))

    return cases


def random_ipv4_prefix() -> int:
    # Focus where merges are common
    return random.choice([31, 30, 29, 28, 27, 26, 25, 24])


def random_ipv6_prefix() -> int:
    # Focus near /64
    return random.choice([66, 65, 64, 63])


def random_ipv4_network(prefix: int) -> ipaddress.IPv4Network:
    # Keep in 10.0.0.0/16 area to constrain randomness
    base = ipaddress.IPv4Network("10.0.0.0/16")
    # choose an aligned start
    size = 1 << (32 - prefix)
    start = random.randrange(int(base.network_address), int(base.broadcast_address) + 1 - size, size)
    return ipaddress.IPv4Network((start, prefix), strict=True)


def random_ipv6_network(prefix: int) -> ipaddress.IPv6Network:
    base = ipaddress.IPv6Network("2001:db8::/32")
    size = 1 << (128 - prefix)
    # keep within a modest range in that /32
    start = random.randrange(int(base.network_address), int(base.broadcast_address) + 1 - size, size)
    return ipaddress.IPv6Network((start, prefix), strict=True)


def make_adjacent_of_same_prefix(net):
    # Uses stdlib semantics to build the immediate right neighbor of the same prefix
    low = int(net.network_address)
    high = int(net.broadcast_address)
    step = high - low + 1
    right_int = high + 1
    try:
        return ipaddress.ip_network((right_int, net.prefixlen), strict=True)
    except ValueError:
        # Overflow; return left neighbor instead if possible
        left_int = low - step
        if left_int >= 0:
            return ipaddress.ip_network((left_int, net.prefixlen), strict=True)
        # Should be extremely rare in our constrained ranges
        return net


def random_mergeable_pair(version: int):
    # Build a supernet S, then pick its two subnets ⇒ guaranteed mergeable siblings
    if version == 4:
        # pick a parent prefix between /23 and /31 so children are between /24 and /32
        child_prefix = random.choice([31, 30, 29, 28, 27, 26, 25, 24])
        parent_prefix = child_prefix - 1
        parent = random_ipv4_network(parent_prefix)
        subs = list(parent.subnets(new_prefix=child_prefix))
        a, b = subs[0], subs[1]
        return a, b
    else:
        child_prefix = random.choice([66, 65, 64])  # parent between /65 and /63
        parent_prefix = child_prefix - 1
        parent = random_ipv6_network(parent_prefix)
        subs = list(parent.subnets(new_prefix=child_prefix))
        a, b = subs[0], subs[1]
        return a, b


def random_nonmergeable_pair(version: int):
    # Produce a variety of non-mergeable situations (std lib will verify)
    if version == 4:
        p = random_ipv4_prefix()
        a = random_ipv4_network(p)
        kind = random.choice(["adjacent_diff_prefix", "containment", "disjoint", "adjacent_misaligned"])
        if kind == "adjacent_diff_prefix":
            b = ipaddress.ip_network((int(a.broadcast_address) + 1, max(0, p - 1)), strict=False)
            return a, b
        elif kind == "containment":
            # pick a stricter subnet inside a
            sub_p = min(32, p + 1)
            b = list(a.subnets(new_prefix=sub_p))[0]
            return a, b
        elif kind == "adjacent_misaligned":
            # make neighbor of same prefix, but ensure they are not siblings under a /p-1 parent
            neighbor = make_adjacent_of_same_prefix(a)
            # If siblings, shift a by one block to break alignment
            parent = list(ipaddress.collapse_addresses([a, neighbor]))[0] if a.prefixlen > 0 else a
            if parent.prefixlen == a.prefixlen - 1:
                # Move one block
                size = (1 << (32 - a.prefixlen))
                a = ipaddress.IPv4Network((int(a.network_address) + size, a.prefixlen), strict=True)
                neighbor = make_adjacent_of_same_prefix(a)
            return a, neighbor
        else:
            # disjoint: pick another random net and retry a few times until not adjacent/overlapping
            tries = 0
            while True:
                b = random_ipv4_network(p)
                if not a.overlaps(b):
                    collapsed = list(ipaddress.collapse_addresses([a, b]))
                    if len(collapsed) != 1:  # not collapsing to one ⇒ not mergeable nor containment
                        return a, b
                tries += 1
                if tries > 10:
                    return a, b
    else:
        p = random_ipv6_prefix()
        a = random_ipv6_network(p)
        kind = random.choice(["adjacent_diff_prefix", "containment", "disjoint", "adjacent_misaligned"])
        if kind == "adjacent_diff_prefix":
            b = ipaddress.ip_network((int(a.broadcast_address) + 1, max(0, p - 1)), strict=False)
            return a, b
        elif kind == "containment":
            sub_p = min(128, p + 1)
            b = list(a.subnets(new_prefix=sub_p))[0]
            return a, b
        elif kind == "adjacent_misaligned":
            neighbor = make_adjacent_of_same_prefix(a)
            parent = list(ipaddress.collapse_addresses([a, neighbor]))[0] if a.prefixlen > 0 else a
            if parent.prefixlen == a.prefixlen - 1:
                # shift one block of size 2^(128-p)
                size = 1 << (128 - a.prefixlen)
                a = ipaddress.IPv6Network((int(a.network_address) + size, a.prefixlen), strict=True)
                neighbor = make_adjacent_of_same_prefix(a)
            return a, neighbor
        else:
            tries = 0
            while True:
                b = random_ipv6_network(p)
                if not a.overlaps(b):
                    collapsed = list(ipaddress.collapse_addresses([a, b]))
                    if len(collapsed) != 1:
                        return a, b
                tries += 1
                if tries > 10:
                    return a, b


def random_cases(count: int) -> List[Dict[str, Any]]:
    cases: List[Dict[str, Any]] = []
    for _ in range(count):
        version = random.choice([4, 6])
        kind = random.choices(
            population=["mergeable", "nonmergeable"],
            weights=[3, 5],  # slightly more negatives to broaden coverage
            k=1
        )[0]

        if kind == "mergeable":
            a, b = random_mergeable_pair(version)
            cases.append(make_case(a, b, "random mergeable siblings"))
        else:
            a, b = random_nonmergeable_pair(version)
            cases.append(make_case(a, b, "random non-mergeable"))
    return cases


def main():
    parser = argparse.ArgumentParser(description="Generate merge test cases using Python stdlib ipaddress.")
    parser.add_argument("--out", type=str, default="../cidre/src/jvmTest/resources/pythontest/merge_cases.json", help="Output JSON file path")
    parser.add_argument("--seed", type=int, default=1337, help="Random seed")
    parser.add_argument("--random-count", type=int, default=200, help="Number of random cases to generate")
    args = parser.parse_args()

    random.seed(args.seed)

    data: List[Dict[str, Any]] = []
    data.extend(edge_cases())
    data.extend(random_cases(args.random_count))

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print(f"Wrote {len(data)} cases to {args.out}")


if __name__ == "__main__":
    main()