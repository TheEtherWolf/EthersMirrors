package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundSetMirrorAccessModePacket {

    private final UUID mirrorId;
    private final PermissionData.AccessMode mode;

    public ServerboundSetMirrorAccessModePacket(UUID mirrorId, PermissionData.AccessMode mode) {
        this.mirrorId = mirrorId;
        this.mode = mode;
    }

    public static void encode(ServerboundSetMirrorAccessModePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.mirrorId);
        buf.writeUtf(msg.mode.getId());
    }

    public static ServerboundSetMirrorAccessModePacket decode(FriendlyByteBuf buf) {
        UUID mirrorId = buf.readUUID();
        PermissionData.AccessMode mode = PermissionData.AccessMode.fromId(buf.readUtf(16));
        return new ServerboundSetMirrorAccessModePacket(mirrorId, mode);
    }

    public static void handle(ServerboundSetMirrorAccessModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.mode == null) return; // fromId() returned null for an unrecognized mode string

            com.ether.mirrors.data.MirrorNetworkData networkData = com.ether.mirrors.data.MirrorNetworkData.get(player.server);
            com.ether.mirrors.data.MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(msg.mirrorId);
            if (entry == null || entry.ownerUUID == null || !entry.ownerUUID.equals(player.getUUID())) return;

            PermissionData permData = PermissionData.get(player.server);
            permData.setMirrorAccessMode(msg.mirrorId, msg.mode);
        });
        ctx.get().setPacketHandled(true);
    }
}
