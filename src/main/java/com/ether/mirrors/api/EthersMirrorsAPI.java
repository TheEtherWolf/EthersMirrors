package com.ether.mirrors.api;

import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.data.PocketDimensionData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.*;

public class EthersMirrorsAPI {

    public static List<MirrorNetworkData.MirrorEntry> getMirrorsForPlayer(UUID playerUUID, MinecraftServer server) {
        if (playerUUID == null || server == null) return Collections.emptyList();
        return MirrorNetworkData.get(server).getMirrorsForPlayer(playerUUID);
    }

    public static Optional<MirrorNetworkData.MirrorEntry> getMirrorAt(BlockPos pos, ResourceKey<Level> dim, MinecraftServer server) {
        if (pos == null || dim == null || server == null) return Optional.empty();
        return MirrorNetworkData.get(server).getAllMirrors().stream()
                .filter(e -> e.pos.equals(pos) && e.dimension.equals(dim))
                .findFirst();
    }

    public static EnumSet<PermissionData.PermissionLevel> getPermissions(UUID grantor, UUID grantee, MinecraftServer server) {
        if (grantor == null || grantee == null || server == null) return EnumSet.noneOf(PermissionData.PermissionLevel.class);
        return PermissionData.get(server).getPermissions(grantor, grantee);
    }

    public static Optional<PocketDimensionData.PocketRegion> getPocketRegion(UUID playerUUID, MinecraftServer server) {
        if (playerUUID == null || server == null) return Optional.empty();
        return Optional.ofNullable(PocketDimensionData.get(server).getRegion(playerUUID));
    }
}
