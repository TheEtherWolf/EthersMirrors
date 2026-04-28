package com.ether.mirrors.network.packets;

import com.ether.mirrors.MirrorsConfig;
import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.MirrorMultiblockPart;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.init.MirrorsBlocks;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import com.ether.mirrors.util.MultiblockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundUpgradeMirrorTierPacket {

    private final BlockPos masterPos;

    public ServerboundUpgradeMirrorTierPacket(BlockPos masterPos) {
        this.masterPos = masterPos;
    }

    public static void encode(ServerboundUpgradeMirrorTierPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.masterPos);
    }

    public static ServerboundUpgradeMirrorTierPacket decode(FriendlyByteBuf buf) {
        return new ServerboundUpgradeMirrorTierPacket(buf.readBlockPos());
    }

    public static void handle(ServerboundUpgradeMirrorTierPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();

            // Proximity check
            if (player.distanceToSqr(msg.masterPos.getX() + 0.5, msg.masterPos.getY() + 0.5,
                    msg.masterPos.getZ() + 0.5) > 64) return;

            BlockState state = level.getBlockState(msg.masterPos);
            if (!(state.getBlock() instanceof MirrorBlock)) return;
            if (state.getValue(MirrorBlock.PART) != MirrorMultiblockPart.MASTER) return;

            if (!(level.getBlockEntity(msg.masterPos) instanceof MirrorBlockEntity sourceBE)) return;

            // Ownership check
            if (!sourceBE.isOwner(player) && !player.hasPermissions(2)) {
                player.displayClientMessage(Component.literal("You don't own this mirror."), true);
                return;
            }

            MirrorTier currentTier = sourceBE.getTier();
            MirrorType mirrorType = sourceBE.getMirrorType();

            // Max tier check
            MirrorTier nextTier = currentTier != null ? currentTier.getNext() : null;
            if (nextTier == null) {
                player.displayClientMessage(Component.literal("This mirror is already at max tier."), true);
                return;
            }

            // Cost check
            int cost = MirrorsConfig.TIER_UPGRADE_COST.get();
            Item costItem = nextTier.getMaterial();
            int available = countItems(player, costItem);
            if (available < cost && !player.isCreative()) {
                player.displayClientMessage(Component.literal(
                        "Not enough materials. Need " + cost + "x "
                                + costItem.getDescription().getString() + " (have " + available + ")."), true);
                return;
            }

            // Save everything from the old block entity before replacement
            java.util.UUID mirrorId = sourceBE.getMirrorId();
            java.util.UUID ownerUUID = sourceBE.getOwnerUUID();
            String mirrorName = sourceBE.getMirrorName();
            java.util.List<String> upgrades = sourceBE.getAppliedUpgrades();
            net.minecraft.nbt.CompoundTag upgradeData = sourceBE.getUpgradeData().copy();
            boolean wasActivated = sourceBE.isActivated();
            int savedDyeColor = sourceBE.getDyeColor();

            Direction facing = state.getValue(MirrorBlock.FACING);
            BlockPos[] positions = MultiblockHelper.getPlacementPositions(msg.masterPos, facing);
            MirrorMultiblockPart[] parts = MultiblockHelper.getPlacementParts();

            Block newBlock = MirrorsBlocks.getMirrorBlock(mirrorType, nextTier).get();

            // Verify existing multiblock structure is intact before replacing
            for (int i = 0; i < MultiblockHelper.PLACEMENT_SIZE; i++) {
                BlockState existingState = level.getBlockState(positions[i]);
                if (!(existingState.getBlock() instanceof MirrorBlock)) {
                    player.displayClientMessage(Component.literal("Mirror structure is incomplete or damaged."), true);
                    return;
                }
            }

            // Suppress onRemove network-data removal while we replace the master block
            MirrorBlock.suppressNetworkRemoval(msg.masterPos);
            try {
                for (int i = 0; i < MultiblockHelper.PLACEMENT_SIZE; i++) {
                    BlockState newState = newBlock.defaultBlockState()
                            .setValue(MirrorBlock.FACING, facing)
                            .setValue(MirrorBlock.PART, parts[i]);
                    level.setBlock(positions[i], newState, 3);
                }
            } finally {
                MirrorBlock.unsuppressNetworkRemoval(msg.masterPos);
            }

            // Restore block entity data on the new master
            if (!(level.getBlockEntity(msg.masterPos) instanceof MirrorBlockEntity newBE)) {
                // Block entity creation failed — revert the block replacement
                com.ether.mirrors.EthersMirrors.LOGGER.error(
                        "[EthersMirrors] Tier upgrade failed: new block entity not created at {}. Reverting.", msg.masterPos);
                Block oldBlock = MirrorsBlocks.getMirrorBlock(mirrorType, currentTier).get();
                for (int i = 0; i < MultiblockHelper.PLACEMENT_SIZE; i++) {
                    BlockState revertState = oldBlock.defaultBlockState()
                            .setValue(MirrorBlock.FACING, facing)
                            .setValue(MirrorBlock.PART, parts[i]);
                    level.setBlock(positions[i], revertState, 3);
                }
                if (level.getBlockEntity(msg.masterPos) instanceof MirrorBlockEntity revertBE) {
                    revertBE.setMirrorId(mirrorId);
                    revertBE.setOwner(ownerUUID);
                    revertBE.setMirrorName(mirrorName);
                    revertBE.setAppliedUpgrades(upgrades);
                    revertBE.setUpgradeData(upgradeData);
                    revertBE.setTier(currentTier);
                    revertBE.setActivated(wasActivated);
                    revertBE.setDyeColor(savedDyeColor);
                    revertBE.setChanged();
                }
                player.displayClientMessage(Component.literal("Upgrade failed — mirror data could not be transferred."), true);
                return;
            }

            newBE.setMirrorId(mirrorId);
            newBE.setOwner(ownerUUID);
            newBE.setMirrorName(mirrorName);
            newBE.setAppliedUpgrades(upgrades);
            newBE.setUpgradeData(upgradeData);
            newBE.setTier(nextTier);
            newBE.setActivated(wasActivated);
            newBE.setDyeColor(savedDyeColor);
            newBE.setChanged();

            // Update network data tier
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            networkData.updateMirrorTier(mirrorId, nextTier);

            // Consume materials AFTER successful restoration (skip in creative)
            if (!player.isCreative()) {
                consumeItems(player, costItem, cost);
            }

            player.displayClientMessage(Component.literal(
                    "Mirror upgraded to " + nextTier.getDisplayName() + " tier!"), true);

            com.ether.mirrors.advancement.MirrorsTriggers.MIRROR_UPGRADED.trigger(player, nextTier.getName());

            // Refresh the management screen with updated data
            ServerboundOpenMirrorManagementPacket.sendTo(player, msg.masterPos);
        });
        ctx.get().setPacketHandled(true);
    }

    private static int countItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private static void consumeItems(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (remaining <= 0) break;
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }
}
