package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.CallLogData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → Server: clear all log entries or only missed-call entries. */
public class ServerboundClearCallHistoryPacket {

    private final boolean missedOnly;

    public ServerboundClearCallHistoryPacket(boolean missedOnly) {
        this.missedOnly = missedOnly;
    }

    public static void encode(ServerboundClearCallHistoryPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.missedOnly);
    }

    public static ServerboundClearCallHistoryPacket decode(FriendlyByteBuf buf) {
        return new ServerboundClearCallHistoryPacket(buf.readBoolean());
    }

    public static void handle(ServerboundClearCallHistoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CallLogData log = CallLogData.get(player.server);
            if (msg.missedOnly) {
                log.clearMissed(player.getUUID());
            } else {
                log.clearAll(player.getUUID());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
