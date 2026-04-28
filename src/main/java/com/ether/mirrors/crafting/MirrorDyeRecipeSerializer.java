package com.ether.mirrors.crafting;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class MirrorDyeRecipeSerializer implements RecipeSerializer<MirrorDyeRecipe> {

    public static final MirrorDyeRecipeSerializer INSTANCE = new MirrorDyeRecipeSerializer();

    @Override
    public MirrorDyeRecipe fromJson(ResourceLocation id, JsonObject json) {
        return new MirrorDyeRecipe(id);
    }

    @Override
    public MirrorDyeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        return new MirrorDyeRecipe(id);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, MirrorDyeRecipe recipe) {
        // No data to write
    }
}
