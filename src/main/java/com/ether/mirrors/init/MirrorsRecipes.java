package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.crafting.MirrorDyeRecipeSerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MirrorsRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, EthersMirrors.MOD_ID);

    public static final RegistryObject<RecipeSerializer<com.ether.mirrors.crafting.MirrorDyeRecipe>> MIRROR_DYE =
            RECIPE_SERIALIZERS.register("mirror_dye", () -> MirrorDyeRecipeSerializer.INSTANCE);
}
