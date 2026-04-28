package com.ether.mirrors.advancement;

import net.minecraft.advancements.CriteriaTriggers;

public final class MirrorsTriggers {

    public static MirrorTeleportedTrigger MIRROR_TELEPORTED;
    public static EnterPocketTrigger      ENTER_POCKET;
    public static MirrorCallMadeTrigger   MIRROR_CALL_MADE;
    public static PocketThemeSetTrigger   POCKET_THEME_SET;
    public static MirrorUpgradedTrigger   MIRROR_UPGRADED;

    private MirrorsTriggers() {}

    public static void register() {
        MIRROR_TELEPORTED = CriteriaTriggers.register(new MirrorTeleportedTrigger());
        ENTER_POCKET      = CriteriaTriggers.register(new EnterPocketTrigger());
        MIRROR_CALL_MADE  = CriteriaTriggers.register(new MirrorCallMadeTrigger());
        POCKET_THEME_SET  = CriteriaTriggers.register(new PocketThemeSetTrigger());
        MIRROR_UPGRADED   = CriteriaTriggers.register(new MirrorUpgradedTrigger());
    }
}
