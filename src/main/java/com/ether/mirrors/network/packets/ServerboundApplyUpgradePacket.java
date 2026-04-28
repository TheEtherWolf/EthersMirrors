package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.init.MirrorsItems;
import com.ether.mirrors.item.MirrorUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundApplyUpgradePacket {

    private final BlockPos masterPos;
    private final String upgradeTypeId;

    public ServerboundApplyUpgradePacket(BlockPos masterPos, String upgradeTypeId) {
        this.masterPos = masterPos;
        this.upgradeTypeId = upgradeTypeId;
    }

    public static void encode(ServerboundApplyUpgradePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.masterPos);
        buf.writeUtf(msg.upgradeTypeId, 32);
    }

    public static ServerboundApplyUpgradePacket decode(FriendlyByteBuf buf) {
        return new ServerboundApplyUpgradePacket(buf.readBlockPos(), buf.readUtf(32));
    }

    public static void handle(ServerboundApplyUpgradePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.masterPos.getX() + 0.5, msg.masterPos.getY() + 0.5, msg.masterPos.getZ() + 0.5) > 64) return;

            MirrorUpgradeType upgradeType = MirrorUpgradeType.fromId(msg.upgradeTypeId);
            if (upgradeType == null) {
                com.ether.mirrors.EthersMirrors.LOGGER.warn(
                        "[EthersMirrors] Unknown upgrade type '{}' in packet from player {}",
                        msg.upgradeTypeId, player.getGameProfile().getName());
                return;
            }

            if (!(player.level().getBlockEntity(msg.masterPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;

            // Max upgrades based on tier
            int maxSlots = mirrorBE.getTier().getUpgradeSlots();
            if (mirrorBE.getAppliedUpgrades().size() >= maxSlots) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("This mirror's tier only supports " + maxSlots + " upgrade(s)."), true);
                return;
            }

            // Already has this upgrade
            if (mirrorBE.hasUpgrade(upgradeType)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("This upgrade is already applied."), true);
                return;
            }

            // Conflict check: WARP_TARGET and ONE_WAY are mutually exclusive
            if (upgradeType == MirrorUpgradeType.WARP_TARGET && mirrorBE.hasUpgrade(MirrorUpgradeType.ONE_WAY)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "WARP_TARGET conflicts with ONE_WAY. Remove ONE_WAY first."), true);
                return;
            }
            if (upgradeType == MirrorUpgradeType.ONE_WAY && mirrorBE.hasUpgrade(MirrorUpgradeType.WARP_TARGET)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "ONE_WAY conflicts with WARP_TARGET. Remove WARP_TARGET first."), true);
                return;
            }

            // Type/tier compatibility checks
            com.ether.mirrors.util.MirrorType mirrorType = mirrorBE.getMirrorType();
            com.ether.mirrors.util.MirrorTier mirrorTier = mirrorBE.getTier();
            if ((upgradeType == MirrorUpgradeType.WARP_TARGET || upgradeType == MirrorUpgradeType.ONE_WAY)
                    && mirrorType != com.ether.mirrors.util.MirrorType.TELEPORT) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        upgradeType.getDisplayName() + " only works on Teleport mirrors."), true);
                return;
            }
            if (upgradeType == MirrorUpgradeType.RANGE_BOOSTER
                    && mirrorTier.ordinal() < com.ether.mirrors.util.MirrorTier.IRON.ordinal()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Range Booster requires an Iron-tier mirror or higher."), true);
                return;
            }

            // Consume upgrade item from inventory
            var upgradeItemOpt = MirrorsItems.UPGRADE_ITEMS.get(upgradeType);
            if (upgradeItemOpt == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        upgradeType.getDisplayName() + " upgrade is not registered. This is a bug — please report it."), true);
                return;
            }
            net.minecraft.world.item.Item upgradeItem = upgradeItemOpt.get();

            // Find and remove one upgrade item from inventory
            boolean found = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(upgradeItem)) {
                    stack.shrink(1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("You don't have this upgrade item."), true);
                return;
            }

            mirrorBE.addUpgrade(upgradeType);

            // Fire API event
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(mirrorBE.getMirrorId());
            if (entry != null) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.ether.mirrors.api.event.MirrorUpgradeAppliedEvent(player, entry, upgradeType.getId()));
            }

            // Keep network data's privacyLocked flag in sync
            if (upgradeType == MirrorUpgradeType.PRIVACY_LOCK) {
                networkData.updateMirrorPrivacyLock(mirrorBE.getMirrorId(), true);
            }

            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    upgradeType.getDisplayName() + " upgrade applied!"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
