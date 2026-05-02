package com.ether.mirrors.network.packets;

import com.ether.mirrors.compat.XaeroMinimapCompat;
import com.ether.mirrors.screen.MirrorSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundMirrorListPacket {

    private final List<MirrorInfo> mirrors;
    private final BlockPos sourceMirrorPos;

    public static class MirrorInfo {
        public final UUID mirrorId;
        public final UUID ownerUUID;
        public final BlockPos pos;
        public final String dimensionName;
        public final String tierName;
        public final String typeName;
        public final String ownerName;
        public final boolean isOwn;
        public final boolean inRange;
        public final double signalStrength;
        public final String mirrorName;
        public final boolean isFavorite;
        public final String folderName;
        public final byte[] iconPixels;

        public MirrorInfo(UUID mirrorId, UUID ownerUUID, BlockPos pos, String dimensionName, String tierName,
                          String typeName, String ownerName, boolean isOwn, boolean inRange,
                          double signalStrength, String mirrorName) {
            this(mirrorId, ownerUUID, pos, dimensionName, tierName, typeName, ownerName,
                    isOwn, inRange, signalStrength, mirrorName, false, "", new byte[256]);
        }

        public MirrorInfo(UUID mirrorId, UUID ownerUUID, BlockPos pos, String dimensionName, String tierName,
                          String typeName, String ownerName, boolean isOwn, boolean inRange,
                          double signalStrength, String mirrorName, boolean isFavorite, String folderName) {
            this(mirrorId, ownerUUID, pos, dimensionName, tierName, typeName, ownerName,
                    isOwn, inRange, signalStrength, mirrorName, isFavorite, folderName, new byte[256]);
        }

        public MirrorInfo(UUID mirrorId, UUID ownerUUID, BlockPos pos, String dimensionName, String tierName,
                          String typeName, String ownerName, boolean isOwn, boolean inRange,
                          double signalStrength, String mirrorName, boolean isFavorite, String folderName,
                          byte[] iconPixels) {
            this.mirrorId = mirrorId;
            this.ownerUUID = ownerUUID;
            this.pos = pos;
            this.dimensionName = dimensionName;
            this.tierName = tierName;
            this.typeName = typeName;
            this.ownerName = ownerName;
            this.isOwn = isOwn;
            this.inRange = inRange;
            this.signalStrength = signalStrength;
            this.mirrorName = mirrorName != null ? mirrorName : "";
            this.isFavorite = isFavorite;
            this.folderName = folderName != null ? folderName : "";
            this.iconPixels = iconPixels != null ? iconPixels : new byte[256];
        }
    }

    // sourceMirrorType: "teleport", "calling", or "pocket". Used to render correct action buttons.
    // isHandheld: true when opened from item in hand rather than a placed mirror.
    // warpTargetMode: true when the player is selecting a warp target destination.
    // cooldownRemainingMs: milliseconds left on the player's teleport cooldown (0 = no cooldown).
    private final String sourceMirrorType;
    private final boolean isHandheld;
    public final boolean warpTargetMode;
    public final long cooldownRemainingMs;

    public ClientboundMirrorListPacket(List<MirrorInfo> mirrors, BlockPos sourceMirrorPos,
                                        String sourceMirrorType, boolean isHandheld) {
        this(mirrors, sourceMirrorPos, sourceMirrorType, isHandheld, false, 0L);
    }

    public ClientboundMirrorListPacket(List<MirrorInfo> mirrors, BlockPos sourceMirrorPos,
                                        String sourceMirrorType, boolean isHandheld, boolean warpTargetMode) {
        this(mirrors, sourceMirrorPos, sourceMirrorType, isHandheld, warpTargetMode, 0L);
    }

    public ClientboundMirrorListPacket(List<MirrorInfo> mirrors, BlockPos sourceMirrorPos,
                                        String sourceMirrorType, boolean isHandheld, boolean warpTargetMode,
                                        long cooldownRemainingMs) {
        this.mirrors = mirrors;
        this.sourceMirrorPos = sourceMirrorPos;
        this.sourceMirrorType = sourceMirrorType;
        this.isHandheld = isHandheld;
        this.warpTargetMode = warpTargetMode;
        this.cooldownRemainingMs = cooldownRemainingMs;
    }

    public static void encode(ClientboundMirrorListPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.sourceMirrorPos);
        buf.writeUtf(msg.sourceMirrorType);
        buf.writeBoolean(msg.isHandheld);
        buf.writeInt(msg.mirrors.size());
        for (MirrorInfo info : msg.mirrors) {
            buf.writeUUID(info.mirrorId);
            buf.writeUUID(info.ownerUUID);
            buf.writeBlockPos(info.pos);
            buf.writeUtf(info.dimensionName);
            buf.writeUtf(info.tierName);
            buf.writeUtf(info.typeName);
            buf.writeUtf(info.ownerName);
            buf.writeBoolean(info.isOwn);
            buf.writeBoolean(info.inRange);
            buf.writeDouble(info.signalStrength);
            buf.writeUtf(info.mirrorName);
            buf.writeBoolean(info.isFavorite);
            buf.writeUtf(info.folderName, 64);
            buf.writeBytes(info.iconPixels != null ? info.iconPixels : new byte[256]);
        }
        buf.writeBoolean(msg.warpTargetMode);
        buf.writeLong(msg.cooldownRemainingMs);
    }

    public static ClientboundMirrorListPacket decode(FriendlyByteBuf buf) {
        BlockPos sourcePos = buf.readBlockPos();
        String sourceMirrorType = buf.readUtf(16);
        boolean isHandheld = buf.readBoolean();
        int size = buf.readInt();
        if (size < 0 || size > 10_000) {
            throw new io.netty.handler.codec.DecoderException("EthersMirrors: mirror list size out of bounds: " + size);
        }
        List<MirrorInfo> mirrors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            mirrors.add(new MirrorInfo(
                    buf.readUUID(),
                    buf.readUUID(),
                    buf.readBlockPos(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readUtf(64),
                    readIconPixels(buf)
            ));
        }
        boolean warpTargetMode = buf.readBoolean();
        long cooldownRemainingMs = buf.readLong();
        return new ClientboundMirrorListPacket(mirrors, sourcePos, sourceMirrorType, isHandheld, warpTargetMode, cooldownRemainingMs);
    }

    private static byte[] readIconPixels(FriendlyByteBuf buf) {
        byte[] pixels = new byte[256];
        buf.readBytes(pixels);
        return pixels;
    }

    public static void handle(ClientboundMirrorListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Opportunistically sync waypoints whenever the mirror list is received
            if (!msg.mirrors.isEmpty()) {
                java.util.List<com.ether.mirrors.network.packets.ClientboundSyncWaypointsPacket.WaypointData>
                        wpData = new java.util.ArrayList<>();
                for (MirrorInfo info : msg.mirrors) {
                    wpData.add(new com.ether.mirrors.network.packets.ClientboundSyncWaypointsPacket.WaypointData(
                            info.mirrorId, info.mirrorName,
                            info.pos.getX(), info.pos.getY(), info.pos.getZ(),
                            info.dimensionName, info.typeName));
                }
                XaeroMinimapCompat.syncWaypoints(wpData);
            }
            Minecraft.getInstance().setScreen(
                    new MirrorSelectionScreen(msg.mirrors, msg.sourceMirrorPos,
                            msg.sourceMirrorType, msg.isHandheld, msg.warpTargetMode, msg.cooldownRemainingMs));
        });
        ctx.get().setPacketHandled(true);
    }
}
