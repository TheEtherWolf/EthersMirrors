package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.CallLogData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.voicechat.MirrorCallManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundCallEndPacket {

    private final UUID callId;

    public ServerboundCallEndPacket(UUID callId) {
        this.callId = callId;
    }

    public static void encode(ServerboundCallEndPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
    }

    public static ServerboundCallEndPacket decode(FriendlyByteBuf buf) {
        return new ServerboundCallEndPacket(buf.readUUID());
    }

    public static void handle(ServerboundCallEndPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            MirrorCallManager callManager = MirrorCallManager.getInstance();
            MirrorCallManager.ActiveCall call = callManager.getCall(msg.callId);
            if (call == null) return;

            // Auth check: only participants can end a call
            if (!call.callerUUID.equals(player.getUUID()) && !call.calleeUUID.equals(player.getUUID())) return;

            UUID otherUUID = call.callerUUID.equals(player.getUUID()) ? call.calleeUUID : call.callerUUID;
            ServerPlayer otherPlayer = player.server.getPlayerList().getPlayer(otherUUID);

            // Log call end with duration for both participants (only if call was connected)
            if (call.getState() == MirrorCallManager.CallState.CONNECTED) {
                CallLogData logData = CallLogData.get(player.server);
                boolean playerWasCaller = call.callerUUID.equals(player.getUUID());
                String otherName = otherPlayer != null
                        ? otherPlayer.getGameProfile().getName()
                        : otherUUID.toString();
                String dimId = player.level().dimension().location().toString();
                logData.recordCallEnd(player.getUUID(), msg.callId, playerWasCaller, otherName, otherUUID, dimId, false);
                if (otherPlayer != null) {
                    logData.recordCallEnd(otherPlayer.getUUID(), msg.callId, !playerWasCaller,
                            player.getGameProfile().getName(), player.getUUID(),
                            otherPlayer.level().dimension().location().toString(), true);
                }
            }

            callManager.endCall(msg.callId);

            // Notify the other player's client
            if (otherPlayer != null) {
                com.ether.mirrors.network.MirrorsNetwork.sendToPlayer(otherPlayer, new ClientboundCallEndedPacket(msg.callId));
            }
            // Also clear our own client state
            com.ether.mirrors.network.MirrorsNetwork.sendToPlayer(player, new ClientboundCallEndedPacket(msg.callId));
        });
        ctx.get().setPacketHandled(true);
    }
}
