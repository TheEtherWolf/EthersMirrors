package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.data.PermissionData.PermissionLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: set a player's global permission tier on the sender's mirrors.
 *
 * <p>Tiers mirror the PermissionScreen ladder:
 * <ul>
 *   <li>0 = NONE  — revoke all permissions
 *   <li>1 = VIEW  — VIEW_CAMERA only
 *   <li>2 = CALL  — USE + VIEW_CAMERA
 *   <li>3 = ENTER — USE + VIEW_CAMERA + BREAK
 * </ul>
 */
public class ServerboundSetPermissionLevelPacket {

    private final UUID targetUUID;
    private final int  permLevel;   // 0-3

    public ServerboundSetPermissionLevelPacket(UUID targetUUID, int permLevel) {
        this.targetUUID = targetUUID;
        this.permLevel  = Math.max(0, Math.min(3, permLevel));
    }

    public static void encode(ServerboundSetPermissionLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetUUID);
        buf.writeByte(msg.permLevel);
    }

    public static ServerboundSetPermissionLevelPacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetPermissionLevelPacket(buf.readUUID(), buf.readByte());
    }

    public static void handle(ServerboundSetPermissionLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer grantor = ctx.get().getSender();
            if (grantor == null) return;

            UUID grantorId = grantor.getUUID();
            UUID granteeId = msg.targetUUID;
            PermissionData data = PermissionData.get(grantor.server);

            // Always clear the existing grants first so we get a clean replacement.
            data.revokeAllPermissions(grantorId, granteeId);

            switch (msg.permLevel) {
                case 1 -> data.grantPermission(grantorId, granteeId, PermissionLevel.VIEW_CAMERA);
                case 2 -> {
                    data.grantPermission(grantorId, granteeId, PermissionLevel.USE);
                    data.grantPermission(grantorId, granteeId, PermissionLevel.VIEW_CAMERA);
                }
                case 3 -> {
                    data.grantPermission(grantorId, granteeId, PermissionLevel.USE);
                    data.grantPermission(grantorId, granteeId, PermissionLevel.VIEW_CAMERA);
                    data.grantPermission(grantorId, granteeId, PermissionLevel.BREAK);
                }
                // case 0 (NONE): already revoked above — nothing more to do
            }

            grantor.displayClientMessage(
                    Component.literal("Permission updated."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
