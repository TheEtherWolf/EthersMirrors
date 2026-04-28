package com.ether.mirrors.util;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

public enum MirrorTier {
    WOOD("wood", 300, false, Items.OAK_PLANKS),
    STONE("stone", 500, false, Items.STONE),
    IRON("iron", 800, false, Items.IRON_INGOT),
    GOLD("gold", 1200, false, Items.GOLD_INGOT),
    DIAMOND("diamond", 2000, true, Items.DIAMOND),
    NETHERITE("netherite", 5000, true, Items.NETHERITE_INGOT);

    private final String name;
    private final int range;
    private final boolean crossDimension;
    private final Item material;

    MirrorTier(String name, int range, boolean crossDimension, Item material) {
        this.name = name;
        this.range = range;
        this.crossDimension = crossDimension;
        this.material = material;
    }

    public String getName() { return name; }
    public int getRange() { return range; }
    public boolean canCrossDimension() { return crossDimension; }
    public Item getMaterial() { return material; }

    public String getSerializedName() { return name; }

    public String getDisplayName() {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /** Returns the next tier, or null if this is the max tier (NETHERITE). */
    @javax.annotation.Nullable
    public MirrorTier getNext() {
        MirrorTier[] values = values();
        int idx = ordinal();
        return idx + 1 < values.length ? values[idx + 1] : null;
    }

    /** Returns the maximum number of upgrades this tier can hold. */
    public int getUpgradeSlots() {
        return switch (this) {
            case WOOD -> 1;
            case STONE -> 2;
            case IRON -> 3;
            case GOLD -> 4;
            case DIAMOND -> 5;
            case NETHERITE -> 6;
        };
    }
}
