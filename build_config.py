"""Resolve local prerequisites without storing credentials or machine paths in source."""
import os
import struct
from pathlib import Path


def local_path(variable):
    value = os.environ.get(variable)
    if not value:
        raise SystemExit(f"Set {variable} as described in README.md")
    path = Path(value).expanduser().resolve()
    if not path.exists():
        raise SystemExit(f"{variable} points to a missing file or directory")
    return path


def swf_tag(code, payload):
    return struct.pack('<HI', (code << 6) | 63, len(payload)) + payload


def first_abc_offset(movie):
    # ASC emits an uncompressed SWF. Skip its RECT, frame rate and frame count.
    if len(movie) < 14 or movie[:3] != b'FWS':
        raise ValueError('Expected an uncompressed ASC SWF')
    offset = 12 + (5 + 4 * (movie[8] >> 3) + 7) // 8
    while offset + 2 <= len(movie):
        tag, = struct.unpack_from('<H', movie, offset)
        code, size = tag >> 6, tag & 63
        header = 2
        if size == 63:
            if offset + 6 > len(movie):
                break
            size, = struct.unpack_from('<I', movie, offset + 2)
            header = 6
        end = offset + header + size
        if end > len(movie):
            break
        if code in (72, 82):
            return offset
        offset = end
    raise ValueError('ASC output has no valid DoABC tag')
