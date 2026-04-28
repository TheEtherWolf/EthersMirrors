package com.ether.mirrors.api.event;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;

@Cancelable
public class MirrorCallEvent extends Event {
    private final UUID callerUUID;
    private final UUID calleeUUID;

    public MirrorCallEvent(UUID callerUUID, UUID calleeUUID) {
        this.callerUUID = callerUUID;
        this.calleeUUID = calleeUUID;
    }

    public UUID getCallerUUID() { return callerUUID; }
    public UUID getCalleeUUID() { return calleeUUID; }
}
