#!/usr/bin/env python3
"""Generate an organic, hand-built economy survival town for WorldEdit."""

from __future__ import annotations

import gzip
import json
import math
import struct
import zlib
from collections import Counter
from pathlib import Path

from generate_spawn import (
    tag_byte_array, tag_compound, tag_int, tag_int_array, tag_list_empty, tag_short, varint,
)

WIDTH = LENGTH = 193
HEIGHT = 48
X0 = Z0 = 96
AIR = "minecraft:air"
blocks = [AIR] * (WIDTH * LENGTH * HEIGHT)


def idx(x: int, y: int, z: int) -> int:
    ax, az = x + X0, z + Z0
    if not (0 <= ax < WIDTH and 0 <= y < HEIGHT and 0 <= az < LENGTH):
        raise ValueError((x, y, z))
    return ax + az * WIDTH + y * WIDTH * LENGTH


def setb(x: int, y: int, z: int, block: str) -> None:
    blocks[idx(x, y, z)] = block


def getb(x: int, y: int, z: int) -> str:
    return blocks[idx(x, y, z)]


def fill(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str) -> None:
    for y in range(min(y1, y2), max(y1, y2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            for x in range(min(x1, x2), max(x1, x2) + 1):
                setb(x, y, z, block)


def disk(cx: int, y: int, cz: int, radius: int, block: str) -> None:
    for dz in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx * dx + dz * dz <= radius * radius:
                x, z = cx + dx, cz + dz
                if -X0 <= x <= X0 and -Z0 <= z <= Z0:
                    setb(x, y, z, block)


def ellipse(cx: int, y: int, cz: int, rx: int, rz: int, block: str) -> None:
    for dz in range(-rz, rz + 1):
        for dx in range(-rx, rx + 1):
            if (dx / rx) ** 2 + (dz / rz) ** 2 <= 1:
                setb(cx + dx, y, cz + dz, block)


def bezier(p0: tuple[int, int], p1: tuple[int, int], p2: tuple[int, int], steps: int = 120):
    for i in range(steps + 1):
        t = i / steps
        x = round((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0])
        z = round((1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])
        yield x, z


def curved_path(p0, p1, p2, radius: int, block: str, y: int = 2) -> None:
    for x, z in bezier(p0, p1, p2):
        disk(x, y, z, radius, block)


def clear_plot(x1: int, z1: int, x2: int, z2: int, surface: str = "minecraft:grass_block") -> None:
    fill(x1, 2, z1, x2, 2, z2, surface)
    fill(x1, 3, z1, x2, HEIGHT - 1, z2, AIR)


def mixed_wall(x: int, y: int, z: int, main: str, secondary: str) -> str:
    return secondary if (x * 31 + y * 17 + z * 13) % 11 in (0, 1) else main


def gable_house(x1: int, z1: int, x2: int, z2: int, floors: int,
                wall: str, timber: str, roof: str, ridge: str = "x",
                entrance: str = "south", secondary: str = "minecraft:cobblestone") -> int:
    base, floor_height = 3, 5
    wall_top = base + floors * floor_height
    fill(x1 - 1, 2, z1 - 1, x2 + 1, 2, z2 + 1, "minecraft:cobblestone")
    fill(x1, base, z1, x2, base, z2, "minecraft:spruce_planks")
    # Irregular masonry ground floor and timber-framed upper floors.
    for y in range(base + 1, wall_top + 1):
        floor = (y - base - 1) // floor_height
        for x in range(x1, x2 + 1):
            for z in (z1, z2):
                material = mixed_wall(x, y, z, wall, secondary) if floor == 0 else wall
                if floor > 0 and ((x - x1) % 5 == 0 or y % floor_height == 1):
                    material = timber
                setb(x, y, z, material)
        for z in range(z1, z2 + 1):
            for x in (x1, x2):
                material = mixed_wall(x, y, z, wall, secondary) if floor == 0 else wall
                if floor > 0 and ((z - z1) % 5 == 0 or y % floor_height == 1):
                    material = timber
                setb(x, y, z, material)
    # Floors, framed windows, shutters.
    for floor in range(floors):
        fy = base + floor * floor_height
        if floor:
            fill(x1 + 1, fy, z1 + 1, x2 - 1, fy, z2 - 1, "minecraft:spruce_planks")
        wy = fy + 2
        for x in range(x1 + 2, x2 - 2, 5):
            fill(x, wy, z1, x + 1, wy + 1, z1, "minecraft:glass_pane")
            fill(x, wy, z2, x + 1, wy + 1, z2, "minecraft:glass_pane")
            for sx in (x - 1, x + 2):
                setb(sx, wy, z1, "minecraft:dark_oak_trapdoor")
                setb(sx, wy, z2, "minecraft:dark_oak_trapdoor")
        for z in range(z1 + 2, z2 - 2, 5):
            fill(x1, wy, z, x1, wy + 1, z + 1, "minecraft:glass_pane")
            fill(x2, wy, z, x2, wy + 1, z + 1, "minecraft:glass_pane")
    # Entrance, small stone stoop, and hanging lanterns.
    if entrance in ("south", "north"):
        c = (x1 + x2) // 2
        z = z2 if entrance == "south" else z1
        fill(c, base + 1, z, c + 1, base + 3, z, AIR)
        outside = z + (1 if entrance == "south" else -1)
        fill(c - 1, base, outside, c + 2, base, outside, "minecraft:stone_bricks")
        setb(c - 2, base + 3, z, "minecraft:lantern[hanging=false,waterlogged=false]")
    else:
        c = (z1 + z2) // 2
        x = x1 if entrance == "west" else x2
        fill(x, base + 1, c, x, base + 3, c + 1, AIR)
        outside = x + (-1 if entrance == "west" else 1)
        fill(outside, base, c - 1, outside, base, c + 2, "minecraft:stone_bricks")
    # True pitched roof with overhang and filled gable ends.
    if ridge == "x":
        half = (z2 - z1 + 2) // 2
        for k in range(half + 1):
            y = wall_top + 1 + k
            zn, zs = z1 - 1 + k, z2 + 1 - k
            north = f"minecraft:{roof}_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
            south = f"minecraft:{roof}_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
            fill(x1 - 1, y, zn, x2 + 1, y, zn, north)
            fill(x1 - 1, y, zs, x2 + 1, y, zs, south)
            for z in range(zn + 1, zs):
                setb(x1, y, z, wall)
                setb(x2, y, z, wall)
        fill(x1 - 1, wall_top + half + 2, (z1 + z2) // 2,
             x2 + 1, wall_top + half + 2, (z1 + z2) // 2,
             f"minecraft:{roof}_slab[type=top,waterlogged=false]")
    else:
        half = (x2 - x1 + 2) // 2
        for k in range(half + 1):
            y = wall_top + 1 + k
            xw, xe = x1 - 1 + k, x2 + 1 - k
            west = f"minecraft:{roof}_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"
            east = f"minecraft:{roof}_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"
            fill(xw, y, z1 - 1, xw, y, z2 + 1, west)
            fill(xe, y, z1 - 1, xe, y, z2 + 1, east)
            for x in range(xw + 1, xe):
                setb(x, y, z1, wall)
                setb(x, y, z2, wall)
        fill((x1 + x2) // 2, wall_top + half + 2, z1 - 1,
             (x1 + x2) // 2, wall_top + half + 2, z2 + 1,
             f"minecraft:{roof}_slab[type=top,waterlogged=false]")
    # Chimney and smoke-like cobweb detail.
    chimney_x, chimney_z = x1 + 3, z1 + 3
    fill(chimney_x, wall_top + 2, chimney_z, chimney_x + 1, wall_top + 8, chimney_z + 1, "minecraft:bricks")
    setb(chimney_x, wall_top + 9, chimney_z, "minecraft:cobweb")
    return wall_top


def furnish_shop(x1: int, z1: int, x2: int, z2: int, color: str) -> None:
    # Counter, shelves, crates, awning, and flower pots.
    fill(x1 + 2, 4, z1 + 6, x2 - 2, 4, z1 + 7, "minecraft:dark_oak_planks")
    for x in range(x1 + 2, x2 - 1, 3):
        setb(x, 5, z1 + 6, "minecraft:lantern[hanging=false,waterlogged=false]")
    for z in range(z1 + 10, z2 - 1, 2):
        setb(x1 + 2, 4, z, "minecraft:barrel")
        setb(x2 - 2, 4, z, "minecraft:bookshelf")
    for x in range(x1, x2 + 1):
        setb(x, 8, z1 - 2, f"minecraft:{color}_wool" if x % 2 else "minecraft:white_wool")
        setb(x, 8, z1 - 1, f"minecraft:{color}_wool" if x % 2 else "minecraft:white_wool")
    setb(x1 + 1, 3, z1 - 2, "minecraft:flower_pot")
    setb(x2 - 1, 3, z1 - 2, "minecraft:flower_pot")


def tree(x: int, z: int, kind: str = "oak", size: int = 1) -> None:
    log = "minecraft:birch_log[axis=y]" if kind == "birch" else "minecraft:stripped_oak_log[axis=y]"
    leaves = "minecraft:birch_leaves[persistent=true,distance=1]" if kind == "birch" else "minecraft:oak_leaves[persistent=true,distance=1]"
    trunk = 5 + size
    fill(x, 3, z, x, 2 + trunk, z, log)
    for layer, radius in ((trunk - 1, 2 + size), (trunk, 3), (trunk + 1, 2), (trunk + 2, 1)):
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if dx * dx + dz * dz <= radius * radius + 2:
                    setb(x + dx, 3 + layer, z + dz, leaves)


def fence_polygon(points: list[tuple[int, int]], block: str = "minecraft:oak_fence") -> None:
    for (x1, z1), (x2, z2) in zip(points, points[1:] + points[:1]):
        steps = max(abs(x2 - x1), abs(z2 - z1))
        for i in range(steps + 1):
            t = i / max(steps, 1)
            setb(round(x1 + (x2 - x1) * t), 3, round(z1 + (z2 - z1) * t), block)


def lamp(x: int, z: int) -> None:
    fill(x, 3, z, x, 7, z, "minecraft:cobblestone_wall")
    setb(x, 8, z, "minecraft:lantern[hanging=true,waterlogged=false]")


def bench(x: int, z: int, facing: str) -> None:
    for dx in range(3):
        setb(x + dx, 3, z, f"minecraft:spruce_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]")


def terrain_and_river() -> None:
    for z in range(-96, 97):
        for x in range(-96, 97):
            edge = max(abs(x), abs(z))
            wave = ((x * 17 + z * 29) % 13) / 13
            h = 2 + (1 if edge > 82 and wave > 0.35 else 0) + (1 if edge > 90 and wave > 0.65 else 0)
            fill(x, 0, z, x, 0, z, "minecraft:stone")
            fill(x, 1, z, x, h - 1, z, "minecraft:dirt")
            setb(x, h, z, "minecraft:grass_block")
    # Meandering river on the east side with sand/gravel banks.
    for z in range(-96, 97):
        center = 62 + round(math.sin(z / 17) * 8 + math.sin(z / 7) * 2)
        for x in range(center - 8, center + 9):
            distance = abs(x - center)
            if distance <= 5:
                fill(x, 1, z, x, 2, z, "minecraft:water[level=0]")
            elif distance <= 7:
                setb(x, 2, z, "minecraft:sand" if (x + z) % 3 else "minecraft:gravel")
    # Natural rocks and edge trees.
    for x, z in ((-91, -72), (-84, -38), (-91, 66), (-72, 89), (88, -79), (88, 73), (74, 91)):
        disk(x, 3, z, 2, "minecraft:mossy_cobblestone")
        setb(x, 4, z, "minecraft:moss_block")


def roads_and_bridge() -> None:
    # Roads grew gradually around the old circular plaza.
    curved_path((0, -96), (-14, -47), (0, -18), 5, "minecraft:gravel")
    curved_path((0, -18), (13, 35), (-5, 96), 5, "minecraft:gravel")
    curved_path((-96, -7), (-46, -25), (-18, 0), 5, "minecraft:gravel")
    curved_path((-18, 0), (22, -13), (72, -7), 5, "minecraft:gravel")
    curved_path((-35, 18), (-48, 55), (-70, 87), 3, "minecraft:coarse_dirt")
    curved_path((17, 18), (38, 43), (54, 75), 3, "minecraft:coarse_dirt")
    # Stone bridge over the river, slightly angled appearance via stepped edges.
    fill(53, 2, -12, 76, 2, -2, "minecraft:stone_bricks")
    for x in range(53, 77):
        setb(x, 3, -12, "minecraft:stone_brick_wall")
        setb(x, 3, -2, "minecraft:stone_brick_wall")
    for x in (54, 64, 75):
        setb(x, 4, -12, "minecraft:lantern[hanging=false,waterlogged=false]")
        setb(x, 4, -2, "minecraft:lantern[hanging=false,waterlogged=false]")


def plaza_and_well() -> None:
    # Uneven round plaza instead of a square grid.
    disk(0, 2, 0, 20, "minecraft:stone_bricks")
    for angle in range(0, 360, 8):
        r = 17 + ((angle // 8) % 3)
        x = round(math.cos(math.radians(angle)) * r)
        z = round(math.sin(math.radians(angle)) * r)
        setb(x, 2, z, "minecraft:mossy_stone_bricks")
    disk(0, 3, 0, 6, "minecraft:cobblestone")
    disk(0, 4, 0, 4, "minecraft:water[level=0]")
    for x, z in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
        fill(x, 4, z, x, 9, z, "minecraft:dark_oak_fence")
    fill(-6, 10, -6, 6, 10, 6, "minecraft:dark_oak_planks")
    fill(-4, 11, -4, 4, 11, 4, "minecraft:dark_oak_slab[type=top,waterlogged=false]")
    for x, z, facing in ((-14, -9, "south"), (11, -9, "south"), (-14, 10, "north"), (11, 10, "north")):
        bench(x, z, facing)


def town_hall() -> None:
    clear_plot(-33, -85, 29, -42)
    # L-shaped civic hall: two intersecting wings with different roofs.
    gable_house(-29, -82, 21, -57, 3, "minecraft:calcite", "minecraft:stripped_spruce_log[axis=y]",
                "deepslate_tile", "x", "south", "minecraft:stone_bricks")
    gable_house(-12, -70, 27, -45, 2, "minecraft:white_terracotta", "minecraft:dark_oak_log[axis=y]",
                "dark_oak", "z", "south", "minecraft:cobblestone")
    # Stone clock/bell tower offset from center.
    fill(-30, 3, -58, -17, 23, -45, "minecraft:stone_bricks")
    for face_x, face_z in ((-30, -51), (-17, -51), (-23, -58), (-23, -45)):
        setb(face_x, 18, face_z, "minecraft:gold_block")
        setb(face_x, 19, face_z, "minecraft:black_concrete")
    for k in range(6):
        fill(-32 + k, 24 + k, -60 + k, -15 - k, 24 + k, -43 - k, "minecraft:deepslate_tiles")
    # Reception and records inside.
    fill(-20, 4, -63, 10, 4, -61, "minecraft:dark_oak_planks")
    for x in range(-25, 17, 4):
        setb(x, 4, -77, "minecraft:bookshelf")
        setb(x, 5, -77, "minecraft:bookshelf")
    # Asymmetric civic garden.
    fence_polygon([(-34, -86), (30, -86), (32, -39), (-25, -39), (-36, -51)])
    for x, z in ((-31, -79), (25, -76), (-29, -45), (23, -42)):
        tree(x, z, "birch")


def bank() -> None:
    clear_plot(-92, -43, -49, -7)
    gable_house(-89, -39, -56, -13, 3, "minecraft:stone_bricks", "minecraft:polished_blackstone",
                "blackstone", "x", "east", "minecraft:andesite")
    # Classical side portico, teller counter, and real-looking vault room.
    for z in range(-31, -17, 5):
        fill(-53, 4, z, -53, 12, z, "minecraft:quartz_pillar[axis=y]")
    fill(-55, 13, -34, -51, 14, -14, "minecraft:smooth_quartz")
    fill(-82, 4, -31, -61, 4, -29, "minecraft:dark_oak_planks")
    fill(-84, 4, -25, -75, 10, -16, "minecraft:iron_block")
    fill(-83, 5, -24, -76, 9, -17, AIR)
    fill(-75, 5, -22, -75, 8, -19, "minecraft:iron_bars")
    for z in (-23, -20, -18):
        setb(-80, 5, z, "minecraft:gold_block")
    for x, z in ((-90, -40), (-52, -37), (-88, -10)):
        tree(x, z)


def jobs_guild() -> None:
    clear_plot(26, -55, 57, -20)
    gable_house(29, -51, 52, -27, 2, "minecraft:bricks", "minecraft:dark_oak_log[axis=y]",
                "spruce", "x", "south", "minecraft:mud_bricks")
    # Small attached workshop makes an irregular footprint.
    gable_house(43, -35, 58, -20, 1, "minecraft:mud_bricks", "minecraft:stripped_oak_log[axis=y]",
                "spruce", "z", "east", "minecraft:cobblestone")
    fill(33, 4, -43, 47, 4, -41, "minecraft:dark_oak_planks")
    for x in range(32, 51, 4):
        setb(x, 4, -33, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
    fill(54, 3, -31, 60, 3, -23, "minecraft:cobblestone")
    setb(56, 4, -27, "minecraft:anvil")
    setb(58, 4, -27, "minecraft:blast_furnace[facing=west,lit=false]")


def shop_street() -> None:
    # Individually sized and staggered shops along a curved old street.
    specs = [
        (-91, 18, -77, 35, "red", "brick", "x"),
        (-74, 21, -59, 40, "blue", "spruce", "x"),
        (-56, 25, -40, 43, "lime", "dark_oak", "x"),
        (-37, 28, -20, 47, "orange", "brick", "x"),
        (-17, 31, -1, 51, "magenta", "spruce", "x"),
        (2, 31, 18, 50, "cyan", "dark_oak", "x"),
        (21, 28, 37, 46, "yellow", "brick", "x"),
        (40, 24, 55, 42, "purple", "spruce", "x"),
        (2, 56, 19, 75, "green", "dark_oak", "z"),
        (23, 54, 40, 72, "pink", "brick", "z"),
    ]
    for i, (x1, z1, x2, z2, color, roof, ridge) in enumerate(specs):
        clear_plot(x1 - 2, z1 - 3, x2 + 2, z2 + 2)
        floors = 1 + (i % 3 != 0)
        gable_house(x1, z1, x2, z2, floors, "minecraft:white_terracotta",
                    "minecraft:dark_oak_log[axis=y]", roof, ridge, "north", "minecraft:bricks")
        furnish_shop(x1, z1, x2, z2, color)
    # Narrow alley decorations.
    for x, z in ((-75, 42), (-57, 45), (-38, 49), (-19, 53), (20, 52), (39, 48)):
        lamp(x, z)
        setb(x + 1, 3, z, "minecraft:barrel")


def farm_and_barn() -> None:
    clear_plot(-94, 48, -43, 91)
    gable_house(-91, 57, -72, 84, 2, "minecraft:red_terracotta", "minecraft:stripped_spruce_log[axis=y]",
                "dark_oak", "z", "east", "minecraft:cobblestone")
    # Irregular fenced fields following the old property lines.
    plots = [
        [(-68, 55), (-46, 52), (-44, 66), (-66, 70)],
        [(-67, 73), (-44, 69), (-42, 88), (-63, 92)],
    ]
    crops = ["minecraft:wheat[age=7]", "minecraft:carrots[age=7]"]
    for poly, crop in zip(plots, crops):
        fence_polygon(poly)
        xs, zs = [p[0] for p in poly], [p[1] for p in poly]
        for x in range(min(xs) + 2, max(xs) - 1):
            for z in range(min(zs) + 2, max(zs) - 1):
                # Loose inside test is adequate for these convex plots.
                if x < -45 - (z - 55) // 7:
                    setb(x, 2, z, "minecraft:farmland[moisture=7]")
                    if (x + z) % 8 == 0:
                        setb(x, 2, z, "minecraft:water[level=0]")
                    else:
                        setb(x, 3, z, crop)
    fill(-88, 4, 63, -76, 4, 78, "minecraft:hay_block[axis=y]")
    setb(-87, 5, 80, "minecraft:composter[level=8]")


def park_and_riverside() -> None:
    # Winding riverside trail.
    curved_path((38, 5), (43, 35), (53, 86), 2, "minecraft:coarse_dirt")
    for x, z in ((38, 4), (45, 17), (49, 35), (48, 53), (53, 76), (81, 24), (85, 52)):
        tree(x, z, "birch" if z % 2 else "oak")
    # Organic pond connected visually to the river.
    for dz in range(-12, 13):
        for dx in range(-17, 18):
            wobble = math.sin((dx + dz) * 0.7) * 0.08
            if (dx / 17) ** 2 + (dz / 12) ** 2 < 1 + wobble:
                setb(73 + dx, 2, 47 + dz, "minecraft:water[level=0]")
    for angle in range(0, 360, 12):
        x = 73 + round(math.cos(math.radians(angle)) * 18)
        z = 47 + round(math.sin(math.radians(angle)) * 13)
        setb(x, 2, z, "minecraft:mossy_cobblestone")
    # Eight-sided gazebo with steep roof.
    disk(48, 3, 76, 7, "minecraft:spruce_planks")
    for x, z in ((42, 76), (54, 76), (48, 70), (48, 82), (44, 72), (52, 72), (44, 80), (52, 80)):
        fill(x, 4, z, x, 10, z, "minecraft:spruce_fence")
    for r in range(8, 2, -1):
        disk(48, 11 + (8 - r), 76, r, "minecraft:dark_oak_planks")
    bench(58, 31, "east")
    bench(83, 65, "west")
    for x, z in ((39, 12), (44, 28), (52, 62), (87, 34), (89, 72)):
        lamp(x, z)


def inn_and_homes() -> None:
    clear_plot(-35, 60, -2, 94)
    # L-shaped inn and two different cottages.
    gable_house(-32, 62, -7, 79, 3, "minecraft:yellow_terracotta", "minecraft:dark_oak_log[axis=y]",
                "spruce", "x", "south", "minecraft:bricks")
    gable_house(-18, 75, -3, 92, 2, "minecraft:white_terracotta", "minecraft:stripped_oak_log[axis=y]",
                "dark_oak", "z", "east", "minecraft:cobblestone")
    fill(-29, 4, 68, -10, 4, 70, "minecraft:dark_oak_planks")
    for x in range(-29, -7, 4):
        setb(x, 4, 74, "minecraft:red_carpet")
    # Stable/courtyard details.
    fence_polygon([(-35, 82), (-21, 81), (-20, 94), (-35, 94)])
    fill(-33, 3, 85, -31, 4, 91, "minecraft:hay_block[axis=y]")
    setb(-25, 3, 88, "minecraft:water_cauldron[level=3]")


def north_gate() -> None:
    clear_plot(-25, -96, 25, -86, "minecraft:gravel")
    for x in (-10, 10):
        fill(x - 3, 3, -95, x + 3, 17, -90, "minecraft:stone_bricks")
        fill(x - 2, 6, -96, x + 2, 14, -89, "minecraft:mossy_stone_bricks")
    # Uneven old gate roof and timber beam.
    fill(-14, 16, -95, 14, 18, -90, "minecraft:dark_oak_log[axis=x]")
    for k in range(5):
        fill(-16 + k, 19 + k, -96 + k, 16 - k, 19 + k, -88 - k, "minecraft:deepslate_tiles")
    for x in range(-6, 7, 4):
        setb(x, 17, -89, "minecraft:lantern[hanging=true,waterlogged=false]")
    # Small guard hut, deliberately off-center.
    gable_house(18, -94, 31, -80, 1, "minecraft:cobblestone", "minecraft:dark_oak_log[axis=y]",
                "spruce", "x", "west", "minecraft:mossy_cobblestone")


def scatter_details() -> None:
    # Hand-placed clutter and greenery keeps the town from looking generated.
    for x, z in ((-42, -11), (-34, 13), (-25, 19), (19, 17), (27, 12), (15, -24), (-18, -29)):
        tree(x, z, "birch" if (x + z) % 2 else "oak")
    for x, z in ((-45, 6), (-31, 9), (25, 4), (33, -4), (8, 23), (-9, 24)):
        lamp(x, z)
    for x, z, flower in ((-38, 4, "poppy"), (-28, 12, "dandelion"), (24, 10, "cornflower"),
                         (31, 6, "azure_bluet"), (42, 58, "oxeye_daisy"), (89, 18, "poppy")):
        setb(x, 3, z, f"minecraft:{flower}")
    for x, z in ((-47, -3), (-40, 18), (32, 18), (18, -20), (13, 22)):
        setb(x, 3, z, "minecraft:barrel")
        setb(x + 1, 3, z, "minecraft:composter[level=5]")


def build_town() -> None:
    terrain_and_river()
    roads_and_bridge()
    plaza_and_well()
    town_hall()
    bank()
    jobs_guild()
    shop_street()
    farm_and_barn()
    park_and_riverside()
    inn_and_homes()
    north_gate()
    scatter_details()


def make_schematic() -> tuple[bytes, dict]:
    palette_names = sorted(set(blocks), key=lambda value: (value != AIR, value))
    palette = {value: i for i, value in enumerate(palette_names)}
    data = b"".join(varint(palette[block]) for block in blocks)
    root = tag_compound("Schematic", [
        tag_int("Version", 2), tag_int("DataVersion", 3465),
        tag_compound("Metadata", [tag_int("WEOffsetX", -X0), tag_int("WEOffsetY", -2), tag_int("WEOffsetZ", -Z0)]),
        tag_short("Width", WIDTH), tag_short("Height", HEIGHT), tag_short("Length", LENGTH),
        tag_int_array("Offset", [-X0, -2, -Z0]), tag_int("PaletteMax", len(palette)),
        tag_compound("Palette", [tag_int(name, value) for name, value in palette.items()]),
        tag_byte_array("BlockData", data), tag_list_empty("BlockEntities"), tag_list_empty("Entities"),
    ])
    count = Counter(blocks)
    report = {
        "format": "Sponge Schematic v2", "map_version": "v3-handbuilt", "data_version": 3465,
        "dimensions": [WIDTH, HEIGHT, LENGTH], "origin_offset": [-X0, -2, -Z0],
        "palette_size": len(palette), "encoded_blocks": len(blocks),
        "non_air_blocks": len(blocks) - count[AIR], "block_data_bytes": len(data),
        "design": ["organic_curved_roads", "meandering_river_and_bridge", "varied_terrain",
                   "l_shaped_civic_hall", "pitched_and_gabled_roofs", "mixed_masonry_and_timber",
                   "staggered_individual_shops", "irregular_fenced_fields", "furnished_interiors",
                   "riverside_park_gazebo", "inn_courtyard", "hand_placed_clutter"],
    }
    return gzip.compress(root, compresslevel=9, mtime=0), report


def write_preview(path: Path) -> None:
    colors = {
        "grass_block": (84, 143, 65), "gravel": (126, 121, 115), "coarse_dirt": (119, 84, 57),
        "stone_bricks": (119, 117, 111), "mossy_stone_bricks": (93, 108, 81), "cobblestone": (108, 106, 101),
        "mossy_cobblestone": (91, 105, 78), "water": (43, 104, 181), "sand": (216, 204, 159),
        "deepslate_tiles": (48, 45, 50), "blackstone_stairs": (42, 37, 43), "brick_stairs": (133, 70, 55),
        "spruce_stairs": (104, 74, 42), "dark_oak_stairs": (62, 39, 19), "spruce_planks": (111, 80, 46),
        "dark_oak_planks": (65, 42, 20), "calcite": (224, 221, 210), "white_terracotta": (210, 178, 161),
        "yellow_terracotta": (186, 133, 35), "red_terracotta": (143, 61, 47), "bricks": (139, 75, 60),
        "mud_bricks": (137, 103, 79), "iron_block": (214, 215, 211), "gold_block": (246, 202, 37),
        "oak_leaves": (51, 112, 46), "birch_leaves": (73, 127, 56), "wheat": (208, 180, 62),
        "carrots": (224, 103, 26), "hay_block": (169, 137, 29), "red_wool": (160, 39, 34),
        "blue_wool": (53, 57, 157), "lime_wool": (112, 185, 25), "orange_wool": (240, 118, 18),
        "magenta_wool": (190, 69, 180), "cyan_wool": (22, 137, 145), "yellow_wool": (249, 198, 39),
        "purple_wool": (121, 42, 172), "green_wool": (84, 109, 27), "pink_wool": (238, 141, 172),
    }
    scale = 3
    rows = []
    for z in range(-Z0, Z0 + 1):
        row = bytearray()
        for x in range(-X0, X0 + 1):
            top = "minecraft:grass_block"
            for y in range(HEIGHT - 1, -1, -1):
                if getb(x, y, z) != AIR:
                    top = getb(x, y, z)
                    break
            name = top.removeprefix("minecraft:").split("[", 1)[0]
            row.extend(bytes(colors.get(name, (126, 93, 67))) * scale)
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
    build_town()
    output = Path(__file__).resolve().parent / "dist"
    output.mkdir(parents=True, exist_ok=True)
    schematic, report = make_schematic()
    (output / "economy_spawn_v3_handbuilt.schem").write_bytes(schematic)
    (output / "economy_spawn_v3_handbuilt_validation.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_preview(output / "economy_spawn_v3_handbuilt_preview.png")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
