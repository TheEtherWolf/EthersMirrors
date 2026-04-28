package com.ether.mirrors.api.event;

import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class MirrorRenamedEvent extends Event {
    private final ServerPlayer player;
    private final MirrorNetworkData.MirrorEntry mirror;
    private final String oldName;
    private final String newName;

    public MirrorRenamedEvent(ServerPlayer player, MirrorNetworkData.MirrorEntry mirror, String oldName, String newName) {
        this.player = player;
        this.mirror = mirror;
        this.oldName = oldName;
        this.newName = newName;
    }

    public ServerPlayer getPlayer() { return player; }
    public MirrorNetworkData.MirrorEntry getMirror() { return mirror; }
    public String getOldName() { return oldName; }
    public String getNewName() { return newName; }
}
