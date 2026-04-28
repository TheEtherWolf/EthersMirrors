package com.ether.mirrors.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class PocketExitEvent extends Event {
    private final ServerPlayer player;

    public PocketExitEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }
}
