package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.util.RangeHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ServerboundCameraViewPacket {

    /** Minimum milliseconds between camera-view requests from the same player. */
    private static final long CAMERA_RATE_LIMIT_MS = 500L;
    private static final Map<UUID, Long> lastCameraRequest = new ConcurrentHashMap<>();

    private final UUID targetMirrorId;

    public ServerboundCameraViewPacket(UUID targetMirrorId) {
        this.targetMirrorId = targetMirrorId;
    }

    public static void encode(ServerboundCameraViewPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetMirrorId);
    }

    public static ServerboundCameraViewPacket decode(FriendlyByteBuf buf) {
        return new ServerboundCameraViewPacket(buf.readUUID());
    }

    public static void handle(ServerboundCameraViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Rate-limit: prevent clients from spamming this to cause repeated permission
            // checks and player-list lookups on the server main thread.
            // Check BEFORE updating so a sustained flood doesn't reset the window and
            // permanently block the player's own legitimate requests.
            long now = System.currentTimeMillis();
            Long last = lastCameraRequest.get(player.getUUID());
            if (last != null && now - last < CAMERA_RATE_LIMIT_MS) return;
            lastCameraRequest.put(player.getUUID(), now);

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            MirrorNetworkData.MirrorEntry target = networkData.getMirrorById(msg.targetMirrorId);
            if (target == null) return;

            // Check permission
            boolean isOwner = target.ownerUUID.equals(player.getUUID());
            if (!isOwner && !permData.hasPermission(target.ownerUUID, player.getUUID(), PermissionData.PermissionLevel.VIEW_CAMERA)) {
                return;
            }

            // Find a player near the target mirror to use as camera, or find the mirror owner
            ServerPlayer cameraTarget = null;

            // For wall-placed calling mirrors, we want to view from the mirror's perspective
            // Use the mirror owner as camera target if they're online
            if (!isOwner) {
                cameraTarget = player.server.getPlayerList().getPlayer(target.ownerUUID);
            }

            if (cameraTarget != null) {
                // Calculate signal strength between viewer and target mirror
                double signal = RangeHelper.getSignalStrength(
                        target.tier, target.tier,
                        player.blockPosition(), player.level().dimension(),
                        target.pos, target.dimension
                );

                // Notify mirror owner that someone is viewing their camera (action bar, not chat)
                if (!isOwner) {
                    cameraTarget.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    player.getGameProfile().getName() + " is viewing your mirror."),
                            true);
                }

                // Send vanilla camera swap packet
                player.connection.send(new ClientboundSetCameraPacket(cameraTarget));

                // Notify client to enable overlay with signal strength for static effect
                MirrorsNetwork.sendToPlayer(player, new ClientboundStartCameraPacket(
                        cameraTarget.getGameProfile().getName(), signal));
            } else if (isOwner) {
                // Owner viewing their own mirror — use themselves as camera (signal = 1.0)
                player.connection.send(new ClientboundSetCameraPacket(player));
                MirrorsNetwork.sendToPlayer(player, new ClientboundStartCameraPacket(
                        player.getGameProfile().getName(), 1.0));
            } else {
                // Target player is offline — notify viewer
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Mirror owner is offline — camera unavailable."), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
