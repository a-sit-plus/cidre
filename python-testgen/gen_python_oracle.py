#!/usr/bin/env python3
"""
Generate compact Python-oracle fixtures (stdlib ipaddress) for Kotlin tests.
"""

from __future__ import annotations

import argparse
import datetime as dt
import ipaddress
import json
from pathlib import Path


def build_payload() -> dict:
    network_samples = [
        "10.0.0.0/9",
        "8.8.8.0/24",
        "127.1.2.0/24",
        "169.254.42.0/24",
        "224.1.2.0/24",
        "::1/128",
        "fe80::1/128",
        "ff01::/16",
    ]

    network_flags = []
    for cidr in network_samples:
        net = ipaddress.ip_network(cidr, strict=False)
        row = {
            "cidr": str(net),
            "is_loopback": bool(net.is_loopback),
            "is_link_local": bool(net.is_link_local),
            "is_multicast": bool(net.is_multicast),
        }
        network_flags.append(row)

    ipv6_inputs = [
        "2001:db8::1",
        "::1",
        "2001:db8:85a3::8a2e:370:7334",
    ]
    ipv6_expanded = [
        {
            "input": str(ipaddress.IPv6Address(x)),
            "exploded": ipaddress.IPv6Address(x).exploded.lower(),
        }
        for x in ipv6_inputs
    ]

    return {
        "meta": {
            "generated": dt.datetime.now(dt.timezone.utc).isoformat(),
            "tool": "gen_python_oracle.py",
            "source": "python stdlib ipaddress",
        },
        "network_flags": network_flags,
        "ipv6_expanded": ipv6_expanded,
    }


def main() -> None:
    default_out = (
        Path(__file__).resolve().parent.parent
        / "cidre"
        / "src"
        / "jvmTest"
        / "resources"
        / "pythontest"
        / "python_oracle.json"
    )
    ap = argparse.ArgumentParser(description="Generate Python oracle fixtures.")
    ap.add_argument(
        "-o",
        "--output",
        default=str(default_out),
        help="Output JSON path",
    )
    args = ap.parse_args()
    payload = build_payload()
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2, sort_keys=True)


if __name__ == "__main__":
    main()
