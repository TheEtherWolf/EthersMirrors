package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.item.MirrorUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundWarpLockTogglePacket {

    private final BlockPos mirrorPos;
    private final boolean locked;

    public ServerboundWarpLockTogglePacket(BlockPos mirrorPos, boolean locked) {
        this.mirrorPos = mirrorPos;
        this.locked = locked;
    }

    public static void encode(ServerboundWarpLockTogglePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
        buf.writeBoolean(msg.locked);
    }

    public static ServerboundWarpLockTogglePacket decode(FriendlyByteBuf buf) {
        return new ServerboundWarpLockTogglePacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(ServerboundWarpLockTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5, msg.mirrorPos.getZ() + 0.5) > 64) return;
            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;
            if (!mirrorBE.hasUpgrade(MirrorUpgradeType.WARP_TARGET)) return;
            mirrorBE.getUpgradeData().putBoolean("WarpTargetLocked", msg.locked);
            mirrorBE.setChanged();
            String state = msg.locked ? "locked" : "unlocked";
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Warp target " + state + "."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
