package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.network.MirrorsNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundPermissionRequestPacket {

    private final String targetPlayerName;

    public ServerboundPermissionRequestPacket(String targetPlayerName) {
        this.targetPlayerName = targetPlayerName;
    }

    public static void encode(ServerboundPermissionRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.targetPlayerName, 16);
    }

    public static ServerboundPermissionRequestPacket decode(FriendlyByteBuf buf) {
        return new ServerboundPermissionRequestPacket(buf.readUtf(16)); // Minecraft usernames are max 16 chars
    }

    public static void handle(ServerboundPermissionRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // Find target player by name
            ServerPlayer targetPlayer = sender.server.getPlayerList().getPlayerByName(msg.targetPlayerName);
            if (targetPlayer == null) {
                sender.displayClientMessage(Component.literal("Player not found or offline."), true);
                return;
            }

            if (targetPlayer.getUUID().equals(sender.getUUID())) {
                sender.displayClientMessage(Component.literal("You can't request permission from yourself."), true);
                return;
            }

            PermissionData permData = PermissionData.get(sender.server);

            // Check if already has permission
            if (permData.hasPermission(targetPlayer.getUUID(), sender.getUUID(), PermissionData.PermissionLevel.USE)) {
                sender.displayClientMessage(Component.literal("You already have access to " + msg.targetPlayerName + "'s mirrors."), true);
                return;
            }

            // Check if request already pending
            if (permData.getPendingRequests(targetPlayer.getUUID()).contains(sender.getUUID())) {
                sender.displayClientMessage(Component.literal("You already have a pending request to " + msg.targetPlayerName + "."), true);
                return;
            }

            // Add pending request
            permData.addPendingRequest(targetPlayer.getUUID(), sender.getUUID());

            // Notify the target player
            MirrorsNetwork.sendToPlayer(targetPlayer,
                    new ClientboundPermissionNotifyPacket(sender.getGameProfile().getName(), sender.getUUID()));

            sender.displayClientMessage(Component.literal("Permission request sent to " + msg.targetPlayerName + "."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
