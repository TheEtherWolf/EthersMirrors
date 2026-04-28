package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.PocketMirrorBlock;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.util.MirrorTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent by the client when the player wants to enter a pocket dimension.
 * Two modes:
 *  - UUID mode: player selected a pocket owner from the pocket selection screen
 *  - BlockPos mode: player right-clicked a pocket mirror block (server resolves owner)
 */
public class ServerboundEnterPocketPacket {

    @Nullable private final UUID pocketOwnerUUID;
    @Nullable private final BlockPos mirrorPos;

    /** UUID mode: enter a specific player's pocket (from selection screen). */
    public ServerboundEnterPocketPacket(UUID pocketOwnerUUID) {
        this.pocketOwnerUUID = pocketOwnerUUID;
        this.mirrorPos = null;
    }

    /** BlockPos mode: enter the pocket of whoever owns the mirror at this position. */
    public ServerboundEnterPocketPacket(BlockPos mirrorPos) {
        this.pocketOwnerUUID = null;
        this.mirrorPos = mirrorPos;
    }

    private ServerboundEnterPocketPacket(@Nullable UUID uuid, @Nullable BlockPos pos) {
        this.pocketOwnerUUID = uuid;
        this.mirrorPos = pos;
    }

    public static void encode(ServerboundEnterPocketPacket msg, FriendlyByteBuf buf) {
        boolean hasUUID = msg.pocketOwnerUUID != null;
        buf.writeBoolean(hasUUID);
        if (hasUUID) {
            buf.writeUUID(msg.pocketOwnerUUID);
        } else {
            buf.writeBlockPos(msg.mirrorPos);
        }
    }

    public static ServerboundEnterPocketPacket decode(FriendlyByteBuf buf) {
        boolean hasUUID = buf.readBoolean();
        if (hasUUID) {
            return new ServerboundEnterPocketPacket(buf.readUUID(), null);
        } else {
            return new ServerboundEnterPocketPacket(null, buf.readBlockPos());
        }
    }

    public static void handle(ServerboundEnterPocketPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Resolve the pocket owner UUID
            UUID ownerUUID = msg.pocketOwnerUUID;
            // UUID mode (selection screen / invitation) always gets full access; BlockPos mode uses the block's tier.
            MirrorTier entryTier = MirrorTier.NETHERITE;
            if (ownerUUID == null && msg.mirrorPos != null) {
                // BlockPos mode: resolve owner from block entity
                if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5,
                        msg.mirrorPos.getZ() + 0.5) > 64) return;
                if (!(player.level().getBlockState(msg.mirrorPos).getBlock() instanceof MirrorBlock)) return;
                if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) return;
                if (!mirrorBE.isActivated()) {
                    player.displayClientMessage(Component.literal("This mirror is not activated."), true);
                    return;
                }
                entryTier = mirrorBE.getTier();
                ownerUUID = mirrorBE.getOwnerUUID();
                if (ownerUUID == null) return;
            }
            if (ownerUUID == null) return;

            // Prevent double-entry: can't enter a pocket if already in one
            if (player.level().dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) {
                player.displayClientMessage(Component.literal("You are already in a pocket dimension."), true);
                return;
            }

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            // Validate the pocket owner exists in the network
            boolean ownerExists = networkData.getMirrorsForPlayer(ownerUUID)
                    .stream().anyMatch(e -> e.type.getName().equals("pocket"));

            // Entering own pocket is always allowed
            boolean isOwn = player.getUUID().equals(ownerUUID);

            if (!isOwn && !ownerExists) {
                player.displayClientMessage(Component.literal("That pocket no longer exists."), true);
                return;
            }

            // For another player's pocket, check if the player has USE permission on any of their pocket mirrors
            if (!isOwn) {
                final UUID finalOwnerUUID = ownerUUID;
                boolean hasAccess = networkData.getMirrorsForPlayer(finalOwnerUUID).stream()
                        .filter(e -> e.type.getName().equals("pocket"))
                        .anyMatch(e -> permData.canPlayerUseMirror(player.getUUID(), e.mirrorId,
                                e.ownerUUID, PermissionData.PermissionLevel.USE));
                if (!hasAccess) {
                    player.displayClientMessage(
                            Component.literal("You don't have permission to enter that pocket dimension."), true);
                    return;
                }
            }

            // Fire enter and teleport
            ServerLevel currentLevel = player.serverLevel();
            PocketMirrorBlock.enterPocket(player, ownerUUID, currentLevel, entryTier);
        });
        ctx.get().setPacketHandled(true);
    }
}
