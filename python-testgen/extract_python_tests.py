#!/usr/bin/env python3
"""
cidr_fixture_extractor_plain.py

Plain-Python (stdlib-only) tool that builds JSON CIDR/IP fixtures from
CPython's ipaddress test data.

It can either:
  A) Download CPython's test_ipaddress.py from GitHub (default branch or tag)
  B) Read a local file you provide

Then it computes structured JSON with expected results using the stdlib
`ipaddress` module (no third-party deps).

Usage examples:
  # Download from CPython "main" branch and write fixtures to ./out
  python cidr_fixture_extractor_plain.py --from-url main --out ./out

  # Download from CPython 3.12 branch
  python cidr_fixture_extractor_plain.py --from-url 3.12 --out ./out312

  # Use a local file
  python cidr_fixture_extractor_plain.py --from-file /path/to/test_ipaddress.py --out ./out_local

Outputs (in --out):
  parsing.json
  normalization.json
  subnetting.json
  supernetting.json
  overlaps_containment.json
  set_operations.json
  INDEX.json
"""

import argparse, ast, ipaddress as ip, json, os, re, sys, datetime, urllib.request, tempfile
from dataclasses import dataclass, asdict
from typing import List, Dict, Tuple

RAW_URL_TMPL = "https://raw.githubusercontent.com/python/cpython/{ref}/Lib/test/test_ipaddress.py"
# --- add near your imports ---
BITS = {4: 32, 6: 128}

def _first_child(n):
    # Return the first child by splitting one bit deeper
    return next(n.subnets(prefixlen_diff=1))

def _second_child(n):
    it = n.subnets(prefixlen_diff=1)
    next(it)
    return next(it)

def build_augmented_pairs(valid_networks):
    """Create deterministic pairs covering containment and disjoint adjacency."""
    nets = [ip.ip_network(s, strict=True) for s in valid_networks]
    pairs = []

    seen = set()
    def add_pair(a, b):
        key = (str(min(a,b, key=lambda x:(x.version, int(x.network_address), x.prefixlen))),
               str(max(a,b, key=lambda x:(x.version, int(x.network_address), x.prefixlen))))
        if key not in seen:
            seen.add(key)
            pairs.append({"a": str(a), "b": str(b)})

    for n in nets:
        # 1) Containment: n vs its parent (if aligned)
        if n.prefixlen > 0:
            try:
                p = n.supernet(new_prefix=n.prefixlen - 1)
                add_pair(n, p)  # should yield overlap + subnet_of
            except ValueError:
                pass  # misaligned (shouldn't happen with strict nets)

        # 2) Containment the other way: n vs one of its own children
        fam_max = BITS[n.version]
        if n.prefixlen + 1 <= fam_max:
            c = _first_child(n)
            add_pair(c, n)

        # 3) Disjoint siblings: two children of the same parent
        if n.prefixlen > 0:
            try:
                p = n.supernet(new_prefix=n.prefixlen - 1)
                kids = list(p.subnets(new_prefix=n.prefixlen))
                if len(kids) >= 2:
                    add_pair(kids[0], kids[1])  # adjacent, disjoint
            except ValueError:
                pass

    return pairs

def truth_table(pairs):
    out = []
    for pair in pairs:
        a = ip.ip_network(pair["a"], strict=True)
        b = ip.ip_network(pair["b"], strict=True)
        out.append({
            "a": str(a),
            "b": str(b),
            "overlaps": a.overlaps(b),
            "a_subnet_of_b": a.subnet_of(b),
            "b_subnet_of_a": b.subnet_of(a),
            "a_supernet_of_b": a.supernet_of(b),
            "b_supernet_of_a": b.supernet_of(a),
        })
    return out


@dataclass
class Meta:
    source_file: str
    generated: str
    source_ref: str
    tool: str = "cidr_fixture_extractor_plain.py"

def download_test_file(ref: str) -> str:
    url = RAW_URL_TMPL.format(ref=ref)
    with urllib.request.urlopen(url) as resp:
        content = resp.read().decode("utf-8")
    fd, tmp = tempfile.mkstemp(prefix="test_ipaddress_", suffix=".py")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(content)
    return tmp

