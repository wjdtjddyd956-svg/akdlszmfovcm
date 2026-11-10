#!/usr/bin/env python3
"""Minimal structural validator for the generated Sponge Schematic v2 file."""

from __future__ import annotations

import gzip
import struct
import sys
from pathlib import Path


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, size: int) -> bytes:
        value = self.data[self.pos:self.pos + size]
        if len(value) != size:
            raise ValueError("unexpected end of NBT data")
        self.pos += size
        return value

    def byte(self) -> int:
        return self.take(1)[0]

    def short(self) -> int:
        return struct.unpack(">h", self.take(2))[0]

    def ushort(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def integer(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def string(self) -> str:
        return self.take(self.ushort()).decode("utf-8")

    def payload(self, kind: int):
        if kind == 2:
            return self.short()
        if kind == 3:
            return self.integer()
        if kind == 7:
            return self.take(self.integer())
        if kind == 9:
            item_type, length = self.byte(), self.integer()
            return [self.payload(item_type) for _ in range(length)]
        if kind == 10:
            result = {}
            while True:
                item_type = self.byte()
                if item_type == 0:
                    return result
                name = self.string()
                result[name] = self.payload(item_type)
        if kind == 11:
            return [self.integer() for _ in range(self.integer())]
        raise ValueError(f"unsupported NBT tag {kind}")


def decode_varints(data: bytes) -> list[int]:
    output, value, shift = [], 0, 0
    for byte in data:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
            if shift > 35:
                raise ValueError("invalid varint")
        else:
            output.append(value)
            value, shift = 0, 0
    if shift:
        raise ValueError("truncated varint")
    return output


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "dist/economy_spawn_v1.schem")
    reader = Reader(gzip.decompress(path.read_bytes()))
    if reader.byte() != 10 or reader.string() != "Schematic":
        raise ValueError("root must be a compound named Schematic")
    root = reader.payload(10)
    required = {"Version", "DataVersion", "Metadata", "Width", "Height", "Length",
                "Offset", "PaletteMax", "Palette", "BlockData", "BlockEntities", "Entities"}
    if missing := required - root.keys():
        raise ValueError(f"missing tags: {sorted(missing)}")
    dimensions = (root["Width"], root["Height"], root["Length"])
    values = decode_varints(root["BlockData"])
    expected = dimensions[0] * dimensions[1] * dimensions[2]
    if len(values) != expected:
        raise ValueError(f"block count {len(values)} != {expected}")
    if root["PaletteMax"] != len(root["Palette"]):
        raise ValueError("PaletteMax does not match palette size")
    if max(values) >= root["PaletteMax"]:
        raise ValueError("block data contains an unknown palette index")
    if reader.pos != len(reader.data):
        raise ValueError("trailing NBT data")
    print(f"valid Sponge v{root['Version']} schematic: {dimensions}, {len(values)} blocks, {len(root['Palette'])} palette entries")


if __name__ == "__main__":
    main()
