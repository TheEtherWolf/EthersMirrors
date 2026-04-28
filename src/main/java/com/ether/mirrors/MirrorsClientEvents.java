package com.ether.mirrors;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.MirrorMultiblockPart;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.network.packets.ClientCallState;
import com.ether.mirrors.screen.CameraOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = EthersMirrors.MOD_ID, value = Dist.CLIENT)
public class MirrorsClientEvents {

    private static int tickCounter = 0;
    private static final Random RANDOM = new Random();
    private static BlockPos lastLookedMirrorPos = null;
    private static int previewTickCounter = 0;

    /**
     * Clear client-side call and camera state when the player disconnects.
     * Prevents stale state from one session bleeding into the next.
     */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCallState.reset();
        CameraOverlayRenderer.stopCameraView();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        tickCounter++;
        if (tickCounter % 4 != 0) return; // Only every 4 ticks for performance

        Level level = mc.level;
        BlockPos playerPos = mc.player.blockPosition();

        // Scan nearby mirror masters for PARTICLE_AURA and ambient sound
        BlockPos.betweenClosed(playerPos.offset(-16, -8, -16), playerPos.offset(16, 8, 16))
                .forEach(pos -> {
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof MirrorBlock)) return;
                    if (state.getValue(MirrorBlock.PART) != MirrorMultiblockPart.MASTER) return;
                    if (!(level.getBlockEntity(pos) instanceof MirrorBlockEntity mirrorBE)) return;

                    // PARTICLE_AURA upgrade: spawn enchant particles
                    if (mirrorBE.hasUpgrade(MirrorUpgradeType.PARTICLE_AURA)) {
                        double cx = pos.getX() + 0.5;
                        double cy = pos.getY() + 0.5;
                        double cz = pos.getZ() + 0.5;
                        for (int i = 0; i < 7; i++) {
                            double ox = (RANDOM.nextDouble() - 0.5) * 3.0;
                            double oy = (RANDOM.nextDouble() - 0.5) * 2.0;
                            double oz = (RANDOM.nextDouble() - 0.5) * 3.0;
                            level.addParticle(ParticleTypes.ENCHANT,
                                    cx + ox, cy + oy, cz + oz,
                                    ox * 0.3, 0.1, oz * 0.3);
                        }
                    }

                    // MUTE_AMBIENT upgrade: suppress ambient sound; otherwise play it occasionally
                    if (!mirrorBE.hasUpgrade(MirrorUpgradeType.MUTE_AMBIENT) && RANDOM.nextInt(80) == 0) {
                        mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                com.ether.mirrors.init.MirrorsSounds.MIRROR_AMBIENT.get(),
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                0.15f, 0.9f + RANDOM.nextFloat() * 0.2f, false);
                    }
                });

        // Portal preview: detect when player looks at a teleport mirror with a warp target
        if (!com.ether.mirrors.MirrorsConfig.PORTAL_PREVIEW_ENABLED.get()) return;
        previewTickCounter++;
        if (previewTickCounter % 5 != 0) return;

        BlockPos newLookedPos = null;
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
            BlockPos hitPos = bhr.getBlockPos();
            if (mc.level.getBlockState(hitPos).getBlock() instanceof MirrorBlock) {
                net.minecraft.world.level.block.state.BlockState bstate = mc.level.getBlockState(hitPos);
                if (bstate.getValue(MirrorBlock.PART) == MirrorMultiblockPart.MASTER) {
                    if (mc.level.getBlockEntity(hitPos) instanceof MirrorBlockEntity beMirror) {
                        if (beMirror.hasUpgrade(MirrorUpgradeType.WARP_TARGET)
                                && beMirror.getUpgradeData().hasUUID("WarpTargetId")) {
                            newLookedPos = hitPos;
                        }
                    }
                } else {
                    // Non-master part — find master
                    net.minecraft.core.Direction facing = bstate.getValue(MirrorBlock.FACING);
                    MirrorMultiblockPart part = bstate.getValue(MirrorBlock.PART);
                    BlockPos masterPos = com.ether.mirrors.util.MultiblockHelper.findMasterPos(hitPos, facing, part);
                    if (mc.level.getBlockEntity(masterPos) instanceof MirrorBlockEntity beMirror) {
                        if (beMirror.hasUpgrade(MirrorUpgradeType.WARP_TARGET)
                                && beMirror.getUpgradeData().hasUUID("WarpTargetId")) {
                            newLookedPos = masterPos;
                        }
                    }
                }
            }
        }

        double previewDist = com.ether.mirrors.MirrorsConfig.PORTAL_PREVIEW_DISTANCE.get();
        if (newLookedPos != null) {
            double dist = mc.player.distanceToSqr(
                    newLookedPos.getX() + 0.5, newLookedPos.getY() + 0.5, newLookedPos.getZ() + 0.5);
            if (dist > previewDist * previewDist) newLookedPos = null;
        }

        if (newLookedPos != null && !newLookedPos.equals(lastLookedMirrorPos)) {
            // Started looking at a new mirror with warp target
            if (!(mc.level.getBlockEntity(newLookedPos) instanceof MirrorBlockEntity beMirror)) {
                lastLookedMirrorPos = newLookedPos;
                return;
            }
            java.util.UUID warpId = beMirror.getUpgradeData().getUUID("WarpTargetId");
            com.ether.mirrors.network.MirrorsNetwork.sendToServer(
                    new com.ether.mirrors.network.packets.ServerboundCameraViewPacket(warpId));
            lastLookedMirrorPos = newLookedPos;
        } else if (newLookedPos == null && lastLookedMirrorPos != null) {
            // Stopped looking — stop camera
            com.ether.mirrors.screen.CameraOverlayRenderer.stopCameraView();
            lastLookedMirrorPos = null;
        }
    }
}
