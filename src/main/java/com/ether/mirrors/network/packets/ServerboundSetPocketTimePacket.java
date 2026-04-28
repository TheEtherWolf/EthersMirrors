package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PocketDimensionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundSetPocketTimePacket {

    private final long fixedTime; // -1 = unlock/normal

    public ServerboundSetPocketTimePacket(long fixedTime) {
        this.fixedTime = fixedTime;
    }

    public static void encode(ServerboundSetPocketTimePacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.fixedTime);
    }

    public static ServerboundSetPocketTimePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetPocketTimePacket(buf.readLong());
    }

    public static void handle(ServerboundSetPocketTimePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PocketDimensionData pocketData = PocketDimensionData.get(player.server);
            PocketDimensionData.PocketRegion region = pocketData.getRegion(player.getUUID());
            if (region == null) return;
            // -1 = unlocked; 0-23999 = valid Minecraft day cycle ticks; reject anything else
            if (msg.fixedTime != -1 && (msg.fixedTime < 0 || msg.fixedTime >= 24000)) return;
            region.fixedTime = msg.fixedTime;
            pocketData.setDirty();
            // Send client-side time override to the player only (not the entire level)
            if (msg.fixedTime >= 0) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                        player.level().getGameTime(), -msg.fixedTime, false));
            }
            String msg2 = msg.fixedTime == -1 ? "Pocket time unlocked." : "Pocket time set.";
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg2), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
