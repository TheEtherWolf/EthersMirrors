package com.ether.mirrors;

import com.ether.mirrors.advancement.MirrorsTriggers;
import com.ether.mirrors.init.MirrorsBlockEntities;
import com.ether.mirrors.init.MirrorsBlocks;
import com.ether.mirrors.init.MirrorsDimensions;
import com.ether.mirrors.init.MirrorsItems;
import com.ether.mirrors.init.MirrorsRecipes;
import com.ether.mirrors.init.MirrorsSounds;
import com.ether.mirrors.init.MirrorsTabs;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.update.UpdateManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EthersMirrors.MOD_ID)
public class EthersMirrors {

    public static final String MOD_ID = "ethersmirrors";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /** Current mod version, read from the JAR manifest — stays in sync with gradle.properties automatically. */
    public static final String VERSION = ModList.get()
            .getModContainerById(MOD_ID)
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("0.0.0");

    public EthersMirrors() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        MirrorsConfig.register();

        MirrorsBlocks.BLOCKS.register(modBus);
        MirrorsItems.ITEMS.register(modBus);
        MirrorsBlockEntities.BLOCK_ENTITIES.register(modBus);
        MirrorsTabs.TABS.register(modBus);
        MirrorsSounds.SOUNDS.register(modBus);
        MirrorsRecipes.RECIPE_SERIALIZERS.register(modBus);

        MirrorsDimensions.register();

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(MirrorsConfig::onConfigLoaded);

        MinecraftForge.EVENT_BUS.register(this);

        UpdateManager.init();

        LOGGER.info("Ether's Mirrors initialized (v{})", VERSION);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MirrorsNetwork.register();
            MirrorsTriggers.register();
        });
    }
}
