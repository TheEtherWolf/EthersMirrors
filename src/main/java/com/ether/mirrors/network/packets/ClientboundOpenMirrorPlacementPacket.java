package com.ether.mirrors.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundOpenMirrorPlacementPacket {

    private final BlockPos mirrorPos;
    private final String mirrorType; // "teleport", "calling", "pocket", "beacon"
    private final String dimension;  // e.g. "minecraft:overworld"
    private final int posX, posY, posZ;

    public ClientboundOpenMirrorPlacementPacket(BlockPos mirrorPos, String mirrorType, String dimension, int posX, int posY, int posZ) {
        this.mirrorPos = mirrorPos;
        this.mirrorType = mirrorType;
        this.dimension = dimension;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public static void encode(ClientboundOpenMirrorPlacementPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
        buf.writeUtf(msg.mirrorType, 32);
        buf.writeUtf(msg.dimension, 256);
        buf.writeInt(msg.posX);
        buf.writeInt(msg.posY);
        buf.writeInt(msg.posZ);
    }

    public static ClientboundOpenMirrorPlacementPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String type = buf.readUtf(32);
        String dim = buf.readUtf(256);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        return new ClientboundOpenMirrorPlacementPacket(pos, type, dim, x, y, z);
    }

    public static void handle(ClientboundOpenMirrorPlacementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new com.ether.mirrors.screen.MirrorPlacementScreen(
                    msg.mirrorPos, msg.mirrorType, msg.dimension, msg.posX, msg.posY, msg.posZ));
        });
        ctx.get().setPacketHandled(true);
    }
}
