package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundToggleFavoritePacket {

    private final UUID mirrorId;

    public ServerboundToggleFavoritePacket(UUID mirrorId) {
        this.mirrorId = mirrorId;
    }

    public static void encode(ServerboundToggleFavoritePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.mirrorId);
    }

    public static ServerboundToggleFavoritePacket decode(FriendlyByteBuf buf) {
        return new ServerboundToggleFavoritePacket(buf.readUUID());
    }

    public static void handle(ServerboundToggleFavoritePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(msg.mirrorId);
            if (entry == null) return;
            // Guard: only allow favoriting mirrors the player owns or has permission to use.
            // Without this check, a client could probe arbitrary UUIDs to enumerate all mirrors.
            if (!entry.ownerUUID.equals(player.getUUID())) {
                PermissionData permData = PermissionData.get(player.server);
                if (!permData.canPlayerUseMirror(player.getUUID(), entry.mirrorId,
                        entry.ownerUUID, PermissionData.PermissionLevel.USE, entry.privacyLocked)) {
                    return;
                }
            }
            networkData.toggleFavorite(player.getUUID(), msg.mirrorId);
        });
        ctx.get().setPacketHandled(true);
    }
}
