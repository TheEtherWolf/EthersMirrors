package com.ether.mirrors.util;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

public enum MirrorType {
    TELEPORT("teleport", Items.ENDER_PEARL),
    CALLING("calling", Items.AMETHYST_SHARD),
    POCKET("pocket", Items.ENDER_EYE),
    BEACON("beacon", Items.BEACON);

    private final String name;
    private final Item centerItem;

    MirrorType(String name, Item centerItem) {
        this.name = name;
        this.centerItem = centerItem;
    }

    public String getName() { return name; }
    public Item getCenterItem() { return centerItem; }
    public String getSerializedName() { return name; }

    public String getDisplayName() {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
