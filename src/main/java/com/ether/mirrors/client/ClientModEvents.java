package com.ether.mirrors.client;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.init.MirrorsBlocks;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EthersMirrors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MirrorsKeybindings.OPEN_MEMORY);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        for (MirrorType type : MirrorType.values()) {
            for (MirrorTier tier : MirrorTier.values()) {
                event.register(MirrorColorProvider.INSTANCE,
                        MirrorsBlocks.getMirrorBlock(type, tier).get());
            }
        }
    }
}
