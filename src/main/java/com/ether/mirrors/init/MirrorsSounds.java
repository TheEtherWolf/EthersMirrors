package com.ether.mirrors.init;

import com.ether.mirrors.EthersMirrors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MirrorsSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EthersMirrors.MOD_ID);

    public static final RegistryObject<SoundEvent> MIRROR_RING = SOUNDS.register("mirror_ring",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EthersMirrors.MOD_ID, "mirror_ring")));

    public static final RegistryObject<SoundEvent> MIRROR_CONNECT = SOUNDS.register("mirror_connect",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EthersMirrors.MOD_ID, "mirror_connect")));

    public static final RegistryObject<SoundEvent> MIRROR_DISCONNECT = SOUNDS.register("mirror_disconnect",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EthersMirrors.MOD_ID, "mirror_disconnect")));

    public static final RegistryObject<SoundEvent> MIRROR_TELEPORT = SOUNDS.register("mirror_teleport",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EthersMirrors.MOD_ID, "mirror_teleport")));

    public static final RegistryObject<SoundEvent> MIRROR_AMBIENT = SOUNDS.register("mirror_ambient",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EthersMirrors.MOD_ID, "mirror_ambient")));
}
