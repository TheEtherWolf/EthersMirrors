package com.ether.mirrors.data;

import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class MirrorNetworkData extends SavedData {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = "ethersmirrors_network";

    private final Map<UUID, MirrorEntry> mirrors = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerMirrors = new java.util.concurrent.ConcurrentHashMap<>(); // ownerUUID -> set of mirrorIds
    private final Map<UUID, List<String>> pendingAlarmMessages = new java.util.concurrent.ConcurrentHashMap<>();
    // Per-player, per-mirror cooldown: playerUUID -> mirrorId -> timestamp
    // mirrorId of UUID(0,0) is used for handheld mirror teleports
    private final Map<UUID, Map<UUID, Long>> playerMirrorCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerFavorites = new java.util.concurrent.ConcurrentHashMap<>(); // playerUUID -> favorited mirrorIds
    private final Map<UUID, String> mirrorFolders = new java.util.concurrent.ConcurrentHashMap<>(); // mirrorId -> folder name (owner-assigned)

    public static class MirrorEntry {
        public final UUID mirrorId;
        public final UUID ownerUUID;
        public final BlockPos pos;
        public final ResourceKey<Level> dimension;
        public final MirrorTier tier;
        public final MirrorType type;
        public final Direction facing;
        /** Mutable — updated in-place by updateMirrorName() and updateMirrorPrivacyLock(). All other fields are immutable. */
        public String name;
        public volatile boolean privacyLocked;

        public MirrorEntry(UUID mirrorId, UUID ownerUUID, BlockPos pos, ResourceKey<Level> dimension,
                           MirrorTier tier, MirrorType type, Direction facing, String name, boolean privacyLocked) {
            this.mirrorId = mirrorId;
            this.ownerUUID = ownerUUID;
            this.pos = pos;
            this.dimension = dimension;
            this.tier = tier;
            this.type = type;
            this.facing = facing;
            this.name = name != null ? name : "";
            this.privacyLocked = privacyLocked;
        }

        public MirrorEntry(UUID mirrorId, UUID ownerUUID, BlockPos pos, ResourceKey<Level> dimension,
                           MirrorTier tier, MirrorType type, Direction facing, String name) {
            this(mirrorId, ownerUUID, pos, dimension, tier, type, facing, name, false);
        }

        public MirrorEntry(UUID mirrorId, UUID ownerUUID, BlockPos pos, ResourceKey<Level> dimension,
                           MirrorTier tier, MirrorType type, Direction facing) {
            this(mirrorId, ownerUUID, pos, dimension, tier, type, facing, "", false);
        }
    }

    public void addMirror(UUID mirrorId, UUID ownerUUID, BlockPos pos, ResourceKey<Level> dimension,
                          MirrorTier tier, MirrorType type, Direction facing, String name) {
        MirrorEntry entry = new MirrorEntry(mirrorId, ownerUUID, pos, dimension, tier, type, facing, name);
        mirrors.put(mirrorId, entry);
        playerMirrors.computeIfAbsent(ownerUUID, k -> new HashSet<>()).add(mirrorId);
        setDirty();
    }

    public void addMirror(UUID mirrorId, UUID ownerUUID, BlockPos pos, ResourceKey<Level> dimension,
                          MirrorTier tier, MirrorType type, Direction facing) {
        addMirror(mirrorId, ownerUUID, pos, dimension, tier, type, facing, "");
    }

    public void updateMirrorTier(UUID mirrorId, MirrorTier newTier) {
        MirrorEntry entry = mirrors.get(mirrorId);
        if (entry != null) {
            MirrorEntry updated = new MirrorEntry(entry.mirrorId, entry.ownerUUID, entry.pos,
                    entry.dimension, newTier, entry.type, entry.facing, entry.name);
            updated.privacyLocked = entry.privacyLocked;
            mirrors.put(mirrorId, updated);
            setDirty();
        }
    }

    public void updateMirrorPrivacyLock(UUID mirrorId, boolean locked) {
        MirrorEntry entry = mirrors.get(mirrorId);
        if (entry != null) {
            entry.privacyLocked = locked;
            setDirty();
        }
    }

    public void updateMirrorName(UUID mirrorId, String name) {
        MirrorEntry entry = mirrors.get(mirrorId);
        if (entry != null) {
            entry.name = name != null ? name : "";
            setDirty();
        }
    }

    public void removeMirror(UUID mirrorId) {
        MirrorEntry entry = mirrors.remove(mirrorId);
        if (entry != null) {
            Set<UUID> set = playerMirrors.get(entry.ownerUUID);
            if (set != null) {
                set.remove(mirrorId);
                if (set.isEmpty()) {
                    playerMirrors.remove(entry.ownerUUID);
                }
            }
        }
        // Clean up orphaned favorites pointing to this mirror
        for (Set<UUID> favSet : playerFavorites.values()) {
            favSet.remove(mirrorId);
        }
        // Clean up orphaned folder entry
        mirrorFolders.remove(mirrorId);
        // Clean up cooldown entries referencing this mirror
        for (Map<UUID, Long> mirrorMap : playerMirrorCooldowns.values()) {
            mirrorMap.remove(mirrorId);
        }
        setDirty();
    }

    @Nullable
    public MirrorEntry getMirrorById(UUID mirrorId) {
        return mirrors.get(mirrorId);
    }

    public List<MirrorEntry> getAllMirrors() {
        return new ArrayList<>(mirrors.values());
    }

    public List<MirrorEntry> getMirrorsForPlayer(UUID ownerUUID) {
        Set<UUID> ids = playerMirrors.get(ownerUUID);
        if (ids == null) return Collections.emptyList();
        return ids.stream()
                .map(mirrors::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all mirrors a player can access: their own + mirrors from players who have granted them permission.
     */
    public List<MirrorEntry> getConnectedMirrors(UUID playerUUID, PermissionData permissions) {
        if (playerUUID == null) return Collections.emptyList();
        if (permissions == null) return getMirrorsForPlayer(playerUUID);
        List<MirrorEntry> result = new ArrayList<>(getMirrorsForPlayer(playerUUID));

        // Add mirrors from other players that this player can access.
        // Uses canPlayerUseMirror which checks: global permissions, per-mirror access modes,
        // per-mirror allow lists, and per-mirror block lists.
        for (Map.Entry<UUID, Set<UUID>> entry : playerMirrors.entrySet()) {
            UUID ownerUUID = entry.getKey();
            if (!ownerUUID.equals(playerUUID)) {
                for (UUID mirrorId : entry.getValue()) {
                    MirrorEntry mirror = mirrors.get(mirrorId);
                    if (mirror != null && !mirror.privacyLocked
                            && permissions.canPlayerUseMirror(playerUUID, mirrorId, ownerUUID, PermissionData.PermissionLevel.USE)) {
                        result.add(mirror);
                    }
                }
            }
        }

        return result;
    }

    /** UUID used as mirrorId key for handheld mirror cooldowns. */
    public static final UUID HANDHELD_MIRROR_ID = new UUID(0L, 0L);

    public void recordTeleport(UUID playerUUID) {
        recordTeleport(playerUUID, HANDHELD_MIRROR_ID);
    }

    public void recordTeleport(UUID playerUUID, UUID mirrorId) {
        playerMirrorCooldowns
                .computeIfAbsent(playerUUID, k -> new HashMap<>())
                .put(mirrorId != null ? mirrorId : HANDHELD_MIRROR_ID, System.currentTimeMillis());
        setDirty();
    }

    public long getCooldownRemainingMs(UUID playerUUID, int cooldownSeconds) {
        return getCooldownRemainingMs(playerUUID, HANDHELD_MIRROR_ID, cooldownSeconds);
    }

    public long getCooldownRemainingMs(UUID playerUUID, UUID mirrorId, int cooldownSeconds) {
        Map<UUID, Long> mirrorMap = playerMirrorCooldowns.get(playerUUID);
        if (mirrorMap == null) return 0L;
        Long last = mirrorMap.get(mirrorId != null ? mirrorId : HANDHELD_MIRROR_ID);
        if (last == null) return 0L;
        long elapsed = System.currentTimeMillis() - last;
        long cooldownMs = cooldownSeconds * 1000L;
        return Math.max(0L, cooldownMs - elapsed);
    }

    public void addPendingAlarm(UUID playerUUID, String message) {
        pendingAlarmMessages.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(message);
        setDirty();
    }

    public List<String> getPendingAlarms(UUID playerUUID) {
        List<String> alarms = pendingAlarmMessages.get(playerUUID);
        return alarms != null ? new ArrayList<>(alarms) : Collections.emptyList();
    }

    public void clearPendingAlarms(UUID playerUUID) {
        pendingAlarmMessages.remove(playerUUID);
        setDirty();
    }

    // ── Favorites ──────────────────────────────────────────────────────────

    public void toggleFavorite(UUID playerUUID, UUID mirrorId) {
        Set<UUID> favs = playerFavorites.computeIfAbsent(playerUUID, k -> new HashSet<>());
        if (!favs.remove(mirrorId)) favs.add(mirrorId);
        setDirty();
    }

    public boolean isFavorite(UUID playerUUID, UUID mirrorId) {
        Set<UUID> favs = playerFavorites.get(playerUUID);
        return favs != null && favs.contains(mirrorId);
    }

    // ── Folders ────────────────────────────────────────────────────────────

    public void setMirrorFolder(UUID mirrorId, String folder) {
        if (folder == null || folder.isBlank()) {
            mirrorFolders.remove(mirrorId);
        } else {
            mirrorFolders.put(mirrorId, folder.trim());
        }
        setDirty();
    }

    public String getMirrorFolder(UUID mirrorId) {
        return mirrorFolders.getOrDefault(mirrorId, "");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag mirrorList = new ListTag();
        for (MirrorEntry entry : mirrors.values()) {
            CompoundTag mirrorTag = new CompoundTag();
            mirrorTag.putUUID("MirrorId", entry.mirrorId);
            mirrorTag.putUUID("OwnerUUID", entry.ownerUUID);
            mirrorTag.putLong("Pos", entry.pos.asLong());
            mirrorTag.putString("Dimension", entry.dimension.location().toString());
            mirrorTag.putString("Tier", entry.tier.getName());
            mirrorTag.putString("Type", entry.type.getName());
            mirrorTag.putString("Facing", entry.facing.getName());
            mirrorTag.putString("Name", entry.name);
            if (entry.privacyLocked) mirrorTag.putBoolean("PrivacyLocked", true);
            mirrorList.add(mirrorTag);
        }
        tag.put("Mirrors", mirrorList);

        // Save pending alarm messages
        ListTag alarmList = new ListTag();
        for (Map.Entry<UUID, List<String>> entry : pendingAlarmMessages.entrySet()) {
            CompoundTag alarmTag = new CompoundTag();
            alarmTag.putUUID("PlayerUUID", entry.getKey());
            ListTag msgList = new ListTag();
            for (String msg : entry.getValue()) {
                msgList.add(net.minecraft.nbt.StringTag.valueOf(msg));
            }
            alarmTag.put("Messages", msgList);
            alarmList.add(alarmTag);
        }
        tag.put("PendingAlarms", alarmList);

        // Save per-mirror cooldown map — prune entries older than the max configured cooldown
        // Use the highest tier cooldown (WOOD is typically longest) + buffer to avoid premature pruning
        long maxCooldownMs = com.ether.mirrors.util.RangeHelper.getMaxCooldownMs() + 60_000L;
        long now = System.currentTimeMillis();
        ListTag cooldownList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Long>> playerEntry : playerMirrorCooldowns.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", playerEntry.getKey());
            CompoundTag mirrorMap = new CompoundTag();
            for (Map.Entry<UUID, Long> mirrorEntry : playerEntry.getValue().entrySet()) {
                if (now - mirrorEntry.getValue() < maxCooldownMs) {
                    mirrorMap.putLong(mirrorEntry.getKey().toString(), mirrorEntry.getValue());
                }
            }
            if (!mirrorMap.isEmpty()) {
                playerTag.put("Mirrors", mirrorMap);
                cooldownList.add(playerTag);
            }
        }
        tag.put("CooldownMap", cooldownList);

        // Save favorites
        ListTag favList = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> entry : playerFavorites.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag favTag = new CompoundTag();
            favTag.putUUID("Player", entry.getKey());
            ListTag idList = new ListTag();
            for (UUID mid : entry.getValue()) {
                CompoundTag idTag = new CompoundTag();
                idTag.putUUID("MirrorId", mid);
                idList.add(idTag);
            }
            favTag.put("Ids", idList);
            favList.add(favTag);
        }
        tag.put("Favorites", favList);

        // Save folders
        CompoundTag folderTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : mirrorFolders.entrySet()) {
            folderTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("Folders", folderTag);

        return tag;
    }

    public static MirrorNetworkData load(CompoundTag tag) {
        MirrorNetworkData data = new MirrorNetworkData();
        ListTag mirrorList = tag.getList("Mirrors", Tag.TAG_COMPOUND);
        for (int i = 0; i < mirrorList.size(); i++) {
            CompoundTag mirrorTag = mirrorList.getCompound(i);
            UUID mirrorId = mirrorTag.getUUID("MirrorId");
            UUID ownerUUID = mirrorTag.getUUID("OwnerUUID");
            BlockPos pos = BlockPos.of(mirrorTag.getLong("Pos"));
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(mirrorTag.getString("Dimension")));

            MirrorTier tier = null;
            String tierName = mirrorTag.getString("Tier");
            for (MirrorTier t : MirrorTier.values()) {
                if (t.getName().equals(tierName)) { tier = t; break; }
            }

            MirrorType type = null;
            String typeName = mirrorTag.getString("Type");
            for (MirrorType t : MirrorType.values()) {
                if (t.getName().equals(typeName)) { type = t; break; }
            }

            Direction facing = Direction.NORTH; // default for legacy data
            if (mirrorTag.contains("Facing")) {
                String facingName = mirrorTag.getString("Facing");
                facing = Direction.byName(facingName);
                if (facing == null) {
                    LOGGER.warn("[EthersMirrors] Mirror {} has invalid facing '{}' in NBT — defaulting to NORTH",
                            mirrorId, facingName);
                    facing = Direction.NORTH;
                }
            }

            String name = mirrorTag.contains("Name") ? mirrorTag.getString("Name") : "";
            boolean privacyLocked = mirrorTag.contains("PrivacyLocked") && mirrorTag.getBoolean("PrivacyLocked");

            if (tier != null && type != null) {
                data.addMirror(mirrorId, ownerUUID, pos, dimension, tier, type, facing, name);
                if (privacyLocked) data.mirrors.get(mirrorId).privacyLocked = true;
            } else {
                LOGGER.warn("[EthersMirrors] Skipping mirror entry for player {} — unrecognized tier '{}' or type '{}'",
                        ownerUUID, mirrorTag.getString("Tier"), mirrorTag.getString("Type"));
            }
        }
        // Load pending alarm messages
        if (tag.contains("PendingAlarms")) {
            ListTag alarmList = tag.getList("PendingAlarms", Tag.TAG_COMPOUND);
            for (int i = 0; i < alarmList.size(); i++) {
                CompoundTag alarmTag = alarmList.getCompound(i);
                UUID playerUUID = alarmTag.getUUID("PlayerUUID");
                ListTag msgList = alarmTag.getList("Messages", Tag.TAG_STRING);
                for (int j = 0; j < msgList.size(); j++) {
                    data.addPendingAlarm(playerUUID, msgList.getString(j));
                }
            }
        }

        // Load per-mirror cooldown map
        if (tag.contains("CooldownMap")) {
            // Handle both old format (CompoundTag) and new format (ListTag)
            if (tag.get("CooldownMap") instanceof ListTag cooldownList) {
                for (int i = 0; i < cooldownList.size(); i++) {
                    CompoundTag playerTag = cooldownList.getCompound(i);
                    try {
                        UUID playerUUID = playerTag.getUUID("Player");
                        CompoundTag mirrorMap = playerTag.getCompound("Mirrors");
                        for (String key : mirrorMap.getAllKeys()) {
                            try {
                                UUID mirrorId = UUID.fromString(key);
                                data.playerMirrorCooldowns
                                        .computeIfAbsent(playerUUID, k -> new HashMap<>())
                                        .put(mirrorId, mirrorMap.getLong(key));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                // Legacy format: old per-player cooldown, migrate to handheld key
                CompoundTag cooldownTag = tag.getCompound("CooldownMap");
                for (String key : cooldownTag.getAllKeys()) {
                    try {
                        UUID playerUUID = UUID.fromString(key);
                        data.playerMirrorCooldowns
                                .computeIfAbsent(playerUUID, k -> new HashMap<>())
                                .put(MirrorNetworkData.HANDHELD_MIRROR_ID, cooldownTag.getLong(key));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        // Load favorites
        if (tag.contains("Favorites")) {
            ListTag favList = tag.getList("Favorites", Tag.TAG_COMPOUND);
            for (int i = 0; i < favList.size(); i++) {
                CompoundTag favTag = favList.getCompound(i);
                UUID playerUUID = favTag.getUUID("Player");
                ListTag idList = favTag.getList("Ids", Tag.TAG_COMPOUND);
                for (int j = 0; j < idList.size(); j++) {
                    UUID mid = idList.getCompound(j).getUUID("MirrorId");
                    data.playerFavorites.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(mid);
                }
            }
        }

        // Load folders
        if (tag.contains("Folders")) {
            CompoundTag folderTag = tag.getCompound("Folders");
            for (String key : folderTag.getAllKeys()) {
                try {
                    data.mirrorFolders.put(UUID.fromString(key), folderTag.getString(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return data;
    }

    public static MirrorNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MirrorNetworkData::load, MirrorNetworkData::new, DATA_NAME);
    }
}
