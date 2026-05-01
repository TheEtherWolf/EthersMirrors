package com.ether.mirrors.block;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.PocketDimensionData;
import com.ether.mirrors.data.PocketTheme;
import com.ether.mirrors.init.MirrorsBlocks;
import com.ether.mirrors.MirrorsConfig;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorManagementPacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorPacket;
import net.minecraft.client.Minecraft;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import com.ether.mirrors.util.MultiblockHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;

import java.util.UUID;

public class PocketMirrorBlock extends MirrorBlock {

    public static final ResourceKey<Level> POCKET_DIMENSION = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation("ethersmirrors", "pocket"));

    public PocketMirrorBlock(Properties properties, MirrorTier tier) {
        super(properties, tier, MirrorType.POCKET);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            BlockPos masterPos = MultiblockHelper.findMasterPos(pos, state.getValue(FACING), state.getValue(PART));

            // Sneak + right-click opens mirror management (owner only)
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
                        net.minecraft.client.Minecraft.getInstance().setScreen(new com.ether.mirrors.screen.MirrorPlacementScreen(
                                bp, "pocket", dimStr, bp.getX(), bp.getY(), bp.getZ()));
                    }
                    return InteractionResult.SUCCESS;
                }
            }

            // Enter pocket dimension directly
            MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundEnterPocketPacket(masterPos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Server-side pocket entry. Called from ServerboundEnterPocketPacket.
     * Handles permission check, teleport, room build, vault, barrier placement.
     * entryTier controls how much of the pocket room the player can access.
     * Pass MirrorTier.NETHERITE for selection-screen / invitation entry (full access).
     */
    public static void enterPocket(net.minecraft.server.level.ServerPlayer serverPlayer,
                                    UUID pocketOwner, net.minecraft.server.level.ServerLevel level,
                                    MirrorTier entryTier) {
        ServerLevel pocketLevel = serverPlayer.server.getLevel(POCKET_DIMENSION);
        if (pocketLevel == null) {
            serverPlayer.displayClientMessage(Component.literal("Pocket dimension not available."), true);
            return;
        }

        PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
        // Double-entry guard: prevent entering pocket again if already inside one
        if (level.dimension().equals(POCKET_DIMENSION)) {
            serverPlayer.displayClientMessage(Component.literal("You are already inside a pocket dimension."), true);
            return;
        }
        // Cap defaultPocketSize to maxPocketSize in case the config has an invalid cross-field value
        int defaultSize = Math.min(MirrorsConfig.DEFAULT_POCKET_SIZE.get(), MirrorsConfig.MAX_POCKET_SIZE.get());
        PocketDimensionData.PocketRegion region = pocketData.getOrCreateRegion(pocketOwner, defaultSize);
        if (region == null) {
            serverPlayer.displayClientMessage(
                    Component.literal("Failed to create pocket dimension. Please try again."), true);
            return;
        }

        // Fire API event before entering pocket dimension
        com.ether.mirrors.api.event.PocketEnterEvent enterEvent =
                new com.ether.mirrors.api.event.PocketEnterEvent(serverPlayer, pocketOwner);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(enterEvent)) return;

        // Save return position — only if NOT already in the pocket dimension.
        // Overwriting with pocket-dim coords would cause exit to loop, then fall back to world spawn.
        if (!level.dimension().equals(POCKET_DIMENSION)) {
            pocketData.setPlayerReturn(serverPlayer.getUUID(),
                    serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    serverPlayer.getYRot(), serverPlayer.getXRot(),
                    level.dimension());
        }
        boolean isFirstEntry = !region.roomBuilt;
        pocketData.setPlayerInPocket(serverPlayer.getUUID(), pocketOwner);

        // Teleport first — ensures destination chunks are loaded
        BlockPos spawnPos = region.getSpawnPos();
        serverPlayer.teleportTo(pocketLevel,
                spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5,
                serverPlayer.getYRot(), serverPlayer.getXRot());
        com.ether.mirrors.advancement.MirrorsTriggers.ENTER_POCKET.trigger(serverPlayer);

        // Build room and barriers AFTER teleport
        buildRoomIfNeeded(pocketLevel, region, pocketData);
        applyTierBarriers(pocketLevel, region, entryTier);

        // First-entry tutorial message
        if (isFirstEntry) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "[Ether's Mirrors] Welcome to your pocket dimension! Use a Netherite mirror for full access. "
                    + "Sneak + right-click the exit mirror to expand or manage this dimension."));
        }

        // Barrier feedback — tell visitors how much space they can access
        if (entryTier != MirrorTier.NETHERITE) {
            int half = getTierHalf(entryTier, region.currentSize / 2);
            serverPlayer.displayClientMessage(Component.literal(
                    "Your " + entryTier.getDisplayName() + " mirror limits access to the inner "
                    + (half * 2) + "x" + (half * 2)
                    + " zone. Use a Netherite mirror for full access."), true);
        }

        // Place exit mirror at spawn floor if not already there
        BlockState exitState = pocketLevel.getBlockState(spawnPos);
        if (!(exitState.getBlock() instanceof ExitMirrorBlock)) {
            pocketLevel.setBlock(spawnPos, MirrorsBlocks.EXIT_MIRROR.get().defaultBlockState(), Block.UPDATE_ALL);
        }

        serverPlayer.displayClientMessage(Component.literal("Entering pocket dimension..."), true);
    }

    /**
     * Builds a walled room in the pocket dimension on first entry.
     * Idempotent: checks for deepslate bricks on the north wall before building.
     * <p>
     * Layout (default size=32):
     *  Y=0  bedrock (from chunk generator, untouched)
     *  Y=1  deepslate tile floor (interior) / deepslate brick walls
     *  Y=2–5  air interior (4-block headroom)
     *  Y=6  deepslate brick ceiling, glowstone every 4 blocks
     */
    /**
     * Places (or removes) barrier walls to limit the accessible area based on entry tier.
     * <p>
     * Tier → accessible inner zone (from center, in blocks each direction):
     *   WOOD=5 (10×10), STONE=7 (14×14), IRON=9 (18×18),
     *   GOLD=11 (22×22), DIAMOND=14 (28×28), NETHERITE=full (no barriers)
     * <p>
     * Safe: only places barriers in AIR; never overwrites player-placed blocks.
     * Removes old barriers from previous entry before placing new ones.
     */
    public static void applyTierBarriers(ServerLevel pocketLevel, PocketDimensionData.PocketRegion region, MirrorTier entryTier) {
        int size = region.currentSize;
        BlockPos spawn = region.getSpawnPos();
        int cx = spawn.getX(), cz = spawn.getZ();
        int fullHalf = size / 2;
        int tierHalf = getTierHalf(entryTier, fullHalf);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Clear all existing barriers in the room interior (Y=1-8)
        for (int x = cx - fullHalf; x < cx + fullHalf; x++) {
            for (int z = cz - fullHalf; z < cz + fullHalf; z++) {
                for (int y = 1; y <= 8; y++) {
                    pos.set(x, y, z);
                    if (pocketLevel.getBlockState(pos).is(Blocks.BARRIER)) {
                        pocketLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        if (entryTier == MirrorTier.NETHERITE) return; // Full access — no interior barriers

        // Barrier wall perimeter around the accessible zone:
        //   Accessible interior: [cx-tierHalf .. cx+tierHalf-1] × [cz-tierHalf .. cz+tierHalf-1]
        //   Barrier wall faces:  x=cx-tierHalf-1, x=cx+tierHalf, z=cz-tierHalf-1, z=cz+tierHalf
        int bx0 = cx - tierHalf - 1;
        int bx1 = cx + tierHalf;
        int bz0 = cz - tierHalf - 1;
        int bz1 = cz + tierHalf;

        for (int y = 1; y <= 8; y++) {
            // North and south walls (full width including corners)
            for (int x = bx0; x <= bx1; x++) {
                placeBarrier(pocketLevel, pos, x, y, bz0);
                placeBarrier(pocketLevel, pos, x, y, bz1);
            }
            // West and east walls (inner Z, corners already done)
            for (int z = bz0 + 1; z < bz1; z++) {
                placeBarrier(pocketLevel, pos, bx0, y, z);
                placeBarrier(pocketLevel, pos, bx1, y, z);
            }
        }
    }

    /** Places a barrier only if the position is currently AIR (preserves player-placed blocks). */
    private static void placeBarrier(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
        }
    }

    /**
     * Returns the half-size of the accessible zone for this tier.
     * NETHERITE returns fullHalf (no barriers). Lower tiers return progressively smaller values.
     */
    private static int getTierHalf(MirrorTier tier, int fullHalf) {
        // Ordinal: WOOD=0, STONE=1, IRON=2, GOLD=3, DIAMOND=4, NETHERITE=5
        int[] halfs = {5, 7, 9, 11, 14};
        int ord = tier.ordinal();
        if (ord >= halfs.length) return fullHalf; // NETHERITE or beyond = full
        return Math.min(halfs[ord], fullHalf);
    }

    public static void buildRoomIfNeeded(ServerLevel pocketLevel, PocketDimensionData.PocketRegion region,
                                          PocketDimensionData pocketData) {
        int size = region.currentSize;
        BlockPos spawn = region.getSpawnPos();
        int cx = spawn.getX(), cz = spawn.getZ();
        int half = size / 2;

        // Outer barrier boundary (1 block outside the accessible area)
        int bx0 = cx - half - 1;  // west wall
        int bx1 = cx + half;      // east wall
        int bz0 = cz - half - 1;  // south wall
        int bz1 = cz + half;      // north wall
        int byBot = 0;             // floor barrier
        int byTop = 9;             // ceiling barrier

        // Migration check: if roomBuilt=true, verify a clean void room exists.
        // If bedrock or sea lanterns are present at Y=0 or Y=1, this is a legacy room — force rebuild.
        if (region.roomBuilt) {
            BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(cx, 0, cz);
            boolean hasLegacyBlocks = pocketLevel.getBlockState(checkPos).is(Blocks.BEDROCK);
            if (!hasLegacyBlocks) {
                checkPos.set(cx, 1, cz + 4); // Check for sea lanterns slightly offset from spawn
                hasLegacyBlocks = pocketLevel.getBlockState(checkPos).is(Blocks.SEA_LANTERN);
            }
            if (!hasLegacyBlocks) {
                // Also confirm the west barrier wall exists
                checkPos.set(bx0, 3, cz);
                if (pocketLevel.getBlockState(checkPos).is(Blocks.BARRIER)) {
                    return; // Clean room confirmed — skip rebuild
                }
                hasLegacyBlocks = true; // No barriers either — force rebuild
            }
            if (hasLegacyBlocks) {
                region.roomBuilt = false; // Force rebuild below
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Clear old structure (sea lamps, deepslate walls, old floor) before building new open design
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                for (int y = 0; y <= 10; y++) {
                    pos.set(x, y, z);
                    if (!pocketLevel.getBlockState(pos).isAir()) {
                        pocketLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        // Floor and ceiling (full face)
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                pos.set(x, byBot, z);
                pocketLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                pos.set(x, byTop, z);
                pocketLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
            }
        }

        // North/south walls (full width, mid-height only — top/bottom already done)
        for (int y = byBot + 1; y < byTop; y++) {
            for (int x = bx0; x <= bx1; x++) {
                pos.set(x, y, bz0);
                pocketLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                pos.set(x, y, bz1);
                pocketLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
            }
            // West/east walls (inner Z, skip corners already placed above)
            for (int z = bz0 + 1; z < bz1; z++) {
                pos.set(bx0, y, z);
                pocketLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
                pos.set(bx1, y, z);
                pocketLevel.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
            }
        }

        // Small spawn platform (5×5 themed floor at Y=1, centered on spawn)
        net.minecraft.world.level.block.state.BlockState floorState = region.theme.floorBlock().defaultBlockState();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                pos.set(x, 1, z);
                pocketLevel.setBlock(pos, floorState, 3);
            }
        }

        // Vault — ender chest (personal storage, persists across dimensions)
        BlockPos vaultPos = new BlockPos(cx + 1, 2, cz);
        pocketLevel.setBlock(vaultPos,
            Blocks.ENDER_CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.EnderChestBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
        region.vaultPos = vaultPos;

        region.roomBuilt = true;
        pocketData.setDirty();
    }
}
