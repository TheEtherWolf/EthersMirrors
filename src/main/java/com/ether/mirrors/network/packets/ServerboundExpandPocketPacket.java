package com.ether.mirrors.network.packets;

import com.ether.mirrors.MirrorsConfig;
import com.ether.mirrors.data.PocketDimensionData;
import com.ether.mirrors.network.MirrorsNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundExpandPocketPacket {

    private final int expansionAmount;

    public ServerboundExpandPocketPacket(int expansionAmount) {
        this.expansionAmount = expansionAmount;
    }

    public static void encode(ServerboundExpandPocketPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.expansionAmount);
    }

    public static ServerboundExpandPocketPacket decode(FriendlyByteBuf buf) {
        return new ServerboundExpandPocketPacket(buf.readInt());
    }

    public static void handle(ServerboundExpandPocketPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PocketDimensionData pocketData = PocketDimensionData.get(player.server);
            PocketDimensionData.PocketRegion region = pocketData.getRegion(player.getUUID());
            if (region == null) {
                player.displayClientMessage(Component.literal("You don't have a pocket dimension."), true);
                return;
            }

            int maxSize = MirrorsConfig.MAX_POCKET_SIZE.get();
            if ((long) region.currentSize + msg.expansionAmount > maxSize) {
                player.displayClientMessage(
                        Component.literal("Cannot expand beyond max size (" + maxSize + "x" + maxSize + ")."), true);
                return;
            }

            // Determine cost based on expansion amount
            net.minecraft.world.item.Item costItem;
            int costAmount;
            switch (msg.expansionAmount) {
                case 16 -> { costItem = Items.OAK_PLANKS; costAmount = 8; }
                case 32 -> { costItem = Items.IRON_INGOT; costAmount = 8; }
                case 64 -> { costItem = Items.DIAMOND; costAmount = 4; }
                case 128 -> { costItem = Items.NETHERITE_INGOT; costAmount = 2; }
                default -> {
                    player.displayClientMessage(Component.literal("Invalid expansion amount."), true);
                    return;
                }
            }

            // Validate player has the materials (skip check in creative mode)
            if (!player.isCreative()) {
                int found = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == costItem) {
                        found += stack.getCount();
                    }
                }
                if (found < costAmount) {
                    player.displayClientMessage(Component.literal(
                            "Need " + costAmount + " " + costItem.getDescription().getString() + " to expand."), true);
                    return;
                }
                // Deduct materials
                int remaining = costAmount;
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == costItem) {
                        int take = Math.min(stack.getCount(), remaining);
                        stack.shrink(take);
                        remaining -= take;
                    }
                }
            }

            // Expand the region (modifies region.currentSize in place)
            pocketData.expandRegion(player.getUUID(), msg.expansionAmount);

            // Force barrier rebuild — clear roomBuilt so the next entry (or immediate rebuild below) resizes walls
            region.roomBuilt = false;
            pocketData.setDirty();

            // Immediately resize barriers if player is currently inside the pocket dimension
            net.minecraft.server.level.ServerLevel pocketLevel =
                    player.server.getLevel(com.ether.mirrors.block.PocketMirrorBlock.POCKET_DIMENSION);
            if (pocketLevel != null && player.level().dimension()
                    .equals(com.ether.mirrors.block.PocketMirrorBlock.POCKET_DIMENSION)) {
                com.ether.mirrors.block.PocketMirrorBlock.buildRoomIfNeeded(pocketLevel, region, pocketData);
                // Use the owner's actual pocket mirror tier for barriers (not hardcoded NETHERITE)
                com.ether.mirrors.util.MirrorTier barrierTier = com.ether.mirrors.util.MirrorTier.WOOD;
                com.ether.mirrors.data.MirrorNetworkData netData = com.ether.mirrors.data.MirrorNetworkData.get(player.server);
                for (com.ether.mirrors.data.MirrorNetworkData.MirrorEntry me : netData.getMirrorsForPlayer(player.getUUID())) {
                    if ("pocket".equals(me.type.getName()) && me.tier.ordinal() > barrierTier.ordinal()) {
                        barrierTier = me.tier;
                    }
                }
                com.ether.mirrors.block.PocketMirrorBlock.applyTierBarriers(
                        pocketLevel, region, barrierTier);
            }

            player.displayClientMessage(Component.literal(
                    "Pocket dimension expanded to " + region.currentSize + "x" + region.currentSize + "."), true);

            // Send updated info back so the client can refresh if needed
            MirrorsNetwork.sendToPlayer(player, new ClientboundPocketInfoPacket(region.currentSize, maxSize));
        });
        ctx.get().setPacketHandled(true);
    }
}
