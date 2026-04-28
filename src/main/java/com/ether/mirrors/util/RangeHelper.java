package com.ether.mirrors.util;

import com.ether.mirrors.MirrorsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class RangeHelper {

    public static int getRangeForTier(MirrorTier tier) {
        return switch (tier) {
            case WOOD -> MirrorsConfig.WOOD_RANGE.get();
            case STONE -> MirrorsConfig.STONE_RANGE.get();
            case IRON -> MirrorsConfig.IRON_RANGE.get();
            case GOLD -> MirrorsConfig.GOLD_RANGE.get();
            case DIAMOND -> MirrorsConfig.DIAMOND_RANGE.get();
            case NETHERITE -> MirrorsConfig.NETHERITE_RANGE.get();
        };
    }

    public static int getCooldownSecondsForTier(MirrorTier tier) {
        return switch (tier) {
            case WOOD -> MirrorsConfig.WOOD_COOLDOWN.get();
            case STONE -> MirrorsConfig.STONE_COOLDOWN.get();
            case IRON -> MirrorsConfig.IRON_COOLDOWN.get();
            case GOLD -> MirrorsConfig.GOLD_COOLDOWN.get();
            case DIAMOND -> MirrorsConfig.DIAMOND_COOLDOWN.get();
            case NETHERITE -> MirrorsConfig.NETHERITE_COOLDOWN.get();
        };
    }

    /** Returns the maximum configured cooldown across all tiers, in milliseconds. */
    public static long getMaxCooldownMs() {
        long max = 0;
        for (MirrorTier tier : MirrorTier.values()) {
            max = Math.max(max, getCooldownSecondsForTier(tier) * 1000L);
        }
        return max;
    }

    public static double getDistance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    public static boolean isSameDimension(ResourceKey<Level> a, ResourceKey<Level> b) {
        return a.location().equals(b.location());
    }

    public static boolean isInRange(MirrorTier tier, BlockPos source, ResourceKey<Level> sourceDim,
                                     BlockPos target, ResourceKey<Level> targetDim) {
        if (!isSameDimension(sourceDim, targetDim)) {
            // Cross-dimension: only Diamond and Netherite
            if (!tier.canCrossDimension()) {
                return false;
            }
            // Apply cross-dimension range multiplier
            // For Nether, coordinates are 8x, so adjust
            double distance = getAdjustedCrossDimDistance(source, sourceDim, target, targetDim);
            double maxRange = getRangeForTier(tier) * MirrorsConfig.CROSS_DIM_RANGE_MULTIPLIER.get();
            return distance <= maxRange;
        }
        double distance = getDistance(source, target);
        return distance <= getRangeForTier(tier);
    }

    /**
     * Range check using the SOURCE mirror's tier.
     * The source determines how far it can reach — a wood mirror always has wood-tier range
     * regardless of the target mirror's tier.
     * Cross-dimension is allowed if EITHER mirror supports it.
     */
    public static boolean isInRange(MirrorTier sourceTier, MirrorTier targetTier, BlockPos source,
                                     ResourceKey<Level> sourceDim, BlockPos target, ResourceKey<Level> targetDim) {
        if (!isSameDimension(sourceDim, targetDim)) {
            // Cross-dimension: allowed if EITHER mirror supports it
            if (!sourceTier.canCrossDimension() && !targetTier.canCrossDimension()) {
                return false;
            }
            double distance = getAdjustedCrossDimDistance(source, sourceDim, target, targetDim);
            double maxRange = getRangeForTier(sourceTier) * MirrorsConfig.CROSS_DIM_RANGE_MULTIPLIER.get();
            return distance <= maxRange;
        }
        double distance = getDistance(source, target);
        return distance <= getRangeForTier(sourceTier);
    }

    /**
     * Signal strength using the source mirror's tier.
     */
    public static double getSignalStrength(MirrorTier sourceTier, MirrorTier targetTier, BlockPos source,
                                            ResourceKey<Level> sourceDim, BlockPos target, ResourceKey<Level> targetDim) {
        return getSignalStrength(sourceTier, source, sourceDim, target, targetDim);
    }

    /**
     * Get the signal strength as a percentage (1.0 = perfect, 0.0 = at max range).
     * Used for static effects on calling mirrors.
     */
    public static double getSignalStrength(MirrorTier tier, BlockPos source, ResourceKey<Level> sourceDim,
                                            BlockPos target, ResourceKey<Level> targetDim) {
        double maxRange;
        double distance;

        if (!isSameDimension(sourceDim, targetDim)) {
            if (!tier.canCrossDimension()) return 0.0;
            distance = getAdjustedCrossDimDistance(source, sourceDim, target, targetDim);
            maxRange = getRangeForTier(tier) * MirrorsConfig.CROSS_DIM_RANGE_MULTIPLIER.get();
        } else {
            distance = getDistance(source, target);
            maxRange = getRangeForTier(tier);
        }

        if (maxRange <= 0) return 0.0;
        return Math.max(0.0, 1.0 - (distance / maxRange));
    }

    private static double getRangeBoostMultiplier() {
        return 1.0 + MirrorsConfig.RANGE_BOOSTER_PERCENT.get() / 100.0;
    }

    public static boolean isInRangeBoosted(MirrorTier sourceTier, MirrorTier targetTier, BlockPos source,
                                            ResourceKey<Level> sourceDim, BlockPos target, ResourceKey<Level> targetDim) {
        if (!isSameDimension(sourceDim, targetDim)) {
            if (!sourceTier.canCrossDimension() && !targetTier.canCrossDimension()) return false;
            double distance = getAdjustedCrossDimDistance(source, sourceDim, target, targetDim);
            double maxRange = getRangeForTier(sourceTier) * MirrorsConfig.CROSS_DIM_RANGE_MULTIPLIER.get() * getRangeBoostMultiplier();
            return distance <= maxRange;
        }
        return getDistance(source, target) <= getRangeForTier(sourceTier) * getRangeBoostMultiplier();
    }

    public static double getSignalStrengthBoosted(MirrorTier sourceTier, MirrorTier targetTier, BlockPos source,
                                                   ResourceKey<Level> sourceDim, BlockPos target, ResourceKey<Level> targetDim) {
        double maxRange;
        double distance;
        if (!isSameDimension(sourceDim, targetDim)) {
            if (!sourceTier.canCrossDimension() && !targetTier.canCrossDimension()) return 0.0;
            distance = getAdjustedCrossDimDistance(source, sourceDim, target, targetDim);
            maxRange = getRangeForTier(sourceTier) * MirrorsConfig.CROSS_DIM_RANGE_MULTIPLIER.get() * getRangeBoostMultiplier();
        } else {
            distance = getDistance(source, target);
            maxRange = getRangeForTier(sourceTier) * getRangeBoostMultiplier();
        }
        if (maxRange <= 0) return 0.0;
        return Math.max(0.0, 1.0 - (distance / maxRange));
    }

    private static double getAdjustedCrossDimDistance(BlockPos source, ResourceKey<Level> sourceDim,
                                                       BlockPos target, ResourceKey<Level> targetDim) {
        // Normalize coordinates to Overworld-equivalent scale before comparing.
        // Nether uses an 8:1 coordinate ratio (1 Nether block = 8 Overworld blocks).
        // The End and any unrecognized custom dimensions use 1:1 (same scale as Overworld),
        // which is the safest fallback — they are left unchanged.
        double sx = source.getX(), sz = source.getZ();
        double tx = target.getX(), tz = target.getZ();

        if (isSameDimension(sourceDim, Level.NETHER)) {
            sx *= 8; sz *= 8;
        }
        if (isSameDimension(targetDim, Level.NETHER)) {
            tx *= 8; tz *= 8;
        }

        double dx = sx - tx;
        double dy = source.getY() - target.getY();
        double dz = sz - tz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
