package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PocketDimensionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundSetPocketWeatherPacket {

    private final String weather; // "normal", "clear", "rain", "thunder"

    public ServerboundSetPocketWeatherPacket(String weather) {
        this.weather = weather;
    }

    public static void encode(ServerboundSetPocketWeatherPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.weather, 16);
    }

    public static ServerboundSetPocketWeatherPacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetPocketWeatherPacket(buf.readUtf(16));
    }

    public static void handle(ServerboundSetPocketWeatherPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Validate weather string to prevent arbitrary data from client
            if (!msg.weather.equals("normal") && !msg.weather.equals("clear")
                    && !msg.weather.equals("rain") && !msg.weather.equals("thunder")) {
                return;
            }
            PocketDimensionData pocketData = PocketDimensionData.get(player.server);
            PocketDimensionData.PocketRegion region = pocketData.getRegion(player.getUUID());
            if (region == null) return;
            region.weather = msg.weather;
            pocketData.setDirty();
            // Send client-side weather override to the player only (not the entire level)
            // Weather is visual-only per-player via game event packets
            boolean thunder = "thunder".equals(msg.weather);
            boolean rain = "rain".equals(msg.weather) || thunder;
            float rainLevel = rain ? 1.0F : 0.0F;
            float thunderLevel = thunder ? 1.0F : 0.0F;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    rain ? net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING
                         : net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rainLevel));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunderLevel));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Pocket weather set to " + msg.weather + "."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
