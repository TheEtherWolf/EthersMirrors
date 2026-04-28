package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.init.MirrorsSounds;
import com.ether.mirrors.item.MirrorItem;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.RangeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundTeleportRequestPacket {

    private final UUID targetMirrorId;
    private final BlockPos sourceMirrorPos;
    private final boolean isHandheld;
    private final boolean saveAsWarpTarget;

    public ServerboundTeleportRequestPacket(UUID targetMirrorId, BlockPos sourceMirrorPos, boolean isHandheld, boolean saveAsWarpTarget) {
        this.targetMirrorId = targetMirrorId;
        this.sourceMirrorPos = sourceMirrorPos;
        this.isHandheld = isHandheld;
        this.saveAsWarpTarget = saveAsWarpTarget;
    }

    public ServerboundTeleportRequestPacket(UUID targetMirrorId, BlockPos sourceMirrorPos, boolean isHandheld) {
        this(targetMirrorId, sourceMirrorPos, isHandheld, false);
    }

    /** Convenience constructor for placed mirrors. */
    public ServerboundTeleportRequestPacket(UUID targetMirrorId, BlockPos sourceMirrorPos) {
        this(targetMirrorId, sourceMirrorPos, false, false);
    }

    public static void encode(ServerboundTeleportRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetMirrorId);
        buf.writeBlockPos(msg.sourceMirrorPos);
        buf.writeBoolean(msg.isHandheld);
        buf.writeBoolean(msg.saveAsWarpTarget);
    }

    public static ServerboundTeleportRequestPacket decode(FriendlyByteBuf buf) {
        UUID targetMirrorId = buf.readUUID();
        BlockPos sourceMirrorPos = buf.readBlockPos();
        boolean isHandheld = buf.readBoolean();
        boolean saveAsWarpTarget = buf.readBoolean();
        return new ServerboundTeleportRequestPacket(targetMirrorId, sourceMirrorPos, isHandheld, saveAsWarpTarget);
    }

    public static void handle(ServerboundTeleportRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            // Find the target mirror
            MirrorNetworkData.MirrorEntry target = networkData.getMirrorById(msg.targetMirrorId);
            if (target == null) {
                player.displayClientMessage(Component.literal("Mirror not found."), true);
                return;
            }

            // Check access permission — also check PRIVACY_LOCK on target
            // Force-load the chunk briefly to ensure the BE is accessible for the privacy check
            boolean privacyLock = false;
            if (target.pos != null) {
                ServerLevel checkLevel = player.server.getLevel(target.dimension);
                if (checkLevel != null) {
                    net.minecraft.world.level.chunk.LevelChunk chunk =
                            checkLevel.getChunkSource().getChunkNow(
                                    target.pos.getX() >> 4, target.pos.getZ() >> 4);
                    if (chunk != null) {
                        net.minecraft.world.level.block.entity.BlockEntity tbe =
                                chunk.getBlockEntity(target.pos);
                        if (tbe instanceof MirrorBlockEntity targetBECheck) {
                            privacyLock = targetBECheck.hasUpgrade(MirrorUpgradeType.PRIVACY_LOCK);
                        }
                    } else {
                        // Chunk not loaded — assume privacy lock is active for safety
                        // (the teleportTo call will load the chunk anyway for the actual teleport)
                        privacyLock = true;
                    }
                }
            }
            if (!permData.canPlayerUseMirror(player.getUUID(), target.mirrorId, target.ownerUUID,
                    PermissionData.PermissionLevel.USE, privacyLock)) {
                player.displayClientMessage(Component.literal("You don't have permission to use this mirror."), true);
                return;
            }

            // Determine source tier and position for range check
            ResourceKey<Level> sourceDim = player.level().dimension();
            BlockPos sourcePos;
            MirrorTier sourceTier;
            MirrorNetworkData.MirrorEntry sourceMirrorEntry = null;

            if (msg.isHandheld) {
                // Source is the player holding the item — get tier from held item
                sourcePos = player.blockPosition();
                MirrorTier held = MirrorItem.getTierFromStack(player.getMainHandItem());
                if (held == null) held = MirrorItem.getTierFromStack(player.getOffhandItem());
                if (held == null) {
                    player.displayClientMessage(Component.literal("No mirror in hand."), true);
                    return;
                }
                sourceTier = held;
            } else {
                // Verify the player is actually near the claimed source mirror position
                double distSq = player.distanceToSqr(
                        msg.sourceMirrorPos.getX() + 0.5,
                        msg.sourceMirrorPos.getY() + 0.5,
                        msg.sourceMirrorPos.getZ() + 0.5);
                if (distSq > 64) {
                    player.displayClientMessage(Component.literal("You are too far from the source mirror."), true);
                    return;
                }

                // Verify there is actually a mirror block at that position
                if (!(player.level().getBlockState(msg.sourceMirrorPos).getBlock() instanceof
                        com.ether.mirrors.block.MirrorBlock)) {
                    player.displayClientMessage(Component.literal("No mirror at source position."), true);
                    return;
                }

                // Look up source mirror from ALL mirrors the player can access (own + permitted)
                for (MirrorNetworkData.MirrorEntry entry : networkData.getConnectedMirrors(player.getUUID(), permData)) {
                    if (entry.pos.equals(msg.sourceMirrorPos) && entry.dimension.equals(sourceDim)) {
                        sourceMirrorEntry = entry;
                        break;
                    }
                }
                if (sourceMirrorEntry == null) {
                    player.displayClientMessage(Component.literal("Source mirror not found."), true);
                    return;
                }
                sourcePos = msg.sourceMirrorPos;
                sourceTier = sourceMirrorEntry.tier;
            }

            // Fetch source mirror block entity once for all upgrade checks
            MirrorBlockEntity sourceBE = null;
            if (!msg.isHandheld) {
                net.minecraft.world.level.block.entity.BlockEntity srcBECheck =
                        player.level().getBlockEntity(msg.sourceMirrorPos);
                if (srcBECheck instanceof MirrorBlockEntity srcMBE) {
                    sourceBE = srcMBE;
                }
            }

            // ONE_WAY upgrade: this mirror only receives teleports, cannot send
            if (sourceBE != null && sourceBE.hasUpgrade(MirrorUpgradeType.ONE_WAY)) {
                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.5F);
                return;
            }

            // Check range — consider RANGE_BOOSTER on source BE
            boolean rangeBooster = sourceBE != null && sourceBE.hasUpgrade(MirrorUpgradeType.RANGE_BOOSTER);
            boolean inRange = RangeHelper.isInRange(sourceTier, target.tier, sourcePos, sourceDim, target.pos, target.dimension);
            if (!inRange && rangeBooster) {
                inRange = RangeHelper.isInRangeBoosted(sourceTier, target.tier, sourcePos, sourceDim, target.pos, target.dimension);
            }
            if (!inRange) {
                player.displayClientMessage(Component.literal("Target mirror is out of range."), true);
                return;
            }

            // TIME_LOCK upgrade: mirror only works during day or night
            if (sourceBE != null && sourceBE.hasUpgrade(MirrorUpgradeType.TIME_LOCK)) {
                String timeLockMode = sourceBE.getUpgradeData().getString("TimeLockMode");
                if (timeLockMode.isEmpty()) timeLockMode = "day";
                long timeOfDay = player.level().getDayTime() % 24000L;
                boolean isDay = timeOfDay < 13000L;
                if ((timeLockMode.equals("day") && !isDay) || (timeLockMode.equals("night") && isDay)) {
                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.get(), 0.5F, 0.5F);
                    return;
                }
            }

            // Check cooldown — based on source tier (lower tier = longer cooldown)
            int cooldownSecs = RangeHelper.getCooldownSecondsForTier(sourceTier);

            // COOLDOWN_REDUCER upgrade: reduce cooldown by configured percent
            if (sourceBE != null && sourceBE.hasUpgrade(MirrorUpgradeType.COOLDOWN_REDUCER)) {
                int pct = com.ether.mirrors.MirrorsConfig.COOLDOWN_REDUCER_PERCENT.get();
                cooldownSecs = (int)(cooldownSecs * (1.0 - pct / 100.0));
            }

            UUID sourceMirrorId = msg.isHandheld
                    ? com.ether.mirrors.data.MirrorNetworkData.HANDHELD_MIRROR_ID
                    : (sourceBE != null ? sourceBE.getMirrorId() : com.ether.mirrors.data.MirrorNetworkData.HANDHELD_MIRROR_ID);
            long cooldownRemainingMs = networkData.getCooldownRemainingMs(player.getUUID(), sourceMirrorId, cooldownSecs);
            if (cooldownRemainingMs > 0) {
                double remaining = cooldownRemainingMs / 1000.0;
                player.displayClientMessage(
                        Component.literal(String.format("Mirror cooldown: %.1fs remaining.", remaining)), true);
                return;
            }

            // Fire API event — cancelable
            com.ether.mirrors.api.event.MirrorTeleportEvent teleportEvent =
                    new com.ether.mirrors.api.event.MirrorTeleportEvent(player, sourceMirrorEntry, target);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(teleportEvent)) return;

            // Execute teleport
            ServerLevel targetLevel = player.server.getLevel(target.dimension);
            if (targetLevel == null) {
                player.displayClientMessage(Component.literal("Target dimension not found."), true);
                return;
            }

            // Teleport to in front of the target mirror, facing toward it.
            BlockPos targetPos = target.pos;
            Direction facing = target.facing;
            double x = targetPos.getX() + 0.5 + facing.getStepX() * 1.5;
            double y = targetPos.getY();
            double z = targetPos.getZ() + 0.5 + facing.getStepZ() * 1.5;
            float yaw = facing.getOpposite().toYRot();

            // Safety: if the computed landing spot is inside a solid block, step further out
            BlockPos landFeet = BlockPos.containing(x, y, z);
            for (int step = 2; step <= 4; step++) {
                if (targetLevel.getBlockState(landFeet).isAir()
                        && targetLevel.getBlockState(landFeet.above()).isAir()) {
                    break;
                }
                x = targetPos.getX() + 0.5 + facing.getStepX() * (step + 0.5);
                z = targetPos.getZ() + 0.5 + facing.getStepZ() * (step + 0.5);
                landFeet = BlockPos.containing(x, y, z);
            }

            // Capture source level and bounding box BEFORE teleport — both change after teleportTo()
            net.minecraft.server.level.ServerLevel sourceLevel =
                    (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.phys.AABB sourceBBox = player.getBoundingBox();

            player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());

            // Mob transport: teleport leashed mobs and nearby tamed pets
            if (com.ether.mirrors.MirrorsConfig.MOB_TRANSPORT_ENABLED.get()) {
                int transported = 0;
                double finalX = x, finalY = y, finalZ = z;
                // Leashed mobs — use pre-teleport bounding box on source level
                java.util.List<net.minecraft.world.entity.Mob> leashed = sourceLevel.getEntitiesOfClass(
                        net.minecraft.world.entity.Mob.class,
                        sourceBBox.inflate(10),
                        e -> e.getLeashHolder() == player);
                int idx = 0;
                for (net.minecraft.world.entity.Mob mob : leashed) {
                    double ox = (idx % 2 == 0 ? 1 : -1) * ((idx / 2 + 1) * 0.8);
                    double oz = (idx % 3 == 0 ? 1 : -1) * 0.8;
                    mob.teleportTo(targetLevel, finalX + ox, finalY, finalZ + oz,
                            java.util.Set.of(), yaw, player.getXRot());
                    idx++;
                    transported++;
                }
                // Tamed pets nearby — use pre-teleport bounding box on source level
                int petRange = com.ether.mirrors.MirrorsConfig.PET_TELEPORT_RANGE.get();
                java.util.List<net.minecraft.world.entity.TamableAnimal> pets = sourceLevel.getEntitiesOfClass(
                        net.minecraft.world.entity.TamableAnimal.class,
                        sourceBBox.inflate(petRange),
                        e -> player.getUUID().equals(e.getOwnerUUID()) && !e.isOrderedToSit());
                for (net.minecraft.world.entity.TamableAnimal pet : pets) {
                    double ox = (idx % 2 == 0 ? 1 : -1) * ((idx / 2 + 1) * 0.8);
                    double oz = (idx % 3 == 0 ? 1 : -1) * 0.8;
                    pet.teleportTo(targetLevel, finalX + ox, finalY, finalZ + oz,
                            java.util.Set.of(), yaw, player.getXRot());
                    idx++;
                    transported++;
                }
                if (transported > 0) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            transported + " mob(s)/pet(s) transported."), true);
                }
            }

            player.playSound(MirrorsSounds.MIRROR_TELEPORT.get(), 1.0F, 1.0F);

            // Spawn particles at destination
            targetLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    x, y + 1, z, 30, 0.5, 0.5, 0.5, 0.1);
            targetLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    x, y + 1, z, 20, 0.3, 0.3, 0.3, 0.05);

            player.displayClientMessage(Component.literal("Teleported!"), true);

            // Record teleport time for cooldown tracking (per-mirror)
            networkData.recordTeleport(player.getUUID(), sourceMirrorId);
            com.ether.mirrors.advancement.MirrorsTriggers.MIRROR_TELEPORTED.trigger(player);

            // If saveAsWarpTarget, store target mirrorId in source BE's upgradeData
            // Only the mirror owner can set/change the warp target
            if (msg.saveAsWarpTarget && !msg.isHandheld) {
                if (sourceLevel.getBlockEntity(msg.sourceMirrorPos) instanceof MirrorBlockEntity srcBE) {
                    if (srcBE.hasUpgrade(MirrorUpgradeType.WARP_TARGET) && srcBE.isOwner(player)) {
                        // WarpTargetLocked flag: prevent overwriting an already-set warp target
                        if (srcBE.getUpgradeData().getBoolean("WarpTargetLocked") && srcBE.getUpgradeData().hasUUID("WarpTargetId")) {
                            player.displayClientMessage(Component.literal("Warp target is locked. Unlock it in the upgrades tab to change it."), true);
                        } else {
                            srcBE.getUpgradeData().putUUID("WarpTargetId", target.mirrorId);
                            srcBE.setChanged();
                            sourceLevel.sendBlockUpdated(msg.sourceMirrorPos, sourceLevel.getBlockState(msg.sourceMirrorPos), sourceLevel.getBlockState(msg.sourceMirrorPos), 3);
                        }
                    }
                }
            }

            // ALARM upgrade check on target — reuse targetLevel (already validated non-null above)
            if (target.pos != null) {
                net.minecraft.world.level.block.entity.BlockEntity tbe =
                        targetLevel.getBlockEntity(target.pos);
                if (tbe instanceof MirrorBlockEntity targetBE) {
                    if (targetBE.hasUpgrade(MirrorUpgradeType.ALARM)
                            && !target.ownerUUID.equals(player.getUUID())) {
                        String alarmMsg = "[Ether's Mirrors] Your mirror \"" + targetBE.getDisplayName()
                                + "\" was used by " + player.getGameProfile().getName();
                        ServerPlayer owner = player.server.getPlayerList().getPlayer(target.ownerUUID);
                        if (owner != null) {
                            owner.sendSystemMessage(Component.literal(alarmMsg)
                                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                        } else {
                            MirrorNetworkData.get(player.server).addPendingAlarm(target.ownerUUID, alarmMsg);
                        }
                    }
                    // REDSTONE_PULSE upgrade
                    if (targetBE.hasUpgrade(MirrorUpgradeType.REDSTONE_PULSE)) {
                        targetBE.triggerPulse();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
