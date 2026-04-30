package com.ether.mirrors.network;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class MirrorsNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EthersMirrors.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        // Client -> Server
        CHANNEL.registerMessage(id++, ServerboundOpenMirrorPacket.class,
                ServerboundOpenMirrorPacket::encode, ServerboundOpenMirrorPacket::decode,
                ServerboundOpenMirrorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundTeleportRequestPacket.class,
                ServerboundTeleportRequestPacket::encode, ServerboundTeleportRequestPacket::decode,
                ServerboundTeleportRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundPermissionRequestPacket.class,
                ServerboundPermissionRequestPacket::encode, ServerboundPermissionRequestPacket::decode,
                ServerboundPermissionRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundPermissionResponsePacket.class,
                ServerboundPermissionResponsePacket::encode, ServerboundPermissionResponsePacket::decode,
                ServerboundPermissionResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundSetMirrorNamePacket.class,
                ServerboundSetMirrorNamePacket::encode, ServerboundSetMirrorNamePacket::decode,
                ServerboundSetMirrorNamePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Server -> Client
        CHANNEL.registerMessage(id++, ClientboundMirrorListPacket.class,
                ClientboundMirrorListPacket::encode, ClientboundMirrorListPacket::decode,
                ClientboundMirrorListPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, ClientboundPermissionNotifyPacket.class,
                ClientboundPermissionNotifyPacket::encode, ClientboundPermissionNotifyPacket::decode,
                ClientboundPermissionNotifyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Call packets - Client -> Server
        CHANNEL.registerMessage(id++, ServerboundCallRequestPacket.class,
                ServerboundCallRequestPacket::encode, ServerboundCallRequestPacket::decode,
                ServerboundCallRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundCallResponsePacket.class,
                ServerboundCallResponsePacket::encode, ServerboundCallResponsePacket::decode,
                ServerboundCallResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundCallEndPacket.class,
                ServerboundCallEndPacket::encode, ServerboundCallEndPacket::decode,
                ServerboundCallEndPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Call packets - Server -> Client
        CHANNEL.registerMessage(id++, ClientboundCallIncomingPacket.class,
                ClientboundCallIncomingPacket::encode, ClientboundCallIncomingPacket::decode,
                ClientboundCallIncomingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, ClientboundCallRingingPacket.class,
                ClientboundCallRingingPacket::encode, ClientboundCallRingingPacket::decode,
                ClientboundCallRingingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, ClientboundCallEstablishedPacket.class,
                ClientboundCallEstablishedPacket::encode, ClientboundCallEstablishedPacket::decode,
                ClientboundCallEstablishedPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Camera view packets
        CHANNEL.registerMessage(id++, ServerboundCameraViewPacket.class,
                ServerboundCameraViewPacket::encode, ServerboundCameraViewPacket::decode,
                ServerboundCameraViewPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ClientboundStartCameraPacket.class,
                ClientboundStartCameraPacket::encode, ClientboundStartCameraPacket::decode,
                ClientboundStartCameraPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Handheld mirror use packets
        CHANNEL.registerMessage(id++, ServerboundOpenHandheldMirrorPacket.class,
                ServerboundOpenHandheldMirrorPacket::encode, ServerboundOpenHandheldMirrorPacket::decode,
                ServerboundOpenHandheldMirrorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundEnterPocketHandheldPacket.class,
                ServerboundEnterPocketHandheldPacket::encode, ServerboundEnterPocketHandheldPacket::decode,
                ServerboundEnterPocketHandheldPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Pocket expansion packets
        CHANNEL.registerMessage(id++, ServerboundExpandPocketPacket.class,
                ServerboundExpandPocketPacket::encode, ServerboundExpandPocketPacket::decode,
                ServerboundExpandPocketPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ClientboundPocketInfoPacket.class,
                ClientboundPocketInfoPacket::encode, ClientboundPocketInfoPacket::decode,
                ClientboundPocketInfoPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Phase 1B: Per-mirror access control
        CHANNEL.registerMessage(id++, ServerboundSetMirrorAccessModePacket.class,
                ServerboundSetMirrorAccessModePacket::encode, ServerboundSetMirrorAccessModePacket::decode,
                ServerboundSetMirrorAccessModePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundSetMirrorOverridePacket.class,
                ServerboundSetMirrorOverridePacket::encode, ServerboundSetMirrorOverridePacket::decode,
                ServerboundSetMirrorOverridePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Phase 2: Mirror management screen
        CHANNEL.registerMessage(id++, ServerboundOpenMirrorManagementPacket.class,
                ServerboundOpenMirrorManagementPacket::encode, ServerboundOpenMirrorManagementPacket::decode,
                ServerboundOpenMirrorManagementPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ClientboundOpenMirrorManagementPacket.class,
                ClientboundOpenMirrorManagementPacket::encode, ClientboundOpenMirrorManagementPacket::decode,
                ClientboundOpenMirrorManagementPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Phase 3: Upgrade system
        CHANNEL.registerMessage(id++, ServerboundApplyUpgradePacket.class,
                ServerboundApplyUpgradePacket::encode, ServerboundApplyUpgradePacket::decode,
                ServerboundApplyUpgradePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundRemoveUpgradePacket.class,
                ServerboundRemoveUpgradePacket::encode, ServerboundRemoveUpgradePacket::decode,
                ServerboundRemoveUpgradePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Phase 6: Tier upgrade system
        CHANNEL.registerMessage(id++, ServerboundUpgradeMirrorTierPacket.class,
                ServerboundUpgradeMirrorTierPacket::encode, ServerboundUpgradeMirrorTierPacket::decode,
                ServerboundUpgradeMirrorTierPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Phase 7: Pocket entry
        CHANNEL.registerMessage(id++, ServerboundEnterPocketPacket.class,
                ServerboundEnterPocketPacket::encode, ServerboundEnterPocketPacket::decode,
                ServerboundEnterPocketPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Phase 7: Pocket theme
        CHANNEL.registerMessage(id++, ServerboundSetPocketThemePacket.class,
                ServerboundSetPocketThemePacket::encode, ServerboundSetPocketThemePacket::decode,
                ServerboundSetPocketThemePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Phase 8: Favorites and folders
        CHANNEL.registerMessage(id++, ServerboundToggleFavoritePacket.class,
                ServerboundToggleFavoritePacket::encode, ServerboundToggleFavoritePacket::decode,
                ServerboundToggleFavoritePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundSetMirrorFolderPacket.class,
                ServerboundSetMirrorFolderPacket::encode, ServerboundSetMirrorFolderPacket::decode,
                ServerboundSetMirrorFolderPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Phase 9: Xaero's Minimap waypoint sync
        CHANNEL.registerMessage(id++, ClientboundSyncWaypointsPacket.class,
                ClientboundSyncWaypointsPacket::encode, ClientboundSyncWaypointsPacket::decode,
                ClientboundSyncWaypointsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Mirror Shard teleport
        CHANNEL.registerMessage(id++, ServerboundShardTeleportPacket.class,
                ServerboundShardTeleportPacket::encode, ServerboundShardTeleportPacket::decode,
                ServerboundShardTeleportPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Mirror DM
        CHANNEL.registerMessage(id++, ClientboundMirrorMessagePacket.class,
                ClientboundMirrorMessagePacket::encode, ClientboundMirrorMessagePacket::decode,
                ClientboundMirrorMessagePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Pocket time/weather control
        CHANNEL.registerMessage(id++, ServerboundSetPocketTimePacket.class,
                ServerboundSetPocketTimePacket::encode, ServerboundSetPocketTimePacket::decode,
                ServerboundSetPocketTimePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ServerboundSetPocketWeatherPacket.class,
                ServerboundSetPocketWeatherPacket::encode, ServerboundSetPocketWeatherPacket::decode,
                ServerboundSetPocketWeatherPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // TIME_LOCK toggle
        CHANNEL.registerMessage(id++, ServerboundTimeLockTogglePacket.class,
                ServerboundTimeLockTogglePacket::encode, ServerboundTimeLockTogglePacket::decode,
                ServerboundTimeLockTogglePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // WARP_TARGET lock/unlock toggle
        CHANNEL.registerMessage(id++, ServerboundWarpLockTogglePacket.class,
                ServerboundWarpLockTogglePacket::encode, ServerboundWarpLockTogglePacket::decode,
                ServerboundWarpLockTogglePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Mirror activation (first use)
        CHANNEL.registerMessage(id++, ServerboundActivateMirrorPacket.class,
                ServerboundActivateMirrorPacket::encode, ServerboundActivateMirrorPacket::decode,
                ServerboundActivateMirrorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Call ended notification
        CHANNEL.registerMessage(id++, ClientboundCallEndedPacket.class,
                ClientboundCallEndedPacket::encode, ClientboundCallEndedPacket::decode,
                ClientboundCallEndedPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Global permissions screen
        CHANNEL.registerMessage(id++, ServerboundOpenPermissionsPacket.class,
                ServerboundOpenPermissionsPacket::encode, ServerboundOpenPermissionsPacket::decode,
                ServerboundOpenPermissionsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ClientboundOpenPermissionsPacket.class,
                ClientboundOpenPermissionsPacket::encode, ClientboundOpenPermissionsPacket::decode,
                ClientboundOpenPermissionsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Pick up mirror
        CHANNEL.registerMessage(id++, ServerboundPickUpMirrorPacket.class,
                ServerboundPickUpMirrorPacket::encode, ServerboundPickUpMirrorPacket::decode,
                ServerboundPickUpMirrorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Permission ladder (PermissionScreen MY NETWORK tab)
        CHANNEL.registerMessage(id++, ServerboundSetPermissionLevelPacket.class,
                ServerboundSetPermissionLevelPacket::encode, ServerboundSetPermissionLevelPacket::decode,
                ServerboundSetPermissionLevelPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /**
     * Builds and sends a waypoint sync packet to the given player.
     * Includes all mirrors the player can access (teleport, pocket, beacon) — not calling.
     */
    public static void sendWaypointSync(net.minecraft.server.level.ServerPlayer player) {
        com.ether.mirrors.data.MirrorNetworkData networkData =
                com.ether.mirrors.data.MirrorNetworkData.get(player.server);
        com.ether.mirrors.data.PermissionData permData =
                com.ether.mirrors.data.PermissionData.get(player.server);

        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        java.util.List<ClientboundSyncWaypointsPacket.WaypointData> list = new java.util.ArrayList<>();

        // Accessible mirrors (own + permitted)
        for (com.ether.mirrors.data.MirrorNetworkData.MirrorEntry e
                : networkData.getConnectedMirrors(player.getUUID(), permData)) {
            if ("calling".equals(e.type.getName())) continue;
            if (seen.add(e.mirrorId)) {
                list.add(new ClientboundSyncWaypointsPacket.WaypointData(
                        e.mirrorId, e.name,
                        e.pos.getX(), e.pos.getY(), e.pos.getZ(),
                        e.dimension.location().toString(), e.type.getName()));
            }
        }
        // Beacon mirrors (globally visible)
        for (com.ether.mirrors.data.MirrorNetworkData.MirrorEntry e : networkData.getAllMirrors()) {
            if (e.type != com.ether.mirrors.util.MirrorType.BEACON) continue;
            if (seen.add(e.mirrorId)) {
                list.add(new ClientboundSyncWaypointsPacket.WaypointData(
                        e.mirrorId, e.name,
                        e.pos.getX(), e.pos.getY(), e.pos.getZ(),
                        e.dimension.location().toString(), e.type.getName()));
            }
        }

        sendToPlayer(player, new ClientboundSyncWaypointsPacket(list));
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
