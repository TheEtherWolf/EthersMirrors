package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class MirrorsTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EthersMirrors.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MIRRORS_TAB = TABS.register("mirrors", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("Ether's Mirrors"))
                    .icon(() -> new ItemStack(MirrorsItems.MIRROR_ITEMS.get(MirrorType.TELEPORT).get(MirrorTier.DIAMOND).get()))
                    .displayItems((params, output) -> {
                        for (MirrorType type : MirrorType.values()) {
                            if (type == MirrorType.POCKET) continue; // handled separately below
                            for (MirrorTier tier : MirrorTier.values()) {
                                output.accept(MirrorsItems.MIRROR_ITEMS.get(type).get(tier).get());
                            }
                        }
                        output.accept(MirrorsItems.POCKET_MIRROR_ITEM.get()); // single tier-independent pocket mirror
                        for (MirrorUpgradeType type : MirrorUpgradeType.values()) {
                            output.accept(MirrorsItems.UPGRADE_ITEMS.get(type).get());
                        }
                        output.accept(MirrorsItems.EXIT_MIRROR_ITEM.get());
                        output.accept(MirrorsItems.MIRROR_SHARD.get());
                        output.accept(MirrorsItems.POCKET_CRYSTAL.get());
                    })
                    .build()
    );
}
