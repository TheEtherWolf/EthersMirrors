package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.CallLogData;
import com.ether.mirrors.network.MirrorsNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → Server: open the call history screen. Server responds with the log data. */
public class ServerboundOpenCallHistoryPacket {

    public static void encode(ServerboundOpenCallHistoryPacket msg, FriendlyByteBuf buf) {}

    public static ServerboundOpenCallHistoryPacket decode(FriendlyByteBuf buf) {
        return new ServerboundOpenCallHistoryPacket();
    }

    public static void handle(ServerboundOpenCallHistoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var entries = CallLogData.get(player.server).getEntries(player.getUUID());
            MirrorsNetwork.sendToPlayer(player, new ClientboundOpenCallHistoryPacket(entries));
        });
        ctx.get().setPacketHandled(true);
    }
}
