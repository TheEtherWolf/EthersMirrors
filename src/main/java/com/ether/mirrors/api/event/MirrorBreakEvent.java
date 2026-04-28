package com.ether.mirrors.api.event;

import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class MirrorBreakEvent extends Event {
    private final ServerPlayer player;
    private final MirrorNetworkData.MirrorEntry mirror;

    public MirrorBreakEvent(ServerPlayer player, MirrorNetworkData.MirrorEntry mirror) {
        this.player = player;
        this.mirror = mirror;
    }

    public ServerPlayer getPlayer() { return player; }
    public MirrorNetworkData.MirrorEntry getMirror() { return mirror; }
}
