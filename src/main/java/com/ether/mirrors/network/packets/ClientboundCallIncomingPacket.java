package com.ether.mirrors.network.packets;

import com.ether.mirrors.init.MirrorsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundCallIncomingPacket {

    private final UUID callId;
    private final String callerName;
    private final UUID callerUUID;

    public ClientboundCallIncomingPacket(UUID callId, String callerName, UUID callerUUID) {
        this.callId = callId;
        this.callerName = callerName;
        this.callerUUID = callerUUID;
    }

    public static void encode(ClientboundCallIncomingPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeUtf(msg.callerName);
        buf.writeUUID(msg.callerUUID);
    }

    public static ClientboundCallIncomingPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCallIncomingPacket(buf.readUUID(), buf.readUtf(), buf.readUUID());
    }

    public static void handle(ClientboundCallIncomingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            Minecraft mc = Minecraft.getInstance();
            mc.player.playSound(MirrorsSounds.MIRROR_RING.get(), 1.0F, 1.0F);
            ClientCallState.setIncomingCall(msg.callId, msg.callerName, msg.callerUUID);
            // Save whatever screen was open so we can restore it if the call is declined
            com.ether.mirrors.screen.MirrorCallScreen.previousScreen = mc.screen;
            mc.setScreen(new com.ether.mirrors.screen.MirrorCallScreen(
                    com.ether.mirrors.screen.MirrorCallScreen.Mode.INCOMING,
                    msg.callerName, msg.callId));
        });
        ctx.get().setPacketHandled(true);
    }
}
