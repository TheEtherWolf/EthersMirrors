package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundSetMirrorOverridePacket {

    private final UUID mirrorId;
    private final String targetPlayerName;
    private final String action; // "allow", "block", "remove_allow", "remove_block"

    public ServerboundSetMirrorOverridePacket(UUID mirrorId, String targetPlayerName, String action) {
        this.mirrorId = mirrorId;
        this.targetPlayerName = targetPlayerName;
        this.action = action;
    }

    public static void encode(ServerboundSetMirrorOverridePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.mirrorId);
        buf.writeUtf(msg.targetPlayerName, 16); // Minecraft usernames are max 16 chars
        buf.writeUtf(msg.action, 16);
    }

    public static ServerboundSetMirrorOverridePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetMirrorOverridePacket(buf.readUUID(), buf.readUtf(16), buf.readUtf(16));
    }

    public static void handle(ServerboundSetMirrorOverridePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.ether.mirrors.data.MirrorNetworkData networkData = com.ether.mirrors.data.MirrorNetworkData.get(player.server);
            com.ether.mirrors.data.MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(msg.mirrorId);
            if (entry == null || entry.ownerUUID == null || !entry.ownerUUID.equals(player.getUUID())) return;

            // Resolve target player UUID — check online first, then profile cache for offline players
            UUID targetUUID;
            var online = player.server.getPlayerList().getPlayerByName(msg.targetPlayerName);
            if (online != null) {
                targetUUID = online.getUUID();
            } else {
                var cached = player.server.getProfileCache().get(msg.targetPlayerName);
                if (cached.isEmpty()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Player \"" + msg.targetPlayerName + "\" has not joined this server."), true);
                    return;
                }
                targetUUID = cached.get().getId();
            }

            // Cannot override yourself
            if (targetUUID.equals(player.getUUID())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "You cannot add yourself to the list."), true);
                return;
            }

            PermissionData permData = PermissionData.get(player.server);
            switch (msg.action) {
                case "allow" -> permData.addMirrorAllowEntry(msg.mirrorId, targetUUID, PermissionData.PermissionLevel.USE);
                case "block" -> permData.addMirrorBlockEntry(msg.mirrorId, targetUUID);
                case "remove_allow" -> permData.removeMirrorAllowEntry(msg.mirrorId, targetUUID);
                case "remove_block" -> permData.removeMirrorBlockEntry(msg.mirrorId, targetUUID);
                default -> com.ether.mirrors.EthersMirrors.LOGGER.warn(
                        "[EthersMirrors] Unknown mirror override action '{}' from player {}",
                        msg.action, player.getGameProfile().getName());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
