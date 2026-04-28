package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.network.MirrorsNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class ServerboundOpenPermissionsPacket {

    public ServerboundOpenPermissionsPacket() {}

    public static void encode(ServerboundOpenPermissionsPacket msg, FriendlyByteBuf buf) {}

    public static ServerboundOpenPermissionsPacket decode(FriendlyByteBuf buf) {
        return new ServerboundOpenPermissionsPacket();
    }

    public static void handle(ServerboundOpenPermissionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PermissionData permData = PermissionData.get(player.server);

            // Build granted entries: players this player has granted access to
            List<ClientboundOpenPermissionsPacket.GrantedEntry> granted = new ArrayList<>();
            Map<UUID, EnumSet<PermissionData.PermissionLevel>> grantedMap = permData.getGrantedPermissions(player.getUUID());
            for (Map.Entry<UUID, EnumSet<PermissionData.PermissionLevel>> entry : grantedMap.entrySet()) {
                UUID granteeUUID = entry.getKey();
                EnumSet<PermissionData.PermissionLevel> levels = entry.getValue();
                String name = resolveName(player.server, granteeUUID);
                granted.add(new ClientboundOpenPermissionsPacket.GrantedEntry(
                        granteeUUID, name,
                        levels.contains(PermissionData.PermissionLevel.USE),
                        levels.contains(PermissionData.PermissionLevel.VIEW_CAMERA),
                        levels.contains(PermissionData.PermissionLevel.BREAK)));
            }

            // Build request entries: pending requests from other players
            List<ClientboundOpenPermissionsPacket.RequestEntry> requests = new ArrayList<>();
            for (UUID requesterUUID : permData.getPendingRequests(player.getUUID())) {
                String name = resolveName(player.server, requesterUUID);
                requests.add(new ClientboundOpenPermissionsPacket.RequestEntry(requesterUUID, name));
            }

            MirrorsNetwork.sendToPlayer(player, new ClientboundOpenPermissionsPacket(granted, requests));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String resolveName(net.minecraft.server.MinecraftServer server, UUID uuid) {
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        var cached = server.getProfileCache().get(uuid).orElse(null);
        if (cached != null) return cached.getName();
        return uuid.toString().substring(0, 8);
    }
}
