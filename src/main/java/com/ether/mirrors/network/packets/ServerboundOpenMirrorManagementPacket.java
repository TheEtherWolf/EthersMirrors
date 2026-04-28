package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.network.MirrorsNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class ServerboundOpenMirrorManagementPacket {

    private final BlockPos masterPos;

    public ServerboundOpenMirrorManagementPacket(BlockPos masterPos) {
        this.masterPos = masterPos;
    }

    public static void encode(ServerboundOpenMirrorManagementPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.masterPos);
    }

    public static ServerboundOpenMirrorManagementPacket decode(FriendlyByteBuf buf) {
        return new ServerboundOpenMirrorManagementPacket(buf.readBlockPos());
    }

    public static void handle(ServerboundOpenMirrorManagementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Validate proximity
            if (player.distanceToSqr(msg.masterPos.getX() + 0.5, msg.masterPos.getY() + 0.5, msg.masterPos.getZ() + 0.5) > 64) return;

            if (!(player.level().getBlockState(msg.masterPos).getBlock() instanceof MirrorBlock)) return;

            // Only the owner can open management
            if (!(player.level().getBlockEntity(msg.masterPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            UUID mirrorId = mirrorBE.getMirrorId();
            PermissionData.AccessMode accessMode = permData.getMirrorAccessMode(mirrorId);

            // Build allow list: UUID -> name
            List<ClientboundOpenMirrorManagementPacket.PlayerEntry> allowList = new ArrayList<>();
            for (UUID uuid : permData.getMirrorAllowList(mirrorId).keySet()) {
                allowList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(uuid, resolveName(player.server, uuid)));
            }

            // Build block list: UUID -> name
            List<ClientboundOpenMirrorManagementPacket.PlayerEntry> blockList = new ArrayList<>();
            for (UUID uuid : permData.getMirrorBlockList(mirrorId)) {
                blockList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(uuid, resolveName(player.server, uuid)));
            }

            String dimensionName = player.level().dimension().location().toString();

            // Pocket size data — only populated for pocket-type mirrors
            int pocketCurrentSize = 0;
            int pocketMaxSize = 0;
            String mType = mirrorBE.getMirrorType() != null ? mirrorBE.getMirrorType().getName() : "";
            if ("pocket".equals(mType)) {
                com.ether.mirrors.data.PocketDimensionData pocketData =
                        com.ether.mirrors.data.PocketDimensionData.get(player.server);
                com.ether.mirrors.data.PocketDimensionData.PocketRegion region =
                        pocketData.getRegion(mirrorBE.getOwnerUUID());
                if (region != null) pocketCurrentSize = region.currentSize;
                pocketMaxSize = com.ether.mirrors.MirrorsConfig.MAX_POCKET_SIZE.get();
            }

            boolean warpTargetLocked = mirrorBE.getUpgradeData().getBoolean("WarpTargetLocked");
            MirrorsNetwork.sendToPlayer(player, new ClientboundOpenMirrorManagementPacket(
                    mirrorId, mirrorBE.getOwnerUUID(), mirrorBE.getMirrorName(),
                    mirrorBE.getTier() != null ? mirrorBE.getTier().getName() : "unknown",
                    mirrorBE.getMirrorType() != null ? mirrorBE.getMirrorType().getName() : "unknown",
                    msg.masterPos, dimensionName, accessMode, allowList, blockList,
                    mirrorBE.getAppliedUpgrades(), pocketCurrentSize, pocketMaxSize, warpTargetLocked
            ));
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Build and send the management packet to a player for the given mirror position.
     * Used server-side to refresh the management screen (e.g. after a tier upgrade).
     */
    public static void sendTo(ServerPlayer player, BlockPos masterPos) {
        if (!(player.level().getBlockState(masterPos).getBlock() instanceof MirrorBlock)) return;
        if (!(player.level().getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE)) return;

        MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
        PermissionData permData = PermissionData.get(player.server);

        UUID mirrorId = mirrorBE.getMirrorId();
        PermissionData.AccessMode accessMode = permData.getMirrorAccessMode(mirrorId);

        List<ClientboundOpenMirrorManagementPacket.PlayerEntry> allowList = new ArrayList<>();
        for (UUID uuid : permData.getMirrorAllowList(mirrorId).keySet()) {
            allowList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(uuid, resolveName(player.server, uuid)));
        }

        List<ClientboundOpenMirrorManagementPacket.PlayerEntry> blockList = new ArrayList<>();
        for (UUID uuid : permData.getMirrorBlockList(mirrorId)) {
            blockList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(uuid, resolveName(player.server, uuid)));
        }

        String dimensionName = player.level().dimension().location().toString();

        MirrorsNetwork.sendToPlayer(player, new ClientboundOpenMirrorManagementPacket(
                mirrorId, mirrorBE.getOwnerUUID(), mirrorBE.getMirrorName(),
                mirrorBE.getTier() != null ? mirrorBE.getTier().getName() : "unknown",
                mirrorBE.getMirrorType() != null ? mirrorBE.getMirrorType().getName() : "unknown",
                masterPos, dimensionName, accessMode, allowList, blockList,
                mirrorBE.getAppliedUpgrades()
        ));
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        var cached = server.getProfileCache().get(uuid).orElse(null);
        if (cached != null) return cached.getName();
        return uuid.toString().substring(0, 8);
    }
}
