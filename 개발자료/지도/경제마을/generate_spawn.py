#!/usr/bin/env python3
"""Generate a Sponge Schematic v2 economy-survival spawn without external packages."""

from __future__ import annotations

import gzip
import json
import struct
import zlib
from collections import Counter
from pathlib import Path

WIDTH = 161
LENGTH = 161
HEIGHT = 32
X0 = WIDTH // 2
Z0 = LENGTH // 2

AIR = "minecraft:air"
blocks = [AIR] * (WIDTH * LENGTH * HEIGHT)


def index(x: int, y: int, z: int) -> int:
    ax, az = x + X0, z + Z0
    if not (0 <= ax < WIDTH and 0 <= y < HEIGHT and 0 <= az < LENGTH):
        raise ValueError(f"out of bounds: {(x, y, z)}")
    return ax + az * WIDTH + y * WIDTH * LENGTH


def set_block(x: int, y: int, z: int, block: str) -> None:
    blocks[index(x, y, z)] = block


def fill(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str) -> None:
    for y in range(min(y1, y2), max(y1, y2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            for x in range(min(x1, x2), max(x1, x2) + 1):
                set_block(x, y, z, block)


def hollow_building(x1: int, z1: int, x2: int, z2: int, height: int,
                    wall: str, trim: str, glass: str, roof: str) -> None:
    fill(x1, 2, z1, x2, 2, z2, "minecraft:smooth_stone")
    for y in range(3, height + 2):
        for z in range(z1, z2 + 1):
            for x in range(x1, x2 + 1):
                edge = x in (x1, x2) or z in (z1, z2)
                if not edge:
                    continue
                material = trim if y in (3, height + 1) else wall
                if y in range(5, height, 3):
                    if (z in (z1, z2) and (x - x1) % 4 in (1, 2)) or (
                            x in (x1, x2) and (z - z1) % 4 in (1, 2)):
                        material = glass
                set_block(x, y, z, material)
    fill(x1, height + 2, z1, x2, height + 2, z2, roof)
    # Front entrance, always on the south wall.
    center = (x1 + x2) // 2
    fill(center - 1, 3, z2, center + 1, 5, z2, AIR)
    fill(center - 2, 2, z2 + 1, center + 2, 2, z2 + 2, "minecraft:smooth_stone")


def tree(x: int, z: int) -> None:
    fill(x, 2, z, x, 6, z, "minecraft:stripped_oak_log[axis=y]")
    for y, radius in ((6, 2), (7, 2), (8, 1)):
        for dz in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                if abs(dx) + abs(dz) <= radius + 1:
                    set_block(x + dx, y, z + dz, "minecraft:oak_leaves[persistent=true,distance=1]")


def lamp(x: int, z: int) -> None:
    fill(x, 2, z, x, 6, z, "minecraft:polished_blackstone_wall")
    set_block(x, 7, z, "minecraft:sea_lantern")


def nbt_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + nbt_string(name) + payload


def tag_short(name: str, value: int) -> bytes:
    return named(2, name, struct.pack(">h", value))


def tag_int(name: str, value: int) -> bytes:
    return named(3, name, struct.pack(">i", value))


def tag_byte_array(name: str, value: bytes) -> bytes:
    return named(7, name, struct.pack(">i", len(value)) + value)


def tag_int_array(name: str, values: list[int]) -> bytes:
    return named(11, name, struct.pack(">i", len(values)) + b"".join(struct.pack(">i", x) for x in values))


def tag_list_empty(name: str, element_type: int = 10) -> bytes:
    return named(9, name, bytes([element_type]) + struct.pack(">i", 0))


def tag_compound(name: str, children: list[bytes]) -> bytes:
    return named(10, name, b"".join(children) + b"\x00")


def varint(value: int) -> bytes:
    output = bytearray()
    while True:
        current = value & 0x7F
        value >>= 7
        output.append(current | (0x80 if value else 0))
        if not value:
            return bytes(output)


def build() -> tuple[bytes, dict]:
    # Foundation and edge curb.
    fill(-80, 0, -80, 80, 0, 80, "minecraft:dirt")
    fill(-80, 1, -80, 80, 1, 80, "minecraft:grass_block")
    for c in range(-80, 81):
        set_block(c, 2, -80, "minecraft:stone_bricks")
        set_block(c, 2, 80, "minecraft:stone_bricks")
        set_block(-80, 2, c, "minecraft:stone_bricks")
        set_block(80, 2, c, "minecraft:stone_bricks")

    # Main roads and ring road.
    fill(-5, 1, -80, 5, 1, 80, "minecraft:gray_concrete")
    fill(-80, 1, -5, 80, 1, 5, "minecraft:gray_concrete")
    for a, b in ((-62, -56), (56, 62)):
        fill(a, 1, -62, b, 1, 62, "minecraft:gray_concrete")
        fill(-62, 1, a, 62, 1, b, "minecraft:gray_concrete")
    # Lane markings.
    for p in range(-76, 77, 8):
        fill(-1, 1, p, 1, 1, p + 3, "minecraft:yellow_concrete")
        fill(p, 1, -1, p + 3, 1, 1, "minecraft:yellow_concrete")

    # Central plaza and fountain.
    fill(-20, 1, -20, 20, 1, 20, "minecraft:smooth_stone")
    fill(-12, 1, -12, 12, 1, 12, "minecraft:polished_andesite")
    fill(-6, 2, -6, 6, 2, 6, "minecraft:stone_bricks")
    fill(-5, 2, -5, 5, 2, 5, "minecraft:water[level=0]")
    fill(-2, 2, -2, 2, 5, 2, "minecraft:quartz_pillar[axis=y]")
    fill(-1, 6, -1, 1, 6, 1, "minecraft:sea_lantern")

    # Public buildings: city hall, bank, and jobs office.
    hollow_building(-24, -69, 24, -39, 15, "minecraft:white_concrete",
                    "minecraft:quartz_block", "minecraft:light_blue_stained_glass", "minecraft:smooth_quartz")
    for x in range(-18, 19, 6):
        fill(x, 3, -37, x, 11, -37, "minecraft:quartz_pillar[axis=y]")

    hollow_building(-73, -30, -43, -11, 10, "minecraft:stone_bricks",
                    "minecraft:gold_block", "minecraft:glass", "minecraft:smooth_stone")
    fill(-63, 2, -10, -53, 2, -8, "minecraft:smooth_stone")

    hollow_building(43, -30, 73, -11, 10, "minecraft:green_concrete",
                    "minecraft:white_concrete", "minecraft:glass", "minecraft:smooth_stone")
    fill(53, 2, -10, 63, 2, -8, "minecraft:smooth_stone")

    # Ten player shops in two rows.
    shop_colors = ["red", "blue", "yellow", "green", "orange"]
    for row, z1 in enumerate((32, 54)):
        for i in range(5):
            x1 = -49 + i * 20
            color = shop_colors[(i + row) % len(shop_colors)]
            hollow_building(x1, z1, x1 + 14, z1 + 14, 7,
                            f"minecraft:{color}_concrete", "minecraft:white_concrete",
                            "minecraft:glass", "minecraft:smooth_stone")
            fill(x1 + 2, 3, z1 - 1, x1 + 12, 3, z1 - 1, f"minecraft:{color}_concrete")

    # Beginner farm and warehouse.
    fill(-74, 1, 15, -42, 1, 43, "minecraft:farmland[moisture=7]")
    for x in range(-73, -42):
        crop = ("minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]")[(x + 74) % 3]
        for z in range(16, 44):
            if x % 6 == 0:
                set_block(x, 1, z, "minecraft:water[level=0]")
            else:
                set_block(x, 2, z, crop)
    hollow_building(-74, 48, -43, 72, 8, "minecraft:red_concrete",
                    "minecraft:white_concrete", "minecraft:glass", "minecraft:spruce_planks")

    # Park and residential expansion lots.
    fill(39, 1, 15, 74, 1, 45, "minecraft:grass_block")
    fill(54, 1, 15, 59, 1, 45, "minecraft:gravel")
    fill(39, 1, 28, 74, 1, 33, "minecraft:gravel")
    for x, z in ((43, 19), (70, 19), (43, 41), (70, 41), (48, 25), (65, 37)):
        tree(x, z)

    # Wild entrance arch at the north edge.
    fill(-8, 2, -77, -6, 12, -75, "minecraft:stone_bricks")
    fill(6, 2, -77, 8, 12, -75, "minecraft:stone_bricks")
    fill(-8, 11, -77, 8, 13, -75, "minecraft:stone_bricks")
    fill(-5, 11, -78, 5, 11, -78, "minecraft:emerald_block")

    # Street lighting and trees around the plaza.
    for p in range(-48, 49, 16):
        for x, z in ((-9, p), (9, p), (p, -9), (p, 9)):
            lamp(x, z)
    for x, z in ((-26, -26), (26, -26), (-26, 26), (26, 26)):
        tree(x, z)

    palette_names = sorted(set(blocks), key=lambda b: (b != AIR, b))
    palette = {name: i for i, name in enumerate(palette_names)}
    block_data = b"".join(varint(palette[b]) for b in blocks)
    palette_children = [tag_int(name, value) for name, value in palette.items()]
    metadata = tag_compound("Metadata", [
        tag_int("WEOffsetX", -X0), tag_int("WEOffsetY", -1), tag_int("WEOffsetZ", -Z0)
    ])
    root = tag_compound("Schematic", [
        tag_int("Version", 2),
        tag_int("DataVersion", 3465),
        metadata,
        tag_short("Width", WIDTH), tag_short("Height", HEIGHT), tag_short("Length", LENGTH),
        tag_int_array("Offset", [-X0, -1, -Z0]),
        tag_int("PaletteMax", len(palette)),
        tag_compound("Palette", palette_children),
        tag_byte_array("BlockData", block_data),
        tag_list_empty("BlockEntities"),
        tag_list_empty("Entities"),
    ])
    raw = gzip.compress(root, compresslevel=9, mtime=0)
    counts = Counter(blocks)
    report = {
        "format": "Sponge Schematic v2",
        "data_version": 3465,
        "dimensions": [WIDTH, HEIGHT, LENGTH],
        "origin_offset": [-X0, -1, -Z0],
        "palette_size": len(palette),
        "encoded_blocks": len(blocks),
        "non_air_blocks": len(blocks) - counts[AIR],
        "block_data_bytes": len(block_data),
        "areas": ["central_plaza", "city_hall", "bank", "jobs_office", "10_player_shops",
                  "beginner_farm", "warehouse", "park", "wild_entrance"],
    }
    return raw, report


def write_preview(path: Path) -> None:
    colors = {
        "air": (232, 239, 225), "grass_block": (91, 150, 66), "dirt": (121, 85, 58),
        "gray_concrete": (55, 58, 62), "yellow_concrete": (239, 193, 45),
        "smooth_stone": (166, 166, 166), "polished_andesite": (135, 136, 137),
        "stone_bricks": (120, 119, 113), "water": (48, 108, 190),
        "white_concrete": (229, 230, 226), "quartz_block": (236, 229, 218),
        "smooth_quartz": (238, 231, 220), "light_blue_stained_glass": (92, 175, 205),
        "glass": (175, 211, 219), "gold_block": (248, 207, 42),
        "green_concrete": (73, 91, 36), "red_concrete": (142, 32, 32),
        "blue_concrete": (45, 47, 143), "orange_concrete": (225, 97, 0),
        "spruce_planks": (114, 84, 48), "farmland": (92, 61, 37),
        "wheat": (207, 180, 64), "carrots": (230, 110, 26), "potatoes": (174, 143, 67),
        "gravel": (126, 123, 119), "oak_leaves": (54, 116, 47),
        "stripped_oak_log": (173, 142, 86), "emerald_block": (42, 203, 91),
        "sea_lantern": (178, 208, 194), "polished_blackstone_wall": (42, 36, 43),
    }
    scale = 3
    rows = []
    for z in range(-Z0, Z0 + 1):
        row = bytearray()
        for x in range(-X0, X0 + 1):
            top = AIR
            for y in range(HEIGHT - 1, -1, -1):
                candidate = blocks[index(x, y, z)]
                if candidate != AIR:
                    top = candidate
                    break
            name = top.removeprefix("minecraft:").split("[", 1)[0]
            row.extend(bytes(colors.get(name, (145, 110, 92))) * scale)
        for _ in range(scale):
            rows.append(b"\x00" + bytes(row))
    raw = b"".join(rows)

    def chunk(kind: bytes, data: bytes) -> bytes:
        checksum = zlib.crc32(kind + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", checksum)

    size = WIDTH * scale
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


def main() -> None:
    output = Path(__file__).resolve().parent / "dist"
    output.mkdir(parents=True, exist_ok=True)
    schematic, report = build()
    (output / "economy_spawn_v1.schem").write_bytes(schematic)
    (output / "validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_preview(output / "economy_spawn_v1_preview.png")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
