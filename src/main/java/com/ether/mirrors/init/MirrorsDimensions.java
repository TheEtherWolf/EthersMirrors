package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.dimension.PocketChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class MirrorsDimensions {

    public static void register() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, new ResourceLocation(EthersMirrors.MOD_ID, "pocket"),
                PocketChunkGenerator.CODEC);
    }
}
