package com.ether.mirrors;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class MirrorsConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Ranges
    public static final ForgeConfigSpec.IntValue WOOD_RANGE;
    public static final ForgeConfigSpec.IntValue STONE_RANGE;
    public static final ForgeConfigSpec.IntValue IRON_RANGE;
    public static final ForgeConfigSpec.IntValue GOLD_RANGE;
    public static final ForgeConfigSpec.IntValue DIAMOND_RANGE;
    public static final ForgeConfigSpec.IntValue NETHERITE_RANGE;

    // Cooldowns (seconds between teleports per player)
    public static final ForgeConfigSpec.IntValue WOOD_COOLDOWN;
    public static final ForgeConfigSpec.IntValue STONE_COOLDOWN;
    public static final ForgeConfigSpec.IntValue IRON_COOLDOWN;
    public static final ForgeConfigSpec.IntValue GOLD_COOLDOWN;
    public static final ForgeConfigSpec.IntValue DIAMOND_COOLDOWN;
    public static final ForgeConfigSpec.IntValue NETHERITE_COOLDOWN;

    // Pocket Dimension
    public static final ForgeConfigSpec.IntValue DEFAULT_POCKET_SIZE;
    public static final ForgeConfigSpec.IntValue MAX_POCKET_SIZE;

    // Calls
    public static final ForgeConfigSpec.IntValue CALL_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue CONNECTED_CALL_MAX_HOURS;
    public static final ForgeConfigSpec.IntValue STATIC_THRESHOLD_PERCENT;

    // Upgrades
    public static final ForgeConfigSpec.IntValue COOLDOWN_REDUCER_PERCENT;
    public static final ForgeConfigSpec.IntValue RANGE_BOOSTER_PERCENT;

    // Mob Transport
    public static final ForgeConfigSpec.BooleanValue MOB_TRANSPORT_ENABLED;
    public static final ForgeConfigSpec.IntValue PET_TELEPORT_RANGE;

    // Mirror Chat
    public static final ForgeConfigSpec.BooleanValue MIRROR_CHAT_ENABLED;
    public static final ForgeConfigSpec.IntValue MIRROR_CHAT_RANGE;

    // General
    public static final ForgeConfigSpec.IntValue MAX_MIRRORS_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue CROSS_DIM_RANGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue TIER_UPGRADE_COST;

    // Portal Preview
    public static final ForgeConfigSpec.BooleanValue PORTAL_PREVIEW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue PORTAL_PREVIEW_DISTANCE;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("Mirror Range Settings").push("ranges");
        WOOD_RANGE = BUILDER.comment("Range for wood tier mirrors (blocks)").defineInRange("woodRange", 300, 50, 50000);
        STONE_RANGE = BUILDER.comment("Range for stone tier mirrors (blocks)").defineInRange("stoneRange", 500, 50, 50000);
        IRON_RANGE = BUILDER.comment("Range for iron tier mirrors (blocks)").defineInRange("ironRange", 800, 50, 50000);
        GOLD_RANGE = BUILDER.comment("Range for gold tier mirrors (blocks)").defineInRange("goldRange", 1200, 50, 50000);
        DIAMOND_RANGE = BUILDER.comment("Range for diamond tier mirrors (blocks)").defineInRange("diamondRange", 2000, 50, 50000);
        NETHERITE_RANGE = BUILDER.comment("Range for netherite tier mirrors (blocks)").defineInRange("netheriteRange", 5000, 50, 50000);
        BUILDER.pop();

        BUILDER.comment("Mirror Cooldown Settings (seconds between teleports per tier)").push("cooldowns");
        WOOD_COOLDOWN = BUILDER.comment("Cooldown for wood tier mirrors (seconds)").defineInRange("woodCooldown", 30, 0, 300);
        STONE_COOLDOWN = BUILDER.comment("Cooldown for stone tier mirrors (seconds)").defineInRange("stoneCooldown", 25, 0, 300);
        IRON_COOLDOWN = BUILDER.comment("Cooldown for iron tier mirrors (seconds)").defineInRange("ironCooldown", 20, 0, 300);
        GOLD_COOLDOWN = BUILDER.comment("Cooldown for gold tier mirrors (seconds)").defineInRange("goldCooldown", 15, 0, 300);
        DIAMOND_COOLDOWN = BUILDER.comment("Cooldown for diamond tier mirrors (seconds)").defineInRange("diamondCooldown", 10, 0, 300);
        NETHERITE_COOLDOWN = BUILDER.comment("Cooldown for netherite tier mirrors (seconds)").defineInRange("netheriteCooldown", 5, 0, 300);
        BUILDER.pop();

        BUILDER.comment("Pocket Dimension Settings").push("pocket");
        DEFAULT_POCKET_SIZE = BUILDER.comment("Default pocket dimension size (blocks)").defineInRange("defaultPocketSize", 32, 16, 256);
        MAX_POCKET_SIZE = BUILDER.comment("Maximum pocket dimension size (blocks)").defineInRange("maxPocketSize", 256, 32, 1024);
        BUILDER.pop();

        BUILDER.comment("Calling Mirror Settings").push("calls");
        CALL_TIMEOUT_SECONDS = BUILDER.comment("Seconds before unanswered call times out").defineInRange("callTimeoutSeconds", 30, 10, 120);
        CONNECTED_CALL_MAX_HOURS = BUILDER.comment("Maximum duration of an active connected call (hours) before it is automatically ended").defineInRange("connectedCallMaxHours", 1, 1, 24);
        STATIC_THRESHOLD_PERCENT = BUILDER.comment("Percent of max range where static begins").defineInRange("staticThresholdPercent", 80, 50, 99);
        BUILDER.pop();

        BUILDER.comment("Upgrade Settings").push("upgrades");
        COOLDOWN_REDUCER_PERCENT = BUILDER.comment("Percent cooldown reduction from the Cooldown Reducer upgrade").defineInRange("cooldownReducerPercent", 30, 5, 75);
        RANGE_BOOSTER_PERCENT = BUILDER.comment("Percent range increase from the Range Booster upgrade").defineInRange("rangeBoosterPercent", 50, 10, 200);
        BUILDER.pop();

        BUILDER.comment("Mob Transport Settings").push("mobtransport");
        MOB_TRANSPORT_ENABLED = BUILDER.comment("If true, leashed mobs and nearby tamed pets (not sitting) teleport with the player").define("mobTransportEnabled", true);
        PET_TELEPORT_RANGE = BUILDER.comment("Radius (blocks) within which tamed pets will teleport with the player").defineInRange("petTeleportRange", 5, 1, 20);
        BUILDER.pop();

        BUILDER.comment("Mirror Chat Settings").push("mirrorchat");
        MIRROR_CHAT_ENABLED = BUILDER.comment("If true, players must be near an accessible mirror to chat globally").define("mirrorChatEnabled", false);
        MIRROR_CHAT_RANGE = BUILDER.comment("Range (blocks) within which a player must be to a mirror to chat").defineInRange("mirrorChatRange", 4, 1, 32);
        BUILDER.pop();

        BUILDER.comment("General Settings").push("general");
        MAX_MIRRORS_PER_PLAYER = BUILDER.comment("Maximum mirrors a player can place").defineInRange("maxMirrorsPerPlayer", 20, 1, 100);
        CROSS_DIM_RANGE_MULTIPLIER = BUILDER.comment("Range multiplier for cross-dimension teleports").defineInRange("crossDimRangeMultiplier", 0.5, 0.1, 2.0);
        TIER_UPGRADE_COST = BUILDER.comment("Number of next-tier material items required to upgrade a mirror's tier").defineInRange("tierUpgradeCost", 8, 1, 64);
        PORTAL_PREVIEW_ENABLED = BUILDER.comment("If true, looking at a mirror with a Warp Target shows a live camera preview").define("portalPreviewEnabled", true);
        PORTAL_PREVIEW_DISTANCE = BUILDER.comment("Max distance to trigger portal preview (blocks)").defineInRange("portalPreviewDistance", 5.0, 1.0, 20.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /** Called after server config loads or reloads; logs warnings for cross-field constraint violations. */
    public static void onConfigLoaded(net.minecraftforge.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        if (DEFAULT_POCKET_SIZE.get() > MAX_POCKET_SIZE.get()) {
            EthersMirrors.LOGGER.warn(
                "[EthersMirrors] Config: defaultPocketSize ({}) > maxPocketSize ({})." +
                " defaultPocketSize will be capped to maxPocketSize at runtime.",
                DEFAULT_POCKET_SIZE.get(), MAX_POCKET_SIZE.get());
        }
    }
}
