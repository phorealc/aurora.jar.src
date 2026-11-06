#!/usr/bin/env python3
"""Read a packet log from mcproxy.py and measure what the client actually sent.

The tool never assumes packet ids for a particular Minecraft version. You
identify the ids you care about from `summary` and `diff`, then feed them to the
measurement commands.
"""

import argparse
import json
import statistics
import sys
from collections import Counter, defaultdict

VANILLA_TICK_RATE = 20.0


def load(path, skip=0.0, phase="postlogin"):
    records = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            record = json.loads(line)
            if record["t"] < skip:
                continue
            if phase != "all" and record["phase"] != phase:
                continue
            records.append(record)
    return records


def span(records):
    if not records:
        return 0.0
    return max(r["t"] for r in records) - min(r["t"] for r in records)


def pick(records, direction):
    return [r for r in records if r["dir"] == direction]


def cmd_summary(args):
    records = pick(load(args.log, args.skip, args.phase), args.dir)
    if not records:
        print("no packets matched", file=sys.stderr)
        return 1
    duration = span(records) or 1.0
    counts = Counter(r["id"] for r in records)
    sizes = defaultdict(list)
    for record in records:
        sizes[record["id"]].append(record["len"])

    print(f"{len(records)} {args.dir} packets over {duration:.1f}s\n")
    print(f"{'id':>6}  {'count':>7}  {'per sec':>8}  {'avg bytes':>9}")
    print("-" * 36)
    for packet_id, count in counts.most_common():
        print(
            f"  0x{packet_id:02x}  {count:7d}  {count / duration:8.2f}  "
            f"{statistics.mean(sizes[packet_id]):9.1f}"
        )
    print(
        "\nThe id at roughly 20/s is the movement packet. Use `diff` against an "
        "idle recording to name the others."
    )
    return 0


def cmd_diff(args):
    base = Counter(r["id"] for r in pick(load(args.log, args.skip, args.phase), args.dir))
    other = Counter(r["id"] for r in pick(load(args.other, args.skip, args.phase), args.dir))
    ids = sorted(set(base) | set(other))
    print(f"{'id':>6}  {'A':>7}  {'B':>7}  {'B-A':>7}")
    print("-" * 32)
    for packet_id in ids:
        delta = other[packet_id] - base[packet_id]
        mark = "  <--" if abs(delta) > 2 else ""
        print(f"  0x{packet_id:02x}  {base[packet_id]:7d}  {other[packet_id]:7d}  {delta:+7d}{mark}")
    print("\nA = first log, B = second log. Ids that only move in B are what the action produced.")
    return 0


def cmd_cps(args):
    records = [
        r for r in pick(load(args.log, args.skip, args.phase), "c2s") if r["id"] == args.id
    ]
    if len(records) < 3:
        print(f"only {len(records)} packets with id 0x{args.id:02x}", file=sys.stderr)
        return 1
    times = sorted(r["t"] for r in records)
    gaps = [b - a for a, b in zip(times, times[1:])]
    duration = times[-1] - times[0] or 1.0
    spread = statistics.pstdev(gaps) if len(gaps) > 1 else 0.0

    print(f"id 0x{args.id:02x}: {len(records)} packets over {duration:.1f}s")
    print(f"  mean rate     {len(records) / duration:.2f}/s")
    print(f"  peak 1s rate  {peak_rate(times):.0f}/s")
    print(f"  gap mean      {statistics.mean(gaps) * 1000:.1f} ms")
    print(f"  gap median    {statistics.median(gaps) * 1000:.1f} ms")
    print(f"  gap stdev     {spread * 1000:.1f} ms")
    print(f"  gap min/max   {min(gaps) * 1000:.1f} / {max(gaps) * 1000:.1f} ms")
    if spread < 0.006:
        print(
            "\n  Gaps this regular are not produced by a hand. That is the signature\n"
            "  of an autoclicker, unless this id is a fixed-rate packet such as\n"
            "  movement or keep-alive."
        )
    else:
        print("\n  Gap spread is consistent with human input.")
    return 0


def peak_rate(times, window=1.0):
    best = 0
    start = 0
    for end, moment in enumerate(times):
        while moment - times[start] > window:
            start += 1
        best = max(best, end - start + 1)
    return best


def cmd_timer(args):
    records = [
        r for r in pick(load(args.log, args.skip, args.phase), "c2s") if r["id"] == args.id
    ]
    if len(records) < 20:
        print(f"only {len(records)} packets with id 0x{args.id:02x}", file=sys.stderr)
        return 1
    times = sorted(r["t"] for r in records)
    duration = times[-1] - times[0]
    rate = len(records) / duration
    print(f"id 0x{args.id:02x}: {rate:.2f} movement packets/s over {duration:.1f}s")
    print(f"  vanilla reference {VANILLA_TICK_RATE:.0f}/s")
    print(f"  peak 1s rate      {peak_rate(times):.0f}/s")
    if rate > VANILLA_TICK_RATE * 1.05:
        print("\n  Above the tick rate. That is what a timer / tick acceleration looks like.")
    elif rate < VANILLA_TICK_RATE * 0.9:
        print("\n  Below the tick rate: lag, or packets withheld (blink / lag switch).")
    else:
        print("\n  Matches vanilla tick pacing.")
    return 0


