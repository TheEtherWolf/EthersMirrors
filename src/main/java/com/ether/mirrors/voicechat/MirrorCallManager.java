package com.ether.mirrors.voicechat;

import com.ether.mirrors.EthersMirrors;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active mirror calls and their associated SVC voice groups.
 */
public class MirrorCallManager {

    private static final MirrorCallManager INSTANCE = new MirrorCallManager();

    public static MirrorCallManager getInstance() { return INSTANCE; }

    public static class ActiveCall {
        public final UUID callId;
        public final UUID callerUUID;
        public final UUID calleeUUID;
        public final long startTime;
        private volatile Group voiceGroup;
        private volatile CallState state;

        public ActiveCall(UUID callerUUID, UUID calleeUUID) {
            this.callId = UUID.randomUUID();
            this.callerUUID = callerUUID;
            this.calleeUUID = calleeUUID;
            this.startTime = System.currentTimeMillis();
            this.state = CallState.RINGING;
        }

        public CallState getState() { return state; }
        public void setState(CallState state) { this.state = state; }
        public Group getVoiceGroup() { return voiceGroup; }
        public void setVoiceGroup(Group group) { this.voiceGroup = group; }
    }

    public enum CallState {
        RINGING,
        CONNECTED,
        ENDED
    }

    // callId -> ActiveCall
    private final Map<UUID, ActiveCall> activeCalls = new ConcurrentHashMap<>();
    // playerUUID -> callId (quick lookup for who is in a call)
    private final Map<UUID, UUID> playerCalls = new ConcurrentHashMap<>();

    @Nullable
    public ActiveCall initiateCall(UUID callerUUID, UUID calleeUUID) {
        // Check if either player is already in a call
        if (playerCalls.containsKey(callerUUID) || playerCalls.containsKey(calleeUUID)) {
            return null;
        }

        ActiveCall call = new ActiveCall(callerUUID, calleeUUID);
        activeCalls.put(call.callId, call);
        playerCalls.put(callerUUID, call.callId);
        playerCalls.put(calleeUUID, call.callId);

        return call;
    }

    public boolean acceptCall(UUID callId, ServerPlayer caller, ServerPlayer callee) {
        ActiveCall call = activeCalls.get(callId);
        if (call == null || call.getState() != CallState.RINGING) return false;

        call.setState(CallState.CONNECTED);

        // Create SVC voice group if available
        if (isSVCAvailable()) {
            try {
                createVoiceGroup(call, caller, callee);
            } catch (Exception e) {
                EthersMirrors.LOGGER.warn("Failed to create voice group for mirror call", e);
            }
        }

        return true;
    }

    public void endCall(UUID callId) {
        endCall(callId, null);
    }

    /**
     * End a call and optionally notify both participants via packet.
     * Pass a non-null server to send ClientboundCallEndedPacket to online participants.
     */
    public void endCall(UUID callId, @Nullable net.minecraft.server.MinecraftServer server) {
        ActiveCall call = activeCalls.remove(callId);
        if (call == null) return;

        call.setState(CallState.ENDED);
        playerCalls.remove(call.callerUUID);
        playerCalls.remove(call.calleeUUID);

        // Notify both participants that the call has ended.
        // Include callId so the client can guard against delayed packets closing a newer call.
        if (server != null) {
            net.minecraft.server.level.ServerPlayer caller = server.getPlayerList().getPlayer(call.callerUUID);
            net.minecraft.server.level.ServerPlayer callee = server.getPlayerList().getPlayer(call.calleeUUID);
            if (caller != null) {
                com.ether.mirrors.network.MirrorsNetwork.sendToPlayer(caller,
                        new com.ether.mirrors.network.packets.ClientboundCallEndedPacket(callId));
            }
            if (callee != null) {
                com.ether.mirrors.network.MirrorsNetwork.sendToPlayer(callee,
                        new com.ether.mirrors.network.packets.ClientboundCallEndedPacket(callId));
            }
        }

        // Clean up SVC group
        if (isSVCAvailable() && call.getVoiceGroup() != null) {
            try {
                cleanupVoiceGroup(call);
            } catch (Exception e) {
                EthersMirrors.LOGGER.warn("Failed to cleanup voice group", e);
            } finally {
                call.setVoiceGroup(null); // Always clear reference to prevent memory leak
            }
        }
    }

