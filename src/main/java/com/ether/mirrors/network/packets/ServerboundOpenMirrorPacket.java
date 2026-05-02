package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.util.RangeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ServerboundOpenMirrorPacket {

    private final BlockPos mirrorPos;

    public ServerboundOpenMirrorPacket(BlockPos mirrorPos) {
        this.mirrorPos = mirrorPos;
    }

    public static void encode(ServerboundOpenMirrorPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
    }

    public static ServerboundOpenMirrorPacket decode(FriendlyByteBuf buf) {
        return new ServerboundOpenMirrorPacket(buf.readBlockPos());
    }

    public static void handle(ServerboundOpenMirrorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Validate player is near the mirror
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5, msg.mirrorPos.getZ() + 0.5) > 64) {
                return;
            }

            if (!(player.level().getBlockState(msg.mirrorPos).getBlock() instanceof MirrorBlock)) {
                return;
            }

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            // Get the source mirror's info
            MirrorBlockEntity sourceBE = null;
            if (player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity be) {
                sourceBE = be;
            }

            // Determine source mirror type for filtering + button rendering.
            String sourceMirrorType;
            if (sourceBE != null && sourceBE.getMirrorType() != null) {
                sourceMirrorType = sourceBE.getMirrorType().getName();
            } else {
                net.minecraft.world.level.block.Block blk =
                        player.level().getBlockState(msg.mirrorPos).getBlock();
                if (blk instanceof com.ether.mirrors.block.CallingMirrorBlock)       sourceMirrorType = "calling";
                else if (blk instanceof com.ether.mirrors.block.PocketMirrorBlock)   sourceMirrorType = "pocket";
                else                                                                  sourceMirrorType = "teleport";
            }

            // WARP_TARGET upgrade: if source mirror has it and has a target set, teleport directly
            if (sourceBE != null && sourceBE.hasUpgrade(MirrorUpgradeType.WARP_TARGET)) {
                net.minecraft.nbt.CompoundTag upgradeData = sourceBE.getUpgradeData();
                if (upgradeData.hasUUID("WarpTargetId")) {
                    UUID warpTargetId = upgradeData.getUUID("WarpTargetId");
                    MirrorNetworkData.MirrorEntry warpTarget = networkData.getMirrorById(warpTargetId);

                    // Check PRIVACY_LOCK on target mirror (load chunk briefly, same pattern as teleport handler)
                    boolean targetPrivacyLock = false;
                    if (warpTarget != null && warpTarget.pos != null) {
                        net.minecraft.server.level.ServerLevel checkLevel = player.server.getLevel(warpTarget.dimension);
                        if (checkLevel != null) {
                            net.minecraft.world.level.chunk.LevelChunk chunk =
                                    checkLevel.getChunkSource().getChunkNow(
                                            warpTarget.pos.getX() >> 4, warpTarget.pos.getZ() >> 4);
                            if (chunk != null) {
                                net.minecraft.world.level.block.entity.BlockEntity tbe =
                                        chunk.getBlockEntity(warpTarget.pos);
                                if (tbe instanceof MirrorBlockEntity targetBECheck) {
                                    targetPrivacyLock = targetBECheck.hasUpgrade(MirrorUpgradeType.PRIVACY_LOCK);
                                }
                            } else {
                                targetPrivacyLock = true; // unloaded chunk — assume locked for safety
                            }
                        }
                    }

                    if (warpTarget != null && permData.canPlayerUseMirror(player.getUUID(), warpTarget.mirrorId,
                            warpTarget.ownerUUID, PermissionData.PermissionLevel.USE, targetPrivacyLock)) {
                        boolean warpInRange = RangeHelper.isInRange(sourceBE.getTier(), warpTarget.tier,
                                msg.mirrorPos, player.level().dimension(), warpTarget.pos, warpTarget.dimension);
                        if (!warpInRange && sourceBE.hasUpgrade(MirrorUpgradeType.RANGE_BOOSTER)) {
                            warpInRange = RangeHelper.isInRangeBoosted(sourceBE.getTier(), warpTarget.tier,
                                    msg.mirrorPos, player.level().dimension(), warpTarget.pos, warpTarget.dimension);
                        }
                        if (warpInRange) {
                            // ONE_WAY upgrade: this mirror only receives teleports, cannot send
                            if (sourceBE.hasUpgrade(MirrorUpgradeType.ONE_WAY)) {
                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.5F);
                                return;
                            }

                            // TIME_LOCK check on source mirror
                            if (sourceBE.hasUpgrade(MirrorUpgradeType.TIME_LOCK)) {
                                String timeLockMode = upgradeData.getString("TimeLockMode");
                                if (timeLockMode.isEmpty()) timeLockMode = "day";
                                long timeOfDay = player.level().getDayTime() % 24000L;
                                boolean isDay = timeOfDay < 13000L;
                                if ((timeLockMode.equals("day") && !isDay) || (timeLockMode.equals("night") && isDay)) {
                                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.5F);
                                    return;
                                }
                            }

                            // Cooldown check
                            int cooldownSecs = RangeHelper.getCooldownSecondsForTier(sourceBE.getTier());
                            if (sourceBE.hasUpgrade(MirrorUpgradeType.COOLDOWN_REDUCER)) {
                                int pct = com.ether.mirrors.MirrorsConfig.COOLDOWN_REDUCER_PERCENT.get();
                                cooldownSecs = (int)(cooldownSecs * (1.0 - pct / 100.0));
                            }
                            long cooldownRemainingMs = networkData.getCooldownRemainingMs(
                                    player.getUUID(), sourceBE.getMirrorId(), cooldownSecs);
                            if (cooldownRemainingMs > 0) {
                                double remaining = cooldownRemainingMs / 1000.0;
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.literal(
                                                String.format("Mirror cooldown: %.1fs remaining.", remaining)), true);
                                return;
                            }

                            net.minecraft.server.level.ServerLevel targetLevel = player.server.getLevel(warpTarget.dimension);
                            if (targetLevel != null) {
                                net.minecraft.core.Direction facing = warpTarget.facing;
                                double x = warpTarget.pos.getX() + 0.5 + facing.getStepX() * 1.5;
                                double y = warpTarget.pos.getY();
                                double z = warpTarget.pos.getZ() + 0.5 + facing.getStepZ() * 1.5;
                                float yaw = facing.getOpposite().toYRot();

                                // Landing-spot safety: step further out if the computed spot is inside a solid block
                                net.minecraft.core.BlockPos landFeet = net.minecraft.core.BlockPos.containing(x, y, z);
                                for (int step = 2; step <= 4; step++) {
                                    if (targetLevel.getBlockState(landFeet).isAir()
                                            && targetLevel.getBlockState(landFeet.above()).isAir()) {
                                        break;
                                    }
                                    x = warpTarget.pos.getX() + 0.5 + facing.getStepX() * (step + 0.5);
                                    z = warpTarget.pos.getZ() + 0.5 + facing.getStepZ() * (step + 0.5);
                                    landFeet = net.minecraft.core.BlockPos.containing(x, y, z);
                                }

                                player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());
                                player.playSound(com.ether.mirrors.init.MirrorsSounds.MIRROR_TELEPORT.get(), 1.0F, 1.0F);
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Warped!"), true);
                                networkData.recordTeleport(player.getUUID(), sourceBE.getMirrorId());
                                com.ether.mirrors.advancement.MirrorsTriggers.MIRROR_TELEPORTED.trigger(player);
                                // ALARM check on warpTarget
                                if (targetLevel.getBlockEntity(warpTarget.pos) instanceof MirrorBlockEntity targetBE) {
                                    if (targetBE.hasUpgrade(MirrorUpgradeType.ALARM)
                                            && !warpTarget.ownerUUID.equals(player.getUUID())) {
                                        String alarmMsg = "[Ether's Mirrors] Your mirror \"" + targetBE.getDisplayName()
                                                + "\" was used by " + player.getGameProfile().getName();
                                        net.minecraft.server.level.ServerPlayer owner =
                                                player.server.getPlayerList().getPlayer(warpTarget.ownerUUID);
                                        if (owner != null) {
                                            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(alarmMsg)
                                                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                                        } else {
                                            networkData.addPendingAlarm(warpTarget.ownerUUID, alarmMsg);
                                        }
                                    }
                                    if (targetBE.hasUpgrade(MirrorUpgradeType.REDSTONE_PULSE)) {
                                        targetBE.triggerPulse();
                                    }
                                }
                                return;
                            }
                        }
                    }
                    // Warp target set but invalid — fall through to open selection in warpTargetMode
                }
                // WARP_TARGET but no valid target: open selection screen in warpTargetMode
                // to let player pick a destination. warpTargetMode flag will be set below.
            }

            // Determine warpTargetMode: source has WARP_TARGET but no target configured yet
            boolean warpTargetMode = sourceBE != null
                    && sourceBE.hasUpgrade(MirrorUpgradeType.WARP_TARGET)
                    && !sourceBE.getUpgradeData().hasUUID("WarpTargetId");

            // Check RANGE_BOOSTER on source mirror
            boolean rangeBooster = sourceBE != null && sourceBE.hasUpgrade(MirrorUpgradeType.RANGE_BOOSTER);

            // Calling mirrors: all players' calling mirrors visible (no permission needed)
            // Beacon mirrors: all players' beacon mirrors visible (global public waypoints)
            // Teleport/pocket: only mirrors the player has access to via permissions
            // In all cases, privacy-locked mirrors belonging to other players are excluded.
            boolean globallyVisible = "calling".equals(sourceMirrorType) || "beacon".equals(sourceMirrorType);
            List<MirrorNetworkData.MirrorEntry> connected;
            if (globallyVisible) {
                connected = new java.util.ArrayList<>();
                for (MirrorNetworkData.MirrorEntry e : networkData.getAllMirrors()) {
                    if (!e.privacyLocked || e.ownerUUID.equals(player.getUUID())) {
                        connected.add(e);
                    }
                }
            } else {
                connected = networkData.getConnectedMirrors(player.getUUID(), permData);
            }

            // For calling mirrors, de-duplicate by owner (one entry per callable player)
            java.util.Set<java.util.UUID> seenOwners = new java.util.HashSet<>();

            // Build the list for the client
            List<ClientboundMirrorListPacket.MirrorInfo> mirrorInfos = new ArrayList<>();
            for (MirrorNetworkData.MirrorEntry entry : connected) {
                // Skip the mirror we're standing at (not for pocket: we want own pocket to appear)
                if (!("pocket".equals(sourceMirrorType))
                        && entry.pos.equals(msg.mirrorPos) && entry.dimension.equals(player.level().dimension())) {
                    continue;
                }

                // Only include mirrors of the same type as the source
                if (!entry.type.getName().equals(sourceMirrorType)) {
                    continue;
                }

                // Guard against corrupted entries with null owner
                if (entry.ownerUUID == null) continue;

                // Skip own mirrors in calling mirror list
                if ("calling".equals(sourceMirrorType) && entry.ownerUUID.equals(player.getUUID())) continue;

                // Calling mirrors: one entry per owner (exclude self)
                if ("calling".equals(sourceMirrorType) && !entry.ownerUUID.equals(player.getUUID())) {
                    if (!seenOwners.add(entry.ownerUUID)) continue;
                }
                // Pocket mirrors: one entry per owner regardless (each owner has exactly one pocket dim)
                if ("pocket".equals(sourceMirrorType)) {
                    if (!seenOwners.add(entry.ownerUUID)) continue;
                }

                // Compute range and signal strength
                boolean inRange;
                double signalStrength;
                if ("pocket".equals(sourceMirrorType)) {
                    // Pocket dimension always accessible regardless of distance
                    inRange = true;
                    signalStrength = 1.0;
                } else if (sourceBE != null) {
                    inRange = RangeHelper.isInRange(sourceBE.getTier(), entry.tier, msg.mirrorPos,
                            player.level().dimension(), entry.pos, entry.dimension);
                    signalStrength = RangeHelper.getSignalStrength(sourceBE.getTier(), entry.tier, msg.mirrorPos,
                            player.level().dimension(), entry.pos, entry.dimension);
                    if (!inRange && rangeBooster) {
                        inRange = RangeHelper.isInRangeBoosted(sourceBE.getTier(), entry.tier, msg.mirrorPos,
                                player.level().dimension(), entry.pos, entry.dimension);
                        signalStrength = RangeHelper.getSignalStrengthBoosted(sourceBE.getTier(), entry.tier, msg.mirrorPos,
                                player.level().dimension(), entry.pos, entry.dimension);
                    }
                } else {
                    inRange = false;
                    signalStrength = 0.0;
                }

                boolean isOwn = entry.ownerUUID.equals(player.getUUID());

                // Get owner name — check online list then profile cache
                String ownerName = isOwn ? player.getGameProfile().getName()
                        : resolvePlayerName(player.server, entry.ownerUUID);

                boolean isFavorite = networkData.isFavorite(player.getUUID(), entry.mirrorId);
                String folderName = networkData.getMirrorFolder(entry.mirrorId);

                mirrorInfos.add(new ClientboundMirrorListPacket.MirrorInfo(
                        entry.mirrorId,
                        entry.ownerUUID,
                        entry.pos,
                        entry.dimension.location().toString(),
                        entry.tier.getName(),
                        entry.type.getName(),
                        ownerName,
                        isOwn,
                        inRange,
                        signalStrength,
                        entry.name,
                        isFavorite,
                        folderName,
                        entry.iconPixels
                ));
            }

            // Compute cooldown remaining for the player using source mirror tier (per-mirror)
            com.ether.mirrors.util.MirrorTier sourceTierForCooldown = sourceBE != null ? sourceBE.getTier()
                    : com.ether.mirrors.util.MirrorTier.WOOD;
            int cooldownSecs = RangeHelper.getCooldownSecondsForTier(sourceTierForCooldown);
            UUID sourceMirrorIdForCooldown = sourceBE != null
                    ? sourceBE.getMirrorId()
                    : com.ether.mirrors.data.MirrorNetworkData.HANDHELD_MIRROR_ID;
            long cooldownRemainingMs = networkData.getCooldownRemainingMs(player.getUUID(), sourceMirrorIdForCooldown, cooldownSecs);

            MirrorsNetwork.sendToPlayer(player, new ClientboundMirrorListPacket(
                    mirrorInfos, msg.mirrorPos, sourceMirrorType, false, warpTargetMode, cooldownRemainingMs));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        var cached = server.getProfileCache().get(uuid).orElse(null);
        if (cached != null) return cached.getName();
        return uuid.toString().substring(0, 8);
    }
}
