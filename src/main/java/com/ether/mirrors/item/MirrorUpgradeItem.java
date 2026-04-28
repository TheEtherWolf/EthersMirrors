package com.ether.mirrors.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class MirrorUpgradeItem extends Item {

    private final MirrorUpgradeType upgradeType;

    public MirrorUpgradeItem(MirrorUpgradeType upgradeType) {
        super(new Properties().stacksTo(1));
        this.upgradeType = upgradeType;
    }

    public MirrorUpgradeType getUpgradeType() { return upgradeType; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(upgradeType.getDisplayName())
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal(upgradeType.getDescription())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
