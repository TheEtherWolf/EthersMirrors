package com.ether.mirrors.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PocketDimensionData extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = "ethersmirrors_pocket";
    private static final int REGION_SPACING = 1024;

    private final Map<UUID, PocketRegion> playerRegions = new ConcurrentHashMap<>();
    private long nextRegionIndex = 0;

    /** Per-entering-player return positions (playerUUID → return point). */
    private final Map<UUID, ReturnPoint> playerReturnPoints = new ConcurrentHashMap<>();

    /** Tracks which owner's pocket each player is currently inside (playerUUID → ownerUUID). */
    private final Map<UUID, UUID> playerCurrentPocket = new ConcurrentHashMap<>();

    public static class ReturnPoint {
        public final double x, y, z;
        public final float yRot, xRot;
        public final ResourceKey<Level> dimension;
        public ReturnPoint(double x, double y, double z, float yRot, float xRot, ResourceKey<Level> dimension) {
            this.x = x; this.y = y; this.z = z;
            this.yRot = yRot; this.xRot = xRot;
            this.dimension = dimension;
        }
    }

    public static class PocketRegion {
        public final long regionIndex;
        public int currentSize;
        public PocketTheme theme = PocketTheme.DEEPSLATE;
        public long fixedTime = -1L;
        public String weather = "normal";
        public BlockPos vaultPos;
        /** True once buildRoomIfNeeded has run; prevents accidental rebuilds of player-customized rooms. */
        public boolean roomBuilt = false;
        /** Legacy: kept for data that was saved before per-player return points. */
        public BlockPos entryReturnPos;
        public ResourceKey<Level> entryReturnDimension;

        public PocketRegion(long regionIndex, int defaultSize) {
            this.regionIndex = regionIndex;
            this.currentSize = defaultSize;
        }

        public BlockPos getSpawnPos() {
            // regionIndex is long; clamp to int range before constructing BlockPos.
            // In practice each player needs 1024 blocks so overflow requires ~2M players.
            long rawX = regionIndex * REGION_SPACING + REGION_SPACING / 2L;
            int x = (int) Math.min(rawX, Integer.MAX_VALUE);
            return new BlockPos(x, 1, REGION_SPACING / 2);
        }

        public BlockPos getOriginPos() {
            long rawX = regionIndex * (long) REGION_SPACING;
            int x = (int) Math.min(rawX, Integer.MAX_VALUE);
            return new BlockPos(x, 0, 0);
        }
    }

    public PocketRegion getOrCreateRegion(UUID playerUUID, int defaultSize) {
        PocketRegion region = playerRegions.get(playerUUID);
        if (region == null) {
            region = new PocketRegion(nextRegionIndex++, defaultSize);
            playerRegions.put(playerUUID, region);
            setDirty();
        }
        return region;
    }

    @Nullable
    public PocketRegion getRegion(UUID playerUUID) {
        return playerRegions.get(playerUUID);
    }

    // ── Per-player return position (for shared pocket access) ────────────────

    public void setPlayerReturn(UUID playerUUID, double x, double y, double z,
                                float yRot, float xRot, ResourceKey<Level> dimension) {
        playerReturnPoints.put(playerUUID, new ReturnPoint(x, y, z, yRot, xRot, dimension));
        setDirty();
    }

    @javax.annotation.Nullable
    public ReturnPoint getPlayerReturn(UUID playerUUID) {
        ReturnPoint rp = playerReturnPoints.get(playerUUID);
        if (rp != null) return rp;
        // Legacy fallback: check the player's own region (block-pos precision)
        PocketRegion region = playerRegions.get(playerUUID);
        if (region != null && region.entryReturnPos != null && region.entryReturnDimension != null) {
            BlockPos bp = region.entryReturnPos;
            return new ReturnPoint(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5,
                    0f, 0f, region.entryReturnDimension);
        }
        return null;
    }

    public void clearPlayerReturn(UUID playerUUID) {
        playerReturnPoints.remove(playerUUID);
        setDirty();
    }

    /** Legacy compat: still stores on region AND in the new per-player map. */
    public void setEntryReturn(UUID playerUUID, double x, double y, double z,
                               float yRot, float xRot, ResourceKey<Level> returnDimension) {
        setPlayerReturn(playerUUID, x, y, z, yRot, xRot, returnDimension);
        // Also store a block-pos approximation on the region for legacy reads
        PocketRegion region = playerRegions.get(playerUUID);
        if (region != null) {
            region.entryReturnPos = new BlockPos((int) x, (int) y, (int) z);
            region.entryReturnDimension = returnDimension;
            // setDirty() already called by setPlayerReturn() above
        }
    }

    // ── Player-in-pocket tracking ─────────────────────────────────────────────

    public void setPlayerInPocket(UUID playerUUID, UUID ownerUUID) {
        playerCurrentPocket.put(playerUUID, ownerUUID);
        setDirty();
    }

    @javax.annotation.Nullable
    public UUID getPocketOwnerForPlayer(UUID playerUUID) {
        return playerCurrentPocket.get(playerUUID);
    }

    public void clearPlayerInPocket(UUID playerUUID) {
        playerCurrentPocket.remove(playerUUID);
        setDirty();
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    public void setRegionTheme(UUID ownerUUID, PocketTheme theme) {
        PocketRegion region = playerRegions.get(ownerUUID);
        if (region != null) {
            region.theme = theme;
            setDirty();
        }
    }

    public void expandRegion(UUID playerUUID, int additionalSize) {
        PocketRegion region = playerRegions.get(playerUUID);
        if (region != null) {
            region.currentSize += additionalSize;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("NextRegionIndex", nextRegionIndex);

        ListTag regionList = new ListTag();
        for (Map.Entry<UUID, PocketRegion> entry : playerRegions.entrySet()) {
            CompoundTag regionTag = new CompoundTag();
            regionTag.putUUID("PlayerUUID", entry.getKey());
            PocketRegion region = entry.getValue();
            regionTag.putLong("RegionIndex", region.regionIndex);
            regionTag.putInt("CurrentSize", region.currentSize);
            regionTag.putString("Theme", region.theme.getId());
            regionTag.putLong("FixedTime", region.fixedTime);
            regionTag.putString("Weather", region.weather);
            regionTag.putBoolean("RoomBuilt", region.roomBuilt);
            if (region.vaultPos != null) {
                regionTag.putLong("VaultPos", region.vaultPos.asLong());
            }
            if (region.entryReturnPos != null) {
                regionTag.putLong("ReturnPos", region.entryReturnPos.asLong());
            }
            if (region.entryReturnDimension != null) {
                regionTag.putString("ReturnDimension", region.entryReturnDimension.location().toString());
            }
            regionList.add(regionTag);
        }
        tag.put("Regions", regionList);

        // Persist per-player return points (needed for guests in shared pockets)
        ListTag returnList = new ListTag();
        for (Map.Entry<UUID, ReturnPoint> entry : playerReturnPoints.entrySet()) {
            ReturnPoint rp = entry.getValue();
            CompoundTag rpTag = new CompoundTag();
            rpTag.putUUID("PlayerUUID", entry.getKey());
            rpTag.putDouble("X", rp.x);
            rpTag.putDouble("Y", rp.y);
            rpTag.putDouble("Z", rp.z);
            rpTag.putFloat("YRot", rp.yRot);
            rpTag.putFloat("XRot", rp.xRot);
            rpTag.putString("Dimension", rp.dimension.location().toString());
            returnList.add(rpTag);
        }
        tag.put("PlayerReturnPoints", returnList);

        // Persist which pocket each player is currently inside
        CompoundTag pocketMap = new CompoundTag();
        for (Map.Entry<UUID, UUID> entry : playerCurrentPocket.entrySet()) {
            pocketMap.putUUID(entry.getKey().toString(), entry.getValue());
        }
        tag.put("PlayerCurrentPocket", pocketMap);

        return tag;
    }

    public static PocketDimensionData load(CompoundTag tag) {
        PocketDimensionData data = new PocketDimensionData();
        // Support both old int saves and new long saves
        data.nextRegionIndex = tag.contains("NextRegionIndex", Tag.TAG_LONG)
                ? tag.getLong("NextRegionIndex") : tag.getInt("NextRegionIndex");

        ListTag regionList = tag.getList("Regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < regionList.size(); i++) {
            CompoundTag regionTag = regionList.getCompound(i);
            UUID playerUUID = regionTag.getUUID("PlayerUUID");
            long regionIndex = regionTag.contains("RegionIndex", Tag.TAG_LONG)
                    ? regionTag.getLong("RegionIndex") : regionTag.getInt("RegionIndex");
            int currentSize = regionTag.getInt("CurrentSize");

            PocketRegion region = new PocketRegion(regionIndex, currentSize);
            if (regionTag.contains("Theme")) {
                PocketTheme t = PocketTheme.fromId(regionTag.getString("Theme"));
                if (t != null) region.theme = t;
            }
            if (regionTag.contains("FixedTime")) {
                region.fixedTime = regionTag.getLong("FixedTime");
            }
            if (regionTag.contains("Weather")) {
                String w = regionTag.getString("Weather");
                region.weather = ("rain".equals(w) || "thunder".equals(w)) ? w : "normal";
            }
            if (regionTag.contains("RoomBuilt")) {
                region.roomBuilt = regionTag.getBoolean("RoomBuilt");
            }
            if (regionTag.contains("VaultPos")) {
                region.vaultPos = BlockPos.of(regionTag.getLong("VaultPos"));
            }
            if (regionTag.contains("ReturnPos")) {
                region.entryReturnPos = BlockPos.of(regionTag.getLong("ReturnPos"));
            }
            if (regionTag.contains("ReturnDimension")) {
                region.entryReturnDimension = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(regionTag.getString("ReturnDimension"))
                );
            }

            data.playerRegions.put(playerUUID, region);
            if (regionIndex >= data.nextRegionIndex) {
                data.nextRegionIndex = regionIndex + 1L;
            }
        }

        // Load per-player return points
        if (tag.contains("PlayerReturnPoints")) {
            ListTag returnList = tag.getList("PlayerReturnPoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < returnList.size(); i++) {
                CompoundTag rpTag = returnList.getCompound(i);
                UUID playerUUID = rpTag.getUUID("PlayerUUID");
                ResourceKey<Level> dimension = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(rpTag.getString("Dimension")));
                double x, y, z;
                float yRot = 0f, xRot = 0f;
                if (rpTag.contains("X")) {
                    // New format: exact doubles
                    x = rpTag.getDouble("X");
                    y = rpTag.getDouble("Y");
                    z = rpTag.getDouble("Z");
                    if (rpTag.contains("YRot")) { yRot = rpTag.getFloat("YRot"); xRot = rpTag.getFloat("XRot"); }
                } else {
                    // Legacy format: BlockPos long
                    BlockPos bp = BlockPos.of(rpTag.getLong("Pos"));
                    x = bp.getX() + 0.5; y = bp.getY(); z = bp.getZ() + 0.5;
                }
                data.playerReturnPoints.put(playerUUID, new ReturnPoint(x, y, z, yRot, xRot, dimension));
            }
        }

        // Load player-in-pocket tracking
        if (tag.contains("PlayerCurrentPocket")) {
            CompoundTag pocketMap = tag.getCompound("PlayerCurrentPocket");
            for (String key : pocketMap.getAllKeys()) {
                try {
                    data.playerCurrentPocket.put(UUID.fromString(key), pocketMap.getUUID(key));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[EthersMirrors] Skipping invalid UUID key '{}' in PlayerCurrentPocket data: {}", key, e.getMessage());
                }
            }
        }

        return data;
    }

    public java.util.Map<UUID, PocketRegion> getAllRegions() {
        return java.util.Collections.unmodifiableMap(playerRegions);
    }

    public static PocketDimensionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PocketDimensionData::load, PocketDimensionData::new, DATA_NAME);
    }
}
