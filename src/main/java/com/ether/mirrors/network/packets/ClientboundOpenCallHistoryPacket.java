package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.CallLogData;
import com.ether.mirrors.screen.CallHistoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server → Client: opens the Call History screen with the serialized log entries. */
public class ClientboundOpenCallHistoryPacket {

    private final List<CallLogData.LogEntry> entries;

    public ClientboundOpenCallHistoryPacket(List<CallLogData.LogEntry> entries) {
        this.entries = entries;
    }

    public static void encode(ClientboundOpenCallHistoryPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entries.size());
        for (CallLogData.LogEntry e : msg.entries) {
            buf.writeByte(e.type.id);
            buf.writeBoolean(e.outgoing);
            buf.writeUtf(e.playerName, 64);
            boolean hasUUID = e.playerUUID != null;
            buf.writeBoolean(hasUUID);
            if (hasUUID) buf.writeUUID(e.playerUUID);
            buf.writeLong(e.timestampMs);
            buf.writeInt(e.durationSeconds);
            buf.writeUtf(e.dimensionId, 128);
            buf.writeUtf(e.mirrorName, 64);
        }
    }

    public static ClientboundOpenCallHistoryPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > 10_000) {
            throw new io.netty.handler.codec.DecoderException(
                    "EthersMirrors: call history entry count out of bounds: " + count);
        }
        List<CallLogData.LogEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CallLogData.EventType type = CallLogData.EventType.fromId(buf.readByte() & 0xFF);
            boolean outgoing         = buf.readBoolean();
            String  playerName       = buf.readUtf(64);
            boolean hasUUID          = buf.readBoolean();
            UUID    playerUUID       = hasUUID ? buf.readUUID() : null;
            long    timestampMs      = buf.readLong();
            int     durationSeconds  = buf.readInt();
            String  dimensionId      = buf.readUtf(128);
            String  mirrorName       = buf.readUtf(64);
            entries.add(new CallLogData.LogEntry(
                    type, outgoing, playerName, playerUUID,
                    timestampMs, durationSeconds, dimensionId, mirrorName));
        }
        return new ClientboundOpenCallHistoryPacket(entries);
    }

    public static void handle(ClientboundOpenCallHistoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().setScreen(new CallHistoryScreen(msg.entries));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
