package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.item.MirrorItem;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import com.ether.mirrors.util.RangeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent by the client when a player right-clicks with a mirror item in hand.
 * The server builds the mirror list using the player's current position and item tier.
 */
public class ServerboundOpenHandheldMirrorPacket {

    private final String tierName;
    private final String typeName;

    public ServerboundOpenHandheldMirrorPacket(String tierName, String typeName) {
        this.tierName = tierName;
        this.typeName = typeName;
    }

    public static void encode(ServerboundOpenHandheldMirrorPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.tierName, 16);
        buf.writeUtf(msg.typeName, 16);
    }

    public static ServerboundOpenHandheldMirrorPacket decode(FriendlyByteBuf buf) {
        return new ServerboundOpenHandheldMirrorPacket(buf.readUtf(16), buf.readUtf(16));
    }

    public static void handle(ServerboundOpenHandheldMirrorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Validate tier and type from the actual held item — never trust client-supplied strings alone
            MirrorTier tier = MirrorItem.getTierFromStack(player.getMainHandItem());
            MirrorType type = MirrorItem.getTypeFromStack(player.getMainHandItem());
            if (tier == null || type == null) {
                tier = MirrorItem.getTierFromStack(player.getOffhandItem());
                type = MirrorItem.getTypeFromStack(player.getOffhandItem());
            }
            if (tier == null || type == null) return; // player isn't actually holding a mirror item

            // Confirm the held item matches what the client claimed (catches version mismatches / modified clients)
            if (!tier.getName().equals(msg.tierName) || !type.getName().equals(msg.typeName)) return;

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            // Calling mirrors see all players' calling mirrors; others require permission
            List<MirrorNetworkData.MirrorEntry> connected = "calling".equals(msg.typeName)
                    ? networkData.getAllMirrors()
                    : networkData.getConnectedMirrors(player.getUUID(), permData);
            BlockPos sourcePos = player.blockPosition();

            // For calling mirrors, de-duplicate by owner
            Set<UUID> seenOwners = new HashSet<>();

            List<ClientboundMirrorListPacket.MirrorInfo> mirrorInfos = new ArrayList<>();
            for (MirrorNetworkData.MirrorEntry entry : connected) {
                // Guard against corrupted entries with null owner
                if (entry.ownerUUID == null) continue;

                // Only show mirrors of the same type
                if (!entry.type.getName().equals(msg.typeName)) continue;

                // Calling: one entry per owner (not self)
                if ("calling".equals(msg.typeName) && !entry.ownerUUID.equals(player.getUUID())) {
                    if (!seenOwners.add(entry.ownerUUID)) continue;
                }
                // Pocket: one entry per owner (each owner has exactly one pocket dim)
                if ("pocket".equals(msg.typeName)) {
                    if (!seenOwners.add(entry.ownerUUID)) continue;
                }

                boolean inRange = RangeHelper.isInRange(tier, entry.tier, sourcePos,
                        player.level().dimension(), entry.pos, entry.dimension);
                double signal = RangeHelper.getSignalStrength(tier, entry.tier, sourcePos,
                        player.level().dimension(), entry.pos, entry.dimension);

                boolean isOwn = entry.ownerUUID.equals(player.getUUID());
                String ownerName = isOwn ? player.getGameProfile().getName()
                        : resolvePlayerName(player.server, entry.ownerUUID);

                mirrorInfos.add(new ClientboundMirrorListPacket.MirrorInfo(
                        entry.mirrorId, entry.ownerUUID, entry.pos,
                        entry.dimension.location().toString(),
                        entry.tier.getName(), entry.type.getName(),
                        ownerName, isOwn, inRange, signal, entry.name));
            }

            // Include cooldown for handheld mirrors (HANDHELD_MIRROR_ID tracks per-hand cooldowns)
            int cooldownSecs = com.ether.mirrors.util.RangeHelper.getCooldownSecondsForTier(tier);
            long cooldownRemainingMs = networkData.getCooldownRemainingMs(
                    player.getUUID(), MirrorNetworkData.HANDHELD_MIRROR_ID, cooldownSecs);
            MirrorsNetwork.sendToPlayer(player, new ClientboundMirrorListPacket(
                    mirrorInfos, BlockPos.ZERO, msg.typeName, true, false, cooldownRemainingMs));
        });
        ctx.get().setPacketHandled(true);
    }

    /** Resolve a player's display name: online first, then profile cache, then UUID fallback. */
    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, UUID uuid) {
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        var cached = server.getProfileCache().get(uuid).orElse(null);
        if (cached != null) return cached.getName();
        return uuid.toString().substring(0, 8);
    }
}
