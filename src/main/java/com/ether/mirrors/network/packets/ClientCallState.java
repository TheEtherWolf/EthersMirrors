package com.ether.mirrors.network.packets;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Client-side state for tracking active mirror calls.
 */
public class ClientCallState {

    private static UUID incomingCallId;
    private static String incomingCallerName;
    private static UUID incomingCallerUUID;

    private static UUID activeCallId;
    private static String activeOtherPlayerName;

    private static UUID outgoingCallId;
    private static String outgoingCalleeName;

    public static void setIncomingCall(UUID callId, String callerName, UUID callerUUID) {
        incomingCallId = callId;
        incomingCallerName = callerName;
        incomingCallerUUID = callerUUID;
    }

    public static void clearIncomingCall() {
        incomingCallId = null;
        incomingCallerName = null;
        incomingCallerUUID = null;
    }

    public static void setActiveCall(UUID callId, String otherPlayerName) {
        activeCallId = callId;
        activeOtherPlayerName = otherPlayerName;
        clearIncomingCall();
        clearOutgoingCall();
    }

    public static void clearActiveCall() {
        activeCallId = null;
        activeOtherPlayerName = null;
    }

    public static void setOutgoingCall(UUID callId, String calleeName) {
        outgoingCallId = callId;
        outgoingCalleeName = calleeName;
    }

    public static void clearOutgoingCall() {
        outgoingCallId = null;
        outgoingCalleeName = null;
    }

    /** Clears all call state. Call on client disconnect to prevent stale state across sessions. */
    public static void reset() {
        incomingCallId = null;
        incomingCallerName = null;
        incomingCallerUUID = null;
        activeCallId = null;
        activeOtherPlayerName = null;
        outgoingCallId = null;
        outgoingCalleeName = null;
    }

    public static boolean hasIncomingCall() { return incomingCallId != null; }
    public static boolean hasActiveCall() { return activeCallId != null; }
    public static boolean hasOutgoingCall() { return outgoingCallId != null; }

    @Nullable public static UUID getIncomingCallId() { return incomingCallId; }
    @Nullable public static String getIncomingCallerName() { return incomingCallerName; }
    @Nullable public static UUID getIncomingCallerUUID() { return incomingCallerUUID; }
    @Nullable public static UUID getActiveCallId() { return activeCallId; }
    @Nullable public static String getActiveOtherPlayerName() { return activeOtherPlayerName; }
    @Nullable public static UUID getOutgoingCallId() { return outgoingCallId; }
    @Nullable public static String getOutgoingCalleeName() { return outgoingCalleeName; }
}
