package com.ether.mirrors.item;

import com.ether.mirrors.block.PocketMirrorBlock;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundExpandPocketPacket;
import net.minecraft.ChatFormatting;
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

public class PocketCrystalItem extends Item {

    public PocketCrystalItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            if (level.dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) {
                MirrorsNetwork.sendToServer(new ServerboundExpandPocketPacket(16));
            } else {
                player.displayClientMessage(
                        Component.literal("Use this inside your pocket dimension."), true);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Expands your pocket dimension by +16 blocks").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Use inside your pocket dimension").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Consumed on use.").withStyle(ChatFormatting.GRAY));
    }
}
