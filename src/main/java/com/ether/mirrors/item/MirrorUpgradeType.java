package com.ether.mirrors.item;

public enum MirrorUpgradeType {
    WARP_TARGET("warp_target"),
    RANGE_BOOSTER("range_booster"),
    PRIVACY_LOCK("privacy_lock"),
    ALARM("alarm"),
    REDSTONE_PULSE("redstone_pulse"),
    PARTICLE_AURA("particle_aura"),
    MUTE_AMBIENT("mute_ambient"),
    ONE_WAY("one_way"),
    COOLDOWN_REDUCER("cooldown_reducer"),
    TIME_LOCK("time_lock");

    private final String id;

    MirrorUpgradeType(String id) { this.id = id; }

    public String getId() { return id; }

    public static MirrorUpgradeType fromId(String id) {
        for (MirrorUpgradeType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }

    public String getDisplayName() {
        return switch (this) {
            case WARP_TARGET -> "Warp Target";
            case RANGE_BOOSTER -> "Range Booster";
            case PRIVACY_LOCK -> "Privacy Lock";
            case ALARM -> "Alarm";
            case REDSTONE_PULSE -> "Redstone Pulse";
            case PARTICLE_AURA -> "Particle Aura";
            case MUTE_AMBIENT -> "Mute Ambient";
            case ONE_WAY -> "One Way";
            case COOLDOWN_REDUCER -> "Cooldown Reducer";
            case TIME_LOCK -> "Time Lock";
        };
    }

    public String getDescription() {
        return switch (this) {
            case WARP_TARGET -> "Auto-teleports to a set destination. Lock the target via the upgrades tab.";
            case RANGE_BOOSTER -> "Increases range by 50%";
            case PRIVACY_LOCK -> "Only you can use this mirror";
            case ALARM -> "Notifies you when the mirror is used";
            case REDSTONE_PULSE -> "Emits a redstone pulse when used";
            case PARTICLE_AURA -> "Displays a particle aura";
            case MUTE_AMBIENT -> "Silences the ambient mirror sound";
            case ONE_WAY -> "Mirror can only receive teleports, not send them";
            case COOLDOWN_REDUCER -> "Reduces teleport cooldown by " + com.ether.mirrors.MirrorsConfig.COOLDOWN_REDUCER_PERCENT.get() + "%";
            case TIME_LOCK -> "Mirror only works during day or night (set via /ethersmirrors timelock)";
        };
    }
}
