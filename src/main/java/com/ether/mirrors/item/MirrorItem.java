package com.ether.mirrors.item;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundOpenHandheldMirrorPacket;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

public class MirrorItem extends BlockItem {

    private final MirrorTier tier;
    private final MirrorType type;

    public MirrorItem(Block block, Properties properties, MirrorTier tier, MirrorType type) {
        super(block, properties);
        this.tier = tier;
        this.type = type;
    }

    /**
     * Right-click on a block face: try to place the mirror normally.
     * If placement fails (wrong face, etc.), trigger the mirror ability instead.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        // Sneaking: always trigger the mirror ability — never try to place the block
        if (player != null && player.isCrouching()) {
            if (context.getLevel().isClientSide()) {
                triggerHandheldAbility(player, context.getHand());
            }
            return InteractionResult.SUCCESS;
        }
        InteractionResult result = super.useOn(context);
        if (result == InteractionResult.FAIL || result == InteractionResult.PASS) {
            if (context.getLevel().isClientSide()) {
                triggerHandheldAbility(player, context.getHand());
            }
            return InteractionResult.SUCCESS;
        }
        return result;
    }

    /**
     * Right-click in air: trigger mirror ability.
     * Allow both hands, but skip offhand if mainhand already holds a mirror to avoid double-fire.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // Skip offhand if mainhand also holds a mirror (mainhand fires first and consumes the action)
            if (hand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof MirrorItem) {
                return super.use(level, player, hand);
            }
            triggerHandheldAbility(player, hand);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    private void triggerHandheldAbility(@Nullable Player player, InteractionHand hand) {
        if (player == null) return;
        MirrorsNetwork.sendToServer(new ServerboundOpenHandheldMirrorPacket(tier.getName(), type.getName()));
    }

    /**
     * Gets the MirrorTier from an ItemStack if it holds a MirrorItem, otherwise null.
     * Used server-side for handheld range validation.
     */
    @Nullable
    public static MirrorTier getTierFromStack(ItemStack stack) {
        if (stack.getItem() instanceof MirrorItem item) {
            return item.tier;
        }
        return null;
    }

    @Nullable
    public static MirrorType getTypeFromStack(ItemStack stack) {
        if (stack.getItem() instanceof MirrorItem item) {
            return item.type;
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal(capitalize(type.getName()) + " Mirror").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("Tier: " + capitalize(tier.getName())).withStyle(ChatFormatting.GRAY));
        if (type == MirrorType.POCKET) {
            // Pocket mirrors: show accessible pocket size, not range
            int[] halfs = {5, 7, 9, 11, 14, Integer.MAX_VALUE};
            int h = halfs[Math.min(tier.ordinal(), halfs.length - 1)];
            String size = h == Integer.MAX_VALUE ? "Full" : (h * 2) + "×" + (h * 2);
            tooltip.add(Component.literal("Pocket size: " + size + " blocks").withStyle(ChatFormatting.AQUA));
        } else if (type == MirrorType.CALLING) {
            tooltip.add(Component.literal("Range: Unlimited").withStyle(ChatFormatting.BLUE));
        } else {
            tooltip.add(Component.literal("Range: " + tier.getRange() + " blocks").withStyle(ChatFormatting.BLUE));
            if (tier.canCrossDimension()) {
                tooltip.add(Component.literal("Cross-Dimensional").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        tooltip.add(Component.literal("Right-click to use in hand").withStyle(ChatFormatting.DARK_GRAY));
        net.minecraft.nbt.CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains("DyeColor")) {
            int colorId = nbt.getInt("DyeColor");
            net.minecraft.world.item.DyeColor dyeColor = net.minecraft.world.item.DyeColor.byId(colorId);
            if (dyeColor != null) {
                tooltip.add(net.minecraft.network.chat.Component.literal("Color: " + capitalize(dyeColor.getName()))
                        .withStyle(net.minecraft.ChatFormatting.WHITE));
            }
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s != null ? s : "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
