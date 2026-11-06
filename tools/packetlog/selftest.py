#!/usr/bin/env python3
"""End-to-end check of mcproxy.py and analyze.py against a scripted session.

Runs a fake server and a fake client through the proxy, replaying a handshake,
compression negotiation, login, steady movement, perfectly regular clicks and a
block break that the server overrides. Then asserts the analyzer reports all of
it. No Minecraft install involved.
"""

import asyncio
import json
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
MOVE_ID = 0x1A
SWING_ID = 0x36
BREAK_ID = 0x24
BLOCK_UPDATE_ID = 0x09
BLOCK = (100, 64, -200)
SWING_COUNT = 12
MOVE_COUNT = 40


def varint(value):
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        out.append(byte | (0x80 if value else 0))
        if not value:
            return bytes(out)


def frame_plain(packet_id, payload=b""):
    body = varint(packet_id) + payload
    return varint(len(body)) + body


def frame_compressed(packet_id, payload=b"", threshold=64):
    data = varint(packet_id) + payload
    if len(data) >= threshold:
        blob = zlib.compress(data)
        body = varint(len(data)) + blob
    else:
        body = varint(0) + data
    return varint(len(body)) + body


def packed_block(x, y, z):
    return ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)


BLOCK_BYTES = packed_block(*BLOCK).to_bytes(8, "big")


async def fake_server(reader, writer):
    await reader.readexactly(1)
    await reader.read(256)
    writer.write(frame_plain(0x03, varint(64)))
    writer.write(frame_compressed(0x02, b"\x00" * 16))
    await writer.drain()
    try:
        while True:
            data = await reader.read(4096)
            if not data:
                break
            if BLOCK_BYTES in data:
                await asyncio.sleep(0.05)
                writer.write(frame_compressed(BLOCK_UPDATE_ID, BLOCK_BYTES + varint(1)))
                await writer.drain()
    except (asyncio.IncompleteReadError, ConnectionResetError):
        pass
    writer.close()


async def fake_client(port):
    reader, writer = await asyncio.open_connection("127.0.0.1", port)
    handshake = varint(767) + varint(9) + b"localhost" + (25565).to_bytes(2, "big") + varint(2)
    writer.write(frame_plain(0x00, handshake))
    writer.write(frame_plain(0x00, varint(5) + b"Tester"[:5]))
    await writer.drain()
    await asyncio.sleep(0.3)

    for index in range(MOVE_COUNT):
        writer.write(frame_compressed(MOVE_ID, index.to_bytes(8, "big") * 3 + b"\x01"))
        await writer.drain()
        await asyncio.sleep(0.05)

    for _ in range(SWING_COUNT):
        writer.write(frame_compressed(SWING_ID, varint(0)))
        await writer.drain()
        await asyncio.sleep(0.05)

    writer.write(
        frame_compressed(BREAK_ID, varint(0) + BLOCK_BYTES + bytes([1]) + varint(7))
    )
    await writer.drain()
    await asyncio.sleep(0.4)
    writer.close()


def run_analyzer(log, *command):
    result = subprocess.run(
        [sys.executable, str(HERE / "analyze.py"), str(log), "--skip", "0", *command],
        capture_output=True,
        text=True,
        timeout=60,
    )
    if result.returncode != 0:
        raise AssertionError(f"analyze.py {command} failed:\n{result.stderr}")
    return result.stdout


async def main():
    workdir = Path(tempfile.mkdtemp(prefix="packetlog-selftest-"))
    log = workdir / "packets.jsonl"

    server = await asyncio.start_server(fake_server, "127.0.0.1", 0)
    server_port = server.sockets[0].getsockname()[1]

    proxy = await asyncio.create_subprocess_exec(
        sys.executable, str(HERE / "mcproxy.py"),
        "--listen", "0", "--target", f"127.0.0.1:{server_port}",
        "--out", str(log), "--quiet",
        stderr=asyncio.subprocess.PIPE,
    )
    # the proxy prints the bound port on its first stderr line
    first_line = (await proxy.stderr.readline()).decode()
    proxy_port = int(first_line.split("->")[0].strip().split(":")[-1])

    async with server:
        await fake_client(proxy_port)
        await asyncio.sleep(0.3)
    proxy.terminate()
    await proxy.wait()

    records = [json.loads(line) for line in log.read_text().splitlines() if line.strip()]
    sent = [r for r in records if r["dir"] == "c2s" and r["phase"] == "postlogin"]
    ids = {r["id"] for r in sent}

    checks = []

    def check(name, condition, detail=""):
        checks.append((name, condition, detail))

    check("compression decoded", bool(sent), f"{len(sent)} play packets recorded")
    check("movement id seen", MOVE_ID in ids)
    check("swing id seen", SWING_ID in ids)
    check("break id seen", BREAK_ID in ids)
    check(
        "movement count",
        sum(1 for r in sent if r["id"] == MOVE_ID) == MOVE_COUNT,
        f"{sum(1 for r in sent if r['id'] == MOVE_ID)} of {MOVE_COUNT}",
    )
    check(
        "server reply recorded",
        any(r["dir"] == "s2c" and r["id"] == BLOCK_UPDATE_ID for r in records),
    )

    summary = run_analyzer(log, "summary")
    check("summary lists movement", f"0x{MOVE_ID:02x}" in summary)

    cps = run_analyzer(log, "cps", hex(SWING_ID))
    check("autoclicker flagged", "not produced by a hand" in cps, cps.strip().splitlines()[-1])

    timer = run_analyzer(log, "timer", hex(MOVE_ID))
    check("tick pacing measured", "movement packets/s" in timer)

    ghost = run_analyzer(log, "ghost")
    check(
        "ghost block correction found",
        f"{BLOCK[0]},{BLOCK[1]},{BLOCK[2]}" in ghost,
        "server override matched to the broken block",
    )

    width = max(len(name) for name, _, _ in checks)
    failed = 0
    for name, condition, detail in checks:
        mark = "PASS" if condition else "FAIL"
        failed += not condition
        suffix = f"  {detail}" if detail else ""
        print(f"[{mark}] {name.ljust(width)}{suffix}")
    print(f"\n{len(checks) - failed}/{len(checks)} checks passed")
    print(f"log kept at {log}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
