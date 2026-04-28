package com.ether.mirrors.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent from server to caller when a call is initiated.
 * Opens a "Calling…" screen for the caller so they have visible feedback and a Cancel button.
 */
public class ClientboundCallRingingPacket {

    private final UUID callId;
    private final String calleeName;

    public ClientboundCallRingingPacket(UUID callId, String calleeName) {
        this.callId = callId;
        this.calleeName = calleeName;
    }

    public static void encode(ClientboundCallRingingPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeUtf(msg.calleeName);
    }

    public static ClientboundCallRingingPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCallRingingPacket(buf.readUUID(), buf.readUtf());
    }

    public static void handle(ClientboundCallRingingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            Minecraft mc = Minecraft.getInstance();
            ClientCallState.setOutgoingCall(msg.callId, msg.calleeName);
            // Save whatever screen was open so we can restore it if the call is cancelled
            com.ether.mirrors.screen.MirrorCallScreen.previousScreen = mc.screen;
            mc.setScreen(new com.ether.mirrors.screen.MirrorCallScreen(
                    com.ether.mirrors.screen.MirrorCallScreen.Mode.OUTGOING,
                    msg.calleeName, msg.callId));
        });
        ctx.get().setPacketHandled(true);
    }
}
