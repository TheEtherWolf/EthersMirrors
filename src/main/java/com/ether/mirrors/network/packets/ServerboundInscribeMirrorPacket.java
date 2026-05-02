package com.ether.mirrors.network.packets;

import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent by MirrorPlacementScreen when the player presses ACTIVATE.
 * Applies name, sigil, tint, description, access mode, and marks the mirror as activated.
 * Replaces the old ServerboundActivateMirrorPacket flow.
 */
public class ServerboundInscribeMirrorPacket {

    private final BlockPos mirrorPos;
    private final String name;       // max 32
    private final byte[] iconPixels; // 256 bytes, 16x16 palette indices
    private final int privacyLevel;  // 0=LOCKED, 1=VIEW, 2=CALL, 3=ENTER
    private final int dyeColor;      // 0-15, or -1 for none
    private final String description; // max 128
    private final boolean pocketBound; // only meaningful for POCKET-type mirrors

    public ServerboundInscribeMirrorPacket(BlockPos mirrorPos, String name, byte[] iconPixels,
                                           int privacyLevel, int dyeColor, String description,
                                           boolean pocketBound) {
        this.mirrorPos = mirrorPos;
        this.name = name;
        this.iconPixels = iconPixels != null && iconPixels.length == 256 ? iconPixels : new byte[256];
        this.privacyLevel = privacyLevel;
        this.dyeColor = dyeColor;
        this.description = description;
        this.pocketBound = pocketBound;
    }

    public static void encode(ServerboundInscribeMirrorPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.mirrorPos);
        buf.writeUtf(msg.name, 32);
        buf.writeBytes(msg.iconPixels != null ? msg.iconPixels : new byte[256]);
        buf.writeByte(msg.privacyLevel);
        buf.writeByte(msg.dyeColor + 1); // shift by 1 so -1 → 0 on wire
        buf.writeUtf(msg.description, 128);
        buf.writeBoolean(msg.pocketBound);
    }

    public static ServerboundInscribeMirrorPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(32);
        byte[] iconPixels = new byte[256];
        buf.readBytes(iconPixels);
        int privacy = buf.readByte() & 0xFF;
        int dye = (buf.readByte() & 0xFF) - 1; // un-shift
        String desc = buf.readUtf(128);
        boolean pocket = buf.readBoolean();
        return new ServerboundInscribeMirrorPacket(pos, name, iconPixels, privacy, dye, desc, pocket);
    }

    public static void handle(ServerboundInscribeMirrorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Proximity check
            if (player.distanceToSqr(msg.mirrorPos.getX() + 0.5, msg.mirrorPos.getY() + 0.5,
                    msg.mirrorPos.getZ() + 0.5) > 64) return;

            if (!(player.level().getBlockEntity(msg.mirrorPos) instanceof MirrorBlockEntity mirrorBE)) return;
            if (!mirrorBE.isOwner(player)) return;
            if (mirrorBE.isActivated()) return; // already inscribed

            // Sanitize
            String name = msg.name.strip();
            if (name.isEmpty()) return;
            if (name.length() > 32) name = name.substring(0, 32);
            String desc = msg.description.length() > 128 ? msg.description.substring(0, 128) : msg.description;
            int privacy = Math.max(0, Math.min(3, msg.privacyLevel));
            int dye = (msg.dyeColor < -1 || msg.dyeColor > 15) ? -1 : msg.dyeColor;

            mirrorBE.setMirrorName(name);
            mirrorBE.setIconPixels(msg.iconPixels);
            mirrorBE.setDyeColor(dye);
            mirrorBE.setDescription(desc);
            mirrorBE.setActivated(true);
            mirrorBE.setChanged();

            // Update the network data entry with the chosen name and icon
            MirrorNetworkData networkData = MirrorNetworkData.get(player.server);
            networkData.updateMirrorName(mirrorBE.getMirrorId(), name);
            networkData.updateMirrorIcon(mirrorBE.getMirrorId(), msg.iconPixels);

            // Set per-mirror access mode: LOCKED → PRIVATE, VIEW/CALL/ENTER → PUBLIC
            PermissionData permData = PermissionData.get(player.server);
            PermissionData.AccessMode accessMode = (privacy == 0)
                    ? PermissionData.AccessMode.PRIVATE
                    : PermissionData.AccessMode.PUBLIC;
            permData.setMirrorAccessMode(mirrorBE.getMirrorId(), accessMode);

            player.playSound(com.ether.mirrors.init.MirrorsSounds.MIRROR_TELEPORT.get(), 0.6F, 1.2F);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Mirror inscribed: " + name), true);

            // Sync waypoints if beacon mirror
            if (mirrorBE.getMirrorType() == com.ether.mirrors.util.MirrorType.BEACON) {
                for (ServerPlayer sp : player.server.getPlayerList().getPlayers()) {
                    com.ether.mirrors.network.MirrorsNetwork.sendWaypointSync(sp);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
