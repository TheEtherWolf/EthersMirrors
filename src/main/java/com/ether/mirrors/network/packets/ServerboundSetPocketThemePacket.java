package com.ether.mirrors.network.packets;

import com.ether.mirrors.data.PocketDimensionData;
import com.ether.mirrors.data.PocketTheme;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundSetPocketThemePacket {

    private final String themeName;

    public ServerboundSetPocketThemePacket(String themeName) {
        this.themeName = themeName;
    }

    public static void encode(ServerboundSetPocketThemePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.themeName, 32);
    }

    public static ServerboundSetPocketThemePacket decode(FriendlyByteBuf buf) {
        return new ServerboundSetPocketThemePacket(buf.readUtf(32));
    }

    public static void handle(ServerboundSetPocketThemePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PocketTheme theme = null;
            for (PocketTheme t : PocketTheme.values()) {
                if (t.getId().equals(msg.themeName)) {
                    theme = t;
                    break;
                }
            }
            if (theme == null) return;

            PocketDimensionData pocketData = PocketDimensionData.get(player.server);
            pocketData.setRegionTheme(player.getUUID(), theme);

            // Force the platform to rebuild with new theme on next entry
            // (roomBuilt=false triggers a clean rebuild in buildRoomIfNeeded)
            PocketDimensionData.PocketRegion region = pocketData.getRegion(player.getUUID());
            if (region != null) {
                region.roomBuilt = false;
                pocketData.setDirty();
            }

            player.displayClientMessage(
                    Component.literal("Pocket theme set to " + theme.getDisplayName() + ". Changes apply on next entry."), true);
            com.ether.mirrors.advancement.MirrorsTriggers.POCKET_THEME_SET.trigger(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
