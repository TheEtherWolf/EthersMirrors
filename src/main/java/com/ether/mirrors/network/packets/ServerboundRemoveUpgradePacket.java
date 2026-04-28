package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.init.MirrorsItems;
import com.ether.mirrors.item.MirrorUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundRemoveUpgradePacket {

    private final BlockPos masterPos;
    private final String upgradeTypeId;

    public ServerboundRemoveUpgradePacket(BlockPos masterPos, String upgradeTypeId) {
        this.masterPos = masterPos;
        this.upgradeTypeId = upgradeTypeId;
    }

    public static void encode(ServerboundRemoveUpgradePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.masterPos);
        buf.writeUtf(msg.upgradeTypeId, 32);
    }

    public static ServerboundRemoveUpgradePacket decode(FriendlyByteBuf buf) {
        return new ServerboundRemoveUpgradePacket(buf.readBlockPos(), buf.readUtf(32));
    }

    public static void handle(ServerboundRemoveUpgradePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.masterPos.getX() + 0.5, msg.masterPos.getY() + 0.5, msg.masterPos.getZ() + 0.5) > 64) return;

            MirrorUpgradeType upgradeType = MirrorUpgradeType.fromId(msg.upgradeTypeId);
            if (upgradeType == null) return;

            if (!(player.level().getBlockEntity(msg.masterPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;
            if (!mirrorBE.hasUpgrade(upgradeType)) return;

            mirrorBE.removeUpgrade(upgradeType);

            // Keep network data's privacyLocked flag in sync
            if (upgradeType == com.ether.mirrors.item.MirrorUpgradeType.PRIVACY_LOCK) {
                com.ether.mirrors.data.MirrorNetworkData.get(player.server)
                        .updateMirrorPrivacyLock(mirrorBE.getMirrorId(), false);
            }

            // Return upgrade item to player (drop on ground if inventory is full)
            var upgradeItemOpt = MirrorsItems.UPGRADE_ITEMS.get(upgradeType);
            if (upgradeItemOpt != null) {
                ItemStack returnStack = new ItemStack(upgradeItemOpt.get());
                if (!player.addItem(returnStack)) {
                    player.drop(returnStack, false);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            upgradeType.getDisplayName() + " upgrade removed. Your inventory was full — item dropped at your feet."), true);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            upgradeType.getDisplayName() + " upgrade removed."), true);
                }
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        upgradeType.getDisplayName() + " upgrade removed."), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
