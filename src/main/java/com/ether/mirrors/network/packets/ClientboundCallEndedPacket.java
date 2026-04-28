package com.ether.mirrors.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundCallEndedPacket {

    private final java.util.UUID callId;

    public ClientboundCallEndedPacket(java.util.UUID callId) {
        this.callId = callId;
    }

    public static void encode(ClientboundCallEndedPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
    }

    public static ClientboundCallEndedPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCallEndedPacket(buf.readUUID());
    }

    public static void handle(ClientboundCallEndedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Guard: only clear state if this packet matches the call the client currently
            // tracks. A delayed END packet from a previous call must not close a new call's
            // screen that opened in the meantime.
            boolean matchesActive   = msg.callId.equals(ClientCallState.getActiveCallId());
            boolean matchesIncoming = msg.callId.equals(ClientCallState.getIncomingCallId());
            if (!matchesActive && !matchesIncoming) return;

            ClientCallState.clearActiveCall();
            ClientCallState.clearIncomingCall();
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof com.ether.mirrors.screen.MirrorCallScreen) {
                mc.setScreen(null);
            }
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Mirror call ended."), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
