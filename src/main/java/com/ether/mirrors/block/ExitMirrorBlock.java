package com.ether.mirrors.block;

import com.ether.mirrors.MirrorsConfig;
import com.ether.mirrors.data.PocketDimensionData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientboundPocketInfoPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Exit mirror placed in the pocket dimension.
 * Right-click to return to where you entered from.
 * Sneak + right-click to open the pocket expansion screen.
 */
public class ExitMirrorBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public ExitMirrorBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(-1.0F, 3600000.0F) // Unbreakable
                .noOcclusion()
                .lightLevel(state -> 12));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;

        // Sneak + right-click: open pocket expansion screen (owner only)
        if (player.isShiftKeyDown()) {
            PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
            java.util.UUID pocketOwner = pocketData.getPocketOwnerForPlayer(serverPlayer.getUUID());
            if (pocketOwner != null && pocketOwner.equals(serverPlayer.getUUID())) {
                // Player is in their own pocket — show expansion UI
                PocketDimensionData.PocketRegion region = pocketData.getRegion(serverPlayer.getUUID());
                if (region != null) {
                    int maxSize = MirrorsConfig.MAX_POCKET_SIZE.get();
                    MirrorsNetwork.sendToPlayer(serverPlayer,
                            new ClientboundPocketInfoPacket(region.currentSize, maxSize));
                }
            } else if (pocketOwner != null) {
                // Player is a guest in someone else's pocket
                serverPlayer.displayClientMessage(
                        Component.literal("Only the pocket owner can expand this space."), true);
            } else {
                serverPlayer.displayClientMessage(
                        Component.literal("You don't own this pocket dimension."), true);
            }
            return InteractionResult.CONSUME;
        }

        // Regular right-click: exit to return position
        PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
        PocketDimensionData.ReturnPoint rp = pocketData.getPlayerReturn(serverPlayer.getUUID());

        // Fire API event so external mods can react before the player leaves
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.ether.mirrors.api.event.PocketExitEvent(serverPlayer));

        if (rp != null && rp.dimension != null) {
            ServerLevel returnLevel = serverPlayer.server.getLevel(rp.dimension);
            if (returnLevel != null) {
                serverPlayer.teleportTo(returnLevel, rp.x, rp.y, rp.z, rp.yRot, rp.xRot);
                // Clear return data AFTER successful teleport
                pocketData.clearPlayerReturn(serverPlayer.getUUID());
                pocketData.clearPlayerInPocket(serverPlayer.getUUID());
                serverPlayer.displayClientMessage(Component.literal("Exiting pocket dimension..."), true);
                return InteractionResult.CONSUME;
            }
        }

        // Fallback: use the player's respawn point (bed/anchor), then overworld spawn
        net.minecraft.core.BlockPos respawnPos = serverPlayer.getRespawnPosition();
        net.minecraft.resources.ResourceKey<Level> respawnDim = serverPlayer.getRespawnDimension();
        if (respawnPos != null) {
            ServerLevel respawnLevel = serverPlayer.server.getLevel(respawnDim);
            if (respawnLevel != null) {
                java.util.Optional<net.minecraft.world.phys.Vec3> exact =
                        net.minecraft.world.entity.player.Player.findRespawnPositionAndUseSpawnBlock(
                                respawnLevel, respawnPos, serverPlayer.getRespawnAngle(), false, false);
                if (exact.isPresent()) {
                    net.minecraft.world.phys.Vec3 v = exact.get();
                    serverPlayer.teleportTo(respawnLevel, v.x, v.y, v.z,
                            serverPlayer.getYRot(), serverPlayer.getXRot());
                    pocketData.clearPlayerReturn(serverPlayer.getUUID());
                    pocketData.clearPlayerInPocket(serverPlayer.getUUID());
                    serverPlayer.displayClientMessage(Component.literal("Returning to your spawn point..."), true);
                    return InteractionResult.CONSUME;
                }
            }
        }
        // Last resort: overworld shared spawn
        ServerLevel overworld = serverPlayer.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        serverPlayer.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                serverPlayer.getYRot(), serverPlayer.getXRot());
        pocketData.clearPlayerReturn(serverPlayer.getUUID());
        pocketData.clearPlayerInPocket(serverPlayer.getUUID());
        serverPlayer.displayClientMessage(Component.literal("Returning to overworld..."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public float getExplosionResistance() {
        return 3600000.0F;
    }
}
