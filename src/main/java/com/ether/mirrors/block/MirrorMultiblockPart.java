package com.ether.mirrors.block;

import net.minecraft.util.StringRepresentable;

public enum MirrorMultiblockPart implements StringRepresentable {
    MASTER("master"),         // Bottom-left from player's perspective
    SLAVE_RIGHT("right"),     // Bottom-right
    SLAVE_UP("up"),           // Top-left
    SLAVE_UP_RIGHT("up_right"); // Top-right

    private final String name;

    MirrorMultiblockPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean isMaster() {
        return this == MASTER;
    }
}
