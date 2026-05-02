package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent by the Management screen's icon editor when the player saves their icon. */
public class ServerboundSaveIconPacket {

    private final BlockPos mirrorPos;
    private final byte[] iconPixels; // exactly 256 bytes

    public ServerboundSaveIconPacket(BlockPos mirrorPos, byte[] iconPixels) {
        this.mirrorPos = mirrorPos;
        this.iconPixels = iconPixels != null && iconPixels.length == 256 ? iconPixels : new byte[256];
    }

    public static void encode(ServerboundSaveIconPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
        buf.writeBytes(msg.iconPixels);
    }

    public static ServerboundSaveIconPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        byte[] pixels = new byte[256];
        buf.readBytes(pixels);
        return new ServerboundSaveIconPacket(pos, pixels);
    }

    public static void handle(ServerboundSaveIconPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5,
                    msg.mirrorPos.getZ() + 0.5) > 64) return;
            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player) && !player.hasPermissions(2)) return;
            mirrorBE.setIconPixels(msg.iconPixels);
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            networkData.updateMirrorIcon(mirrorBE.getMirrorId(), msg.iconPixels);
        });
        ctx.get().setPacketHandled(true);
    }
}