def cmd_rate(args):
    records = [
        r for r in pick(load(args.log, args.skip, args.phase), "c2s") if r["id"] == args.id
    ]
    if not records:
        print(f"no packets with id 0x{args.id:02x}", file=sys.stderr)
        return 1
    times = sorted(r["t"] for r in records)
    duration = span(records) or 1.0
    print(f"id 0x{args.id:02x}: {len(records)} packets, mean {len(records) / duration:.2f}/s")
    print(f"  peak 1s rate {peak_rate(times):.0f}/s")
    print(
        "\n  Compare the peak against the cap the server granted "
        "(maxPlacePerSec / maxBreakPerSec in latest.log)."
    )
    return 0


def sign(value, bits):
    value &= (1 << bits) - 1
    if value & (1 << (bits - 1)):
        value -= 1 << bits
    return value


def decode_blockpos(packed):
    return sign(packed >> 38, 26), sign(packed, 12), sign(packed >> 12, 26)


def block_positions(payload, max_coord):
    """Every 8-byte window that decodes to a plausible block position.

    Returns (offset, packed, (x, y, z)). The scan is a heuristic: it needs no
    knowledge of packet layouts, at the cost of windows that decode to a
    plausible position by chance. Matching against the other direction and
    preferring the position a reply carries at offset 0 removes those.
    """
    found = []
    for offset in range(0, max(0, len(payload) - 7)):
        packed = int.from_bytes(payload[offset:offset + 8], "big")
        x, y, z = decode_blockpos(packed)
        if -64 <= y <= 320 and abs(x) <= max_coord and abs(z) <= max_coord:
            found.append((offset, packed, (x, y, z)))
    return found


def cmd_ghost(args):
    records = load(args.log, args.skip, args.phase)
    window = args.window / 1000.0

    sent = []
    for record in pick(records, "c2s"):
        if not 9 <= record["len"] <= 64:
            continue
        candidates = block_positions(bytes.fromhex(record["hex"]), args.max_coord)
        if candidates:
            sent.append((record["t"], record["id"], candidates))

    incoming = defaultdict(list)
    for record in pick(records, "s2c"):
        for offset, packed, _ in block_positions(bytes.fromhex(record["hex"]), args.max_coord):
            incoming[packed].append((record["t"], record["id"], offset))

    hits = []
    for moment, packet_id, candidates in sent:
        best = None
        for _, packed, position in candidates:
            for reply_time, reply_id, reply_offset in incoming.get(packed, ()):
                if not 0 < reply_time - moment <= window:
                    continue
                key = (reply_offset, reply_time - moment)
                if best is None or key < best[0]:
                    best = (key, position, reply_time, reply_id)
        if best is not None:
            _, position, reply_time, reply_id = best
            hits.append((moment, packet_id, position, reply_time, reply_id))

    print(
        f"{len(sent)} client packets could carry a block position, "
        f"{len(hits)} were answered by the server about that same block "
        f"within {args.window:.0f} ms\n"
    )
    if not hits:
        print(
            "No correction found. Either the server accepted everything, or this\n"
            "recording holds no block interaction."
        )
        return 0
    print(f"{'t':>8}  {'c2s':>5}  {'block':>22}  {'reply':>8}  {'s2c':>5}")
    print("-" * 60)
    for moment, packet_id, position, reply_time, reply_id in hits[: args.limit]:
        pretty = f"{position[0]},{position[1]},{position[2]}"
        print(
            f"{moment:8.3f}  0x{packet_id:02x}  {pretty:>22}  "
            f"{(reply_time - moment) * 1000:6.0f}ms  0x{reply_id:02x}"
        )
    if len(hits) > args.limit:
        print(f"... {len(hits) - args.limit} more")
    print(
        "\n  A reply about the block you just touched is the server overriding your\n"
        "  client. That is the packet behind a ghost block. Many of these on a\n"
        "  server that granted no prediction caps means the client is drawing\n"
        "  outcomes it was never allowed to predict."
    )
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", help="JSONL file from mcproxy.py")
    parser.add_argument("--skip", type=float, default=5.0, help="drop the first N seconds")
    parser.add_argument(
        "--phase", default="postlogin", choices=["postlogin", "login", "handshake", "status", "all"]
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("summary", help="packet ids by volume")
    p.add_argument("--dir", default="c2s", choices=["c2s", "s2c"])
    p.set_defaults(func=cmd_summary)

    p = sub.add_parser("diff", help="compare two logs to attribute ids to actions")
    p.add_argument("other", help="second JSONL file")
    p.add_argument("--dir", default="c2s", choices=["c2s", "s2c"])
    p.set_defaults(func=cmd_diff)

    p = sub.add_parser("cps", help="click pacing for one id")
    p.add_argument("id", type=lambda v: int(v, 0))
    p.set_defaults(func=cmd_cps)

    p = sub.add_parser("timer", help="movement packet rate against the tick rate")
    p.add_argument("id", type=lambda v: int(v, 0))
    p.set_defaults(func=cmd_timer)

    p = sub.add_parser("rate", help="peak per-second rate for one id")
    p.add_argument("id", type=lambda v: int(v, 0))
    p.set_defaults(func=cmd_rate)

    p = sub.add_parser("ghost", help="find server corrections on blocks you touched")
    p.add_argument("--window", type=float, default=400.0, help="reply window in ms")
    p.add_argument(
        "--max-coord", type=int, default=100_000,
        help="reject decoded positions beyond this |x| or |z| (cuts chance matches)",
    )
    p.add_argument("--limit", type=int, default=25)
    p.set_defaults(func=cmd_ghost)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