def collect_string_literals(py_path: str) -> List[Tuple[int, str]]:
    with open(py_path, "r", encoding="utf-8") as f:
        source = f.read()
    tree = ast.parse(source, filename=py_path)
    out = []
    class V(ast.NodeVisitor):
        def visit_Constant(self, node):
            if isinstance(node.value, str):
                out.append((getattr(node, "lineno", -1), node.value))
        def visit_Str(self, node):  # Py<3.8
            out.append((getattr(node, "lineno", -1), node.s))
    V().visit(tree)
    return out

def classify_literals(lits):
    v4_addrs, v6_addrs = set(), set()
    v4_nets, v6_nets = set(), set()
    v4_nets_loose, v6_nets_loose = set(), set()
    v4_ifaces, v6_ifaces = set(), set()
    invalids = set()

    for _, s in lits:
        s = s.strip()
        if not s:
            continue
        if not re.search(r'[0-9:/\.a-fA-F]', s):
            continue

        # 🚫 Reject IPv6 zone indices (RFC 4007) early; ipaddress doesn't accept them
        if '%' in s:
            invalids.add(s)
            continue

        if '/' not in s:
            try:
                a = ip.ip_address(s)
                (v4_addrs if a.version == 4 else v6_addrs).add(s)
                continue
            except ValueError:
                pass

        if '/' in s:
            # Interfaces first (still no % allowed because of early guard)
            try:
                i = ip.ip_interface(s)
                (v4_ifaces if i.version == 4 else v6_ifaces).add(str(i))
            except ValueError:
                pass

            # Networks (strict), otherwise loose
            try:
                n = ip.ip_network(s, strict=True)
                (v4_nets if n.version == 4 else v6_nets).add(str(n))
            except ValueError:
                try:
                    n = ip.ip_network(s, strict=False)
                    (v4_nets_loose if n.version == 4 else v6_nets_loose).add(str(n))
                except ValueError:
                    invalids.add(s)
                continue
            else:
                continue

        # Fallback: looks IP-ish but didn't parse
        if re.search(r'[0-9a-fA-F]', s) and ('.' in s or ':' in s):
            invalids.add(s)

    return {
        "v4_addrs": sorted(v4_addrs), "v6_addrs": sorted(v6_addrs),
        "v4_nets": sorted(v4_nets), "v6_nets": sorted(v6_nets),
        "v4_nets_loose": sorted(v4_nets_loose), "v6_nets_loose": sorted(v6_nets_loose),
        "v4_ifaces": sorted(v4_ifaces), "v6_ifaces": sorted(v6_ifaces),
        "invalids": sorted(invalids),
    }


def find_network_pairs(strings: List[Tuple[int,str]]):
    pairs = set()
    byline = {}
    for lineno, s in strings:
        byline.setdefault(lineno, []).append(s)
    for lineno, arr in byline.items():
        nets = []
        for s in arr:
            s = s.strip()
            if '/' in s:
                try:
                    n = ip.ip_network(s, strict=False)
                    nets.append(str(n))
                except ValueError:
                    pass
        for i in range(len(nets)):
            for j in range(i+1, len(nets)):
                a, b = nets[i], nets[j]
                if a != b:
                    pairs.add(tuple(sorted((a,b))))
    return [{"a":a,"b":b} for a,b in sorted(pairs)]

