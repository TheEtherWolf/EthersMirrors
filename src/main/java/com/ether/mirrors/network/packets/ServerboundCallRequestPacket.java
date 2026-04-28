package com.ether.mirrors.network.packets;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.voicechat.MirrorCallManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundCallRequestPacket {

    private final UUID targetPlayerUUID;

    public ServerboundCallRequestPacket(UUID targetPlayerUUID) {
        this.targetPlayerUUID = targetPlayerUUID;
    }

    public static void encode(ServerboundCallRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetPlayerUUID);
    }

    public static ServerboundCallRequestPacket decode(FriendlyByteBuf buf) {
        return new ServerboundCallRequestPacket(buf.readUUID());
    }

    public static void handle(ServerboundCallRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer caller = ctx.get().getSender();
            if (caller == null) return;

            ServerPlayer callee = caller.server.getPlayerList().getPlayer(msg.targetPlayerUUID);
            if (callee == null) {
                caller.displayClientMessage(Component.literal("Player is offline."), true);
                return;
            }

            // Calling mirrors work like a phone — no prior permission needed to ring someone.
            // The callee can accept or decline. Self-call auto-accepts.
            boolean isSelfCall = caller.getUUID().equals(callee.getUUID());

            MirrorCallManager callManager = MirrorCallManager.getInstance();

            if (callManager.isInCall(caller.getUUID())) {
                caller.displayClientMessage(Component.literal("You are already in a call."), true);
                return;
            }

            // Self-call: not allowed
            if (isSelfCall) {
                caller.displayClientMessage(Component.literal("You can't call yourself."), true);
                return;
            }

            MirrorCallManager.ActiveCall call = callManager.initiateCall(caller.getUUID(), callee.getUUID());
            if (call == null) {
                caller.displayClientMessage(Component.literal("That player is already in a call."), true);
                return;
            }

            // Fire API event
            com.ether.mirrors.api.event.MirrorCallEvent callEvent = new com.ether.mirrors.api.event.MirrorCallEvent(caller.getUUID(), callee.getUUID());
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(callEvent)) {
                callManager.endCall(call.callId);
                return;
            }

            com.ether.mirrors.advancement.MirrorsTriggers.MIRROR_CALL_MADE.trigger(caller);

            // Notify the callee
            MirrorsNetwork.sendToPlayer(callee, new ClientboundCallIncomingPacket(
                    call.callId, caller.getGameProfile().getName(), caller.getUUID()));

            caller.displayClientMessage(Component.literal("Calling " + callee.getGameProfile().getName() + "..."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
