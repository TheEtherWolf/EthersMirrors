package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PermissionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerboundPermissionResponsePacket {

    // Permission flag constants for the bitfield
    public static final int FLAG_USE         = 1;
    public static final int FLAG_VIEW_CAMERA = 2;
    public static final int FLAG_BREAK       = 4;

    // Action: 0 = respond to pending request, 1 = revoke existing permissions
    private static final int ACTION_RESPOND = 0;
    private static final int ACTION_REVOKE  = 1;

    private final UUID requesterUUID;
    private final boolean accepted;
    // Which permissions to grant (bitfield: FLAG_USE | FLAG_VIEW_CAMERA | FLAG_BREAK)
    private final int permissionFlags;
    private final int action;

    public ServerboundPermissionResponsePacket(UUID requesterUUID, boolean accepted, int permissionFlags) {
        this(requesterUUID, accepted, permissionFlags, ACTION_RESPOND);
    }

    public ServerboundPermissionResponsePacket(UUID requesterUUID, boolean accepted, int permissionFlags, int action) {
        this.requesterUUID = requesterUUID;
        this.accepted = accepted;
        this.permissionFlags = permissionFlags;
        this.action = action;
    }

    /** Create a revoke packet that removes all permissions for a player. */
    public static ServerboundPermissionResponsePacket revoke(UUID targetUUID) {
        return new ServerboundPermissionResponsePacket(targetUUID, false, 0, ACTION_REVOKE);
    }

    public static void encode(ServerboundPermissionResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.requesterUUID);
        buf.writeBoolean(msg.accepted);
        buf.writeInt(msg.permissionFlags);
        buf.writeInt(msg.action);
    }

    public static ServerboundPermissionResponsePacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        boolean accepted = buf.readBoolean();
        int flags = buf.readInt();
        int action = buf.readInt();
        return new ServerboundPermissionResponsePacket(uuid, accepted, flags, action);
    }

    public static void handle(ServerboundPermissionResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer grantor = ctx.get().getSender();
            if (grantor == null) return;

            PermissionData permData = PermissionData.get(grantor.server);

            if (msg.action == ACTION_REVOKE) {
                // Revoke all permissions for this player from the grantor's mirrors
                permData.revokePermission(grantor.getUUID(), msg.requesterUUID, PermissionData.PermissionLevel.USE);
                permData.revokePermission(grantor.getUUID(), msg.requesterUUID, PermissionData.PermissionLevel.VIEW_CAMERA);
                permData.revokePermission(grantor.getUUID(), msg.requesterUUID, PermissionData.PermissionLevel.BREAK);

                ServerPlayer target = grantor.server.getPlayerList().getPlayer(msg.requesterUUID);
                if (target != null) {
                    target.displayClientMessage(
                            Component.literal(grantor.getGameProfile().getName() + " revoked your mirror access."), false);
                }
                grantor.displayClientMessage(Component.literal("Access revoked."), true);
                return;
            }

            // Remove the pending request
            permData.removePendingRequest(grantor.getUUID(), msg.requesterUUID);

            if (msg.accepted) {
                // Only grant if there was actually a pending request (prevent arbitrary grants)
                // Grant the selected permissions
                if ((msg.permissionFlags & FLAG_USE) != 0) {
                    permData.grantPermission(grantor.getUUID(), msg.requesterUUID, PermissionData.PermissionLevel.USE);
                }
                if ((msg.permissionFlags & FLAG_VIEW_CAMERA) != 0) {
                    permData.grantPermission(grantor.getUUID(), msg.requesterUUID, PermissionData.PermissionLevel.VIEW_CAMERA);
                }
                if ((msg.permissionFlags & FLAG_BREAK) != 0) {
                    permData.grantPermission(grantor.getUUID(), msg.requesterUUID, PermissionData.PermissionLevel.BREAK);
                }

                // Notify requester if online
                ServerPlayer requester = grantor.server.getPlayerList().getPlayer(msg.requesterUUID);
                if (requester != null) {
                    requester.displayClientMessage(
                            Component.literal(grantor.getGameProfile().getName() + " granted you mirror access!"), false);
                }
                grantor.displayClientMessage(Component.literal("Permission granted."), true);
            } else {
                // Notify requester if online
                ServerPlayer requester = grantor.server.getPlayerList().getPlayer(msg.requesterUUID);
                if (requester != null) {
                    requester.displayClientMessage(
                            Component.literal(grantor.getGameProfile().getName() + " denied your mirror access request."), false);
                }
                grantor.displayClientMessage(Component.literal("Permission denied."), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
