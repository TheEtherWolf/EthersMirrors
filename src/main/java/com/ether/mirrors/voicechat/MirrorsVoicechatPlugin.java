package com.ether.mirrors.voicechat;

import com.ether.mirrors.EthersMirrors;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;

@ForgeVoicechatPlugin
public class MirrorsVoicechatPlugin implements VoicechatPlugin {

    public static volatile VoicechatApi API;

    @Override
    public String getPluginId() {
        return EthersMirrors.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        API = api;
        EthersMirrors.LOGGER.info("Ether's Mirrors Voice Chat plugin initialized");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Events can be registered here if needed in the future
    }
}
