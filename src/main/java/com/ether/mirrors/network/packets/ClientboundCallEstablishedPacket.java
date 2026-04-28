package com.ether.mirrors.network.packets;

import com.ether.mirrors.init.MirrorsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundCallEstablishedPacket {

    private final UUID callId;
    private final String otherPlayerName;

    public ClientboundCallEstablishedPacket(UUID callId, String otherPlayerName) {
        this.callId = callId;
        this.otherPlayerName = otherPlayerName;
    }

    public static void encode(ClientboundCallEstablishedPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeUtf(msg.otherPlayerName);
    }

    public static ClientboundCallEstablishedPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCallEstablishedPacket(buf.readUUID(), buf.readUtf());
    }

    public static void handle(ClientboundCallEstablishedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            Minecraft.getInstance().player.playSound(MirrorsSounds.MIRROR_CONNECT.get(), 1.0F, 1.0F);
            ClientCallState.setActiveCall(msg.callId, msg.otherPlayerName);
            // Open active call screen (or update existing incoming screen)
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof com.ether.mirrors.screen.MirrorCallScreen callScreen)
                    || callScreen.getMode() == com.ether.mirrors.screen.MirrorCallScreen.Mode.INCOMING) {
                mc.setScreen(new com.ether.mirrors.screen.MirrorCallScreen(
                        com.ether.mirrors.screen.MirrorCallScreen.Mode.ACTIVE,
                        msg.otherPlayerName, msg.callId));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
