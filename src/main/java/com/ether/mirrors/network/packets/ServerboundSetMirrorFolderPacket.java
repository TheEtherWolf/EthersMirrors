package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundSetMirrorFolderPacket {

    private final UUID mirrorId;
    private final String folderName;

    public ServerboundSetMirrorFolderPacket(UUID mirrorId, String folderName) {
        this.mirrorId = mirrorId;
        this.folderName = folderName != null ? folderName : "";
    }

    public static void encode(ServerboundSetMirrorFolderPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.mirrorId);
        buf.writeUtf(msg.folderName, 64);
    }

    public static ServerboundSetMirrorFolderPacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetMirrorFolderPacket(buf.readUUID(), buf.readUtf(64));
    }

    public static void handle(ServerboundSetMirrorFolderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(msg.mirrorId);
            // Only the owner may change a mirror's folder
            if (entry != null && entry.ownerUUID != null && entry.ownerUUID.equals(player.getUUID())) {
                networkData.setMirrorFolder(msg.mirrorId, msg.folderName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
