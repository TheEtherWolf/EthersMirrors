package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.item.MirrorUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundTimeLockTogglePacket {

    private final BlockPos mirrorPos;
    private final String mode;

    public ServerboundTimeLockTogglePacket(BlockPos mirrorPos, String mode) {
        this.mirrorPos = mirrorPos;
        this.mode = mode;
    }

    public static void encode(ServerboundTimeLockTogglePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
        buf.writeUtf(msg.mode, 8);
    }

    public static ServerboundTimeLockTogglePacket decode(FriendlyByteBuf buf) {
        return new ServerboundTimeLockTogglePacket(buf.readBlockPos(), buf.readUtf(8));
    }

    public static void handle(ServerboundTimeLockTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5, msg.mirrorPos.getZ() + 0.5) > 64) return;
            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;
            if (!mirrorBE.hasUpgrade(MirrorUpgradeType.TIME_LOCK)) return;
            String mode = msg.mode;
            if (!mode.equals("day") && !mode.equals("night")) return;
            mirrorBE.getUpgradeData().putString("TimeLockMode", mode);
            mirrorBE.setChanged();
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Time Lock mode set to " + mode + "."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
