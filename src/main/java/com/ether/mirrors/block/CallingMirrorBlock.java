package com.ether.mirrors.block;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorPacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorManagementPacket;
import com.ether.mirrors.screen.MirrorNamingScreen;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import com.ether.mirrors.util.MultiblockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CallingMirrorBlock extends MirrorBlock {

    public CallingMirrorBlock(Properties properties, MirrorTier tier) {
        super(properties, tier, MirrorType.CALLING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            BlockPos masterPos = MultiblockHelper.findMasterPos(pos,
                    state.getValue(FACING), state.getValue(PART));

            // Sneak + right-click opens mirror management (owner only)
            if (player.isCrouching()) {
                if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity be && be.isOwner(player)) {
                    MirrorsNetwork.sendToServer(new ServerboundOpenMirrorManagementPacket(masterPos));
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }

            if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE) {
                if (!mirrorBE.isActivated()) {
                    if (mirrorBE.isOwner(player)) {
                        MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundActivateMirrorPacket(masterPos));
                    }
                    return InteractionResult.SUCCESS;
                }
                if (!mirrorBE.hasCustomName() && mirrorBE.isOwner(player)) {
                    Minecraft.getInstance().setScreen(new MirrorNamingScreen(masterPos, mirrorBE.getDisplayName()));
                    return InteractionResult.SUCCESS;
                }
            }

            MirrorsNetwork.sendToServer(new ServerboundOpenMirrorPacket(masterPos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
