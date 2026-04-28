package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MirrorsBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EthersMirrors.MOD_ID);

    public static final RegistryObject<BlockEntityType<MirrorBlockEntity>> MIRROR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mirror", () -> {
                // Gather all mirror blocks: single pocket mirror + tier×type matrix (POCKET excluded from matrix)
                java.util.List<Block> blockList = new java.util.ArrayList<>();
                blockList.add(MirrorsBlocks.POCKET_MIRROR.get());
                for (MirrorType type : MirrorType.values()) {
                    if (type == MirrorType.POCKET) continue;
                    for (MirrorTier tier : MirrorTier.values()) {
                        blockList.add(MirrorsBlocks.getMirrorBlock(type, tier).get());
                    }
                }
                return BlockEntityType.Builder.of(MirrorBlockEntity::new, blockList.toArray(new Block[0])).build(null);
            });
}
