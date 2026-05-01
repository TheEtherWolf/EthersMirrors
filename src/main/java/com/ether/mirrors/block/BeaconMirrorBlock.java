package com.ether.mirrors.block;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorManagementPacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorPacket;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import com.ether.mirrors.util.MultiblockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class BeaconMirrorBlock extends MirrorBlock {

    /** Maximum beacon mirrors per player. */
    public static final int MAX_BEACON_MIRRORS = 3;

    public BeaconMirrorBlock(Properties properties, MirrorTier tier) {
        super(properties, tier, MirrorType.BEACON);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Enforce per-player beacon mirror limit before the base class places slave blocks
        if (!level.isClientSide() && state.getValue(PART).isMaster()
                && placer instanceof Player player && level instanceof ServerLevel serverLevel) {
            MirrorNetworkData networkData = MirrorNetworkData.get(serverLevel.getServer());
            long beaconCount = networkData.getMirrorsForPlayer(player.getUUID()).stream()
                    .filter(e -> e.type == MirrorType.BEACON).count();
            if (beaconCount >= MAX_BEACON_MIRRORS && !player.hasPermissions(2)) {
                // Remove the master block that was placed before setPlacedBy was called.
                // Give the item back since Minecraft already decremented the player's stack.
                level.removeBlock(pos, false);
                if (!player.getAbilities().instabuild) {
                    player.getInventory().add(new ItemStack(state.getBlock()));
                }
                player.displayClientMessage(
                        Component.literal("Beacon mirror limit reached (" + MAX_BEACON_MIRRORS
                                + "). Break an existing beacon mirror first."), true);
                return;
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            BlockPos masterPos = MultiblockHelper.findMasterPos(pos,
                    state.getValue(FACING), state.getValue(PART));

            // Sneak + right-click opens mirror management for the owner
            if (player.isCrouching()) {
                if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity be && be.isOwner(player)) {
                    MirrorsNetwork.sendToServer(new ServerboundOpenMirrorManagementPacket(masterPos));
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }

            // Activation gate — owner must inscribe on first use
            if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE) {
                if (!mirrorBE.isActivated()) {
                    if (mirrorBE.isOwner(player)) {
                        net.minecraft.core.BlockPos bp = masterPos;
                        String dimStr = level.dimension().location().toString();
                        net.minecraft.client.Minecraft.getInstance().setScreen(
                                new com.ether.mirrors.screen.MirrorPlacementScreen(
                                        bp, "beacon", dimStr, bp.getX(), bp.getY(), bp.getZ()));
                    }
                    return InteractionResult.SUCCESS;
                }

                // Naming gate — owner must name beacon before it's usable (legacy fallback)
                if (mirrorBE.isOwner(player) && (mirrorBE.getMirrorName() == null || mirrorBE.getMirrorName().isEmpty())) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(
                            new com.ether.mirrors.screen.MirrorNamingScreen(masterPos, null, false));
                    return InteractionResult.SUCCESS;
                }
            }

            // Open the beacon waypoint screen (read-only, shows all beacon mirrors globally)
            MirrorsNetwork.sendToServer(new ServerboundOpenMirrorPacket(masterPos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
