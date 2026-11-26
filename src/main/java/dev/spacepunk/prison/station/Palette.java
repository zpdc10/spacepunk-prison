package dev.spacepunk.prison.station;

import org.bukkit.block.data.BlockData;

import static dev.spacepunk.prison.station.Canvas.data;

/**
 * Hub blocks. Most are blocks the resource pack retextures into grungy station parts (hull plating, rusted
 * copper panels, slag tiles, froglight strip lights), so the hub matches the mines.
 */
final class Palette {
    private Palette() {}

    static final BlockData HULL = data("minecraft:polished_deepslate");
    static final BlockData HULL_TILE = data("minecraft:deepslate_tiles", "minecraft:polished_deepslate");
    static final BlockData PLATE = data("minecraft:waxed_weathered_cut_copper");
    static final BlockData PLATE_DARK = data("minecraft:waxed_oxidized_cut_copper", "minecraft:waxed_weathered_cut_copper");
    static final BlockData BRICK = data("minecraft:polished_blackstone_bricks");
    static final BlockData BRICK_CRACKED = data("minecraft:cracked_polished_blackstone_bricks");
    static final BlockData TRIM = data("minecraft:smooth_basalt");
    static final BlockData FLOOR_A = data("minecraft:polished_andesite");
    static final BlockData FLOOR_B = data("minecraft:polished_tuff", "minecraft:smooth_stone");
    static final BlockData FLOOR_PAD = data("minecraft:smooth_stone");
    static final BlockData GRATE = data("minecraft:waxed_copper_grate", "minecraft:iron_bars");
    static final BlockData SLAB = data("minecraft:smooth_stone_slab[type=bottom]");
    static final BlockData PIPE_X = data("minecraft:polished_basalt[axis=x]");
    static final BlockData PIPE_Z = data("minecraft:polished_basalt[axis=z]");
    static final BlockData PIPE_Y = data("minecraft:polished_basalt[axis=y]");

    static final BlockData LIGHT = data("minecraft:sea_lantern");
    static final BlockData WARM = data("minecraft:ochre_froglight[axis=y]", "minecraft:ochre_froglight");
    static final BlockData GREEN = data("minecraft:verdant_froglight[axis=y]", "minecraft:verdant_froglight");
    static final BlockData VIOLET = data("minecraft:pearlescent_froglight[axis=y]", "minecraft:pearlescent_froglight");
    static final BlockData ORANGE = data("minecraft:shroomlight");
    static final BlockData LANTERN = data("minecraft:lantern[hanging=true]");

    static final BlockData WINDOW = data("minecraft:glass");
    static final BlockData TINTED = data("minecraft:tinted_glass");
    static final BlockData CORE_GLASS = data("minecraft:lime_stained_glass");
    static final BlockData FIELD = data("minecraft:light_blue_stained_glass");
    static final BlockData RED_GLASS = data("minecraft:red_stained_glass");

    static final BlockData HAZ_Y = data("minecraft:yellow_concrete");
    static final BlockData HAZ_B = data("minecraft:black_concrete");
    static final BlockData DOOR = data("minecraft:iron_block");
    static final BlockData BARS = data("minecraft:iron_bars");
    static final BlockData CRATE = data("minecraft:barrel[facing=up]");
    static final BlockData CONSOLE = data("minecraft:lodestone");
    static final BlockData BUTTON = data("minecraft:polished_blackstone_button[face=floor,facing=north]");
    static final BlockData ANTENNA = data("minecraft:lightning_rod");
    static final BlockData COBWEB = data("minecraft:cobweb");
    static final BlockData BREWER = data("minecraft:brewing_stand");

    static BlockData stairs(String facing) {
        return data("minecraft:polished_blackstone_brick_stairs[facing=" + facing + "]");
    }
}
