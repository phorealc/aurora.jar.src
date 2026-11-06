#!/usr/bin/env python3
"""Transparent Minecraft proxy that records every packet to a JSONL file.

Point the game client at this proxy instead of the server. Traffic is relayed
byte for byte: nothing is modified, injected, reordered or dropped. The proxy
only reads along the way.

Requires the target server to run with online-mode=false. Online mode encrypts
the stream right after login, and an encrypted stream cannot be decoded here;
the proxy detects that case, says so, and keeps relaying blindly.
"""

import argparse
import asyncio
import json
import sys
import time
import zlib

C2S_PAYLOAD_CAP = 256
S2C_PAYLOAD_CAP = 48

LOGIN_ENCRYPTION_REQUEST = 0x01
LOGIN_SUCCESS = 0x02
LOGIN_SET_COMPRESSION = 0x03


class ProtocolError(Exception):
    pass


def read_varint(buf, pos=0):
    value = 0
    for shift in range(0, 35, 7):
        if pos >= len(buf):
            raise ProtocolError("truncated varint")
        byte = buf[pos]
        pos += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, pos
    raise ProtocolError("varint longer than 5 bytes")


def read_string(buf, pos):
    length, pos = read_varint(buf, pos)
    return buf[pos:pos + length].decode("utf-8", "replace"), pos + length


async def read_frame(reader):
    """Read one length-prefixed packet. Returns (raw_bytes, body)."""
    raw = bytearray()
    length = 0
    for shift in range(0, 35, 7):
        byte = await reader.readexactly(1)
        raw += byte
        length |= (byte[0] & 0x7F) << shift
        if not byte[0] & 0x80:
            break
    else:
        raise ProtocolError("length varint longer than 5 bytes")
    if length > 1 << 24:
        raise ProtocolError(f"implausible frame length {length}")
    body = await reader.readexactly(length)
    raw += body
    return bytes(raw), body


def decode_body(body, threshold):
    """Strip the compression envelope and the packet id off a frame body."""
    if threshold is None:
        data = body
    else:
        uncompressed_size, pos = read_varint(body, 0)
        data = zlib.decompress(body[pos:]) if uncompressed_size else body[pos:]
    packet_id, pos = read_varint(data, 0)
    return packet_id, data[pos:]


class Session:
    """Per-connection protocol state and log sink."""

    def __init__(self, sink, cid, quiet):
        self.sink = sink
        self.cid = cid
        self.quiet = quiet
        self.t0 = time.monotonic()
        self.threshold = None
        self.encrypted = False
        self.phase = "handshake"
        self.counts = {"c2s": 0, "s2c": 0}
        self.undecodable = 0

    def note(self, message):
        if not self.quiet:
            print(f"[conn {self.cid}] {message}", file=sys.stderr, flush=True)

    def observe(self, direction, body):
        self.counts[direction] += 1
        if self.encrypted:
            return
        try:
            packet_id, payload = decode_body(body, self.threshold)
        except (ProtocolError, zlib.error) as exc:
            self.undecodable += 1
            if self.undecodable <= 3:
                self.note(f"could not decode a {direction} frame: {exc}")
            return

        self.sink.write({
            "t": round(time.monotonic() - self.t0, 4),
            "cid": self.cid,
            "dir": direction,
            "phase": self.phase,
            "id": packet_id,
            "len": len(payload),
            "hex": payload[:C2S_PAYLOAD_CAP if direction == "c2s" else S2C_PAYLOAD_CAP].hex(),
        })
        self._advance(direction, packet_id, payload)

    def _advance(self, direction, packet_id, payload):
        if self.phase == "handshake" and direction == "c2s" and packet_id == 0x00:
            self._read_handshake(payload)
        elif self.phase == "login" and direction == "s2c":
            if packet_id == LOGIN_SET_COMPRESSION:
                self.threshold = read_varint(payload, 0)[0]
                self.note(f"compression enabled, threshold {self.threshold}")
            elif packet_id == LOGIN_ENCRYPTION_REQUEST:
                self.encrypted = True
                self.note(
                    "server asked for encryption: it is in online mode. "
                    "Set online-mode=false on your test server, or packets cannot be read."
                )
            elif packet_id == LOGIN_SUCCESS:
                self.phase = "postlogin"
                self.note("login done, recording the play session")

    def _read_handshake(self, payload):
        try:
            protocol, pos = read_varint(payload, 0)
            _address, pos = read_string(payload, pos)
            pos += 2
            next_state, _ = read_varint(payload, pos)
        except (ProtocolError, UnicodeDecodeError):
            self.phase = "login"
            return
        self.phase = "status" if next_state == 1 else "login"
        self.note(f"handshake: protocol {protocol}, next state {next_state}")


