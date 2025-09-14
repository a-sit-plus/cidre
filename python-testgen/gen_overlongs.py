#!/usr/bin/env python3
import json, random
from datetime import datetime

SEED = 20250911
random.seed(SEED)

MAX_128 = (1 << 128) - 1
MAX_129 = (1 << 129) - 1
TWO_POW_128 = 1 << 128

def encode_overlong_hex(n: int) -> str:
    if n < 0 or n > MAX_129:
        raise ValueError("n out of range (0..2^129-1)")
    length = 16 if n < TWO_POW_128 else 17
    return n.to_bytes(length, "big").hex()

def op_inv(a: int) -> int:  return (~a) & MAX_129
def op_and(a: int, b: int) -> int:  return (a & b) & MAX_129
def op_or(a: int, b: int) -> int:   return (a | b) & MAX_129
def op_xor(a: int, b: int) -> int:  return (a ^ b) & MAX_129

def op_shl(a: int, s: int) -> int:
    if not (0 <= s <= 128): raise ValueError("shift out of range (0..128)")
    return (a << s) & MAX_129

def op_shr(a: int, s: int) -> int:
    if not (0 <= s <= 128): raise ValueError("shift out of range (0..128)")
    return a >> s

def rand_overlong() -> int:
    r = random.random()
    if r < 0.10:
        return random.getrandbits(129)
    choices = [
        0, 1, 2, 3, 0xFF,
        (1 << 64) - 1, (1 << 64),
        (1 << 127) - 1, (1 << 127), (1 << 127) + 1,
        MAX_128 - 1, MAX_128, MAX_128 + 1,
        TWO_POW_128 - 1, TWO_POW_128, TWO_POW_128 + 1,
        MAX_129 - 1, MAX_129
    ]
    return random.choice(choices)

def interesting_shifts(): return [0,1,7,8,15,16,31,32,63,64,127,128]

def build():
    tests = []

    # INV
    for _ in range(500):
        a = rand_overlong()
        tests.append({"input": encode_overlong_hex(a), "operation": "INV", "argument": None,
                      "output": encode_overlong_hex(op_inv(a))})

    # AND/OR/XOR
    for _ in range(800):
        a, b = rand_overlong(), rand_overlong()
        tests.append({"input": encode_overlong_hex(a), "operation": "AND",
                      "argument": encode_overlong_hex(b), "output": encode_overlong_hex(op_and(a,b))})
    for _ in range(800):
        a, b = rand_overlong(), rand_overlong()
        tests.append({"input": encode_overlong_hex(a), "operation": "OR",
                      "argument": encode_overlong_hex(b), "output": encode_overlong_hex(op_or(a,b))})
    for _ in range(800):
        a, b = rand_overlong(), rand_overlong()
        tests.append({"input": encode_overlong_hex(a), "operation": "XOR",
                      "argument": encode_overlong_hex(b), "output": encode_overlong_hex(op_xor(a,b))})

    # SHL/SHR across edge shifts, plus randoms
    for s in interesting_shifts():
        edges = [0,1,2,3,0xFF,(1<<63)-1,(1<<63),(1<<127)-1,(1<<127),(1<<127)+1,
                 MAX_128, MAX_128+1, TWO_POW_128, TWO_POW_128+1, MAX_129-1, MAX_129]
        for a in edges:
            tests.append({"input": encode_overlong_hex(a), "operation": "SHL",
                          "argument": str(s), "output": encode_overlong_hex(op_shl(a,s))})
            tests.append({"input": encode_overlong_hex(a), "operation": "SHR",
                          "argument": str(s), "output": encode_overlong_hex(op_shr(a,s))})
        for _ in range(50):
            a = rand_overlong()
            tests.append({"input": encode_overlong_hex(a), "operation": "SHL",
                          "argument": str(s), "output": encode_overlong_hex(op_shl(a,s))})
            a = rand_overlong()
            tests.append({"input": encode_overlong_hex(a), "operation": "SHR",
                          "argument": str(s), "output": encode_overlong_hex(op_shr(a,s))})

    random.shuffle(tests)
    return {
        "generated_at": datetime.utcnow().isoformat() + "Z",
        "width_bits": 129,
        "encoding": "big-endian; 16 bytes if < 2^128 else 17 bytes",
        "tests": tests
    }

if __name__ == "__main__":
    suite = build()
    with open("../cidre/src/jvmTest/resources/pythontest/overlongs.json", "w", encoding="utf-8") as f:
        json.dump(suite, f, indent=2)
