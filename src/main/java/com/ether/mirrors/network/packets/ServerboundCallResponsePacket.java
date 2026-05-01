package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.CallLogData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.voicechat.MirrorCallManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundCallResponsePacket {

    private final UUID callId;
    private final boolean accepted;

    public ServerboundCallResponsePacket(UUID callId, boolean accepted) {
        this.callId = callId;
        this.accepted = accepted;
    }

    public static void encode(ServerboundCallResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.callId);
        buf.writeBoolean(msg.accepted);
    }

    public static ServerboundCallResponsePacket decode(FriendlyByteBuf buf) {
        return new ServerboundCallResponsePacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ServerboundCallResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer responder = ctx.get().getSender();
            if (responder == null) return;

            MirrorCallManager callManager = MirrorCallManager.getInstance();
            MirrorCallManager.ActiveCall call = callManager.getCall(msg.callId);
            if (call == null) return;

            // Only the intended callee may accept or decline
            if (!responder.getUUID().equals(call.calleeUUID)) return;

            if (msg.accepted) {
                ServerPlayer caller = responder.server.getPlayerList().getPlayer(call.callerUUID);
                ServerPlayer callee = responder.server.getPlayerList().getPlayer(call.calleeUUID);

                if (caller != null && callee != null) {
                    callManager.acceptCall(msg.callId, caller, callee);

                    // Log call connected for both participants
                    CallLogData logData = CallLogData.get(caller.server);
                    logData.recordCallConnect(msg.callId);
                    // Persist connect time — actual entry written on call end

                    // Notify both players
                    MirrorsNetwork.sendToPlayer(caller, new ClientboundCallEstablishedPacket(call.callId, callee.getGameProfile().getName()));
                    MirrorsNetwork.sendToPlayer(callee, new ClientboundCallEstablishedPacket(call.callId, caller.getGameProfile().getName()));

                    // If Simple Voice Chat is not installed, tell both players voice won't work
                    if (!callManager.isSVCAvailable()) {
                        Component noVoiceMsg = Component.literal(
                                "[Mirror Call] Simple Voice Chat is not installed — this call is text-only. Voice is unavailable.");
                        caller.displayClientMessage(noVoiceMsg, false);
                        callee.displayClientMessage(noVoiceMsg, false);
                    }
                } else {
                    // One or both players went offline between ringing and accept — clean up
                    callManager.endCall(msg.callId, responder.server);
                    if (caller != null) {
                        caller.displayClientMessage(Component.literal("Call failed: the other player disconnected."), true);
                    }
                    responder.displayClientMessage(Component.literal("Call failed: the other player disconnected."), true);
                }
            } else {
                // Call declined — notify caller via packet so their call UI closes, then via message
                ServerPlayer caller = responder.server.getPlayerList().getPlayer(call.callerUUID);
                callManager.endCall(msg.callId, responder.server);
                // Log declined for both parties
                CallLogData logData = CallLogData.get(responder.server);
                String callerName   = caller != null ? caller.getGameProfile().getName() : call.callerUUID.toString();
                logData.recordDeclinedCall(responder.getUUID(), false, callerName, call.callerUUID);
                if (caller != null) {
                    logData.recordDeclinedCall(caller.getUUID(), true, responder.getGameProfile().getName(), responder.getUUID());
                    caller.displayClientMessage(Component.literal(responder.getGameProfile().getName() + " declined your call."), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
