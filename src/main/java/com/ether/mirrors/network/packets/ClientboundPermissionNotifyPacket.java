package com.ether.mirrors.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundPermissionNotifyPacket {

    private final String requesterName;
    private final UUID requesterUUID;

    public ClientboundPermissionNotifyPacket(String requesterName, UUID requesterUUID) {
        this.requesterName = requesterName;
        this.requesterUUID = requesterUUID;
    }

    public static void encode(ClientboundPermissionNotifyPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.requesterName, 48); // bounded to match decode limit
        buf.writeUUID(msg.requesterUUID);
    }

    public static ClientboundPermissionNotifyPacket decode(FriendlyByteBuf buf) {
        return new ClientboundPermissionNotifyPacket(buf.readUtf(48), buf.readUUID());
    }

    public static void handle(ClientboundPermissionNotifyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal(msg.requesterName + " is requesting access to your mirrors. Open a mirror to manage."),
                        false
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
