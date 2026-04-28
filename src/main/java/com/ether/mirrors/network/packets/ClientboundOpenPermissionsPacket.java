package com.ether.mirrors.network.packets;

import com.ether.mirrors.screen.PermissionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ClientboundOpenPermissionsPacket {

    public record GrantedEntry(UUID playerUUID, String playerName,
                               boolean hasUse, boolean hasViewCamera, boolean hasBreak) {}

    public record RequestEntry(UUID requesterUUID, String requesterName) {}

    private final List<GrantedEntry> grantedEntries;
    private final List<RequestEntry> requestEntries;

    public ClientboundOpenPermissionsPacket(List<GrantedEntry> grantedEntries, List<RequestEntry> requestEntries) {
        this.grantedEntries = grantedEntries;
        this.requestEntries = requestEntries;
    }

    public static void encode(ClientboundOpenPermissionsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.grantedEntries.size());
        for (GrantedEntry e : msg.grantedEntries) {
            buf.writeUUID(e.playerUUID);
            buf.writeUtf(e.playerName, 48);
            buf.writeBoolean(e.hasUse);
            buf.writeBoolean(e.hasViewCamera);
            buf.writeBoolean(e.hasBreak);
        }
        buf.writeInt(msg.requestEntries.size());
        for (RequestEntry e : msg.requestEntries) {
            buf.writeUUID(e.requesterUUID);
            buf.writeUtf(e.requesterName, 48);
        }
    }

    public static ClientboundOpenPermissionsPacket decode(FriendlyByteBuf buf) {
        int grantedCount = buf.readInt();
        if (grantedCount < 0 || grantedCount > 1000)
            throw new io.netty.handler.codec.DecoderException("EthersMirrors: granted list size out of bounds: " + grantedCount);
        List<GrantedEntry> granted = new ArrayList<>();
        for (int i = 0; i < grantedCount; i++) {
            granted.add(new GrantedEntry(buf.readUUID(), buf.readUtf(48),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }

        int requestCount = buf.readInt();
        if (requestCount < 0 || requestCount > 1000)
            throw new io.netty.handler.codec.DecoderException("EthersMirrors: request list size out of bounds: " + requestCount);
        List<RequestEntry> requests = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            requests.add(new RequestEntry(buf.readUUID(), buf.readUtf(48)));
        }

        return new ClientboundOpenPermissionsPacket(granted, requests);
    }

    public static void handle(ClientboundOpenPermissionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            PermissionScreen screen = new PermissionScreen();

            // Convert to PermissionScreen's entry types
            List<PermissionScreen.GrantedEntry> granted = new ArrayList<>();
            for (GrantedEntry e : msg.grantedEntries) {
                granted.add(new PermissionScreen.GrantedEntry(
                        e.playerUUID, e.playerName, e.hasUse, e.hasViewCamera, e.hasBreak));
            }
            screen.setGrantedEntries(granted);

            List<PermissionScreen.RequestEntry> requests = new ArrayList<>();
            for (RequestEntry e : msg.requestEntries) {
                requests.add(new PermissionScreen.RequestEntry(e.requesterUUID, e.requesterName));
            }
            screen.setRequestEntries(requests);

            Minecraft.getInstance().setScreen(screen);
        });
        ctx.get().setPacketHandled(true);
    }
}
