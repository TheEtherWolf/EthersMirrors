package com.ether.mirrors.data;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CallLogData extends SavedData {

    private static final String DATA_NAME   = "ethersmirrors_calllog";
    private static final int    MAX_ENTRIES = 500;
    private static final long   WINDOW_MS   = 30L * 24 * 60 * 60 * 1000; // 30 days

    // ── Event type ────────────────────────────────────────────────────────────

    public enum EventType {
        CALL_CONNECTED(0), CALL_MISSED(1), CALL_DECLINED(2),
        TELEPORT(3), PERMISSION_GRANT(4), PERMISSION_REVOKE(5);

        public final int id;
        EventType(int id) { this.id = id; }

        public static EventType fromId(int id) {
            for (EventType t : values()) if (t.id == id) return t;
            return CALL_MISSED;
        }
    }

    // ── Entry ─────────────────────────────────────────────────────────────────

    public static class LogEntry {
        public final EventType type;
        /** For calls: true = we initiated. For teleports: true = entering (in). */
        public final boolean   outgoing;
        /** Other player's name, or mirror/target name for system events. */
        public final String    playerName;
        /** Other player UUID; null for system events with no player. */
        public final UUID      playerUUID;
        public final long      timestampMs;
        /** Seconds; -1 if not applicable. */
        public final int       durationSeconds;
        /** e.g. "minecraft:overworld". Empty string if N/A. */
        public final String    dimensionId;
        /** Mirror/permission label. Empty string if N/A. */
        public final String    mirrorName;

        public LogEntry(EventType type, boolean outgoing, String playerName, UUID playerUUID,
                        long timestampMs, int durationSeconds, String dimensionId, String mirrorName) {
            this.type            = type;
            this.outgoing        = outgoing;
            this.playerName      = playerName;
            this.playerUUID      = playerUUID;
            this.timestampMs     = timestampMs;
            this.durationSeconds = durationSeconds;
            this.dimensionId     = dimensionId;
            this.mirrorName      = mirrorName;
        }
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    // ownerUUID → newest-first list of entries
    private final Map<UUID, List<LogEntry>> logs = new ConcurrentHashMap<>();

    // callId → System.currentTimeMillis() when call was accepted (in-memory only, not persisted)
    private final Map<UUID, Long> pendingConnectMs = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call accepted: store connect time so we can compute duration on end. */
    public void recordCallConnect(UUID callId) {
        pendingConnectMs.put(callId, System.currentTimeMillis());
    }

    /**
     * Call ended normally: write CALL_CONNECTED entry for both participants.
     * @param ownerUUID player we are writing for
     * @param callId    used to look up stored connect time
     * @param outgoing  true if ownerUUID was the caller
     * @param otherName display name of the other participant
     * @param otherUUID UUID of the other participant
     * @param dimId     dimension the call took place in (caller's dimension at connect)
     * @param removeConnectMs true only for the first call (cleans up the pending map)
     */
    public void recordCallEnd(UUID ownerUUID, UUID callId, boolean outgoing,
                               String otherName, UUID otherUUID,
                               String dimId, boolean removeConnectMs) {
        Long connectMs = removeConnectMs
                ? pendingConnectMs.remove(callId)
                : pendingConnectMs.get(callId);
        int duration = connectMs != null
                ? Math.max(0, (int) ((System.currentTimeMillis() - connectMs) / 1000))
                : -1;
        addEntry(ownerUUID, new LogEntry(
                EventType.CALL_CONNECTED, outgoing,
                otherName, otherUUID,
                System.currentTimeMillis(), duration, dimId, ""));
    }

    public void recordMissedCall(UUID calleeUUID, String callerName, UUID callerUUID) {
        addEntry(calleeUUID, new LogEntry(
                EventType.CALL_MISSED, false,
                callerName, callerUUID,
                System.currentTimeMillis(), -1, "", ""));
    }

    public void recordDeclinedCall(UUID ownerUUID, boolean outgoing,
                                    String otherName, UUID otherUUID) {
        addEntry(ownerUUID, new LogEntry(
                EventType.CALL_DECLINED, outgoing,
                otherName, otherUUID,
                System.currentTimeMillis(), -1, "", ""));
    }

    /** @param entering true = player entered the target mirror/pocket */
    public void recordTeleport(UUID playerUUID, boolean entering,
                                String targetName, String dimId) {
        addEntry(playerUUID, new LogEntry(
                EventType.TELEPORT, entering,
                targetName, null,
                System.currentTimeMillis(), -1, dimId, targetName));
    }

    /** permLabel e.g. "CALL", "ENTER", "VIEW" */
    public void recordPermissionGrant(UUID ownerUUID,
                                       String targetName, UUID targetUUID,
                                       String permLabel) {
        addEntry(ownerUUID, new LogEntry(
                EventType.PERMISSION_GRANT, true,
                targetName, targetUUID,
                System.currentTimeMillis(), -1, "", permLabel));
    }

    public void recordPermissionRevoke(UUID ownerUUID,
                                        String targetName, UUID targetUUID) {
        addEntry(ownerUUID, new LogEntry(
                EventType.PERMISSION_REVOKE, true,
                targetName, targetUUID,
                System.currentTimeMillis(), -1, "", ""));
    }

    /** Returns a sorted (newest first), 30-day-windowed snapshot. */
    public List<LogEntry> getEntries(UUID player) {
        List<LogEntry> list = logs.get(player);
        if (list == null) return Collections.emptyList();
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        return list.stream()
                .filter(e -> e.timestampMs >= cutoff)
                .collect(Collectors.toList());
    }

    public void clearAll(UUID player) {
        logs.remove(player);
        setDirty();
    }

    public void clearMissed(UUID player) {
        List<LogEntry> list = logs.get(player);
        if (list != null) {
            list.removeIf(e -> e.type == EventType.CALL_MISSED);
            setDirty();
        }
    }

    /**
     * Delete entry by its original index in the stored list (as sent to client).
     * The client sends the index into the full (unfiltered) list.
     */
    public void deleteEntry(UUID player, int index) {
        List<LogEntry> list = logs.get(player);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            setDirty();
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void addEntry(UUID owner, LogEntry entry) {
        List<LogEntry> list = logs.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(0, entry); // newest first
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        list.removeIf(e -> e.timestampMs < cutoff);
        if (list.size() > MAX_ENTRIES) {
            list.subList(MAX_ENTRIES, list.size()).clear();
        }
        setDirty();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<LogEntry>> pe : logs.entrySet()) {
            CompoundTag pt = new CompoundTag();
            pt.putUUID("P", pe.getKey());
            ListTag el = new ListTag();
            for (LogEntry e : pe.getValue()) {
                CompoundTag et = new CompoundTag();
                et.putByte("T",  (byte) e.type.id);
                et.putBoolean("O", e.outgoing);
                et.putString("N", e.playerName);
                if (e.playerUUID != null) et.putUUID("U", e.playerUUID);
                et.putLong("Ms",  e.timestampMs);
                et.putInt("D",   e.durationSeconds);
                et.putString("Di", e.dimensionId);
                et.putString("M",  e.mirrorName);
                el.add(et);
            }
            pt.put("E", el);
            players.add(pt);
        }
        tag.put("Players", players);
        return tag;
    }

    public static CallLogData load(CompoundTag tag) {
        CallLogData data = new CallLogData();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pt  = players.getCompound(i);
            UUID player     = pt.getUUID("P");
            List<LogEntry> entries = new ArrayList<>();
            ListTag el = pt.getList("E", Tag.TAG_COMPOUND);
            for (int j = 0; j < el.size(); j++) {
                CompoundTag et = el.getCompound(j);
                entries.add(new LogEntry(
                        EventType.fromId(et.getByte("T") & 0xFF),
                        et.getBoolean("O"),
                        et.getString("N"),
                        et.contains("U") ? et.getUUID("U") : null,
                        et.getLong("Ms"),
                        et.getInt("D"),
                        et.getString("Di"),
                        et.getString("M")
                ));
            }
            data.logs.put(player, entries);
        }
        return data;
    }

    public static CallLogData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CallLogData::load, CallLogData::new, DATA_NAME);
    }
}
