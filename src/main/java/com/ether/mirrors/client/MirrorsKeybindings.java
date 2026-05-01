package com.ether.mirrors.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class MirrorsKeybindings {

    public static final String CATEGORY = "key.categories.ethersmirrors";

    /** M — open the MEMORY / Call History screen. Free in the OTP profile. */
    public static final KeyMapping OPEN_MEMORY = new KeyMapping(
            "key.ethersmirrors.open_memory",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    );

    private MirrorsKeybindings() {}
}
