package com.ether.mirrors.network.packets;

import com.ether.mirrors.screen.CameraOverlayRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundStartCameraPacket {

    private final String viewingPlayerName;
    private final double signalStrength;

    public ClientboundStartCameraPacket(String viewingPlayerName, double signalStrength) {
        this.viewingPlayerName = viewingPlayerName;
        this.signalStrength = signalStrength;
    }

    public static void encode(ClientboundStartCameraPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.viewingPlayerName);
        buf.writeDouble(msg.signalStrength);
    }

    public static ClientboundStartCameraPacket decode(FriendlyByteBuf buf) {
        return new ClientboundStartCameraPacket(buf.readUtf(48), buf.readDouble());
    }

    public static void handle(ClientboundStartCameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CameraOverlayRenderer.startCameraView(msg.viewingPlayerName, msg.signalStrength);
        });
        ctx.get().setPacketHandled(true);
    }
}
