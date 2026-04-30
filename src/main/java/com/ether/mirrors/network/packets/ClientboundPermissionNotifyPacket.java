package com.ether.mirrors.network.packets;

import com.ether.mirrors.screen.PermissionRequestModal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new PermissionRequestModal(msg.requesterName, msg.requesterUUID));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
