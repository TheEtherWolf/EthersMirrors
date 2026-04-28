package com.ether.mirrors.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundMirrorMessagePacket {

    private final String message;

    public ClientboundMirrorMessagePacket(String message) {
        this.message = message;
    }

    public static void encode(ClientboundMirrorMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.message, 512);
    }

    public static ClientboundMirrorMessagePacket decode(FriendlyByteBuf buf) {
        return new ClientboundMirrorMessagePacket(buf.readUtf(512));
    }

    public static void handle(ClientboundMirrorMessagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(msg.message)
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
