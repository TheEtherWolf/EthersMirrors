package com.ether.mirrors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class PermissionData extends SavedData {

    private static final String DATA_NAME = "ethersmirrors_permissions";
    private static final Logger LOGGER = LogManager.getLogger();

    public enum PermissionLevel {
        USE("use"),
        VIEW_CAMERA("view_camera"),
        BREAK("break"),
        // NOTE: POCKET_ACCESS is reserved for future per-mirror pocket entry control.
        // Currently unused in canPlayerUseMirror checks — pocket entry is controlled by
        // enterPocket() directly (owner or invited guests via ServerboundEnterPocketPacket).
        POCKET_ACCESS("pocket_access");

        private final String name;
        PermissionLevel(String name) { this.name = name; }
        public String getName() { return name; }

        public static PermissionLevel fromName(String name) {
            for (PermissionLevel p : values()) {
                if (p.name.equals(name)) return p;
            }
            return null;
        }
    }

    public enum AccessMode {
        INHERIT("inherit"),
        PUBLIC("public"),
        PRIVATE("private");

        private final String id;
        AccessMode(String id) { this.id = id; }
        public String getId() { return id; }

        public static AccessMode fromId(String id) {
            for (AccessMode m : values()) {
                if (m.id.equals(id)) return m;
            }
            return INHERIT;
        }
    }

    // grantor UUID -> grantee UUID -> granted permissions (global, player-level)
    private final Map<UUID, Map<UUID, EnumSet<PermissionLevel>>> permissions = new java.util.concurrent.ConcurrentHashMap<>();

    // target UUID -> list of requester UUIDs (pending permission requests)
    private final Map<UUID, List<UUID>> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();

    // Phase 1B: per-mirror access control
    private final Map<UUID, AccessMode> mirrorAccessModes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, EnumSet<PermissionLevel>>> mirrorAllowList = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> mirrorBlockList = new java.util.concurrent.ConcurrentHashMap<>();

    // ---- Global permission methods (unchanged) ----

    public void grantPermission(UUID grantor, UUID grantee, PermissionLevel level) {
        permissions.computeIfAbsent(grantor, k -> new HashMap<>())
                .computeIfAbsent(grantee, k -> EnumSet.noneOf(PermissionLevel.class))
                .add(level);
        setDirty();
    }

    public void revokePermission(UUID grantor, UUID grantee, PermissionLevel level) {
        Map<UUID, EnumSet<PermissionLevel>> grantorPerms = permissions.get(grantor);
        if (grantorPerms != null) {
            EnumSet<PermissionLevel> levels = grantorPerms.get(grantee);
            if (levels != null && levels.remove(level)) {
                if (levels.isEmpty()) grantorPerms.remove(grantee);
                if (grantorPerms.isEmpty()) permissions.remove(grantor);
                setDirty();
            }
        }
    }

    public void revokeAllPermissions(UUID grantor, UUID grantee) {
        Map<UUID, EnumSet<PermissionLevel>> grantorPerms = permissions.get(grantor);
        if (grantorPerms != null && grantorPerms.remove(grantee) != null) {
            if (grantorPerms.isEmpty()) permissions.remove(grantor);
            setDirty();
        }
    }

    public boolean hasPermission(UUID grantor, UUID grantee, PermissionLevel level) {
        Map<UUID, EnumSet<PermissionLevel>> grantorPerms = permissions.get(grantor);
        if (grantorPerms == null) return false;
        EnumSet<PermissionLevel> levels = grantorPerms.get(grantee);
        return levels != null && levels.contains(level);
    }

    public EnumSet<PermissionLevel> getPermissions(UUID grantor, UUID grantee) {
        Map<UUID, EnumSet<PermissionLevel>> grantorPerms = permissions.get(grantor);
        if (grantorPerms == null) return EnumSet.noneOf(PermissionLevel.class);
        EnumSet<PermissionLevel> levels = grantorPerms.get(grantee);
        return levels != null ? EnumSet.copyOf(levels) : EnumSet.noneOf(PermissionLevel.class);
    }

    public Map<UUID, EnumSet<PermissionLevel>> getGrantedPermissions(UUID grantor) {
        Map<UUID, EnumSet<PermissionLevel>> grantorPerms = permissions.get(grantor);
        if (grantorPerms == null) return Collections.emptyMap();
        Map<UUID, EnumSet<PermissionLevel>> result = new HashMap<>();
        for (Map.Entry<UUID, EnumSet<PermissionLevel>> entry : grantorPerms.entrySet()) {
            result.put(entry.getKey(), EnumSet.copyOf(entry.getValue()));
        }
        return result;
    }

    // ---- Pending requests ----

    public void addPendingRequest(UUID targetPlayer, UUID requester) {
        List<UUID> requests = pendingRequests.computeIfAbsent(targetPlayer, k -> new ArrayList<>());
        if (!requests.contains(requester)) {
            requests.add(requester);
            setDirty();
        }
    }

    public void removePendingRequest(UUID targetPlayer, UUID requester) {
        List<UUID> requests = pendingRequests.get(targetPlayer);
        if (requests != null) {
            requests.remove(requester);
            if (requests.isEmpty()) pendingRequests.remove(targetPlayer);
            setDirty();
        }
    }

    public List<UUID> getPendingRequests(UUID targetPlayer) {
        List<UUID> requests = pendingRequests.get(targetPlayer);
        return requests != null ? Collections.unmodifiableList(requests) : Collections.emptyList();
    }

    // ---- Per-mirror access mode ----

    public void setMirrorAccessMode(UUID mirrorId, AccessMode mode) {
        if (mode == AccessMode.INHERIT) {
            mirrorAccessModes.remove(mirrorId);
        } else {
            mirrorAccessModes.put(mirrorId, mode);
        }
        setDirty();
    }

    public AccessMode getMirrorAccessMode(UUID mirrorId) {
        return mirrorAccessModes.getOrDefault(mirrorId, AccessMode.INHERIT);
    }

    // ---- Per-mirror allow list ----

    public void addMirrorAllowEntry(UUID mirrorId, UUID playerUUID, PermissionLevel level) {
        mirrorAllowList.computeIfAbsent(mirrorId, k -> new HashMap<>())
                .computeIfAbsent(playerUUID, k -> EnumSet.noneOf(PermissionLevel.class))
                .add(level);
        setDirty();
    }

    public void removeMirrorAllowEntry(UUID mirrorId, UUID playerUUID) {
        Map<UUID, EnumSet<PermissionLevel>> allowMap = mirrorAllowList.get(mirrorId);
        if (allowMap != null) {
            allowMap.remove(playerUUID);
            if (allowMap.isEmpty()) mirrorAllowList.remove(mirrorId);
            setDirty();
        }
    }

    public Map<UUID, EnumSet<PermissionLevel>> getMirrorAllowList(UUID mirrorId) {
        Map<UUID, EnumSet<PermissionLevel>> map = mirrorAllowList.get(mirrorId);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    // ---- Per-mirror block list ----

    public void addMirrorBlockEntry(UUID mirrorId, UUID playerUUID) {
        mirrorBlockList.computeIfAbsent(mirrorId, k -> new HashSet<>()).add(playerUUID);
        setDirty();
    }

    public void removeMirrorBlockEntry(UUID mirrorId, UUID playerUUID) {
        Set<UUID> blocked = mirrorBlockList.get(mirrorId);
        if (blocked != null) {
            blocked.remove(playerUUID);
            if (blocked.isEmpty()) mirrorBlockList.remove(mirrorId);
            setDirty();
        }
    }

    /**
     * Remove all per-mirror permission data when a mirror is deleted.
     * Cleans up access modes, allow list, and block list for the given mirror.
     */
    public void removeAllPermissionsForMirror(UUID mirrorId) {
        mirrorAccessModes.remove(mirrorId);
        mirrorAllowList.remove(mirrorId);
        mirrorBlockList.remove(mirrorId);
        setDirty();
    }

    public Set<UUID> getMirrorBlockList(UUID mirrorId) {
        Set<UUID> blocked = mirrorBlockList.get(mirrorId);
        return blocked != null ? Collections.unmodifiableSet(blocked) : Collections.emptySet();
    }

    // ---- Unified check ----

    /**
     * Unified permission check that respects per-mirror access modes, allow lists, and block lists.
     * @param playerUUID  the player requesting access
     * @param mirrorId    the specific mirror being accessed
     * @param ownerUUID   the mirror's owner
     * @param level       the permission level being checked
     * @return true if the player is allowed
     */
    public boolean canPlayerUseMirror(UUID playerUUID, UUID mirrorId, UUID ownerUUID, PermissionLevel level) {
        if (ownerUUID == null) return false; // Mirror with no owner is accessible to no one
        // Owner always has access
        if (playerUUID.equals(ownerUUID)) return true;

        // Blocked players are always denied (per-mirror block list)
        if (mirrorBlockList.getOrDefault(mirrorId, Set.of()).contains(playerUUID)) return false;

        AccessMode mode = mirrorAccessModes.getOrDefault(mirrorId, AccessMode.INHERIT);

        // Public mode: anyone can use
        if (mode == AccessMode.PUBLIC) return true;

        // Check per-mirror allow list
        Map<UUID, EnumSet<PermissionLevel>> allowMap = mirrorAllowList.get(mirrorId);
        if (allowMap != null) {
            EnumSet<PermissionLevel> allowed = allowMap.get(playerUUID);
            if (allowed != null && allowed.contains(level)) return true;
        }

        // Private mode: deny if not on allow list
        if (mode == AccessMode.PRIVATE) return false;

        // INHERIT mode: fall back to global player-level permission
        return hasPermission(ownerUUID, playerUUID, level);
    }

    /**
     * Overload that checks PRIVACY_LOCK before the standard permission check.
     */
    public boolean canPlayerUseMirror(UUID playerUUID, UUID mirrorId, UUID ownerUUID,
                                       PermissionLevel level, boolean privacyLockActive) {
        if (playerUUID == null) return false;
        if (playerUUID.equals(ownerUUID)) return true;
        if (privacyLockActive) return false;
        return canPlayerUseMirror(playerUUID, mirrorId, ownerUUID, level);
    }

    // ---- NBT Save/Load ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        // Save global permissions
        ListTag permList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, EnumSet<PermissionLevel>>> grantorEntry : permissions.entrySet()) {
            for (Map.Entry<UUID, EnumSet<PermissionLevel>> granteeEntry : grantorEntry.getValue().entrySet()) {
                CompoundTag permTag = new CompoundTag();
                permTag.putUUID("Grantor", grantorEntry.getKey());
                permTag.putUUID("Grantee", granteeEntry.getKey());
                ListTag levelList = new ListTag();
                for (PermissionLevel level : granteeEntry.getValue()) {
                    CompoundTag levelTag = new CompoundTag();
                    levelTag.putString("Level", level.getName());
                    levelList.add(levelTag);
                }
                permTag.put("Levels", levelList);
                permList.add(permTag);
            }
        }
        tag.put("Permissions", permList);

        // Save pending requests
        ListTag requestList = new ListTag();
        for (Map.Entry<UUID, List<UUID>> entry : pendingRequests.entrySet()) {
            CompoundTag requestTag = new CompoundTag();
            requestTag.putUUID("Target", entry.getKey());
            ListTag requesterList = new ListTag();
            for (UUID requester : entry.getValue()) {
                CompoundTag reqTag = new CompoundTag();
                reqTag.putUUID("Requester", requester);
                requesterList.add(reqTag);
            }
            requestTag.put("Requesters", requesterList);
            requestList.add(requestTag);
        }
        tag.put("PendingRequests", requestList);

        // Save mirror access modes
        ListTag modeList = new ListTag();
        for (Map.Entry<UUID, AccessMode> entry : mirrorAccessModes.entrySet()) {
            CompoundTag modeTag = new CompoundTag();
            modeTag.putUUID("MirrorId", entry.getKey());
            modeTag.putString("Mode", entry.getValue().getId());
            modeList.add(modeTag);
        }
        tag.put("MirrorAccessModes", modeList);

        // Save mirror allow list
        ListTag allowList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, EnumSet<PermissionLevel>>> mirrorEntry : mirrorAllowList.entrySet()) {
            for (Map.Entry<UUID, EnumSet<PermissionLevel>> playerEntry : mirrorEntry.getValue().entrySet()) {
                CompoundTag allowTag = new CompoundTag();
                allowTag.putUUID("MirrorId", mirrorEntry.getKey());
                allowTag.putUUID("PlayerUUID", playerEntry.getKey());
                ListTag levelList = new ListTag();
                for (PermissionLevel level : playerEntry.getValue()) {
                    CompoundTag levelTag = new CompoundTag();
                    levelTag.putString("Level", level.getName());
                    levelList.add(levelTag);
                }
                allowTag.put("Levels", levelList);
                allowList.add(allowTag);
            }
        }
        tag.put("MirrorAllowList", allowList);

        // Save mirror block list
        ListTag blockList = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> entry : mirrorBlockList.entrySet()) {
            for (UUID blockedPlayer : entry.getValue()) {
                CompoundTag blockTag = new CompoundTag();
                blockTag.putUUID("MirrorId", entry.getKey());
                blockTag.putUUID("PlayerUUID", blockedPlayer);
                blockList.add(blockTag);
            }
        }
        tag.put("MirrorBlockList", blockList);

        return tag;
    }

    public static PermissionData load(CompoundTag tag) {
        PermissionData data = new PermissionData();

        // Load global permissions
        ListTag permList = tag.getList("Permissions", Tag.TAG_COMPOUND);
        for (int i = 0; i < permList.size(); i++) {
            CompoundTag permTag = permList.getCompound(i);
            UUID grantor = permTag.getUUID("Grantor");
            UUID grantee = permTag.getUUID("Grantee");
            ListTag levelList = permTag.getList("Levels", Tag.TAG_COMPOUND);
            for (int j = 0; j < levelList.size(); j++) {
                String levelName = levelList.getCompound(j).getString("Level");
                PermissionLevel level = PermissionLevel.fromName(levelName);
                if (level != null) data.grantPermission(grantor, grantee, level);
                else LOGGER.warn("[EthersMirrors] Unknown global permission level '{}' in save data — skipping", levelName);
            }
        }

        // Load pending requests
        ListTag requestList = tag.getList("PendingRequests", Tag.TAG_COMPOUND);
        for (int i = 0; i < requestList.size(); i++) {
            CompoundTag requestTag = requestList.getCompound(i);
            UUID target = requestTag.getUUID("Target");
            ListTag requesterList = requestTag.getList("Requesters", Tag.TAG_COMPOUND);
            for (int j = 0; j < requesterList.size(); j++) {
                UUID requester = requesterList.getCompound(j).getUUID("Requester");
                data.addPendingRequest(target, requester);
            }
        }

        // Load mirror access modes
        ListTag modeList = tag.getList("MirrorAccessModes", Tag.TAG_COMPOUND);
        for (int i = 0; i < modeList.size(); i++) {
            CompoundTag modeTag = modeList.getCompound(i);
            UUID mirrorId = modeTag.getUUID("MirrorId");
            AccessMode mode = AccessMode.fromId(modeTag.getString("Mode"));
            data.mirrorAccessModes.put(mirrorId, mode);
        }

        // Load mirror allow list
        ListTag allowList = tag.getList("MirrorAllowList", Tag.TAG_COMPOUND);
        for (int i = 0; i < allowList.size(); i++) {
            CompoundTag allowTag = allowList.getCompound(i);
            UUID mirrorId = allowTag.getUUID("MirrorId");
            UUID playerUUID = allowTag.getUUID("PlayerUUID");
            ListTag levelList = allowTag.getList("Levels", Tag.TAG_COMPOUND);
            for (int j = 0; j < levelList.size(); j++) {
                PermissionLevel level = PermissionLevel.fromName(levelList.getCompound(j).getString("Level"));
                if (level != null) data.addMirrorAllowEntry(mirrorId, playerUUID, level);
            }
        }

        // Load mirror block list
        ListTag blockList = tag.getList("MirrorBlockList", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag blockTag = blockList.getCompound(i);
            UUID mirrorId = blockTag.getUUID("MirrorId");
            UUID playerUUID = blockTag.getUUID("PlayerUUID");
            data.addMirrorBlockEntry(mirrorId, playerUUID);
        }

        return data;
    }

    public static PermissionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PermissionData::load, PermissionData::new, DATA_NAME);
    }
}