    @Nullable
    public ActiveCall getCallForPlayer(UUID playerUUID) {
        UUID callId = playerCalls.get(playerUUID);
        return callId != null ? activeCalls.get(callId) : null;
    }

    @Nullable
    public ActiveCall getCall(UUID callId) {
        return activeCalls.get(callId);
    }

    public boolean isInCall(UUID playerUUID) {
        return playerCalls.containsKey(playerUUID);
    }

    /** Clear all call state. Called on server start to prevent stale calls from a previous session. */
    public void clearAll() {
        activeCalls.clear();
        playerCalls.clear();
    }

    /**
     * End all calls involving a player (e.g., on disconnect).
     */
    public void endAllCallsForPlayer(UUID playerUUID) {
        endAllCallsForPlayer(playerUUID, null);
    }

    public void endAllCallsForPlayer(UUID playerUUID, @Nullable net.minecraft.server.MinecraftServer server) {
        UUID callId = playerCalls.get(playerUUID);
        if (callId != null) {
            endCall(callId, server);
        }
    }

    private static final long CONNECTED_CALL_MAX_MS = 4 * 60 * 60 * 1000L; // 4 hours

    /**
     * Check for timed-out ringing calls and cancel them.
     */
    public void tickTimeouts(long timeoutMs, @Nullable net.minecraft.server.MinecraftServer server) {
        List<UUID> toCancel = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ActiveCall call : activeCalls.values()) {
            if (call.getState() == CallState.RINGING) {
                if (now - call.startTime > timeoutMs) {
                    toCancel.add(call.callId);
                }
            } else if (call.getState() == CallState.CONNECTED) {
                if (now - call.startTime > CONNECTED_CALL_MAX_MS) {
                    toCancel.add(call.callId);
                }
            }
        }
        toCancel.forEach(id -> endCall(id, server));
    }

    // Cache whether the SVC class is on the classpath — Class.forName is slow and never changes.
    private static boolean svcClassChecked = false;
    private static boolean svcClassPresent = false;

    private boolean isSVCAvailable() {
        if (!svcClassChecked) {
            try {
                Class.forName("de.maxhenkel.voicechat.api.VoicechatApi");
                svcClassPresent = true;
            } catch (ClassNotFoundException e) {
                svcClassPresent = false;
            }
            svcClassChecked = true;
        }
        // API field is checked each time — it may be null early in startup even if class is present.
        return svcClassPresent && MirrorsVoicechatPlugin.API != null;
    }

    private void createVoiceGroup(ActiveCall call, ServerPlayer caller, ServerPlayer callee) {
        VoicechatServerApi serverApi = (VoicechatServerApi) MirrorsVoicechatPlugin.API;
        if (serverApi == null) return;

        Group group = serverApi.groupBuilder()
                .setName("Mirror Call")
                .setType(Group.Type.ISOLATED)
                .setPersistent(false)
                .build();

        call.setVoiceGroup(group);

        // Add both players to the group.
        // Guard: skip players already in a group (e.g. Walkie Talkie mod) — overwriting would
        // silently kick them out of their existing channel.
        VoicechatConnection callerConn = serverApi.getConnectionOf(caller.getUUID());
        VoicechatConnection calleeConn = serverApi.getConnectionOf(callee.getUUID());

        if (callerConn != null && callerConn.getGroup() == null) callerConn.setGroup(group);
        if (calleeConn != null && calleeConn.getGroup() == null) calleeConn.setGroup(group);
    }

    private void cleanupVoiceGroup(ActiveCall call) {
        VoicechatServerApi serverApi = (VoicechatServerApi) MirrorsVoicechatPlugin.API;
        if (serverApi == null) return;

        Group ourGroup = call.getVoiceGroup();

        // Only remove a player from the group if they are still in *our* group —
        // they may have joined a different group (Walkie Talkie) between call start and end.
        VoicechatConnection callerConn = serverApi.getConnectionOf(call.callerUUID);
        VoicechatConnection calleeConn = serverApi.getConnectionOf(call.calleeUUID);

        if (callerConn != null && ourGroup.equals(callerConn.getGroup())) callerConn.setGroup(null);
        if (calleeConn != null && ourGroup.equals(calleeConn.getGroup())) calleeConn.setGroup(null);
    }
}
