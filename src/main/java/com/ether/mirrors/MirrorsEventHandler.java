package com.ether.mirrors;

import com.ether.mirrors.block.MirrorBlock;
import com.ether.mirrors.block.MirrorMultiblockPart;
import com.ether.mirrors.block.PocketMirrorBlock;
import com.ether.mirrors.block.entity.MirrorBlockEntity;
import com.ether.mirrors.command.MirrorsCommand;
import com.ether.mirrors.data.MirrorNetworkData;
import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.data.PocketDimensionData;
import com.ether.mirrors.init.MirrorsItems;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.update.UpdateManager;
import com.ether.mirrors.util.MultiblockHelper;
import com.ether.mirrors.voicechat.MirrorCallManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EthersMirrors.MOD_ID)
public class MirrorsEventHandler {

    private static int tickCounter = 0;
    private static int pocketTickCounter = 0;

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        MirrorsCommand.register(event.getDispatcher());
    }

    /** Clear stale call state on server start to prevent ghost calls from a previous session/crash. */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        MirrorCallManager.getInstance().clearAll();
    }

    /**
     * Safety net: cancel block break events for non-owners of mirror blocks.
     * This catches cases that playerWillDestroy might miss (e.g. other mods breaking blocks).
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof MirrorBlock)) return;

        Player player = event.getPlayer();
        if (!(event.getLevel() instanceof Level level)) return;

        // Find master position to check ownership
        Direction facing = state.getValue(MirrorBlock.FACING);
        MirrorMultiblockPart part = state.getValue(MirrorBlock.PART);
        BlockPos masterPos = MultiblockHelper.findMasterPos(event.getPos(), facing, part);

        if (level.getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE) {
            if (!mirrorBE.isOwner(player)) {
                // Creative mode operators can break any mirror
                if (player.isCreative() && player.hasPermissions(2)) {
                    return;
                }
                // Check if they have explicit BREAK permission
                if (level.getServer() != null) {
                    PermissionData perms = PermissionData.get(level.getServer());
                    if (!perms.canPlayerUseMirror(player.getUUID(), mirrorBE.getMirrorId(), mirrorBE.getOwnerUUID(), PermissionData.PermissionLevel.BREAK)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("You don't have permission to break this mirror."), true);
                    }
                }
            }
        }
    }

    /**
     * On player login:
     * 1. If the player is still inside a pocket dimension (e.g. disconnected while inside),
     *    teleport them back to their saved return point or overworld spawn.
     * 2. Notify about pending mirror permission requests.
     * 3. Deliver any pending alarm messages.
     * 4. Sync mirror waypoints to Xaero's Minimap (if present).
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // ServerCore may fire PlayerLoggedInEvent on an async thread — defer all SavedData
        // access to the main server thread to avoid ConcurrentModificationException.
        serverPlayer.server.execute(() -> onPlayerLoginMain(serverPlayer));
    }

    private static void onPlayerLoginMain(ServerPlayer serverPlayer) {
        // Safety: if player logged in while inside a pocket dimension, teleport them back out
        if (serverPlayer.level().dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) {
            PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
            PocketDimensionData.ReturnPoint returnPoint = pocketData.getPlayerReturn(serverPlayer.getUUID());
            if (returnPoint != null) {
                ServerLevel returnLevel = serverPlayer.server.getLevel(returnPoint.dimension);
                if (returnLevel != null) {
                    serverPlayer.teleportTo(returnLevel,
                            returnPoint.x, returnPoint.y, returnPoint.z,
                            returnPoint.yRot, returnPoint.xRot);
                    pocketData.clearPlayerReturn(serverPlayer.getUUID());
                    pocketData.clearPlayerInPocket(serverPlayer.getUUID());
                } else {
                    // Return dimension unavailable — fall back to overworld spawn
                    ServerLevel overworld = serverPlayer.server.overworld();
                    var spawn = overworld.getSharedSpawnPos();
                    serverPlayer.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            serverPlayer.getYRot(), serverPlayer.getXRot());
                }
            } else {
                // No return point recorded — fall back to overworld spawn
                ServerLevel overworld = serverPlayer.server.overworld();
                var spawn = overworld.getSharedSpawnPos();
                serverPlayer.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        serverPlayer.getYRot(), serverPlayer.getXRot());
            }
        }

        PermissionData perms = PermissionData.get(serverPlayer.server);
        int pendingCount = perms.getPendingRequests(serverPlayer.getUUID()).size();
        if (pendingCount > 0) {
            serverPlayer.displayClientMessage(
                    Component.literal("You have " + pendingCount + " pending mirror permission request(s). Right-click any of your mirrors to manage."),
                    false
            );
        }
        // Deliver pending alarm messages
        MirrorNetworkData networkData = MirrorNetworkData.get(serverPlayer.server);
        List<String> alarms = networkData.getPendingAlarms(serverPlayer.getUUID());
        if (!alarms.isEmpty()) {
            for (String msg : alarms) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg)
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
            networkData.clearPendingAlarms(serverPlayer.getUUID());
        }
        // Sync mirror waypoints to Xaero's Minimap (if present)
        MirrorsNetwork.sendWaypointSync(serverPlayer);

        // Notify about a downloaded update — shown to all players so everyone knows to restart
        if (UpdateManager.getStatus() == UpdateManager.Status.READY_RESTART) {
            sendUpdateNotification(serverPlayer);
        }
    }

    private static void sendUpdateNotification(ServerPlayer player) {
        String ver = UpdateManager.getLatestVersion();
        String url  = UpdateManager.getReleasesPage();

        // Line 1: header
        MutableComponent header = Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .withStyle(ChatFormatting.DARK_AQUA);

        // Line 2: "Ether's Mirrors  vX.X.X  is ready!"
        MutableComponent msg = Component.literal(" ✦ Ether's Mirrors ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("v" + ver).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" has been downloaded!").withStyle(ChatFormatting.AQUA));

        // Line 3: clickable restart hint
        MutableComponent hint = Component.literal(" ► Restart to apply  ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("[View Release Notes]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GOLD)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Open GitHub release page")))));

        player.sendSystemMessage(header);
        player.sendSystemMessage(msg);
        player.sendSystemMessage(hint);
        player.sendSystemMessage(header);
    }

    /**
     * Tick call timeouts every second (20 ticks).
     * Ensures mirror calls don't stay open indefinitely.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter >= 20) { // Every second
            tickCounter = 0;
            long timeoutMs = MirrorsConfig.CALL_TIMEOUT_SECONDS.get() * 1000L;
            MirrorCallManager.getInstance().tickTimeouts(timeoutMs, event.getServer());
        }
    }

    /**
     * Apply fixed time and weather to the pocket dimension every 20 ticks.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) return;

        pocketTickCounter++;
        if (pocketTickCounter < 20) return;
        pocketTickCounter = 0;

        // Apply per-region time/weather overrides to individual players (not the shared level)
        PocketDimensionData pocketData = PocketDimensionData.get(serverLevel.getServer());
        for (net.minecraft.server.level.ServerPlayer sp : serverLevel.players()) {
            java.util.UUID pocketOwner = pocketData.getPocketOwnerForPlayer(sp.getUUID());
            if (pocketOwner == null) continue;
            PocketDimensionData.PocketRegion region = pocketData.getRegion(pocketOwner);
            if (region == null) continue;
            if (region.fixedTime >= 0) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                        serverLevel.getGameTime(), -region.fixedTime, false));
            }
            if (!"normal".equals(region.weather)) {
                boolean thunder = "thunder".equals(region.weather);
                boolean rain = "rain".equals(region.weather) || thunder;
                float rainLevel = rain ? 1.0F : 0.0F;
                float thunderLevel = thunder ? 1.0F : 0.0F;
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        rain ? net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING
                             : net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0));
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rainLevel));
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunderLevel));
            }
        }
    }

    /**
     * If a player dies inside a pocket dimension, notify them where their items/corpse are.
     * This fires at HIGH priority so the location is captured before Corpse mod or Waystones
     * intercepts the event and potentially moves the player.
     *
     * We cannot move the corpse (it hasn't been spawned yet), but we record a pending alarm
     * so the player knows to re-enter the pocket dimension to retrieve their items.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerDeathInPocket(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (!serverPlayer.level().dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) return;

        PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
        java.util.UUID pocketOwner = pocketData.getPocketOwnerForPlayer(serverPlayer.getUUID());
        if (pocketOwner == null) return;

        MirrorNetworkData networkData = MirrorNetworkData.get(serverPlayer.server);
        boolean isOwnPocket = pocketOwner.equals(serverPlayer.getUUID());

        String ownerName = pocketOwner.equals(serverPlayer.getUUID()) ? "your"
                : resolvePlayerName(serverPlayer.server, pocketOwner) + "'s";

        String msg = "[Ether's Mirrors] You died inside " + ownerName + " pocket dimension. "
                + (isOwnPocket
                ? "Re-enter your pocket mirror to retrieve your items."
                : "Ask the pocket owner to let you back in, or your items may be lost.");
        networkData.addPendingAlarm(serverPlayer.getUUID(), msg);
    }

    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, java.util.UUID uuid) {
        var online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        var cached = server.getProfileCache().get(uuid).orElse(null);
        if (cached != null) return cached.getName();
        return uuid.toString().substring(0, 8);
    }

    /**
     * End any active mirror calls when a player changes dimension.
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            MirrorCallManager.getInstance().endAllCallsForPlayer(serverPlayer.getUUID(), serverPlayer.server);
        }
    }

    /**
     * End any active mirror calls when a player logs out.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        // End any active calls — notify the other participant
        net.minecraft.server.MinecraftServer server = player instanceof ServerPlayer sp ? sp.server : null;
        MirrorCallManager.getInstance().endAllCallsForPlayer(player.getUUID(), server);

        // Clear pocket tracking but preserve return point — the login handler will use it
        // to teleport the player back if they logged out while inside a pocket
        if (player instanceof ServerPlayer serverPlayer) {
            PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
            pocketData.clearPlayerInPocket(serverPlayer.getUUID());
            // Do NOT clear return data here — it's needed for safe re-entry on login
        }
    }

    /**
     * If a player dies in the pocket dimension, respawn them in the overworld instead.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            // If they respawned in the pocket dimension, move them to overworld spawn
            if (serverPlayer.level().dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) {
                ServerLevel overworld = serverPlayer.server.overworld();
                BlockPos spawn = overworld.getSharedSpawnPos();
                serverPlayer.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        serverPlayer.getYRot(), serverPlayer.getXRot());
            }
        }
    }

    /**
     * Mirror Shard binding: right-clicking a mirror while holding a shard in the main hand
     * binds the shard to that mirror. Cancels the normal mirror interaction.
     */
    @SubscribeEvent
    public static void onShardRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack heldMain = player.getMainHandItem();

        if (!heldMain.is(MirrorsItems.MIRROR_SHARD.get())) return;

        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof MirrorBlock)) return;

        // Cancel normal mirror interaction on both sides
        event.setCanceled(true);

        if (level.isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Direction facing = state.getValue(MirrorBlock.FACING);
        MirrorMultiblockPart part = state.getValue(MirrorBlock.PART);
        BlockPos masterPos = MultiblockHelper.findMasterPos(event.getPos(), facing, part);

        if (!(level.getBlockEntity(masterPos) instanceof MirrorBlockEntity mirrorBE)) return;

        MirrorNetworkData networkData = MirrorNetworkData.get(serverPlayer.server);
        MirrorNetworkData.MirrorEntry entry = networkData.getMirrorById(mirrorBE.getMirrorId());
        if (entry == null) return;

        // Player must have at least USE permission to bind a shard to this mirror
        PermissionData permData = PermissionData.get(serverPlayer.server);
        boolean privacyLock = mirrorBE.hasUpgrade(com.ether.mirrors.item.MirrorUpgradeType.PRIVACY_LOCK);
        if (!permData.canPlayerUseMirror(serverPlayer.getUUID(), entry.mirrorId, entry.ownerUUID,
                PermissionData.PermissionLevel.USE, privacyLock)) {
            serverPlayer.displayClientMessage(
                    Component.literal("You don't have permission to bind to this mirror."), true);
            return;
        }

        String mirrorName = entry.name != null && !entry.name.isEmpty() ? entry.name : "Unnamed";
        CompoundTag nbt = heldMain.getOrCreateTag();
        nbt.putUUID("BoundMirrorId", entry.mirrorId);
        nbt.putString("BoundDimension", entry.dimension.location().toString());
        nbt.putString("BoundMirrorName", mirrorName);

        serverPlayer.displayClientMessage(
                Component.literal("Mirror Shard bound to: " + mirrorName), true);
    }

    /**
     * Mirror-gated chat: if MIRROR_CHAT_ENABLED, cancel chat if player is not near any accessible mirror.
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        if (!MirrorsConfig.MIRROR_CHAT_ENABLED.get()) return;
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        MirrorNetworkData networkData = MirrorNetworkData.get(serverPlayer.server);
        PermissionData permData = PermissionData.get(serverPlayer.server);
        int range = MirrorsConfig.MIRROR_CHAT_RANGE.get();

        boolean nearMirror = false;
        for (MirrorNetworkData.MirrorEntry entry : networkData.getAllMirrors()) {
            if (!entry.dimension.equals(serverPlayer.level().dimension())) continue;
            double dist = serverPlayer.distanceToSqr(
                    entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5);
            if (dist <= range * range) {
                // Check if player has access to this mirror
                if (permData.canPlayerUseMirror(serverPlayer.getUUID(), entry.mirrorId, entry.ownerUUID,
                        PermissionData.PermissionLevel.USE)) {
                    nearMirror = true;
                    break;
                }
            }
        }
        if (!nearMirror) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(
                    Component.literal("You must be near a mirror to chat."), true);
        }
    }

    /**
     * Protect vault chests in pocket dimensions: only the pocket owner may open them.
     */
    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();

        if (!level.dimension().equals(PocketMirrorBlock.POCKET_DIMENSION)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        PocketDimensionData pocketData = PocketDimensionData.get(serverPlayer.server);
        java.util.UUID pocketOwner = pocketData.getPocketOwnerForPlayer(serverPlayer.getUUID());
        if (pocketOwner == null) return;

        // Only restrict the vault chest (trapped chest at vaultPos)
        PocketDimensionData.PocketRegion region = pocketData.getRegion(pocketOwner);
        if (region == null || region.vaultPos == null) return;

        if (event.getPos().equals(region.vaultPos)) {
            // Allow the owner; deny everyone else
            if (!serverPlayer.getUUID().equals(pocketOwner)) {
                event.setCanceled(true);
                serverPlayer.displayClientMessage(
                        Component.literal("This vault belongs to the pocket owner."), true);
            }
        }
    }
}
