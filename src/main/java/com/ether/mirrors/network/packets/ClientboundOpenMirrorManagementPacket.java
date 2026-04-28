package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.screen.MirrorManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class ClientboundOpenMirrorManagementPacket {

    public record PlayerEntry(UUID uuid, String name) {}

    public final UUID mirrorId;
    public final UUID ownerUUID;
    public final String mirrorName;
    public final String tierName;
    public final String typeName;
    public final BlockPos pos;
    public final String dimensionName;
    public final PermissionData.AccessMode accessMode;
    public final List<PlayerEntry> allowList;
    public final List<PlayerEntry> blockList;
    public final List<String> appliedUpgrades;
    public final int pocketCurrentSize;
    public final int pocketMaxSize;
    public final boolean warpTargetLocked;

    public ClientboundOpenMirrorManagementPacket(
            UUID mirrorId, UUID ownerUUID, String mirrorName, String tierName, String typeName,
            BlockPos pos, String dimensionName, PermissionData.AccessMode accessMode,
            List<PlayerEntry> allowList, List<PlayerEntry> blockList) {
        this(mirrorId, ownerUUID, mirrorName, tierName, typeName, pos, dimensionName,
                accessMode, allowList, blockList, Collections.emptyList(), 0, 0);
    }

    public ClientboundOpenMirrorManagementPacket(
            UUID mirrorId, UUID ownerUUID, String mirrorName, String tierName, String typeName,
            BlockPos pos, String dimensionName, PermissionData.AccessMode accessMode,
            List<PlayerEntry> allowList, List<PlayerEntry> blockList, List<String> appliedUpgrades) {
        this(mirrorId, ownerUUID, mirrorName, tierName, typeName, pos, dimensionName,
                accessMode, allowList, blockList, appliedUpgrades, 0, 0);
    }

    public ClientboundOpenMirrorManagementPacket(
            UUID mirrorId, UUID ownerUUID, String mirrorName, String tierName, String typeName,
            BlockPos pos, String dimensionName, PermissionData.AccessMode accessMode,
            List<PlayerEntry> allowList, List<PlayerEntry> blockList, List<String> appliedUpgrades,
            int pocketCurrentSize, int pocketMaxSize) {
        this(mirrorId, ownerUUID, mirrorName, tierName, typeName, pos, dimensionName,
                accessMode, allowList, blockList, appliedUpgrades, pocketCurrentSize, pocketMaxSize, false);
    }

    public ClientboundOpenMirrorManagementPacket(
            UUID mirrorId, UUID ownerUUID, String mirrorName, String tierName, String typeName,
            BlockPos pos, String dimensionName, PermissionData.AccessMode accessMode,
            List<PlayerEntry> allowList, List<PlayerEntry> blockList, List<String> appliedUpgrades,
            int pocketCurrentSize, int pocketMaxSize, boolean warpTargetLocked) {
        this.mirrorId = mirrorId;
        this.ownerUUID = ownerUUID;
        this.mirrorName = mirrorName;
        this.tierName = tierName;
        this.typeName = typeName;
        this.pos = pos;
        this.dimensionName = dimensionName;
        this.accessMode = accessMode;
        this.allowList = allowList;
        this.blockList = blockList;
        this.appliedUpgrades = appliedUpgrades != null ? appliedUpgrades : Collections.emptyList();
        this.pocketCurrentSize = pocketCurrentSize;
        this.pocketMaxSize = pocketMaxSize;
        this.warpTargetLocked = warpTargetLocked;
    }

    public static void encode(ClientboundOpenMirrorManagementPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.mirrorId);
        buf.writeUUID(msg.ownerUUID);
        buf.writeUtf(msg.mirrorName, 64);
        buf.writeUtf(msg.tierName, 32);
        buf.writeUtf(msg.typeName, 32);
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.dimensionName, 128);
        buf.writeUtf(msg.accessMode.getId(), 16);
        buf.writeInt(msg.allowList.size());
        for (PlayerEntry e : msg.allowList) { buf.writeUUID(e.uuid()); buf.writeUtf(e.name(), 48); }
        buf.writeInt(msg.blockList.size());
        for (PlayerEntry e : msg.blockList) { buf.writeUUID(e.uuid()); buf.writeUtf(e.name(), 48); }
        buf.writeInt(msg.appliedUpgrades.size());
        for (String s : msg.appliedUpgrades) buf.writeUtf(s, 32);
        buf.writeInt(msg.pocketCurrentSize);
        buf.writeInt(msg.pocketMaxSize);
        buf.writeBoolean(msg.warpTargetLocked);
    }

    public static ClientboundOpenMirrorManagementPacket decode(FriendlyByteBuf buf) {
        UUID mirrorId = buf.readUUID();
        UUID ownerUUID = buf.readUUID();
        String mirrorName = buf.readUtf(64);
        String tierName = buf.readUtf(32);
        String typeName = buf.readUtf(32);
        BlockPos pos = buf.readBlockPos();
        String dimensionName = buf.readUtf(128);
        PermissionData.AccessMode accessMode = PermissionData.AccessMode.fromId(buf.readUtf(16));
        if (accessMode == null) accessMode = PermissionData.AccessMode.INHERIT; // safe default for unknown IDs
        int allowCount = buf.readInt();
        if (allowCount < 0 || allowCount > 1000) throw new io.netty.handler.codec.DecoderException("EthersMirrors: allow list size out of bounds: " + allowCount);
        List<PlayerEntry> allowList = new ArrayList<>();
        for (int i = 0; i < allowCount; i++) allowList.add(new PlayerEntry(buf.readUUID(), buf.readUtf(48)));
        int blockCount = buf.readInt();
        if (blockCount < 0 || blockCount > 1000) throw new io.netty.handler.codec.DecoderException("EthersMirrors: block list size out of bounds: " + blockCount);
        List<PlayerEntry> blockList = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) blockList.add(new PlayerEntry(buf.readUUID(), buf.readUtf(48)));
        int upgradeCount = buf.readInt();
        if (upgradeCount < 0 || upgradeCount > 16) throw new io.netty.handler.codec.DecoderException("EthersMirrors: upgrade list size out of bounds: " + upgradeCount);
        List<String> appliedUpgrades = new ArrayList<>();
        for (int i = 0; i < upgradeCount; i++) appliedUpgrades.add(buf.readUtf(32));
        int pocketCurrentSize = buf.readInt();
        int pocketMaxSize = buf.readInt();
        boolean warpTargetLocked = buf.readBoolean();
        return new ClientboundOpenMirrorManagementPacket(mirrorId, ownerUUID, mirrorName, tierName, typeName,
                pos, dimensionName, accessMode, allowList, blockList, appliedUpgrades, pocketCurrentSize, pocketMaxSize, warpTargetLocked);
    }

    public static void handle(ClientboundOpenMirrorManagementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> Minecraft.getInstance().setScreen(new MirrorManagementScreen(msg)));
        ctx.get().setPacketHandled(true);
    }
}
