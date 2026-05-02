package com.ether.mirrors.network.packets;

import com.ether.mirrors.compat.JourneyMapCompat;
import com.ether.mirrors.compat.XaeroMinimapCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundSyncWaypointsPacket {

    public static class WaypointData {
        public final UUID   mirrorId;
        public final String name;
        public final int    x, y, z;
        public final String dimension;
        public final String typeName;
        // Rich metadata for premium compat displays
        public final double signalStrength; // 0.0–1.0 (0 = out of range / cross-dim unsupported)
        public final String tierName;       // "wood", "stone", "iron", "gold", "diamond", "netherite"
        public final String ownerName;      // display name of the mirror's owner
        public final boolean isOwn;         // true when the receiving player owns this mirror
        public final int    rangeBlocks;    // effective max range in blocks at this tier

        /** Legacy constructor — fills rich fields with neutral defaults. */
        public WaypointData(UUID mirrorId, String name, int x, int y, int z,
                            String dimension, String typeName) {
            this(mirrorId, name, x, y, z, dimension, typeName, 0.0, "iron", "", false, 256);
        }

        public WaypointData(UUID mirrorId, String name, int x, int y, int z,
                            String dimension, String typeName,
                            double signalStrength, String tierName, String ownerName,
                            boolean isOwn, int rangeBlocks) {
            this.mirrorId       = mirrorId;
            this.name           = name          != null ? name          : "";
            this.x              = x;
            this.y              = y;
            this.z              = z;
            this.dimension      = dimension     != null ? dimension     : "";
            this.typeName       = typeName      != null ? typeName      : "";
            this.signalStrength = Math.max(0.0, Math.min(1.0, signalStrength));
            this.tierName       = tierName      != null ? tierName      : "iron";
            this.ownerName      = ownerName     != null ? ownerName     : "";
            this.isOwn          = isOwn;
            this.rangeBlocks    = Math.max(0, rangeBlocks);
        }
    }

    private final List<WaypointData> waypoints;

    public ClientboundSyncWaypointsPacket(List<WaypointData> waypoints) {
        this.waypoints = waypoints;
    }

    public static void encode(ClientboundSyncWaypointsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.waypoints.size());
        for (WaypointData wp : msg.waypoints) {
            buf.writeUUID(wp.mirrorId);
            buf.writeUtf(wp.name, 64);
            buf.writeInt(wp.x);
            buf.writeInt(wp.y);
            buf.writeInt(wp.z);
            buf.writeUtf(wp.dimension, 128);
            buf.writeUtf(wp.typeName, 16);
            buf.writeDouble(wp.signalStrength);
            buf.writeUtf(wp.tierName, 16);
            buf.writeUtf(wp.ownerName, 40);
            buf.writeBoolean(wp.isOwn);
            buf.writeInt(wp.rangeBlocks);
        }
    }

    public static ClientboundSyncWaypointsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > 10_000) {
            throw new io.netty.handler.codec.DecoderException(
                    "EthersMirrors: waypoint list size out of bounds: " + size);
        }
        List<WaypointData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new WaypointData(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(128),
                    buf.readUtf(16),
                    buf.readDouble(),
                    buf.readUtf(16),
                    buf.readUtf(40),
                    buf.readBoolean(),
                    buf.readInt()
            ));
        }
        return new ClientboundSyncWaypointsPacket(list);
    }

    public static void handle(ClientboundSyncWaypointsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(ClientboundSyncWaypointsPacket msg) {
        XaeroMinimapCompat.syncWaypoints(msg.waypoints);
        JourneyMapCompat.syncWaypoints(msg.waypoints);
    }
}
