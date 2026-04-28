package com.ether.mirrors.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class MirrorDyeRecipe extends CustomRecipe {

    public MirrorDyeRecipe(ResourceLocation id) {
        super(id, CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        boolean hasMirror = false;
        boolean hasDye = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof com.ether.mirrors.item.MirrorItem) {
                hasMirror = true;
            } else if (stack.getItem() instanceof DyeItem) {
                hasDye = true;
            } else {
                return false; // extra items
            }
        }
        return hasMirror && hasDye;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, net.minecraft.core.RegistryAccess registryAccess) {
        ItemStack mirrorStack = ItemStack.EMPTY;
        DyeColor dyeColor = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof com.ether.mirrors.item.MirrorItem) {
                mirrorStack = stack.copy();
            } else if (stack.getItem() instanceof DyeItem dyeItem) {
                dyeColor = dyeItem.getDyeColor();
            }
        }
        if (mirrorStack.isEmpty() || dyeColor == null) return ItemStack.EMPTY;
        if (dyeColor == DyeColor.WHITE) {
            mirrorStack.removeTagKey("DyeColor");
        } else {
            mirrorStack.getOrCreateTag().putInt("DyeColor", dyeColor.getId());
        }
        return mirrorStack;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MirrorDyeRecipeSerializer.INSTANCE;
    }
}
