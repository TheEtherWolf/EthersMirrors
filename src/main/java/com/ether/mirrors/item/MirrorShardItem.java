package com.ether.mirrors.item;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundShardTeleportPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class MirrorShardItem extends Item {

    public MirrorShardItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag nbt = stack.getTag();

        if (nbt == null || !nbt.hasUUID("BoundMirrorId")) {
            if (level.isClientSide()) {
                player.displayClientMessage(
                        Component.literal("Right-click a mirror to bind this shard to it.").withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            MirrorsNetwork.sendToServer(new ServerboundShardTeleportPacket(
                    nbt.getUUID("BoundMirrorId"), hand == InteractionHand.MAIN_HAND));
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.hasUUID("BoundMirrorId")) {
            String name = nbt.getString("BoundMirrorName");
            tooltip.add(Component.literal("Bound to: " + (name.isEmpty() ? "Unnamed Mirror" : name))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            String dim = nbt.getString("BoundDimension");
            if (!dim.isEmpty()) {
                // Show only the path part of the dimension resource location (e.g. "overworld")
                String dimLabel = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
                dimLabel = dimLabel.replace("_", " ");
                if (!dimLabel.isEmpty()) {
                    dimLabel = Character.toUpperCase(dimLabel.charAt(0)) + dimLabel.substring(1);
                }
                tooltip.add(Component.literal("Dimension: " + dimLabel)
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            tooltip.add(Component.literal("Right-click to teleport (single use).")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Unbound — right-click a mirror to bind.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
