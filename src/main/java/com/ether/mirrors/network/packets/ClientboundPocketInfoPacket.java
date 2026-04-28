package com.ether.mirrors.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundPocketInfoPacket {

    private final int currentSize;
    private final int maxSize;

    public ClientboundPocketInfoPacket(int currentSize, int maxSize) {
        this.currentSize = currentSize;
        this.maxSize = maxSize;
    }

    public static void encode(ClientboundPocketInfoPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.currentSize);
        buf.writeInt(msg.maxSize);
    }

    public static ClientboundPocketInfoPacket decode(FriendlyByteBuf buf) {
        return new ClientboundPocketInfoPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(ClientboundPocketInfoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // Pocket expansion UI is handled inline in MirrorManagementScreen; no standalone screen needed.
        ctx.get().setPacketHandled(true);
    }
}
