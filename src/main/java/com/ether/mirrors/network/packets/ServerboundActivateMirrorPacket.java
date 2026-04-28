package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundActivateMirrorPacket {

    private final BlockPos mirrorPos;

    public ServerboundActivateMirrorPacket(BlockPos mirrorPos) {
        this.mirrorPos = mirrorPos;
    }

    public static void encode(ServerboundActivateMirrorPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
    }

    public static ServerboundActivateMirrorPacket decode(FriendlyByteBuf buf) {
        return new ServerboundActivateMirrorPacket(buf.readBlockPos());
    }

    public static void handle(ServerboundActivateMirrorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5, msg.mirrorPos.getZ() + 0.5) > 64) return;
            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;
            if (mirrorBE.isActivated()) return;
            mirrorBE.setActivated(true);
            player.playSound(com.ether.mirrors.init.MirrorsSounds.MIRROR_TELEPORT.get(), 0.6F, 1.2F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Mirror activated."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
