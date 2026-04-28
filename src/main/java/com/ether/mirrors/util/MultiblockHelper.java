package com.ether.mirrors.util;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.MirrorMultiblockPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Handles 1x2 wall-mounted multi-block placement and breaking.
 * The mirror is placed on a wall face. From the player's perspective looking at the wall:
 * - Master is the bottom block
 * - SLAVE_UP is the top block
 *
 * SLAVE_RIGHT and SLAVE_UP_RIGHT enum values are kept for backward compatibility
 * with any 2x2 mirrors that may exist in older world saves.
 */
public class MultiblockHelper {

    /** Number of blocks in a mirror multiblock structure — kept at 4 for backward-compat break logic. */
    public static final int MULTIBLOCK_SIZE = 4;

    /** Number of blocks placed when creating a new mirror (1×2 = 2). */
    public static final int PLACEMENT_SIZE = 2;

    /**
     * Get the horizontal offset direction (right from the player's perspective when facing the wall).
     * When facing NORTH, right is WEST. When facing SOUTH, right is EAST.
     * When facing EAST, right is NORTH. When facing WEST, right is SOUTH.
     * Note: FACING stores the direction the mirror faces (outward from wall), so the wall is behind it.
     */
    public static Direction getRightDirection(Direction facing) {
        return facing.getCounterClockWise();
    }

    /**
     * Get the positions for all 4 parts of the multi-block given the master position and facing.
     * Kept for backward-compatible breaking of old 2×2 mirrors.
     */
    public static BlockPos[] getMultiblockPositions(BlockPos master, Direction facing) {
        Direction right = getRightDirection(facing);
        return new BlockPos[] {
                master,                                    // MASTER (bottom)
                master.relative(right),                    // SLAVE_RIGHT (bottom-right, legacy)
                master.above(),                            // SLAVE_UP (top)
                master.above().relative(right)             // SLAVE_UP_RIGHT (top-right, legacy)
        };
    }

    /**
     * Get the 2 positions used when placing a new 1×2 mirror (master + slave_up).
     */
    public static BlockPos[] getPlacementPositions(BlockPos master, Direction facing) {
        return new BlockPos[] {
                master,         // MASTER (bottom)
                master.above()  // SLAVE_UP (top)
        };
    }

    /**
     * Get the parts used for new 1×2 mirror placement.
     */
    public static MirrorMultiblockPart[] getPlacementParts() {
        return new MirrorMultiblockPart[] {
                MirrorMultiblockPart.MASTER,
                MirrorMultiblockPart.SLAVE_UP
        };
    }

    /**
     * Check if the 2 placement positions are available for a new mirror.
     * Allows placement if a position is: air, a fluid, replaceable (plants, snow, etc.),
     * or an orphaned/ghost mirror block (leftover from incomplete cleanup).
     */
    public static boolean canPlace(Level level, BlockPos master, Direction facing) {
        BlockPos[] positions = getPlacementPositions(master, facing);
        for (BlockPos pos : positions) {
            if (!canReplace(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a single position can be replaced for mirror placement.
     */
    private static boolean canReplace(Level level, BlockPos pos) {
        BlockState existing = level.getBlockState(pos);

        // Air is always replaceable
        if (existing.isAir()) {
            return true;
        }

        // Standard replaceable check (covers tall grass, snow layers, etc.)
        if (existing.canBeReplaced()) {
            return true;
        }

        // Fluid source blocks (water, lava) - allow placement in them
        FluidState fluidState = existing.getFluidState();
        if (!fluidState.isEmpty() && fluidState.isSource()) {
            return true;
        }

        // Ghost mirror blocks: if there's a mirror block here but no valid master block entity,
        // it's an orphaned block from incomplete cleanup - allow replacing it
        if (existing.getBlock() instanceof MirrorBlock) {
            Direction ghostFacing = existing.getValue(MirrorBlock.FACING);
            MirrorMultiblockPart ghostPart = existing.getValue(MirrorBlock.PART);
            BlockPos ghostMaster = findMasterPos(pos, ghostFacing, ghostPart);
            // If the master position has no block entity, this is an orphaned ghost block
            if (!(level.getBlockEntity(ghostMaster) instanceof com.ether.mirrors.block.entity.MirrorBlockEntity)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Place all 4 parts of the multi-block (legacy 2×2 path — new mirrors use MirrorBlock.setPlacedBy).
     */
    public static void placeMultiblock(Level level, BlockPos master, Direction facing, BlockState baseState) {
        BlockPos[] positions = getMultiblockPositions(master, facing);
        MirrorMultiblockPart[] parts = MirrorMultiblockPart.values();
        int count = Math.min(positions.length, parts.length);

        for (int i = 0; i < count; i++) {
            BlockState partState = baseState
                    .setValue(MirrorBlock.FACING, facing)
                    .setValue(MirrorBlock.PART, parts[i]);
            level.setBlock(positions[i], partState, 3);
        }
    }

    /**
     * Find the master position from any part's position.
     */
    public static BlockPos findMasterPos(BlockPos partPos, Direction facing, MirrorMultiblockPart part) {
        Direction right = getRightDirection(facing);
        return switch (part) {
            case MASTER -> partPos;
            case SLAVE_RIGHT -> partPos.relative(right.getOpposite());
            case SLAVE_UP -> partPos.below();
            case SLAVE_UP_RIGHT -> partPos.below().relative(right.getOpposite());
        };
    }

    /**
     * Break the entire multi-block structure. Call from any part.
     */
    public static void breakMultiblock(Level level, BlockPos partPos, Direction facing, MirrorMultiblockPart part) {
        BlockPos master = findMasterPos(partPos, facing, part);
        BlockPos[] positions = getMultiblockPositions(master, facing);

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MirrorBlock) {
                // Use destroyBlock(pos, false) to remove without drops (we handle drops from master only)
                level.removeBlock(pos, false);
            }
        }
    }
}
