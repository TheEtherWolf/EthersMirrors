package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.block.BeaconMirrorBlock;
import com.ether.mirrors.block.CallingMirrorBlock;
import com.ether.mirrors.block.ExitMirrorBlock;
import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.PocketMirrorBlock;
import com.ether.mirrors.block.TeleportMirrorBlock;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public class MirrorsBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EthersMirrors.MOD_ID);

    // Organized as type -> tier -> block (POCKET is excluded — see POCKET_MIRROR below)
    public static final Map<MirrorType, Map<MirrorTier, RegistryObject<Block>>> MIRROR_BLOCKS = new EnumMap<>(MirrorType.class);

    // B11: Pocket mirrors are tier-independent — one single block for all pocket access
    public static final RegistryObject<Block> POCKET_MIRROR = BLOCKS.register("pocket_mirror",
            () -> new PocketMirrorBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.0F, 3600000.0F)
                            .sound(SoundType.GLASS)
                            .noOcclusion()
                            .lightLevel(state -> 7),
                    MirrorTier.NETHERITE));

    static {
        for (MirrorType type : MirrorType.values()) {
            if (type == MirrorType.POCKET) continue; // Single untired pocket mirror; use POCKET_MIRROR
            Map<MirrorTier, RegistryObject<Block>> tierMap = new EnumMap<>(MirrorTier.class);
            for (MirrorTier tier : MirrorTier.values()) {
                String name = tier.getName() + "_" + type.getName() + "_mirror";
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .strength(2.0F, 3600000.0F) // Normal break speed for owners, explosion-proof
                        .sound(SoundType.GLASS)
                        .noOcclusion()
                        .lightLevel(state -> 7);

                RegistryObject<Block> block = BLOCKS.register(name, () -> switch (type) {
                    case TELEPORT -> new TeleportMirrorBlock(props, tier);
                    case CALLING -> new CallingMirrorBlock(props, tier);
                    case BEACON -> new BeaconMirrorBlock(props, tier);
                    default -> new TeleportMirrorBlock(props, tier); // unreachable; POCKET excluded above
                });
                tierMap.put(tier, block);
            }
            MIRROR_BLOCKS.put(type, tierMap);
        }
    }

    // EXIT_MIRROR is registered separately because it is placed programmatically inside pocket
    // dimensions, not crafted or placed by players. It does not participate in the tier×type matrix.
    public static final RegistryObject<Block> EXIT_MIRROR = BLOCKS.register("exit_mirror",
            ExitMirrorBlock::new);

    public static RegistryObject<Block> getMirrorBlock(MirrorType type, MirrorTier tier) {
        if (type == MirrorType.POCKET) return POCKET_MIRROR; // B11: single pocket mirror
        Map<MirrorTier, RegistryObject<Block>> tierMap = MIRROR_BLOCKS.get(type);
        if (tierMap == null) return null;
        return tierMap.get(tier);
    }
}
