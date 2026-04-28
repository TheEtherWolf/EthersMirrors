package com.ether.mirrors.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;

@Cancelable
public class PocketEnterEvent extends Event {
    private final ServerPlayer player;
    private final UUID pocketOwnerUUID;

    public PocketEnterEvent(ServerPlayer player, UUID pocketOwnerUUID) {
        this.player = player;
        this.pocketOwnerUUID = pocketOwnerUUID;
    }

    public ServerPlayer getPlayer() { return player; }
    public UUID getPocketOwnerUUID() { return pocketOwnerUUID; }
}
