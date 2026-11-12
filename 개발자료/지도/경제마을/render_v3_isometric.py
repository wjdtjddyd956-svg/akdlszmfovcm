#!/usr/bin/env python3
"""Render a simple isometric preview of the hand-built v3 schematic source."""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

import generate_spawn_v3_handbuilt as town


COLORS = {
    "grass_block": (91, 145, 68), "gravel": (130, 125, 120), "coarse_dirt": (119, 84, 57),
    "dirt": (119, 83, 56), "stone": (118, 118, 118), "stone_bricks": (123, 121, 115),
    "mossy_stone_bricks": (96, 111, 83), "cobblestone": (112, 110, 105),
    "mossy_cobblestone": (91, 106, 78), "water": (49, 113, 194), "sand": (220, 209, 166),
    "deepslate_tiles": (48, 45, 51), "blackstone": (43, 39, 45), "blackstone_stairs": (43, 39, 45),
    "brick_stairs": (144, 76, 59), "brick_slab": (144, 76, 59), "bricks": (144, 76, 59),
    "spruce_stairs": (110, 79, 44), "spruce_slab": (110, 79, 44), "spruce_planks": (113, 81, 47),
    "dark_oak_stairs": (68, 43, 21), "dark_oak_slab": (68, 43, 21), "dark_oak_planks": (68, 43, 21),
    "calcite": (226, 223, 213), "white_terracotta": (211, 179, 162), "yellow_terracotta": (188, 136, 39),
    "red_terracotta": (145, 63, 48), "mud_bricks": (138, 104, 80), "andesite": (133, 134, 134),
    "polished_blackstone": (52, 48, 55), "quartz_pillar": (232, 226, 215), "smooth_quartz": (239, 233, 223),
    "iron_block": (218, 219, 215), "iron_bars": (130, 134, 134), "gold_block": (248, 205, 40),
    "black_concrete": (23, 24, 29), "glass_pane": (173, 210, 217), "oak_leaves": (54, 115, 48),
    "birch_leaves": (76, 130, 59), "stripped_oak_log": (175, 143, 88), "birch_log": (213, 205, 164),
    "dark_oak_log": (62, 46, 26), "stripped_spruce_log": (108, 83, 52), "oak_fence": (151, 113, 65),
    "dark_oak_fence": (68, 44, 22), "spruce_fence": (111, 80, 46), "wheat": (211, 181, 64),
    "carrots": (226, 104, 27), "farmland": (91, 60, 36), "hay_block": (177, 143, 31),
    "red_wool": (162, 42, 36), "blue_wool": (55, 59, 159), "lime_wool": (114, 187, 27),
    "orange_wool": (241, 120, 19), "magenta_wool": (192, 71, 182), "cyan_wool": (24, 139, 147),
    "yellow_wool": (250, 201, 42), "purple_wool": (123, 44, 174), "green_wool": (86, 111, 29),
    "pink_wool": (239, 143, 174), "white_wool": (234, 236, 230), "barrel": (109, 80, 43),
    "bookshelf": (116, 86, 48), "lantern": (242, 178, 54), "cobweb": (220, 224, 225),
}


def base_color(block: str) -> tuple[int, int, int]:
    name = block.removeprefix("minecraft:").split("[", 1)[0]
    return COLORS.get(name, (139, 105, 75))


def shade(color, factor):
    return tuple(max(0, min(255, round(channel * factor))) for channel in color)


def main() -> None:
    town.build_town()
    tile_x, tile_y, block_h = 5, 3, 6
    background = (205, 220, 235)
    image = Image.new("RGB", (2300, 1800), background)
    draw = ImageDraw.Draw(image)
    ox, oy = 1150, 650

    exposed = []
    for z in range(-town.Z0, town.Z0 + 1):
        for x in range(-town.X0, town.X0 + 1):
            for y in range(2, town.HEIGHT):
                block = town.getb(x, y, z)
                if block == town.AIR:
                    continue
                top = y == town.HEIGHT - 1 or town.getb(x, y + 1, z) == town.AIR
                east = x == town.X0 or town.getb(x + 1, y, z) == town.AIR
                south = z == town.Z0 or town.getb(x, y, z + 1) == town.AIR
                if top or east or south:
                    exposed.append((x + z, y, x, z, block, top, east, south))

    for _, y, x, z, block, top, east, south in sorted(exposed):
        sx = ox + (x - z) * tile_x
        sy = oy + (x + z) * tile_y - y * block_h
        color = base_color(block)
        if east:
            draw.polygon([(sx + tile_x, sy), (sx + tile_x, sy + block_h),
                          (sx, sy + tile_y + block_h), (sx, sy + tile_y)], fill=shade(color, 0.70))
        if south:
            draw.polygon([(sx - tile_x, sy), (sx - tile_x, sy + block_h),
                          (sx, sy + tile_y + block_h), (sx, sy + tile_y)], fill=shade(color, 0.82))
        if top:
            draw.polygon([(sx, sy - tile_y), (sx + tile_x, sy),
                          (sx, sy + tile_y), (sx - tile_x, sy)], fill=color)

    difference = ImageChops.difference(image, Image.new("RGB", image.size, background))
    crop = difference.getbbox()
    if crop:
        margin = 30
        crop = (max(0, crop[0] - margin), max(0, crop[1] - margin),
                min(image.width, crop[2] + margin), min(image.height, crop[3] + margin))
        image = image.crop(crop)
    output = Path(__file__).resolve().parent / "dist" / "economy_spawn_v3_handbuilt_isometric.png"
    image.save(output, optimize=True)
    print(output)


if __name__ == "__main__":
    main()
