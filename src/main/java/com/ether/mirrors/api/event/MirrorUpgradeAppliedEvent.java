package com.ether.mirrors.api.event;

import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class MirrorUpgradeAppliedEvent extends Event {
    private final ServerPlayer player;
    private final MirrorNetworkData.MirrorEntry mirror;
    private final String upgradeTypeId;

    public MirrorUpgradeAppliedEvent(ServerPlayer player, MirrorNetworkData.MirrorEntry mirror, String upgradeTypeId) {
        this.player = player;
        this.mirror = mirror;
        this.upgradeTypeId = upgradeTypeId;
    }

    public ServerPlayer getPlayer() { return player; }
    public MirrorNetworkData.MirrorEntry getMirror() { return mirror; }
    public String getUpgradeTypeId() { return upgradeTypeId; }
}
