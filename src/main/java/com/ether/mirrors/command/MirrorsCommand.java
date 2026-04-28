package com.ether.mirrors.command;

import com.ether.mirrors.MirrorsConfig;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.item.MirrorUpgradeType;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientboundMirrorMessagePacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class MirrorsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Admin subcommands — require operator permission
        dispatcher.register(Commands.literal("ethersmirrors")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MirrorsCommand::inspect)))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MirrorsCommand::reset)))
                .then(Commands.literal("list")
                        .executes(MirrorsCommand::listAll))
                .then(Commands.literal("timelock")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"day", "night"}, builder))
                                        .executes(MirrorsCommand::setTimeLock))))
        );

        // Player subcommands — available to all players
        dispatcher.register(Commands.literal("ethersmirrors")
                .then(Commands.literal("dm")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("message", MessageArgument.message())
                                        .executes(MirrorsCommand::sendDm))))
        );
    }

    private static int inspect(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack source = ctx.getSource();

        MirrorNetworkData networkData = MirrorNetworkData.get(source.getServer());
        PermissionData permData = PermissionData.get(source.getServer());

        List<MirrorNetworkData.MirrorEntry> mirrors = networkData.getMirrorsForPlayer(target.getUUID());

        source.sendSuccess(() -> Component.literal("--- Mirrors for " + target.getGameProfile().getName() + " ---"), false);
        source.sendSuccess(() -> Component.literal("Total mirrors: " + mirrors.size()), false);

        for (MirrorNetworkData.MirrorEntry entry : mirrors) {
            String name = entry.name != null && !entry.name.isEmpty() ? entry.name : "Unnamed";
            String info = String.format("  [%s] %s %s at %d, %d, %d in %s",
                    name,
                    entry.tier.getDisplayName(),
                    entry.type.getDisplayName(),
                    entry.pos.getX(), entry.pos.getY(), entry.pos.getZ(),
                    entry.dimension.location().toString());
            source.sendSuccess(() -> Component.literal(info), false);
        }

        // Show permissions
        var granted = permData.getGrantedPermissions(target.getUUID());
        if (!granted.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Granted permissions to:"), false);
            for (var grantEntry : granted.entrySet()) {
                String grantee = grantEntry.getKey().toString();
                ServerPlayer granteePlayer = source.getServer().getPlayerList().getPlayer(grantEntry.getKey());
                if (granteePlayer != null) {
                    grantee = granteePlayer.getGameProfile().getName();
                }
                String perms = grantEntry.getValue().toString();
                final String display = "  " + grantee + ": " + perms;
                source.sendSuccess(() -> Component.literal(display), false);
            }
        }

        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CommandSourceStack source = ctx.getSource();

        MirrorNetworkData networkData = MirrorNetworkData.get(source.getServer());

        List<MirrorNetworkData.MirrorEntry> mirrors = networkData.getMirrorsForPlayer(target.getUUID());
        int count = mirrors.size();

        // Remove all mirrors from network data and clean up permissions
        com.ether.mirrors.data.PermissionData permData = com.ether.mirrors.data.PermissionData.get(source.getServer());
        for (MirrorNetworkData.MirrorEntry entry : mirrors) {
            networkData.removeMirror(entry.mirrorId);
            permData.removeAllPermissionsForMirror(entry.mirrorId);
        }

        source.sendSuccess(() -> Component.literal("Removed " + count + " mirror(s) from " + target.getGameProfile().getName() + "'s network data."), false);
        source.sendSuccess(() -> Component.literal("Note: Physical mirror blocks still exist in the world. Use /fill or break them manually."), false);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.literal("--- All Registered Mirrors ---"), false);
        source.sendSuccess(() -> Component.literal("Use /ethersmirrors inspect <player> to see specific player's mirrors."), false);

        return 1;
    }

    private static int setTimeLock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String mode = StringArgumentType.getString(ctx, "mode");

        if (!mode.equals("day") && !mode.equals("night")) {
            source.sendFailure(Component.literal("Mode must be 'day' or 'night'."));
            return 0;
        }

        if (!(source.getLevel().getBlockEntity(pos) instanceof MirrorBlockEntity mirrorBE)) {
            source.sendFailure(Component.literal("No mirror block entity at " + pos.toShortString() + "."));
            return 0;
        }

        if (!mirrorBE.hasUpgrade(MirrorUpgradeType.TIME_LOCK)) {
            source.sendFailure(Component.literal("That mirror does not have the Time Lock upgrade applied."));
            return 0;
        }

        mirrorBE.getUpgradeData().putString("TimeLockMode", mode);
        mirrorBE.setChanged();

        final String modeFinal = mode;
        source.sendSuccess(() -> Component.literal(
                "Mirror at " + pos.toShortString() + " will now only work during the " + modeFinal + "."), false);
        return 1;
    }

    private static int sendDm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer sender = source.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Component messageComponent = MessageArgument.getMessage(ctx, "message");
        String messageText = messageComponent.getString();

        MirrorNetworkData networkData = MirrorNetworkData.get(source.getServer());
        PermissionData permData = PermissionData.get(source.getServer());
        int range = MirrorsConfig.MIRROR_CHAT_RANGE.get();

        // Sender must be near an accessible mirror to send a DM
        boolean nearMirror = false;
        for (MirrorNetworkData.MirrorEntry entry : networkData.getAllMirrors()) {
            if (!entry.dimension.equals(sender.level().dimension())) continue;
            double dist = sender.distanceToSqr(
                    entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5);
            if (dist <= (double) range * range) {
                if (permData.canPlayerUseMirror(sender.getUUID(), entry.mirrorId, entry.ownerUUID,
                        PermissionData.PermissionLevel.USE)) {
                    nearMirror = true;
                    break;
                }
            }
        }

        if (!nearMirror) {
            source.sendFailure(Component.literal("You must be near a mirror to send a DM."));
            return 0;
        }

        String senderName = sender.getGameProfile().getName();
        String dmMessage = "[Mirror DM] " + senderName + ": " + messageText;

        MirrorsNetwork.sendToPlayer(target, new ClientboundMirrorMessagePacket(dmMessage));
        source.sendSuccess(() -> Component.literal("DM sent to " + target.getGameProfile().getName() + "."), false);
        return 1;
    }
}