def normalize_interfaces_and_loose(v4_ifaces, v6_ifaces, v4_loose, v6_loose):
    cases = []
    for s in list(v4_ifaces)+list(v6_ifaces):
        i = ip.ip_interface(s)
        cases.append({"input": s, "expect_network": str(i.network)})
    # ... existing code ...
    for s in list(v4_loose)+list(v6_loose):
        n = ip.ip_network(s, strict=False)
        cases.append({"input": s, "expect_network": str(n)})
    # ... existing code ...
    # Augment with non-normalized host inputs for every network; the fixture may grow.
    # For each unique network, generate several host addresses within it that normalize back to the network.
    unique_nets = {}
    for c in cases:
        try:
            net = ip.ip_network(c["expect_network"], strict=True)
            unique_nets[str(net)] = net
        except Exception:
            pass
    augmented = []
    for net_str, net in unique_nets.items():
        # Single-address networks cannot produce a distinct host input
        if net.num_addresses <= 1:
            continue
        total = net.num_addresses
        # Select representative offsets inside the network:
        # - first usable (1), small offset (min(5, last)), middle, last
        # These are safe for both IPv4 and IPv6; for IPv4, including the broadcast as input is fine for normalization.
        last = int(total) - 1
        offsets = {1, min(5, last), last // 2, last}
        for off in sorted(offsets):
            try:
                host = net.network_address + off
                # Skip if somehow equals the network address (off == 0), otherwise include
                if host == net.network_address:
                    continue
                host_input = f"{host}/{net.prefixlen}"
                augmented.append({"input": host_input, "expect_network": net_str})
            except Exception:
                # Be robust in case of any edge-case arithmetic or type issue
                continue
    cases.extend(augmented)
    # ... existing code ...
    seen = set(); out = []
    for c in cases:
        key = (c["input"], c["expect_network"])
        if key not in seen:
            out.append(c); seen.add(key)
    return out

def subnet_cases(nets: List[str]):
    cases, errors = [], []
    for s in nets:
        n = ip.ip_network(s, strict=True)
        fam_max = 32 if n.version==4 else 128
        p = n.prefixlen
        for step in (1,2):
            np = p+step
            if np <= fam_max:
                subs = [str(x) for x in n.subnets(prefixlen_diff=step)]
                cases.append({"parent": str(n), "prefixlen_diff": step, "expect": subs})
        if p+4 <= fam_max:
            subs = [str(x) for x in n.subnets(new_prefix=p+4)]
            cases.append({"parent": str(n), "new_prefix": p+4, "expect": subs})
        if p-1 >= 0:
            errors.append({"parent": str(n), "new_prefix": p-1, "expect_error":"ValueError"})
        errors.append({"parent": str(n), "new_prefix": fam_max+1, "expect_error":"ValueError"})
    return cases, errors

def supernet_cases(nets: List[str]):
    cases, errors = [], []
    for s in nets:
        n = ip.ip_network(s, strict=True)
        p = n.prefixlen
        fam_min = 0
        if p > fam_min:
            target = p-1
            try:
                res = n.supernet(new_prefix=target)
                cases.append({"child": str(n), "new_prefix": target, "expect": str(res)})
            except ValueError:
                errors.append({"child": str(n), "new_prefix": target, "expect_error":"ValueError","reason":"misaligned"})
        step = 2
        if p-step >= fam_min:
            try:
                res = n.supernet(prefixlen_diff=step)
                cases.append({"child": str(n), "prefixlen_diff": step, "expect": str(res)})
            except ValueError:
                errors.append({"child": str(n), "prefixlen_diff": step, "expect_error":"ValueError","reason":"misaligned"})
        try:
            n.supernet(new_prefix=p+1)
            errors.append({"child": str(n), "new_prefix": p+1, "expect_error":"ValueError"})
        except ValueError:
            errors.append({"child": str(n), "new_prefix": p+1, "expect_error":"ValueError"})
    return cases, errors

def truth_table_pairs(pairs: List[Dict[str,str]]):
    out = []
    for pair in pairs:
        a = ip.ip_network(pair["a"], strict=False)
        b = ip.ip_network(pair["b"], strict=False)
        rec = {
            "a": str(a), "b": str(b),
            "overlaps": a.overlaps(b),
            "a_subnet_of_b": a.subnet_of(b),
            "b_subnet_of_a": b.subnet_of(a),
            "a_supernet_of_b": a.supernet_of(b),
            "b_supernet_of_a": b.supernet_of(a)
        }
        out.append(rec)
    return out

def set_operations_examples(nets: List[str]):
    nets_objs = [ip.ip_network(s, strict=True) for s in nets]
    out = {"union": [], "intersection": [], "difference": []}
    for a in nets_objs[:6]:
        for b in nets_objs[:6]:
            if a.version != b.version or a == b:
                continue
            collapsed = [str(x) for x in ip.collapse_addresses([a, b])]
            start = min(a.network_address, b.network_address)
            end   = max(a.broadcast_address, b.broadcast_address)
            covering = [str(x) for x in ip.summarize_address_range(start, end)]
            out["union"].append({"inputs":[str(a),str(b)],"collapse":collapsed,"covering":covering})
            if a.overlaps(b):
                start_i = max(a.network_address, b.network_address)
                end_i   = min(a.broadcast_address, b.broadcast_address)
                inter = [str(x) for x in ip.summarize_address_range(start_i, end_i)]
            else:
                inter = []
            out["intersection"].append({"a":str(a),"b":str(b),"expect": inter})
            if not a.overlaps(b):
                diff = [str(a)]
            else:
                left = []
                if int(b.network_address) > int(a.network_address):
                    left += [str(x) for x in ip.summarize_address_range(
                        a.network_address, ip.ip_address(int(b.network_address)-1))]
                right = []
                if int(b.broadcast_address) < int(a.broadcast_address):
                    right += [str(x) for x in ip.summarize_address_range(
                        ip.ip_address(int(b.broadcast_address)+1), a.broadcast_address)]
                diff = left + right
            out["difference"].append({"a":str(a),"b":str(b),"expect": diff})
    return out

def main():
    ap = argparse.ArgumentParser(description="Build JSON CIDR fixtures from CPython ipaddress tests (plain stdlib).")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--from-url", metavar="REF", help="CPython ref to download from (e.g. 'main', '3.12')")
    src.add_argument("--from-file", metavar="PATH", help="Local path to test_ipaddress.py")
    ap.add_argument("--out", default="./cidre/src/jvmTest/resources/pythontest", help="Output directory")
    args = ap.parse_args()

    if args.from_url:
        py_path = download_test_file(args.from_url)
        source_ref = f"github:{args.from_url}"
    else:
        if not os.path.isfile(args.from_file):
            sys.exit(f"Not a file: {args.from_file}")
        py_path = args.from_file
        source_ref = "local"

    meta = Meta(source_file=os.path.abspath(py_path),
                generated=datetime.datetime.utcnow().isoformat()+"Z",
                source_ref=source_ref)

    strings = collect_string_literals(py_path)
    data = classify_literals(strings)
    os.makedirs(args.out, exist_ok=True)

    parsing = {
        "meta": asdict(meta),
        "valid_addresses": {
            s: ip.ip_address(s).packed.hex()
            for s in sorted(set(data["v4_addrs"] + data["v6_addrs"]))
        },
        "invalid_addresses": [],
        "valid_networks": sorted(set(data["v4_nets"] + data["v6_nets"])),
        "loose_networks": sorted(set(data["v4_nets_loose"] + data["v6_nets_loose"])),
        "interfaces": sorted(set(data["v4_ifaces"] + data["v6_ifaces"])),
        "invalid_network_like": []
    }
    for s in data["invalids"]:
        (parsing["invalid_network_like"] if '/' in s else parsing["invalid_addresses"]).append(s)
    for k in ("invalid_addresses","invalid_network_like"):
        parsing[k] = sorted(set(parsing[k]))
    with open(os.path.join(args.out,"parsing.json"),"w",encoding="utf-8") as f:
        json.dump(parsing,f,indent=2,sort_keys=True)

    norm_cases = normalize_interfaces_and_loose(data["v4_ifaces"], data["v6_ifaces"],
                                                data["v4_nets_loose"], data["v6_nets_loose"])
    with open(os.path.join(args.out,"normalization.json"),"w",encoding="utf-8") as f:
        json.dump({"meta": asdict(meta), "cases": norm_cases}, f, indent=2, sort_keys=True)

    nets_all = parsing["valid_networks"]
    subs, sub_errs = subnet_cases(nets_all)
    with open(os.path.join(args.out,"subnetting.json"),"w",encoding="utf-8") as f:
        json.dump({"meta": asdict(meta), "cases": subs, "error_cases": sub_errs}, f, indent=2, sort_keys=True)

    sups, sup_errs = supernet_cases(nets_all)
    with open(os.path.join(args.out,"supernetting.json"),"w",encoding="utf-8") as f:
        json.dump({"meta": asdict(meta), "cases": sups, "error_cases": sup_errs}, f, indent=2, sort_keys=True)

    pairs = build_augmented_pairs(parsing["valid_networks"])
    tt = truth_table(pairs)
    with open(os.path.join(args.out, "overlaps_containment.json"), "w", encoding="utf-8") as f:
        json.dump({"meta": asdict(meta), "pairs": tt}, f, indent=2, sort_keys=True)

    setops = set_operations_examples(nets_all)
    with open(os.path.join(args.out,"set_operations.json"),"w",encoding="utf-8") as f:
        json.dump({"meta": asdict(meta), **setops}, f, indent=2, sort_keys=True)

    index = {"meta": asdict(meta),
             "files": ["parsing.json","normalization.json","subnetting.json","supernetting.json",
                       "overlaps_containment.json","set_operations.json"]}
    with open(os.path.join(args.out,"INDEX.json"),"w",encoding="utf-8") as f:
        json.dump(index,f,indent=2,sort_keys=True)

    print(f"Wrote JSON pack to: {os.path.abspath(args.out)}")

if __name__ == "__main__":
    main()
