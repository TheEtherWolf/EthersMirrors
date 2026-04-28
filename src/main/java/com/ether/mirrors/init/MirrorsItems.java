package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.item.MirrorItem;
import com.ether.mirrors.item.MirrorShardItem;
import com.ether.mirrors.item.MirrorUpgradeItem;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.item.PocketCrystalItem;
import net.minecraft.world.item.BlockItem;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class MirrorsItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EthersMirrors.MOD_ID);

    public static final Map<MirrorType, Map<MirrorTier, RegistryObject<Item>>> MIRROR_ITEMS = new EnumMap<>(MirrorType.class);
    public static final Map<MirrorUpgradeType, RegistryObject<Item>> UPGRADE_ITEMS = new EnumMap<>(MirrorUpgradeType.class);

    public static final RegistryObject<Item> EXIT_MIRROR_ITEM = ITEMS.register("exit_mirror",
            () -> new BlockItem(MirrorsBlocks.EXIT_MIRROR.get(), new Item.Properties()));

    public static final RegistryObject<Item> MIRROR_SHARD = ITEMS.register("mirror_shard",
            MirrorShardItem::new);

    public static final RegistryObject<Item> POCKET_CRYSTAL = ITEMS.register("pocket_crystal",
            PocketCrystalItem::new);

    // B11: Single tier-independent pocket mirror item
    public static final RegistryObject<Item> POCKET_MIRROR_ITEM = ITEMS.register("pocket_mirror",
            () -> new MirrorItem(
                    MirrorsBlocks.POCKET_MIRROR.get(),
                    new Item.Properties().stacksTo(1),
                    MirrorTier.NETHERITE,
                    MirrorType.POCKET
            ));

    static {
        for (MirrorType type : MirrorType.values()) {
            if (type == MirrorType.POCKET) continue; // Single untired pocket mirror; use POCKET_MIRROR_ITEM
            Map<MirrorTier, RegistryObject<Item>> tierMap = new EnumMap<>(MirrorTier.class);
            for (MirrorTier tier : MirrorTier.values()) {
                String name = tier.getName() + "_" + type.getName() + "_mirror";
                RegistryObject<Item> item = ITEMS.register(name, () ->
                        new MirrorItem(
                                MirrorsBlocks.getMirrorBlock(type, tier).get(),
                                new Item.Properties().stacksTo(1),
                                tier,
                                type
                        )
                );
                tierMap.put(tier, item);
            }
            MIRROR_ITEMS.put(type, tierMap);
        }
        for (MirrorUpgradeType type : MirrorUpgradeType.values()) {
            UPGRADE_ITEMS.put(type, ITEMS.register(type.getId() + "_upgrade",
                    () -> new MirrorUpgradeItem(type)));
        }
    }

    public static RegistryObject<Item> getMirrorItem(MirrorType type, MirrorTier tier) {
        if (type == MirrorType.POCKET) return POCKET_MIRROR_ITEM; // B11: single pocket mirror
        Map<MirrorTier, RegistryObject<Item>> tierMap = MIRROR_ITEMS.get(type);
        if (tierMap == null) return null;
        return tierMap.get(tier);
    }
}
