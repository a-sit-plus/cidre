#!/usr/bin/env python3
# Python 3.x
# Requires: pip install netaddr

import argparse
import json
from datetime import datetime
import ipaddress as ip

def build_cases():
    cases = []

    variants = [
        ("V4", 4, 32, "0.0.0.0"),
        ("V6", 16, 128, "::"),
    ]

    for version, octets, max_prefix, base in variants:
        for p in range(0, max_prefix + 1):
            n = ip.ip_network(f"{base}/{p}", strict=True)
            mask = n.netmask
            packed = mask.packed  # bytes
            if len(packed) != octets:
                raise RuntimeError(f"Unexpected netmask length for {version}/{p}: {len(packed)} vs {octets}")
            cases.append({
                "version": version,           # "V4" or "V6"
                "prefix": p,                  # integer prefix length
                "octet_count": octets,        # 4 or 16
                "netmask_hex": packed.hex(),  # lowercase hex, no separators
            })
    return cases


def main():
    ap = argparse.ArgumentParser(description="Generate prefix->netmask cases using netaddr")
    ap.add_argument("-o", "--output", default="../cidre/src/jvmTest/resources/pythontest/netmask.json", help="Output JSON file (default: ../cidre/src/jvmTest/resources/pythontest/netmask.json)")
    args = ap.parse_args()

    payload = {
        "meta": {
            "tool": "gen_netmasks_netaddr.py",
            "generated": datetime.utcnow().isoformat() + "Z",
            "source": "netaddr",
        },
        "cases": build_cases()
    }

    text = json.dumps(payload, indent=2)
    if args.output == "-" or not args.output:
        print(text)
    else:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(text)

if __name__ == "__main__":
    main()