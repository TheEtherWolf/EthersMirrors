package com.ether.mirrors.network.packets;

import com.ether.mirrors.MirrorsConfig;
import com.ether.mirrors.block.ExitMirrorBlock;
import com.ether.mirrors.block.PocketMirrorBlock;
import com.ether.mirrors.data.PocketDimensionData;
import com.ether.mirrors.init.MirrorsBlocks;
import com.ether.mirrors.item.MirrorItem;
import com.ether.mirrors.util.MirrorTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent when the player right-clicks a pocket mirror item in hand.
 * Server enters the player's own pocket dimension directly.
 */
public class ServerboundEnterPocketHandheldPacket {

    public ServerboundEnterPocketHandheldPacket() {}

    public static void encode(ServerboundEnterPocketHandheldPacket msg, FriendlyByteBuf buf) {}

    public static ServerboundEnterPocketHandheldPacket decode(FriendlyByteBuf buf) {
        return new ServerboundEnterPocketHandheldPacket();
    }

    public static void handle(ServerboundEnterPocketHandheldPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Prevent re-entry while already in a pocket (would overwrite return point)
            if (player.level().dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) {
                player.displayClientMessage(Component.literal("You are already in a pocket dimension."), true);
                return;
            }

            ServerLevel pocketLevel = player.server.getLevel(PocketMirrorBlock.POCKET_DIMENSION);
            if (pocketLevel == null) {
                player.displayClientMessage(Component.literal("Pocket dimension not available."), true);
                return;
            }

            PocketDimensionData pocketData = PocketDimensionData.get(player.server);
            PocketDimensionData.PocketRegion region = pocketData.getOrCreateRegion(
                    player.getUUID(), MirrorsConfig.DEFAULT_POCKET_SIZE.get());

            // Fire API event — cancelable (matches block-based entry in PocketMirrorBlock.enterPocket)
            com.ether.mirrors.api.event.PocketEnterEvent enterEvent =
                    new com.ether.mirrors.api.event.PocketEnterEvent(player, player.getUUID());
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(enterEvent)) return;

            // Save return position and track which pocket the player is in
            if (!player.level().dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) {
                pocketData.setEntryReturn(player.getUUID(),
                        player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot(),
                        player.level().dimension());
            }
            pocketData.setPlayerInPocket(player.getUUID(), player.getUUID());

            // Teleport first — ensures destination chunks are loaded
            BlockPos spawnPos = region.getSpawnPos();
            player.teleportTo(pocketLevel,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5,
                    player.getYRot(), player.getXRot());

            // Fire advancement trigger after teleport (same order as block-based entry)
            com.ether.mirrors.advancement.MirrorsTriggers.ENTER_POCKET.trigger(player);

            // Build room and barriers AFTER teleport (chunk now guaranteed loaded)
            PocketMirrorBlock.buildRoomIfNeeded(pocketLevel, region, pocketData);
            MirrorTier entryTier = MirrorItem.getTierFromStack(player.getMainHandItem());
            if (entryTier == null) entryTier = MirrorItem.getTierFromStack(player.getOffhandItem());
            if (entryTier == null) entryTier = MirrorTier.WOOD; // Default to most restrictive tier if no mirror held
            PocketMirrorBlock.applyTierBarriers(pocketLevel, region, entryTier);

            // Place exit mirror if not already there
            BlockState exitState = pocketLevel.getBlockState(spawnPos);
            if (!(exitState.getBlock() instanceof ExitMirrorBlock)) {
                pocketLevel.setBlock(spawnPos, MirrorsBlocks.EXIT_MIRROR.get().defaultBlockState(), Block.UPDATE_ALL);
            }

            player.displayClientMessage(Component.literal("Entering pocket dimension..."), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
