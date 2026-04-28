package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundSetMirrorNamePacket {

    private final BlockPos mirrorPos;
    private final String name;

    public ServerboundSetMirrorNamePacket(BlockPos mirrorPos, String name) {
        this.mirrorPos = mirrorPos;
        this.name = name;
    }

    public static void encode(ServerboundSetMirrorNamePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
        buf.writeUtf(msg.name, 48); // Max 48 characters (matches server-side sanitization limit)
    }

    public static ServerboundSetMirrorNamePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetMirrorNamePacket(buf.readBlockPos(), buf.readUtf(48));
    }

    public static void handle(ServerboundSetMirrorNamePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Validate player is near the mirror
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5, msg.mirrorPos.getZ() + 0.5) > 64) {
                return;
            }

            // Get the block entity
            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) {
                return;
            }

            // Validate player is the owner
            if (!mirrorBE.isOwner(player)) {
                return;
            }

            // Sanitize name: strip control characters, formatting codes, whitespace, limit length
            String sanitizedName = msg.name.replaceAll("[\\p{Cntrl}§]", "").trim();
            if (sanitizedName.length() > 48) {
                sanitizedName = sanitizedName.substring(0, 48);
            }

            // Capture old name before updating
            String oldName = mirrorBE.getMirrorName();

            // Update block entity
            mirrorBE.setMirrorName(sanitizedName);

            // Update mirror network data
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            networkData.updateMirrorName(mirrorBE.getMirrorId(), sanitizedName);

            // Fire API event
            MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(mirrorBE.getMirrorId());
            if (entry != null) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.ether.mirrors.api.event.MirrorRenamedEvent(player, entry, oldName, sanitizedName));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
