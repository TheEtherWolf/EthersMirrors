package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.CallLogData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: delete a single log entry.
 * The client sends the entry's index in the full (unfiltered, unsearched) list
 * that was received from the server via {@link ClientboundOpenCallHistoryPacket}.
 */
public class ServerboundDeleteCallHistoryEntryPacket {

    private final int index;

    public ServerboundDeleteCallHistoryEntryPacket(int index) {
        this.index = index;
    }

    public static void encode(ServerboundDeleteCallHistoryEntryPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.index);
    }

    public static ServerboundDeleteCallHistoryEntryPacket decode(FriendlyByteBuf buf) {
        return new ServerboundDeleteCallHistoryEntryPacket(buf.readInt());
    }

    public static void handle(ServerboundDeleteCallHistoryEntryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CallLogData.get(player.server).deleteEntry(player.getUUID(), msg.index);
        });
        ctx.get().setPacketHandled(true);
    }
}
