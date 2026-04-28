package com.ether.mirrors.network.packets;

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
        public final UUID mirrorId;
        public final String name;
        public final int x, y, z;
        public final String dimension;
        public final String typeName;

        public WaypointData(UUID mirrorId, String name, int x, int y, int z,
                            String dimension, String typeName) {
            this.mirrorId = mirrorId;
            this.name = name != null ? name : "";
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.typeName = typeName;
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
        }
    }

    public static ClientboundSyncWaypointsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > 10_000) {
            throw new io.netty.handler.codec.DecoderException("EthersMirrors: waypoint list size out of bounds: " + size);
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
                    buf.readUtf(16)
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
    }
}
