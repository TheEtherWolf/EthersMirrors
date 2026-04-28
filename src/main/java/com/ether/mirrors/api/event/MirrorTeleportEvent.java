package com.ether.mirrors.api.event;

import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

@Cancelable
public class MirrorTeleportEvent extends Event {
    private final ServerPlayer player;
    private final MirrorNetworkData.MirrorEntry source;
    private final MirrorNetworkData.MirrorEntry target;

    public MirrorTeleportEvent(ServerPlayer player, MirrorNetworkData.MirrorEntry source, MirrorNetworkData.MirrorEntry target) {
        this.player = player;
        this.source = source;
        this.target = target;
    }

    public ServerPlayer getPlayer() { return player; }
    public MirrorNetworkData.MirrorEntry getSource() { return source; }
    public MirrorNetworkData.MirrorEntry getTarget() { return target; }
}