class Sink:
    """Buffered JSONL writer."""

    def __init__(self, path):
        self.handle = open(path, "a", encoding="utf-8")
        self.pending = 0

    def write(self, record):
        self.handle.write(json.dumps(record, separators=(",", ":")) + "\n")
        self.pending += 1
        if self.pending >= 200:
            self.flush()

    def flush(self):
        self.handle.flush()
        self.pending = 0

    def close(self):
        self.flush()
        self.handle.close()


async def relay(session, reader, writer, direction):
    try:
        while True:
            if session.encrypted:
                chunk = await reader.read(65536)
                if not chunk:
                    break
                writer.write(chunk)
                await writer.drain()
                continue
            raw, body = await read_frame(reader)
            writer.write(raw)
            await writer.drain()
            session.observe(direction, body)
    except (asyncio.IncompleteReadError, ConnectionResetError, BrokenPipeError):
        pass
    except ProtocolError as exc:
        session.note(f"stopped decoding {direction}: {exc}")
    finally:
        try:
            writer.close()
        except Exception:
            pass


class Proxy:
    def __init__(self, sink, target_host, target_port, quiet):
        self.sink = sink
        self.target_host = target_host
        self.target_port = target_port
        self.quiet = quiet
        self.next_cid = 0

    async def handle(self, client_reader, client_writer):
        self.next_cid += 1
        session = Session(self.sink, self.next_cid, self.quiet)
        try:
            server_reader, server_writer = await asyncio.open_connection(
                self.target_host, self.target_port
            )
        except OSError as exc:
            session.note(f"cannot reach {self.target_host}:{self.target_port}: {exc}")
            client_writer.close()
            return
        session.note(f"connected to {self.target_host}:{self.target_port}")
        await asyncio.gather(
            relay(session, client_reader, server_writer, "c2s"),
            relay(session, server_reader, client_writer, "s2c"),
        )
        self.sink.flush()
        session.note(
            f"closed after {session.counts['c2s']} c2s / {session.counts['s2c']} s2c packets"
        )


def parse_target(value):
    host, _, port = value.rpartition(":")
    if not host:
        raise argparse.ArgumentTypeError("target must be host:port")
    return host, int(port)


async def main_async(args):
    sink = Sink(args.out)
    proxy = Proxy(sink, *args.target, args.quiet)
    server = await asyncio.start_server(proxy.handle, args.host, args.listen)
    bound = server.sockets[0].getsockname()[1]
    print(
        f"listening on {args.host}:{bound} -> {args.target[0]}:{args.target[1]}\n"
        f"writing {args.out}\n"
        f"add the server {args.host}:{bound} in Minecraft, then play.",
        file=sys.stderr,
        flush=True,
    )
    try:
        async with server:
            await server.serve_forever()
    finally:
        sink.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--listen", type=int, default=25566, help="port to accept the game on")
    parser.add_argument("--host", default="127.0.0.1", help="address to bind")
    parser.add_argument(
        "--target", type=parse_target, default=("127.0.0.1", 25565), help="real server host:port"
    )
    parser.add_argument("--out", default="packets.jsonl", help="JSONL log to append to")
    parser.add_argument("--quiet", action="store_true", help="no progress on stderr")
    args = parser.parse_args()
    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        print("stopped", file=sys.stderr)


if __name__ == "__main__":
    main()
