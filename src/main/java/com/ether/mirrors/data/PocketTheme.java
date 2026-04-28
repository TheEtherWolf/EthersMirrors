package com.ether.mirrors.data;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum PocketTheme {

    DEEPSLATE("deepslate") {
        @Override public Block floorBlock()  { return Blocks.DEEPSLATE_TILES; }
        @Override public Block wallBlock()   { return Blocks.DEEPSLATE_BRICKS; }
        @Override public Block lightBlock()  { return Blocks.GLOWSTONE; }
    },
    NETHER("nether") {
        @Override public Block floorBlock()  { return Blocks.NETHER_BRICKS; }
        @Override public Block wallBlock()   { return Blocks.NETHER_BRICKS; }
        @Override public Block lightBlock()  { return Blocks.SHROOMLIGHT; }
    },
    END("end") {
        @Override public Block floorBlock()  { return Blocks.PURPUR_BLOCK; }
        @Override public Block wallBlock()   { return Blocks.END_STONE_BRICKS; }
        @Override public Block lightBlock()  { return Blocks.SEA_LANTERN; }
    },
    MUSHROOM("mushroom") {
        @Override public Block floorBlock()  { return Blocks.MYCELIUM; }
        @Override public Block wallBlock()   { return Blocks.MUSHROOM_STEM; }
        @Override public Block lightBlock()  { return Blocks.SHROOMLIGHT; }
    },
    CRYSTAL("crystal") {
        @Override public Block floorBlock()  { return Blocks.AMETHYST_BLOCK; }
        @Override public Block wallBlock()   { return Blocks.CALCITE; }
        @Override public Block lightBlock()  { return Blocks.SEA_LANTERN; }
    };

    private final String id;

    PocketTheme(String id) { this.id = id; }

    public String getId() { return id; }
    public String getDisplayName() { return id.substring(0, 1).toUpperCase() + id.substring(1); }

    public abstract Block floorBlock();
    public abstract Block wallBlock();
    public abstract Block lightBlock();

    public static PocketTheme fromId(String id) {
        for (PocketTheme t : values()) {
            if (t.id.equals(id)) return t;
        }
        return DEEPSLATE;
    }
}
