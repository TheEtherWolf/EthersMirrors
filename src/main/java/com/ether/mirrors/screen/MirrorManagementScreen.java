package com.ether.mirrors.screen;

import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientboundOpenMirrorManagementPacket;
import com.ether.mirrors.network.packets.ServerboundApplyUpgradePacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorManagementPacket;
import com.ether.mirrors.network.packets.ServerboundRemoveUpgradePacket;
import com.ether.mirrors.network.packets.ServerboundSetMirrorAccessModePacket;
import com.ether.mirrors.network.packets.ServerboundSetMirrorOverridePacket;
import com.ether.mirrors.network.packets.ServerboundSetMirrorFolderPacket;
import com.ether.mirrors.network.packets.ServerboundSetPocketThemePacket;
import com.ether.mirrors.network.packets.ServerboundUpgradeMirrorTierPacket;
import com.ether.mirrors.util.MirrorTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MirrorManagementScreen extends Screen {

    // ── Data ────────────────────────────────────────────────────────────────
    private final UUID mirrorId;
    private final String mirrorName;
    private final String tierName;
    private final String typeName;
    private final BlockPos mirrorPos;
    private final String dimensionName;
    private PermissionData.AccessMode accessMode;
    private final List<ClientboundOpenMirrorManagementPacket.PlayerEntry> allowList;
    private final List<ClientboundOpenMirrorManagementPacket.PlayerEntry> blockList;
    private List<String> appliedUpgrades;
    private String pendingRemovalUpgrade = null; // id of upgrade awaiting confirm, or null
    private boolean warpTargetLocked = false; // client-side mirror of WarpTargetLocked NBT flag
    private boolean pendingPickUp = false; // two-step confirmation for pick up

    // ── Layout ──────────────────────────────────────────────────────────────
    private static final int PANEL_W  = 400;
    private static final int HEADER_H = 26;
    private static final int TAB_H    = 18;
    private static final int CONTENT_H = 230;
    private static final int FOOTER_H = 28;
    private static final int PANEL_H  = HEADER_H + TAB_H + 4 + CONTENT_H + FOOTER_H;

    private int activeTab = 0; // 0=Upgrades, 1=Access, 2=Info

    // Access tab widgets
    private EditBox allowField;
    private EditBox blockField;
    private int allowScrollOffset = 0;
    private int blockScrollOffset = 0;
    private static final int MAX_LIST_VISIBLE = 3;

    // Info tab widgets
    private EditBox folderField;

    public MirrorManagementScreen(ClientboundOpenMirrorManagementPacket data) {
        super(Component.literal("Mirror Management"));
        this.mirrorId = data.mirrorId;
        this.mirrorName = data.mirrorName;
        this.tierName = data.tierName;
        this.typeName = data.typeName;
        this.mirrorPos = data.pos;
        this.dimensionName = data.dimensionName;
        this.accessMode = data.accessMode;
        this.allowList = new ArrayList<>(data.allowList);
        this.blockList = new ArrayList<>(data.blockList);
        this.appliedUpgrades = new ArrayList<>(data.appliedUpgrades);
        this.warpTargetLocked = data.warpTargetLocked;
    }

    private int panelLeft()    { return (this.width  - PANEL_W) / 2; }
    private int panelTop()     { return Math.max(10, (this.height - PANEL_H) / 2); }
    private int contentTop()   { return panelTop() + HEADER_H + TAB_H + 4; }
    private int tabRowY()      { return panelTop() + HEADER_H + 2; }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int pl = panelLeft(), pt = panelTop();
        int ct = contentTop();
        int pr = pl + PANEL_W;

        // ── Tab buttons ────────────────────────────────────────────────────
        int tabW = 88;
        String[] tabNames = {"Upgrades", "Access", "Info"};
        for (int i = 0; i < 3; i++) {
            final int tabIdx = i;
            int tx = pl + 8 + i * (tabW + 4);
            int ty = tabRowY();
            MirrorButton btn = (activeTab == tabIdx)
                    ? MirrorButton.purple(tx, ty, tabW, TAB_H, Component.literal(tabNames[i]), b -> {})
                    : MirrorButton.of(tx, ty, tabW, TAB_H, Component.literal(tabNames[i]),
                              b -> { activeTab = tabIdx; allowScrollOffset = 0; blockScrollOffset = 0; pendingRemovalUpgrade = null; rebuildWidgets(); },
                              UITheme.BORDER_MID, UITheme.TEXT_MUTED);
            if (activeTab == tabIdx) btn.active = false;
            addRenderableWidget(btn);
        }

        // ── Tab 0: Upgrades ────────────────────────────────────────────────
        if (activeTab == 0) {
            int upgradeSlots = getUpgradeSlotsForTier(tierName);
            for (int i = 0; i < upgradeSlots; i++) {
                int rowY = ct + 10 + i * 30;
                if (i < appliedUpgrades.size()) {
                    // Slot filled — show Remove / Confirm? button (two-step confirmation)
                    final String upgradeId = appliedUpgrades.get(i);
                    if (upgradeId.equals(pendingRemovalUpgrade)) {
                        // Second click: confirm removal
                        addRenderableWidget(MirrorButton.red(pl + PANEL_W - 66, rowY + 7, 58, 14,
                                Component.literal("Confirm?"), b -> {
                                    MirrorsNetwork.sendToServer(new ServerboundRemoveUpgradePacket(mirrorPos, upgradeId));
                                    appliedUpgrades.remove(upgradeId);
                                    pendingRemovalUpgrade = null;
                                    rebuildWidgets();
                                }));
                    } else {
                        // First click: enter pending state
                        addRenderableWidget(MirrorButton.red(pl + PANEL_W - 66, rowY + 7, 58, 14,
                                Component.literal("Remove"), b -> {
                                    pendingRemovalUpgrade = upgradeId;
                                    rebuildWidgets();
                                }));
                    }
                    if ("time_lock".equals(upgradeId)) {
                        // TIME_LOCK mode toggle buttons
                        MirrorButton dayBtn = MirrorButton.purple(pl + PANEL_W - 134, rowY + 7, 32, 14,
                                Component.literal("\u2600 Day"), b -> {
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundTimeLockTogglePacket(mirrorPos, "day"));
                                });
                        dayBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("Only allow teleport during daytime")));
                        addRenderableWidget(dayBtn);
                        MirrorButton nightBtn = MirrorButton.teal(pl + PANEL_W - 100, rowY + 7, 32, 14,
                                Component.literal("\u263D Night"), b -> {
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundTimeLockTogglePacket(mirrorPos, "night"));
                                });
                        nightBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("Only allow teleport during nighttime")));
                        addRenderableWidget(nightBtn);
                    }
                    if ("warp_target".equals(upgradeId)) {
                        // WARP_TARGET lock/unlock toggle
                        if (warpTargetLocked) {
                            addRenderableWidget(MirrorButton.red(pl + PANEL_W - 130, rowY + 7, 62, 14,
                                    Component.literal("Unlock"), b -> {
                                        MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundWarpLockTogglePacket(mirrorPos, false));
                                        warpTargetLocked = false;
                                        rebuildWidgets();
                                    })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                            Component.literal("Allow the warp destination to be changed")));
                        } else {
                            addRenderableWidget(MirrorButton.teal(pl + PANEL_W - 130, rowY + 7, 62, 14,
                                    Component.literal("Lock"), b -> {
                                        MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundWarpLockTogglePacket(mirrorPos, true));
                                        warpTargetLocked = true;
                                        rebuildWidgets();
                                    })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                            Component.literal("Prevent the warp destination from being changed")));
                        }
                    }
                } else {
                    // Slot empty — scan full inventory for applicable upgrade items
                    java.util.List<com.ether.mirrors.item.MirrorUpgradeType> available = new java.util.ArrayList<>();
                    var _player = net.minecraft.client.Minecraft.getInstance().player;
                    if (_player != null) {
                        var inv = _player.getInventory();
                        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                            net.minecraft.world.item.ItemStack s = inv.getItem(slot);
                            if (s.getItem() instanceof com.ether.mirrors.item.MirrorUpgradeItem ui) {
                                com.ether.mirrors.item.MirrorUpgradeType ut = ui.getUpgradeType();
                                if (!appliedUpgrades.contains(ut.getId()) && !available.contains(ut)) {
                                    available.add(ut);
                                }
                            }
                        }
                    }
                    // Show a compact install button for each available upgrade type (max 4 per row)
                    int btnW = 80, btnGap = 2;
                    int bxStart = pl + 58;
                    for (int ai = 0; ai < Math.min(available.size(), 4); ai++) {
                        final com.ether.mirrors.item.MirrorUpgradeType ut = available.get(ai);
                        int bx2 = bxStart + ai * (btnW + btnGap);
                        MirrorButton installBtn = MirrorButton.green(bx2, rowY + 7, btnW, 14,
                                Component.literal("\u2295 " + ut.getDisplayName()), b -> {
                                    MirrorsNetwork.sendToServer(new ServerboundApplyUpgradePacket(mirrorPos, ut.getId()));
                                    appliedUpgrades.add(ut.getId());
                                    rebuildWidgets();
                                });
                        installBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("Install: " + ut.getDescription())));
                        addRenderableWidget(installBtn);
                    }
                }
            }
        }

        // ── Tab 1: Access ──────────────────────────────────────────────────
        if (activeTab == 1) {
            // Mode buttons row
            String[] modes = {"Inherit", "Public", "Private"};
            PermissionData.AccessMode[] modeVals = {
                PermissionData.AccessMode.INHERIT,
                PermissionData.AccessMode.PUBLIC,
                PermissionData.AccessMode.PRIVATE
            };
            int btnW = 86;
            for (int i = 0; i < 3; i++) {
                final PermissionData.AccessMode m = modeVals[i];
                int bx = pl + 8 + i * (btnW + 4);
                int by = ct + 14; // 14px below content top — leaves room for "Access Mode:" label
                if (accessMode == m) {
                    addRenderableWidget(MirrorButton.purple(bx, by, btnW, 16, Component.literal(modes[i]), btn -> {})).active = false;
                } else {
                    addRenderableWidget(MirrorButton.of(bx, by, btnW, 16, Component.literal(modes[i]),
                            btn -> {
                                accessMode = m;
                                MirrorsNetwork.sendToServer(new ServerboundSetMirrorAccessModePacket(mirrorId, m));
                                rebuildWidgets();
                            }, UITheme.BORDER_MID, UITheme.TEXT_MUTED));
                }
            }

            // Allow list scroll buttons (pushed down to make room for mode buttons)
            int listY = ct + 40;
            if (allowScrollOffset > 0) {
                addRenderableWidget(MirrorButton.purple(pl + PANEL_W - 30, listY - 13, 22, 11,
                        Component.literal("^"), b -> { allowScrollOffset--; rebuildWidgets(); }));
            }
            if (allowScrollOffset + MAX_LIST_VISIBLE < allowList.size()) {
                addRenderableWidget(MirrorButton.purple(pl + PANEL_W - 30, listY + MAX_LIST_VISIBLE * 16 + 2, 22, 11,
                        Component.literal("v"), b -> { allowScrollOffset++; rebuildWidgets(); }));
            }
            // Remove allow buttons
            int visAllow = Math.min(MAX_LIST_VISIBLE, allowList.size() - allowScrollOffset);
            for (int i = 0; i < visAllow; i++) {
                final ClientboundOpenMirrorManagementPacket.PlayerEntry allowEntry = allowList.get(i + allowScrollOffset);
                int ry = listY + i * 16;
                addRenderableWidget(MirrorButton.red(pl + PANEL_W - 56, ry + 2, 48, 12,
                        Component.literal("Remove"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundSetMirrorOverridePacket(mirrorId, allowEntry.name(), "remove_allow"));
                            allowList.remove(allowEntry);
                            allowScrollOffset = Math.max(0, Math.min(allowScrollOffset, Math.max(0, allowList.size() - MAX_LIST_VISIBLE)));
                            rebuildWidgets();
                        }));
            }

            // Allow input field
            int fieldY = ct + 42 + MAX_LIST_VISIBLE * 16 + 8;
            allowField = new EditBox(this.font, pl + 8, fieldY, 200, 14, Component.literal("Allow player"));
            allowField.setMaxLength(16); // Minecraft usernames are max 16 chars
            allowField.setHint(Component.literal("Player name..."));
            allowField.setBordered(false);
            addRenderableWidget(allowField);

            addRenderableWidget(MirrorButton.teal(pl + 214, fieldY - 1, 60, 16,
                    Component.literal("Allow"), b -> {
                        String name = allowField.getValue().trim();
                        if (!name.isEmpty()) {
                            MirrorsNetwork.sendToServer(new ServerboundSetMirrorOverridePacket(mirrorId, name, "allow"));
                            allowField.setValue("");
                            // Optimistic add for display (server resolves UUID)
                            allowList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(UUID.randomUUID(), name));
                            rebuildWidgets();
                        }
                    }));

            // Block input field
            int blockFieldY = fieldY + 22;
            blockField = new EditBox(this.font, pl + 8, blockFieldY, 200, 14, Component.literal("Block player"));
            blockField.setMaxLength(16); // Minecraft usernames are max 16 chars
            blockField.setHint(Component.literal("Player name..."));
            blockField.setBordered(false);
            addRenderableWidget(blockField);

            addRenderableWidget(MirrorButton.red(pl + 214, blockFieldY - 1, 60, 16,
                    Component.literal("Block"), b -> {
                        String name = blockField.getValue().trim();
                        if (!name.isEmpty()) {
                            MirrorsNetwork.sendToServer(new ServerboundSetMirrorOverridePacket(mirrorId, name, "block"));
                            blockField.setValue("");
                            blockList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(UUID.randomUUID(), name));
                            rebuildWidgets();
                        }
                    }));

            // Online players — one-click allow/block
            List<String> onlineNames = getOnlinePlayerNames();
            int onlineRowY = blockFieldY + 24;
            for (int i = 0; i < Math.min(onlineNames.size(), 5); i++) {
                final String name = onlineNames.get(i);
                int ry = onlineRowY + i * 16;
                addRenderableWidget(MirrorButton.teal(pl + PANEL_W - 118, ry, 50, 13,
                        Component.literal("Allow"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundSetMirrorOverridePacket(mirrorId, name, "allow"));
                            allowList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(UUID.randomUUID(), name));
                            rebuildWidgets();
                        }));
                addRenderableWidget(MirrorButton.red(pl + PANEL_W - 64, ry, 50, 13,
                        Component.literal("Block"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundSetMirrorOverridePacket(mirrorId, name, "block"));
                            blockList.add(new ClientboundOpenMirrorManagementPacket.PlayerEntry(UUID.randomUUID(), name));
                            rebuildWidgets();
                        }));
            }
        }

        // ── Tab 2: Info ────────────────────────────────────────────────────
        if (activeTab == 2) {
            int bx = pl + 8;
            int ct2 = contentTop();

            // Folder field — right below the "Folder:" label rendered at ct+82
            int folderFieldY = ct2 + 92;
            folderField = new EditBox(this.font, pl + 8, folderFieldY, 180, 14,
                    Component.literal("Folder"));
            folderField.setMaxLength(32);
            folderField.setHint(Component.literal("Folder name..."));
            folderField.setBordered(false);
            addRenderableWidget(folderField);
            addRenderableWidget(MirrorButton.teal(pl + 192, folderFieldY - 1, 58, 16,
                    Component.literal("Set Folder"), b -> {
                        String folder = folderField.getValue().trim();
                        MirrorsNetwork.sendToServer(new ServerboundSetMirrorFolderPacket(mirrorId, folder));
                        folderField.setValue("");
                    }));

            // Rename + Upgrade Tier — below folder row
            int actionY = ct2 + 114;
            addRenderableWidget(MirrorButton.gold(bx, actionY, 90, 16,
                    Component.literal("Rename"), b -> {
                        this.minecraft.setScreen(new MirrorNamingScreen(mirrorPos, mirrorName, true).withContext(tierName, typeName));
                    })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("Set a custom name for this mirror")));
            boolean isMaxTier = "netherite".equals(tierName);
            MirrorButton upgBtn = MirrorButton.purple(bx + 96, actionY, 110, 16,
                    Component.literal("Upgrade Tier"), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundUpgradeMirrorTierPacket(mirrorPos));
                    });
            upgBtn.active = !isMaxTier;
            upgBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    isMaxTier ? Component.literal("Already at maximum tier")
                              : Component.literal("Upgrade to the next tier (requires materials)")));
            addRenderableWidget(upgBtn);

            // Pick Up button — two-step confirmation to prevent accidental pickup
            if (pendingPickUp) {
                addRenderableWidget(MirrorButton.red(bx + 212, actionY, 90, 16,
                        Component.literal("Confirm?"), b -> {
                            MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundPickUpMirrorPacket(mirrorPos));
                            onClose();
                        })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("Confirm: remove the mirror and add it to your inventory")));
            } else {
                addRenderableWidget(MirrorButton.red(bx + 212, actionY, 90, 16,
                        Component.literal("Pick Up"), b -> {
                            pendingPickUp = true;
                            rebuildWidgets();
                        })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.literal("Remove the mirror block and add it to your inventory")));
            }

            // Theme / Time / Weather — only for pocket mirrors
            if ("pocket".equals(typeName)) {
                int themeW = 70, themeGap = 4;
                int themeRowX = pl + 8;

                int themeRowY = ct2 + 136;
                String[] themeIds = {"deepslate", "nether", "end", "mushroom", "crystal"};
                String[] themeLabels = {"Deepslate", "Nether", "End", "Mushroom", "Crystal"};
                for (int ti = 0; ti < themeIds.length; ti++) {
                    final String themeId = themeIds[ti];
                    addRenderableWidget(MirrorButton.teal(themeRowX + ti * (themeW + themeGap), themeRowY, themeW, 14,
                            Component.literal(themeLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new ServerboundSetPocketThemePacket(themeId))));
                }

                int timeRowY = ct2 + 154;
                int[] times = {1000, 13000, 18000, -1};
                String[] timeLabels = {"Day", "Night", "Midnight", "Unlock"};
                for (int ti = 0; ti < times.length; ti++) {
                    final long t = times[ti];
                    addRenderableWidget(MirrorButton.purple(themeRowX + ti * (themeW + themeGap), timeRowY, themeW, 14,
                            Component.literal(timeLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundSetPocketTimePacket(t))));
                }

                int weatherRowY = ct2 + 172;
                String[] weatherIds = {"clear", "rain", "thunder", "normal"};
                String[] weatherLabels = {"Clear", "Rain", "Thunder", "Normal"};
                for (int ti = 0; ti < weatherIds.length; ti++) {
                    final String w = weatherIds[ti];
                    addRenderableWidget(MirrorButton.teal(themeRowX + ti * (themeW + themeGap), weatherRowY, themeW, 14,
                            Component.literal(weatherLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundSetPocketWeatherPacket(w))));
                }

                // Expand Pocket buttons (permanent upgrade with material cost)
                int expandRowY = ct2 + 192;
                int[] expandAmounts = {16, 32, 64, 128};
                String[] expandLabels = {"+16 (8 Planks)", "+32 (8 Iron)", "+64 (4 Diamond)", "+128 (2 Netherite)"};
                for (int ti = 0; ti < expandAmounts.length; ti++) {
                    final int amount = expandAmounts[ti];
                    addRenderableWidget(MirrorButton.gold(themeRowX + ti * (themeW + themeGap), expandRowY, themeW, 14,
                            Component.literal(expandLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundExpandPocketPacket(amount))));
                }
            }
        }

        // ── Close button (always visible) ────────────────────────────────
        addRenderableWidget(MirrorButton.gold(
                panelLeft() + PANEL_W / 2 - 36, panelTop() + PANEL_H - FOOTER_H + 6, 72, 16,
                Component.literal("Close"), b -> onClose()));
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + PANEL_H;
        int ct = contentTop();

        UITheme.drawPanel(g, pl, pt, pr, pb);
        UITheme.drawHeader(g, pl, pt, pr, HEADER_H);

        // Pulse glow
        float pulse = UITheme.pulse();
        int glowAlpha = (int)(pulse * 0x60 + 0x20);
        g.fill(pl - 1, pt - 1, pr + 1, pt, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha));
        g.fill(pl - 1, pb,     pr + 1, pb + 1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha));

        g.drawCenteredString(font, "Mirror Management", pl + PANEL_W / 2, pt + 4, UITheme.TEXT_MUTED);
        String displayName = mirrorName != null && !mirrorName.isEmpty() ? mirrorName
                : cap(tierName) + " " + cap(typeName) + " Mirror";
        g.drawCenteredString(font, "* " + displayName + " *", pl + PANEL_W / 2, pt + HEADER_H - 12, UITheme.TEXT_GOLD);

        // Content separator
        UITheme.drawRule(g, pl + 8, pr - 8, ct - 2);

        // ── Tab 0: Upgrades ───────────────────────────────────────────────
        if (activeTab == 0) {
            int sy = ct + 10;
            int upgradeSlots = getUpgradeSlotsForTier(tierName);
            for (int i = 0; i < upgradeSlots; i++) {
                int rowY = sy + i * 30;
                UITheme.drawRow(g, pl + 8, rowY, pr - 8, rowY + 28, i % 2 == 1, false);
                g.drawString(font, "Slot " + (i + 1) + ":", pl + 14, rowY + 10, UITheme.TEXT_LAVENDER, false);
                if (i < appliedUpgrades.size()) {
                    com.ether.mirrors.item.MirrorUpgradeType type =
                            com.ether.mirrors.item.MirrorUpgradeType.fromId(appliedUpgrades.get(i));
                    String name = type != null ? type.getDisplayName() : appliedUpgrades.get(i);
                    g.drawString(font, name, pl + 60, rowY + 10, UITheme.TEXT_OWN, false);
                } else {
                    // Count how many install buttons are visible for this slot
                    var _pl2 = net.minecraft.client.Minecraft.getInstance().player;
                    boolean hasItems = _pl2 != null && _pl2.getInventory().items.stream()
                            .anyMatch(s -> s.getItem() instanceof com.ether.mirrors.item.MirrorUpgradeItem ui2
                                    && !appliedUpgrades.contains(ui2.getUpgradeType().getId()));
                    String emptyLabel = hasItems ? "— pick an upgrade \u2192" : "— Empty (carry upgrade items to install) —";
                    g.drawString(font, emptyLabel, pl + 60, rowY + 10, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xAA), false);
                }
            }
            if (appliedUpgrades.size() < upgradeSlots) {
                g.drawCenteredString(font, "\u2295 = install  \u00D7 = remove",
                        pl + PANEL_W / 2, sy + upgradeSlots * 30 + 8, UITheme.withAlpha(UITheme.TEXT_MUTED, 0x88));
            }
        }

        // ── Tab 1: Access ─────────────────────────────────────────────────
        if (activeTab == 1) {
            // Section label (above mode buttons)
            g.drawString(font, "Access Mode:", pl + 8, ct + 4, UITheme.TEXT_LAVENDER, false);

            // Allow list
            int listY = ct + 40;
            g.drawString(font, "Allow List:", pl + 8, listY - 10, UITheme.TEXT_MUTED, false);
            if (allowList.isEmpty()) {
                g.drawString(font, "(none)", pl + 14, listY + 3, UITheme.TEXT_MUTED, false);
            } else {
                int visAllow = Math.min(MAX_LIST_VISIBLE, allowList.size() - allowScrollOffset);
                for (int i = 0; i < visAllow; i++) {
                    int idx = i + allowScrollOffset;
                    int ry = listY + i * 16;
                    UITheme.drawRow(g, pl + 8, ry, pr - 62, ry + 14, i % 2 == 1, false);
                    g.drawString(font, allowList.get(idx).name(), pl + 12, ry + 3, UITheme.TEXT_OWN, false);
                }
            }

            // Field backgrounds
            int fieldY = ct + 42 + MAX_LIST_VISIBLE * 16 + 8;
            g.fill(pl + 7, fieldY - 2, pl + 210, fieldY + 14, UITheme.BORDER_ACCENT);
            g.fill(pl + 8, fieldY - 1, pl + 209, fieldY + 13, 0xFF060022);
            g.drawString(font, "Allow:", pl + 8, fieldY - 11, UITheme.TEXT_MUTED, false);

            int blockFieldY = fieldY + 22;
            g.fill(pl + 7, blockFieldY - 2, pl + 210, blockFieldY + 14, UITheme.BORDER_ACCENT);
            g.fill(pl + 8, blockFieldY - 1, pl + 209, blockFieldY + 13, 0xFF060022);
            g.drawString(font, "Block:", pl + 8, blockFieldY - 11, UITheme.TEXT_MUTED, false);

            // Online player quick-add list
            List<String> onlineNames = getOnlinePlayerNames();
            int onlineRowY = blockFieldY + 24;
            if (!onlineNames.isEmpty()) {
                g.drawString(font, "Online \u2014 quick add:", pl + 8, onlineRowY - 9, UITheme.TEXT_MUTED, false);
                for (int i = 0; i < Math.min(onlineNames.size(), 5); i++) {
                    int ry = onlineRowY + i * 16;
                    UITheme.drawRow(g, pl + 8, ry, pr - 8, ry + 13, i % 2 == 1, false);
                    g.drawString(font, onlineNames.get(i), pl + 12, ry + 3, UITheme.TEXT_OWN, false);
                }
            } else {
                g.drawString(font, "No other players online", pl + 8, onlineRowY, UITheme.withAlpha(UITheme.TEXT_MUTED, 0x88), false);
            }
        }

        // ── Tab 2: Info ───────────────────────────────────────────────────
        if (activeTab == 2) {
            int iy = ct + 8;
            int labelX = pl + 14;
            int valueX = pl + 100;
            int lineH = 14;

            g.drawString(font, "Name:", labelX, iy, UITheme.TEXT_MUTED, false);
            String displayN = mirrorName != null && !mirrorName.isEmpty() ? mirrorName : "(unnamed)";
            g.drawString(font, displayN, valueX, iy, UITheme.TEXT_WHITE, false);
            iy += lineH;

            g.drawString(font, "Tier:", labelX, iy, UITheme.TEXT_MUTED, false);
            g.drawString(font, cap(tierName), valueX, iy, UITheme.TEXT_LAVENDER, false);
            iy += lineH;

            g.drawString(font, "Type:", labelX, iy, UITheme.TEXT_MUTED, false);
            g.drawString(font, cap(typeName), valueX, iy, UITheme.TEXT_LAVENDER, false);
            iy += lineH;

            g.drawString(font, "Position:", labelX, iy, UITheme.TEXT_MUTED, false);
            g.drawString(font, mirrorPos.getX() + ", " + mirrorPos.getY() + ", " + mirrorPos.getZ(),
                    valueX, iy, UITheme.TEXT_WHITE, false);
            iy += lineH;

            g.drawString(font, "Dimension:", labelX, iy, UITheme.TEXT_MUTED, false);
            g.drawString(font, formatDimName(dimensionName), valueX, iy, UITheme.TEXT_WHITE, false);
            iy += lineH + 4;

            // Folder label — field widget is at ct+92
            g.drawString(font, "Folder:", labelX, iy, UITheme.TEXT_MUTED, false);
            int folderFieldY = ct + 92;
            g.fill(pl + 7, folderFieldY - 2, pl + 189, folderFieldY + 14, UITheme.BORDER_ACCENT);
            g.fill(pl + 8, folderFieldY - 1, pl + 188, folderFieldY + 13, 0xFF060022);


            // Expand Pocket label (pocket mirrors only) + Upgrade cost below actions row
            if ("pocket".equals(typeName)) {
                g.drawString(font, "Expand Pocket:", labelX, ct + 184, UITheme.TEXT_LAVENDER, false);
            }
            int costY = "pocket".equals(typeName) ? ct + 210 : ct + 134;
            MirrorTier tier = null;
            for (MirrorTier t : MirrorTier.values()) {
                if (t.getName().equals(tierName)) { tier = t; break; }
            }
            MirrorTier next = tier != null ? tier.getNext() : null;
            if (next != null) {
                String costText = "Upgrade cost: 8\u00d7 "
                        + cap(next.getName()) + " (" + cap(next.getMaterial().getDescriptionId()
                        .replace("item.minecraft.", "").replace("_", " ")) + ")";
                g.drawString(font, costText, labelX, costY, UITheme.TEXT_LAVENDER, false);
            }
        }

        UITheme.drawRule(g, pl + 8, pr - 8, pt + PANEL_H - FOOTER_H + 3);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String formatDimName(String dim) {
        if (dim == null || dim.isEmpty()) return "Unknown";
        if (dim.contains("overworld"))   return "Overworld";
        if (dim.contains("the_nether"))  return "The Nether";
        if (dim.contains("the_end"))     return "The End";
        if (dim.contains("pocket"))      return "Pocket Dimension";
        // Custom dimension: strip namespace and prettify
        String path = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        return cap(path.replace("_", " "));
    }

    private List<String> getOnlinePlayerNames() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return List.of();
        String self = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getGameProfile().getName() : "";
        return conn.getOnlinePlayers().stream()
                .map(pi -> pi.getProfile().getName())
                .filter(n -> n != null && !n.isEmpty() && !n.equals(self))
                .limit(6)
                .toList();
    }

    private static int getUpgradeSlotsForTier(String tierName) {
        if (tierName == null) return 3;
        return switch (tierName.toLowerCase()) {
            case "wood" -> 1;
            case "stone" -> 2;
            case "iron" -> 3;
            case "gold" -> 4;
            case "diamond" -> 5;
            case "netherite" -> 6;
            default -> 3;
        };
    }
}
