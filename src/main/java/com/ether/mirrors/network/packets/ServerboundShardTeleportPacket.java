package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.init.MirrorsSounds;
import com.ether.mirrors.init.MirrorsItems;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.RangeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundShardTeleportPacket {

    private final UUID boundMirrorId;
    private final boolean mainHand;

    public ServerboundShardTeleportPacket(UUID boundMirrorId, boolean mainHand) {
        this.boundMirrorId = boundMirrorId;
        this.mainHand = mainHand;
    }

    public static void encode(ServerboundShardTeleportPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.boundMirrorId);
        buf.writeBoolean(msg.mainHand);
    }

    public static ServerboundShardTeleportPacket decode(FriendlyByteBuf buf) {
        return new ServerboundShardTeleportPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ServerboundShardTeleportPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Verify the player is actually holding a shard
            ItemStack shard = msg.mainHand ? player.getMainHandItem() : player.getOffhandItem();
            if (!shard.is(MirrorsItems.MIRROR_SHARD.get())) return;

            net.minecraft.nbt.CompoundTag nbt = shard.getTag();
            if (nbt == null || !nbt.hasUUID("BoundMirrorId")) return;

            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            MirrorNetworkData.MirrorEntry target = networkData.getMirrorById(msg.boundMirrorId);
            if (target == null) {
                player.displayClientMessage(Component.literal("The bound mirror no longer exists."), true);
                nbt.remove("BoundMirrorId");
                nbt.remove("BoundDimension");
                nbt.remove("BoundMirrorName");
                return;
            }

            // Check PRIVACY_LOCK on the destination.
            // Start from the cached network-data value so unloaded chunks default to the
            // authoritative state rather than false (which would bypass the lock).
            boolean privacyLock = target.privacyLocked;
            if (target.pos != null) {
                ServerLevel privacyCheckLevel = player.server.getLevel(target.dimension);
                if (privacyCheckLevel != null) {
                    net.minecraft.world.level.block.entity.BlockEntity tbe =
                            privacyCheckLevel.getBlockEntity(target.pos);
                    if (tbe instanceof MirrorBlockEntity targetBECheck) {
                        privacyLock = targetBECheck.hasUpgrade(MirrorUpgradeType.PRIVACY_LOCK);
                    }
                }
            }
            if (!permData.canPlayerUseMirror(player.getUUID(), target.mirrorId, target.ownerUUID,
                    PermissionData.PermissionLevel.USE, privacyLock)) {
                player.displayClientMessage(Component.literal("You don't have permission to use this mirror."), true);
                return;
            }

            // Cooldown — use the actual target mirror's tier
            MirrorTier shardCooldownTier = target.tier != null ? target.tier : MirrorTier.IRON;
            int cooldownSecs = RangeHelper.getCooldownSecondsForTier(shardCooldownTier);
            // Apply COOLDOWN_REDUCER if the target mirror has it
            if (target.pos != null) {
                ServerLevel shardCheckLevel = player.server.getLevel(target.dimension);
                if (shardCheckLevel != null && shardCheckLevel.isLoaded(target.pos)) {
                    net.minecraft.world.level.block.entity.BlockEntity shardBE = shardCheckLevel.getBlockEntity(target.pos);
                    if (shardBE instanceof com.ether.mirrors.block.entity.MirrorBlockEntity shardMirrorBE
                            && shardMirrorBE.hasUpgrade(com.ether.mirrors.item.MirrorUpgradeType.COOLDOWN_REDUCER)) {
                        int pct = com.ether.mirrors.MirrorsConfig.COOLDOWN_REDUCER_PERCENT.get();
                        cooldownSecs = (int)(cooldownSecs * (1.0 - pct / 100.0));
                    }
                }
            }
            long cooldownRemainingMs = networkData.getCooldownRemainingMs(player.getUUID(), msg.boundMirrorId, cooldownSecs);
            if (cooldownRemainingMs > 0) {
                double remaining = cooldownRemainingMs / 1000.0;
                player.displayClientMessage(
                        Component.literal(String.format("Mirror cooldown: %.1fs remaining.", remaining)), true);
                return;
            }

            ServerLevel targetLevel = player.server.getLevel(target.dimension);
            if (targetLevel == null) {
                player.displayClientMessage(Component.literal("Target dimension not found."), true);
                return;
            }

            // Compute landing position in front of the destination mirror
            BlockPos targetPos = target.pos;
            Direction facing = target.facing;
            double x = targetPos.getX() + 0.5 + facing.getStepX() * 1.5;
            double y = targetPos.getY();
            double z = targetPos.getZ() + 0.5 + facing.getStepZ() * 1.5;
            float yaw = facing.getOpposite().toYRot();

            // Step further out if landing spot is solid
            BlockPos landFeet = BlockPos.containing(x, y, z);
            for (int step = 2; step <= 4; step++) {
                if (targetLevel.getBlockState(landFeet).isAir()
                        && targetLevel.getBlockState(landFeet.above()).isAir()) break;
                x = targetPos.getX() + 0.5 + facing.getStepX() * (step + 0.5);
                z = targetPos.getZ() + 0.5 + facing.getStepZ() * (step + 0.5);
                landFeet = BlockPos.containing(x, y, z);
            }

            // Capture source AABB BEFORE teleport for mob transport queries
            net.minecraft.world.phys.AABB sourceBBox = player.getBoundingBox();
            net.minecraft.server.level.ServerLevel sourceLevel = player.serverLevel();

            player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());

            // Mob transport: teleport leashed mobs and nearby tamed pets
            if (com.ether.mirrors.MirrorsConfig.MOB_TRANSPORT_ENABLED.get()) {
                int transported = 0;
                double finalX = x, finalY = y, finalZ = z;
                // Leashed mobs
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
                // Tamed pets nearby
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
            targetLevel.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 30, 0.5, 0.5, 0.5, 0.1);
            targetLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 20, 0.3, 0.3, 0.3, 0.05);

            player.displayClientMessage(Component.literal("Teleported via Mirror Shard!"), true);

            networkData.recordTeleport(player.getUUID(), msg.boundMirrorId);

            // Trigger ALARM and REDSTONE_PULSE on the destination
            if (target.pos != null) {
                net.minecraft.world.level.block.entity.BlockEntity tbe =
                        targetLevel.getBlockEntity(target.pos);
                if (tbe instanceof MirrorBlockEntity targetBE) {
                    if (targetBE.hasUpgrade(MirrorUpgradeType.ALARM)
                            && !target.ownerUUID.equals(player.getUUID())) {
                        String alarmMsg = "[Ether's Mirrors] Your mirror \"" + targetBE.getDisplayName()
                                + "\" was used by " + player.getGameProfile().getName() + " (via Mirror Shard)";
                        ServerPlayer owner = player.server.getPlayerList().getPlayer(target.ownerUUID);
                        if (owner != null) {
                            owner.sendSystemMessage(Component.literal(alarmMsg)
                                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                        } else {
                            networkData.addPendingAlarm(target.ownerUUID, alarmMsg);
                        }
                    }
                    if (targetBE.hasUpgrade(MirrorUpgradeType.REDSTONE_PULSE)) {
                        targetBE.triggerPulse();
                    }
                }
            }

            // Consume one shard
            shard.shrink(1);
        });
        ctx.get().setPacketHandled(true);
    }
}
