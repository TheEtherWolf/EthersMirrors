package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.init.MirrorsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundPickUpMirrorPacket {

    private final BlockPos mirrorPos;

    public ServerboundPickUpMirrorPacket(BlockPos mirrorPos) {
        this.mirrorPos = mirrorPos;
    }

    public static void encode(ServerboundPickUpMirrorPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
    }

    public static ServerboundPickUpMirrorPacket decode(FriendlyByteBuf buf) {
        return new ServerboundPickUpMirrorPacket(buf.readBlockPos());
    }

    public static void handle(ServerboundPickUpMirrorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Proximity check — must be within interaction range
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5, msg.mirrorPos.getZ() + 0.5) > 64) return;

            // Block must exist and be a mirror
            if (!(player.level().getBlockState(msg.mirrorPos).getBlock() instanceof MirrorBlock mirrorBlock)) {
                return;
            }

            // Must have a block entity
            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) {
                return;
            }

            // Only the owner can pick up
            if (!mirrorBE.isOwner(player)) {
                player.displayClientMessage(Component.literal("You can only pick up your own mirrors."), true);
                return;
            }

            // Get the mirror item to give back
            ItemStack mirrorItem = new ItemStack(mirrorBlock.asItem());

            // Remove from network data and permissions
            UUID mirrorId = mirrorBE.getMirrorId();
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            PermissionData permData = PermissionData.get(player.server);

            // Fire the same API event as a normal break — allows other mods to cancel pickup
            MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(mirrorId);
            if (entry != null) {
                com.ether.mirrors.api.event.MirrorBreakEvent breakEvent =
                        new com.ether.mirrors.api.event.MirrorBreakEvent(player, entry);
                if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breakEvent)) {
                    player.displayClientMessage(Component.literal("Mirror pickup was cancelled."), true);
                    return;
                }
            }

            networkData.removeMirror(mirrorId);
            permData.removeAllPermissionsForMirror(mirrorId);

            // Remove the block (MirrorBlock.onRemove handles multiblock cleanup)
            player.level().removeBlock(msg.mirrorPos, false);

            // Give item to player or drop
            if (!player.addItem(mirrorItem)) {
                player.drop(mirrorItem, false);
            }

            player.displayClientMessage(Component.literal("Mirror picked up."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
