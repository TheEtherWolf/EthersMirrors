package com.ether.mirrors.block.entity;

import com.ether.mirrors.init.MirrorsBlockEntities;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.util.MirrorTier;
import com.ether.mirrors.util.MirrorType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MirrorBlockEntity extends BlockEntity {

    private UUID ownerUUID;
    private UUID mirrorId;
    private MirrorTier tier;
    private MirrorType type;
    private String mirrorName = "";
    private List<String> appliedUpgrades = new ArrayList<>();
    private CompoundTag upgradeData = new CompoundTag();
    private int pulseTicks = 0;
    private int dyeColor = -1; // -1 = no dye, 0-15 = DyeColor ordinal
    private boolean activated = false; // must be activated by owner before anyone can use it
    private byte[] iconPixels = new byte[256]; // 16x16 pixel art icon (palette indices)
    private String description = ""; // optional mirror description, max 128 chars

    public MirrorBlockEntity(BlockPos pos, BlockState state, MirrorTier tier, MirrorType type) {
        super(MirrorsBlockEntities.MIRROR_BLOCK_ENTITY.get(), pos, state);
        this.tier = tier;
        this.type = type;
        this.mirrorId = UUID.randomUUID();
    }

    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(MirrorsBlockEntities.MIRROR_BLOCK_ENTITY.get(), pos, state);
        this.mirrorId = UUID.randomUUID();
    }

    public void setOwner(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        setChanged();
    }

    public void setMirrorId(UUID id) {
        this.mirrorId = id;
        setChanged();
    }

    public void setTier(MirrorTier tier) {
        this.tier = tier;
        setChanged();
    }

    @Nullable
    public UUID getOwnerUUID() { return ownerUUID; }
    public UUID getMirrorId() { return mirrorId; }
    public MirrorTier getTier() { return tier; }
    public MirrorType getMirrorType() { return type; }

    public String getMirrorName() { return mirrorName; }

    public void setMirrorName(String name) {
        this.mirrorName = name != null ? name : "";
        setChanged();
        // Sync to clients
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasCustomName() {
        return mirrorName != null && !mirrorName.isEmpty();
    }

    /**
     * Returns a display name for this mirror: custom name if set, otherwise "Tier Type Mirror".
     * Pocket mirrors omit the tier since they are now tier-independent (B11).
     */
    public String getDisplayName() {
        if (hasCustomName()) {
            return mirrorName;
        }
        // B11: pocket mirrors have no meaningful tier in the display name
        if (type == com.ether.mirrors.util.MirrorType.POCKET) {
            return "Pocket Mirror";
        }
        String tierName = tier != null ? capitalize(tier.getName()) : "Unknown";
        String typeName = type != null ? capitalize(type.getName()) : "Mirror";
        return tierName + " " + typeName + " Mirror";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public boolean isOwner(Player player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    public boolean isActivated() { return activated; }
    public void setActivated(boolean activated) {
        this.activated = activated;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasUpgrade(MirrorUpgradeType type) {
        return appliedUpgrades.contains(type.getId());
    }

    public void addUpgrade(MirrorUpgradeType type) {
        if (!appliedUpgrades.contains(type.getId())) {
            appliedUpgrades.add(type.getId());
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void removeUpgrade(MirrorUpgradeType type) {
        appliedUpgrades.remove(type.getId());
        upgradeData.remove(type.getId());
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public List<String> getAppliedUpgrades() { return new ArrayList<>(appliedUpgrades); }
    public void setAppliedUpgrades(List<String> upgrades) { this.appliedUpgrades = new ArrayList<>(upgrades); setChanged(); }
    public CompoundTag getUpgradeData() { return upgradeData; }
    public void setUpgradeData(CompoundTag data) { this.upgradeData = data != null ? data : new CompoundTag(); setChanged(); }

    public int getSignal() { return pulseTicks > 0 ? 15 : 0; }
    public void triggerPulse() { pulseTicks = 2; setChanged(); }

    public int getDyeColor() { return dyeColor; }
    public void setDyeColor(int dyeColor) { this.dyeColor = dyeColor; this.setChanged(); }

    public byte[] getIconPixels() { return iconPixels; }
    public void setIconPixels(byte[] p) { this.iconPixels = p != null && p.length == 256 ? p.clone() : new byte[256]; setChanged(); }

    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc != null ? desc : ""; setChanged(); }

    public void serverTick() {
        if (pulseTicks > 0) {
            pulseTicks--;
            if (level != null && !level.isClientSide()) {
                level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
            }
            if (pulseTicks == 0) {
                setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        if (mirrorId != null) {
            tag.putUUID("MirrorId", mirrorId);
        }
        if (tier != null) {
            tag.putString("Tier", tier.getName());
        }
        if (type != null) {
            tag.putString("Type", type.getName());
        }
        tag.putString("MirrorName", mirrorName);
        ListTag upgradeList = new ListTag();
        for (String u : appliedUpgrades) upgradeList.add(StringTag.valueOf(u));
        tag.put("Upgrades", upgradeList);
        tag.put("UpgradeData", upgradeData.copy());
        tag.putInt("PulseTicks", pulseTicks);
        if (dyeColor >= 0) tag.putInt("DyeColor", dyeColor);
        tag.putBoolean("Activated", activated);
        tag.putByteArray("IconPixels", iconPixels);
        tag.putString("Description", description);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
        if (tag.hasUUID("MirrorId")) {
            this.mirrorId = tag.getUUID("MirrorId");
        }
        if (tag.contains("Tier")) {
            String tierName = tag.getString("Tier");
            for (MirrorTier t : MirrorTier.values()) {
                if (t.getName().equals(tierName)) {
                    this.tier = t;
                    break;
                }
            }
            if (this.tier == null) {
                com.ether.mirrors.EthersMirrors.LOGGER.warn("[EthersMirrors] Mirror at {} has unrecognized tier '{}', falling back to WOOD. World data may be corrupted.", getBlockPos(), tierName);
                this.tier = MirrorTier.WOOD;
            }
        }
        if (tag.contains("Type")) {
            String typeName = tag.getString("Type");
            for (MirrorType t : MirrorType.values()) {
                if (t.getName().equals(typeName)) {
                    this.type = t;
                    break;
                }
            }
            if (this.type == null) {
                com.ether.mirrors.EthersMirrors.LOGGER.warn("[EthersMirrors] Mirror at {} has unrecognized type '{}', falling back to TELEPORT. World data may be corrupted.", getBlockPos(), typeName);
                this.type = MirrorType.TELEPORT;
            }
        }
        if (tag.contains("MirrorName")) {
            this.mirrorName = tag.getString("MirrorName");
        }
        if (tag.contains("Upgrades")) {
            appliedUpgrades = new ArrayList<>();
            ListTag upgradeList = tag.getList("Upgrades", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < upgradeList.size(); i++) {
                appliedUpgrades.add(upgradeList.getString(i));
            }
        }
        if (tag.contains("UpgradeData")) {
            upgradeData = tag.getCompound("UpgradeData").copy();
        }
        if (tag.contains("PulseTicks")) {
            pulseTicks = tag.getInt("PulseTicks");
        }
        if (tag.contains("DyeColor")) dyeColor = tag.getInt("DyeColor");
        if (tag.contains("Activated")) activated = tag.getBoolean("Activated");
        if (tag.contains("IconPixels")) {
            byte[] p = tag.getByteArray("IconPixels");
            if (p.length == 256) iconPixels = p;
        }
        // Legacy: ignore SigilIndex (not migrated — old sigil just disappears)
        if (tag.contains("Description")) description = tag.getString("Description");
    }

    // Client sync methods
    @Override
    public CompoundTag getUpdateTag() {
        // Send what the client needs for rendering, display, and interaction logic.
        // OwnerUUID is needed client-side for ownership checks (activation, naming, management).
        // Player UUIDs are already public (tab list, chat, etc.) so this is not a privacy concern.
        CompoundTag tag = super.getUpdateTag();
        if (mirrorId != null) tag.putUUID("MirrorId", mirrorId);
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        if (tier != null) tag.putString("Tier", tier.getName());
        if (type != null) tag.putString("Type", type.getName());
        tag.putString("MirrorName", mirrorName);
        ListTag upgradeList = new ListTag();
        for (String u : appliedUpgrades) upgradeList.add(StringTag.valueOf(u));
        tag.put("Upgrades", upgradeList);
        tag.put("UpgradeData", upgradeData.copy());
        tag.putInt("PulseTicks", pulseTicks);
        if (dyeColor >= 0) tag.putInt("DyeColor", dyeColor);
        tag.putBoolean("Activated", activated);
        tag.putByteArray("IconPixels", iconPixels);
        tag.putString("Description", description);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
