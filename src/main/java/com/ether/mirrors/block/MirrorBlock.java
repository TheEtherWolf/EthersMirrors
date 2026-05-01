package com.ether.mirrors.block;

import com.ether.mirrors.MirrorsConfig;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import com.ether.mirrors.util.MultiblockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public abstract class MirrorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<MirrorMultiblockPart> PART = EnumProperty.create("part", MirrorMultiblockPart.class);


    // Thin shape for a wall-mounted mirror (4 pixels deep from the wall, matches visual model)
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 12, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0,  0, 16, 16,  4);
    private static final VoxelShape SHAPE_EAST  = Block.box( 0, 0, 0,  4, 16, 16);
    private static final VoxelShape SHAPE_WEST  = Block.box(12, 0, 0, 16, 16, 16);

    // Tracks positions currently being cleaned up to prevent recursive onRemove cascading
    private static final ThreadLocal<Set<BlockPos>> BREAKING_POSITIONS = ThreadLocal.withInitial(HashSet::new);

    protected final MirrorTier tier;
    protected final MirrorType type;

    protected MirrorBlock(Properties properties, MirrorTier tier, MirrorType type) {
        super(properties);
        this.tier = tier;
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, MirrorMultiblockPart.MASTER));
    }

    public MirrorTier getTier() { return tier; }
    public MirrorType getMirrorType() { return type; }

    /**
     * Prevent pistons from pushing mirror blocks (Quark compat).
     * Piston displacement would orphan the BlockEntity's SavedData registration.
     */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST  -> SHAPE_EAST;
            case WEST  -> SHAPE_WEST;
            default    -> SHAPE_NORTH;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();

        // Only allow placement on wall faces (horizontal directions)
        if (clickedFace.getAxis().isVertical()) {
            return null;
        }

        // The mirror faces outward from the wall (same direction as clicked face)
        Direction facing = clickedFace;
        BlockPos masterPos = context.getClickedPos();

        if (!MultiblockHelper.canPlace(context.getLevel(), masterPos, facing)) {
            return null;
        }

        // Server-side: enforce mirror limit before the block ever enters the world
        Level level = context.getLevel();
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            Player player = context.getPlayer();
            if (player != null && !player.hasPermissions(2)) {
                MirrorNetworkData networkData = MirrorNetworkData.get(serverLevel.getServer());
                int maxMirrors = MirrorsConfig.MAX_MIRRORS_PER_PLAYER.get();
                if (networkData.getMirrorsForPlayer(player.getUUID()).size() >= maxMirrors) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "Mirror limit reached (" + maxMirrors + "). Break an existing mirror first."),
                            true);
                    return null;
                }
            }
        }

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, MirrorMultiblockPart.MASTER);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only master block gets a block entity
        if (state.getValue(PART).isMaster()) {
            return new MirrorBlockEntity(pos, state, tier, type);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide() && state.getValue(PART).isMaster()) {
            Direction facing = state.getValue(FACING);

            // Place the slave block above (1×2 mirror: MASTER at bottom, SLAVE_UP on top)
            BlockPos[] positions = MultiblockHelper.getPlacementPositions(pos, facing);
            MirrorMultiblockPart[] parts = MultiblockHelper.getPlacementParts();

            for (int i = 1; i < MultiblockHelper.PLACEMENT_SIZE; i++) {
                BlockState partState = state.setValue(PART, parts[i]);
                level.setBlock(positions[i], partState, 3);
            }

            // Set owner on master block entity and register in network
            if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MirrorBlockEntity mirrorBE) {
                mirrorBE.setOwner(player.getUUID());

                // Apply dye color from item NBT if present
                net.minecraft.nbt.CompoundTag itemTag = stack.getTag();
                if (itemTag != null && itemTag.contains("DyeColor")) {
                    mirrorBE.setDyeColor(itemTag.getInt("DyeColor"));
                }

                if (level instanceof ServerLevel serverLevel) {
                    MirrorNetworkData networkData = MirrorNetworkData.get(serverLevel.getServer());
                    networkData.addMirror(mirrorBE.getMirrorId(), player.getUUID(), pos,
                            serverLevel.dimension(), tier, type, facing);
                    // Sync waypoints to all online players when a beacon mirror is placed
                    if (type == com.ether.mirrors.util.MirrorType.BEACON) {
                        for (net.minecraft.server.level.ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
                            com.ether.mirrors.network.MirrorsNetwork.sendWaypointSync(sp);
                        }
                    }
                    // Open placement/setup screen for the placing player
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        String dimStr = serverLevel.dimension().location().toString();
                        com.ether.mirrors.network.MirrorsNetwork.sendToPlayer(sp,
                                new com.ether.mirrors.network.packets.ClientboundOpenMirrorPlacementPacket(
                                        pos, type.getName(), dimStr, pos.getX(), pos.getY(), pos.getZ()));
                    }
                }
            }
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return canPlayerBreak(level, pos, state, player) ? super.getDestroyProgress(state, player, level, pos) : 0.0F;
    }

    /**
     * Check if the player is allowed to break this mirror.
     * Creative: owner, op (level 2+), or BREAK-permitted players can always break.
     * Survival: owner can break by sneaking (mirror drops as item).
     * If the block entity is already gone (partial cleanup), allow the break to finish cleanup.
     */
    private boolean canPlayerBreak(BlockGetter level, BlockPos pos, BlockState state, Player player) {
        BlockPos masterPos = getMasterPos(pos, state);
        if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE) {
            if (player.isCreative()) {
                if (mirrorBE.isOwner(player)) return true;
                if (player.hasPermissions(2)) return true;
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    PermissionData perms = PermissionData.get(serverLevel.getServer());
                    if (perms.canPlayerUseMirror(player.getUUID(), mirrorBE.getMirrorId(),
                            mirrorBE.getOwnerUUID(), PermissionData.PermissionLevel.BREAK)) {
                        return true;
                    }
                }
                return false;
            }
            // Survival: owner can pick up by sneaking
            return mirrorBE.isOwner(player) && player.isCrouching();
        }
        // No block entity (already partially broken) — allow cleanup
        return true;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            // Check if this position is already being cleaned up by another part's break
            Set<BlockPos> breaking = BREAKING_POSITIONS.get();
            if (breaking.contains(pos)) {
                super.playerWillDestroy(level, pos, state, player);
                return;
            }

            // Check if the player is allowed to break this mirror
            if (!canPlayerBreak(level, pos, state, player)) {
                return;
            }

            Direction facing = state.getValue(FACING);
            MirrorMultiblockPart part = state.getValue(PART);

            // Find all positions in this multiblock — use placement positions (1×2 only)
            // to avoid accidentally removing adjacent unrelated mirrors
            BlockPos masterBlockPos = MultiblockHelper.findMasterPos(pos, facing, part);
            BlockPos[] positions = MultiblockHelper.getPlacementPositions(masterBlockPos, facing);

            // Mark all positions as being broken to prevent recursive cleanup
            for (BlockPos p : positions) {
                breaking.add(p);
            }

            try {
                // Remove the mirror from the network BEFORE removing blocks
                // (while the block entity still exists)
                if (level instanceof ServerLevel serverLevel) {
                    if (level.getBlockEntity(masterBlockPos) instanceof MirrorBlockEntity mirrorBE) {
                        MirrorNetworkData networkData = MirrorNetworkData.get(serverLevel.getServer());
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(mirrorBE.getMirrorId());
                            if (entry != null) {
                                com.ether.mirrors.api.event.MirrorBreakEvent breakEvent =
                                    new com.ether.mirrors.api.event.MirrorBreakEvent(serverPlayer, entry);
                                if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breakEvent)) {
                                    // Event was cancelled — abort break
                                    for (BlockPos p : positions) breaking.remove(p);
                                    return;
                                }
                            }
                        }
                        UUID removedId = mirrorBE.getMirrorId();
                        boolean wasBeacon = mirrorBE.getMirrorType() == com.ether.mirrors.util.MirrorType.BEACON;
                        networkData.removeMirror(removedId);
                        PermissionData.get(serverLevel.getServer()).removeAllPermissionsForMirror(removedId);
                        // Sync waypoints to all online players when a beacon mirror is removed
                        if (wasBeacon) {
                            for (net.minecraft.server.level.ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
                                com.ether.mirrors.network.MirrorsNetwork.sendWaypointSync(sp);
                            }
                        }
                    }
                }

                // Drop mirror item if owner breaking in survival (sneak-pick-up)
                if (!player.isCreative() && level instanceof ServerLevel serverLevel) {
                    if (level.getBlockEntity(masterBlockPos) instanceof MirrorBlockEntity mirrorBE) {
                        net.minecraft.world.item.ItemStack drop = new net.minecraft.world.item.ItemStack(
                                level.getBlockState(masterBlockPos).getBlock().asItem());
                        // Preserve dye color in item NBT
                        if (mirrorBE.getDyeColor() >= 0) {
                            drop.getOrCreateTag().putInt("DyeColor", mirrorBE.getDyeColor());
                        }
                        Block.popResource(level, masterBlockPos, drop);
                    }
                }

                // Remove all OTHER parts of the multiblock (vanilla will handle the broken pos)
                for (BlockPos otherPos : positions) {
                    if (!otherPos.equals(pos)) {
                        BlockState otherState = level.getBlockState(otherPos);
                        if (otherState.getBlock() instanceof MirrorBlock) {
                            level.removeBlock(otherPos, false);
                        }
                    }
                }
            } finally {
                // Always clean up the breaking set
                for (BlockPos p : positions) {
                    breaking.remove(p);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Skip network removal if the block is being moved by a piston — it still exists
            if (isMoving) {
                super.onRemove(state, level, pos, newState, isMoving);
                return;
            }

            // Skip network removal if this block is being cleaned up as part of multiblock breaking
            // (playerWillDestroy already handled network removal before any blocks were removed)
            Set<BlockPos> breaking = BREAKING_POSITIONS.get();
            if (!breaking.contains(pos)) {
                // Only remove from mirror network if this is an unexpected removal (e.g., commands, explosions)
                if (state.getValue(PART).isMaster() && level instanceof ServerLevel serverLevel) {
                    if (level.getBlockEntity(pos) instanceof MirrorBlockEntity mirrorBE) {
                        MirrorNetworkData networkData = MirrorNetworkData.get(serverLevel.getServer());
                        UUID removedId = mirrorBE.getMirrorId();
                        networkData.removeMirror(removedId);
                        PermissionData.get(serverLevel.getServer()).removeAllPermissionsForMirror(removedId);
                    }
                    // Clean up the slave block to prevent orphans
                    Direction facing = state.getValue(FACING);
                    BlockPos[] positions = MultiblockHelper.getPlacementPositions(pos, facing);
                    for (BlockPos otherPos : positions) {
                        if (!otherPos.equals(pos)) {
                            breaking.add(otherPos);
                        }
                    }
                    try {
                        for (BlockPos otherPos : positions) {
                            if (!otherPos.equals(pos)) {
                                BlockState otherState = level.getBlockState(otherPos);
                                if (otherState.getBlock() instanceof MirrorBlock) {
                                    level.removeBlock(otherPos, false);
                                }
                            }
                        }
                    } finally {
                        for (BlockPos otherPos : positions) {
                            breaking.remove(otherPos);
                        }
                    }
                }
                // If slave block removed unexpectedly, clean up the master too
                if (!state.getValue(PART).isMaster() && level instanceof ServerLevel serverLevel) {
                    Direction facing = state.getValue(FACING);
                    MirrorMultiblockPart part = state.getValue(PART);
                    BlockPos masterPos = MultiblockHelper.findMasterPos(pos, facing, part);
                    BlockState masterState = level.getBlockState(masterPos);
                    if (masterState.getBlock() instanceof MirrorBlock) {
                        // Remove network data from the master BE before removing the block
                        if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE) {
                            MirrorNetworkData networkData = MirrorNetworkData.get(serverLevel.getServer());
                            UUID removedId = mirrorBE.getMirrorId();
                            networkData.removeMirror(removedId);
                            PermissionData.get(serverLevel.getServer()).removeAllPermissionsForMirror(removedId);
                        }
                        // Add to breaking set to prevent recursive cleanup
                        breaking.add(masterPos);
                        try {
                            level.removeBlock(masterPos, false);
                        } finally {
                            breaking.remove(masterPos);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return state.getValue(PART).isMaster();
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        if (!state.getValue(PART).isMaster()) return 0;
        if (level.getBlockEntity(pos) instanceof com.ether.mirrors.block.entity.MirrorBlockEntity mirrorBE) {
            return mirrorBE.getSignal();
        }
        return 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(PART).isMaster();
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(PART).isMaster()) return 0;
        if (level.getBlockEntity(pos) instanceof com.ether.mirrors.block.entity.MirrorBlockEntity mirrorBE) {
            return mirrorBE.getSignal();
        }
        return 0;
    }

    @Override
    @javax.annotation.Nullable
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(PART).isMaster()) return null;
        return net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper(
                type, com.ether.mirrors.init.MirrorsBlockEntities.MIRROR_BLOCK_ENTITY.get(),
                (lvl, pos, blockState, be) -> be.serverTick());
    }

    @Override
    public float getExplosionResistance() {
        return 3600000.0F;
    }

    private BlockPos getMasterPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        MirrorMultiblockPart part = state.getValue(PART);
        return MultiblockHelper.findMasterPos(pos, facing, part);
    }

    /**
     * Temporarily suppress onRemove network-data removal for a position.
     * Use during in-place tier upgrades so the mirror entry is preserved.
     */
    public static void suppressNetworkRemoval(BlockPos pos) {
        BREAKING_POSITIONS.get().add(pos);
    }

    public static void unsuppressNetworkRemoval(BlockPos pos) {
        BREAKING_POSITIONS.get().remove(pos);
    }
}
