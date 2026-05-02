package com.ether.mirrors.screen;

import com.ether.mirrors.data.PermissionData;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientboundOpenMirrorManagementPacket;
import com.ether.mirrors.network.packets.ServerboundApplyUpgradePacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorManagementPacket;
import com.ether.mirrors.network.packets.ServerboundRemoveUpgradePacket;
import com.ether.mirrors.network.packets.ServerboundSetMirrorAccessModePacket;
import com.ether.mirrors.network.packets.ServerboundSetMirrorOverridePacket;
import com.ether.mirrors.network.packets.ServerboundSaveIconPacket;
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
    private static final int CONTENT_H = 310;
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

    // Icon editor state
    private byte[] iconPixels = new byte[256];
    private int iconSelectedColor = 1;
    private boolean iconDragging = false;
    private int iconEditorX, iconEditorY;
    private int iconHoverX = -1, iconHoverY = -1;

    private static final int ICON_CELL      = 4;
    private static final int ICON_CANVAS_PX = ICON_CELL * 16; // 64
    private static final int PAL_SW_SZ      = 14;
    private static final int PAL_SW_GAP     = 2;
    private static final int PAL_ROW_GAP    = 3;
    private static final int PAL_TOTAL_W    = 16 * PAL_SW_SZ + 15 * PAL_SW_GAP; // 254

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
        byte[] p = data.iconPixels;
        this.iconPixels = (p != null && p.length == 256) ? p.clone() : new byte[256];
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

            // Online player chips — click to fill the allow field
            List<String> onlineNames = getOnlinePlayerNames();
            int chipY = blockFieldY + 22;
            int chipX = pl + 8;
            for (String name : onlineNames) {
                int cw = this.font.width(name) + 12;
                if (chipX + cw > pl + PANEL_W - 14) break; // don't overflow panel
                addRenderableWidget(MirrorButton.of(chipX, chipY, cw, 13,
                        Component.literal(name),
                        b -> allowField.setValue(name),
                        UITheme.BORDER_MID, UITheme.TEXT_OWN));
                chipX += cw + 4;
            }
        }

        // ── Tab 2: Info ────────────────────────────────────────────────────
        if (activeTab == 2) {
            int bx = pl + 8;
            int ct2 = contentTop();

            // Compute icon editor position (canvas 64px centered in 400px panel)
            iconEditorX = pl + (PANEL_W - ICON_CANVAS_PX) / 2; // pl+168
            iconEditorY = ct2 + 14;

            // Save icon button
            addRenderableWidget(MirrorButton.teal(pl + PANEL_W - 80, ct2 + 8, 72, 14,
                    Component.literal("Save Icon"), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundSaveIconPacket(mirrorPos, iconPixels));
                    }));

            // Folder field — shifted down by 80px from original ct2+92 -> ct2+172
            int folderFieldY = ct2 + 172;
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

            // Rename + Upgrade Tier — shifted down by 80px: ct2+114 -> ct2+194
            int actionY = ct2 + 194;
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

            // Theme / Time / Weather — only for pocket mirrors, shifted down by 80px
            if ("pocket".equals(typeName)) {
                int themeW = 70, themeGap = 4;
                int themeRowX = pl + 8;

                int themeRowY = ct2 + 216;
                String[] themeIds = {"deepslate", "nether", "end", "mushroom", "crystal"};
                String[] themeLabels = {"Deepslate", "Nether", "End", "Mushroom", "Crystal"};
                for (int ti = 0; ti < themeIds.length; ti++) {
                    final String themeId = themeIds[ti];
                    addRenderableWidget(MirrorButton.teal(themeRowX + ti * (themeW + themeGap), themeRowY, themeW, 14,
                            Component.literal(themeLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new ServerboundSetPocketThemePacket(themeId))));
                }

                int timeRowY = ct2 + 234;
                int[] times = {1000, 13000, 18000, -1};
                String[] timeLabels = {"Day", "Night", "Midnight", "Unlock"};
                for (int ti = 0; ti < times.length; ti++) {
                    final long t = times[ti];
                    addRenderableWidget(MirrorButton.purple(themeRowX + ti * (themeW + themeGap), timeRowY, themeW, 14,
                            Component.literal(timeLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundSetPocketTimePacket(t))));
                }

                int weatherRowY = ct2 + 252;
                String[] weatherIds = {"clear", "rain", "thunder", "normal"};
                String[] weatherLabels = {"Clear", "Rain", "Thunder", "Normal"};
                for (int ti = 0; ti < weatherIds.length; ti++) {
                    final String w = weatherIds[ti];
                    addRenderableWidget(MirrorButton.teal(themeRowX + ti * (themeW + themeGap), weatherRowY, themeW, 14,
                            Component.literal(weatherLabels[ti]), b ->
                                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundSetPocketWeatherPacket(w))));
                }

                // Expand Pocket buttons (permanent upgrade with material cost)
                int expandRowY = ct2 + 272;
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

            // Online player chips label
            g.drawString(font, "Online:", pl + 8, blockFieldY + 14, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xAA), false);
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

            // Draw "Icon" label
            g.drawString(font, "Icon:", labelX, ct + 10, UITheme.TEXT_MUTED, false);

            // Draw icon editor grid cells
            for (int py = 0; py < 16; py++) {
                for (int px = 0; px < 16; px++) {
                    int color = MirrorPlacementScreen.ICON_PALETTE[iconPixels[py * 16 + px] & 0xFF];
                    int cx2 = iconEditorX + px * ICON_CELL;
                    int cy2 = iconEditorY + py * ICON_CELL;
                    if ((color >>> 24) == 0) {
                        g.fill(cx2, cy2, cx2 + ICON_CELL, cy2 + ICON_CELL,
                                ((px + py) % 2 == 0) ? 0xFF2A2A3A : 0xFF222232);
                    } else {
                        g.fill(cx2, cy2, cx2 + ICON_CELL, cy2 + ICON_CELL, color);
                    }
                }
            }
            // Grid lines (subtle)
            for (int row = 1; row < 16; row++)
                g.fill(iconEditorX, iconEditorY + row * ICON_CELL,
                       iconEditorX + ICON_CANVAS_PX, iconEditorY + row * ICON_CELL + 1, 0x18FFFFFF);
            for (int col2 = 1; col2 < 16; col2++)
                g.fill(iconEditorX + col2 * ICON_CELL, iconEditorY,
                       iconEditorX + col2 * ICON_CELL + 1, iconEditorY + ICON_CANVAS_PX, 0x18FFFFFF);
            // Hover highlight
            if (iconHoverX >= 0 && iconHoverY >= 0)
                g.fill(iconEditorX + iconHoverX * ICON_CELL, iconEditorY + iconHoverY * ICON_CELL,
                       iconEditorX + iconHoverX * ICON_CELL + ICON_CELL,
                       iconEditorY + iconHoverY * ICON_CELL + ICON_CELL, 0x40FFFFFF);
            // Double border
            int ice = iconEditorX + ICON_CANVAS_PX, icb = iconEditorY + ICON_CANVAS_PX;
            g.fill(iconEditorX - 1, iconEditorY - 1, ice + 1, iconEditorY,     UITheme.BORDER_MID);
            g.fill(iconEditorX - 1, icb,              ice + 1, icb + 1,          UITheme.BORDER_MID);
            g.fill(iconEditorX - 1, iconEditorY - 1, iconEditorX, icb + 1,     UITheme.BORDER_MID);
            g.fill(ice,              iconEditorY - 1, ice + 1, icb + 1,          UITheme.BORDER_MID);
            g.fill(iconEditorX - 2, iconEditorY - 2, ice + 2, iconEditorY - 1, UITheme.BORDER_ACCENT);
            g.fill(iconEditorX - 2, icb + 1,          ice + 2, icb + 2,          UITheme.BORDER_ACCENT);
            g.fill(iconEditorX - 2, iconEditorY - 2, iconEditorX - 1, icb + 2, UITheme.BORDER_ACCENT);
            g.fill(ice + 1,          iconEditorY - 2, ice + 2, icb + 2,          UITheme.BORDER_ACCENT);

            // Info strip: hint left, selected color name right
            int stripY = iconEditorY + ICON_CANVAS_PX + 4;
            g.drawString(font, "LMB paint \u00b7 RMB erase \u00b7 C clear",
                    pl + 14, stripY, UITheme.TEXT_MUTED, false);
            String selName = MirrorPlacementScreen.PAL_NAMES[iconSelectedColor];
            int selNameW = font.width(selName);
            int selSwX = pl + PANEL_W - 14 - selNameW - 12;
            int selSwCol = (iconSelectedColor == 0) ? 0xFF444455
                    : MirrorPlacementScreen.ICON_PALETTE[iconSelectedColor];
            g.fill(selSwX, stripY, selSwX + 8, stripY + 8, selSwCol);
            g.drawString(font, selName, selSwX + 10, stripY, UITheme.TEXT_WHITE, false);

            // Palette: 2 rows of 16, centered
            int paletteY = stripY + 8 + 4;
            int paletteX = pl + (PANEL_W - PAL_TOTAL_W) / 2;
            for (int palRow = 0; palRow < 2; palRow++) {
                int rowY = paletteY + palRow * (PAL_SW_SZ + PAL_ROW_GAP);
                for (int palCol = 0; palCol < 16; palCol++) {
                    int c = palRow * 16 + palCol;
                    int sc = paletteX + palCol * (PAL_SW_SZ + PAL_SW_GAP);
                    if (c == iconSelectedColor) {
                        g.fill(sc - 2, rowY - 2, sc + PAL_SW_SZ + 2, rowY + PAL_SW_SZ + 2, UITheme.BORDER_MID);
                        g.fill(sc - 1, rowY - 1, sc + PAL_SW_SZ + 1, rowY + PAL_SW_SZ + 1, UITheme.TEXT_GOLD);
                    }
                    if (c == 0) {
                        int h = PAL_SW_SZ / 2;
                        g.fill(sc,     rowY,     sc + h, rowY + h, 0xFF444455);
                        g.fill(sc + h, rowY,     sc + PAL_SW_SZ, rowY + h, 0xFF2A2A3A);
                        g.fill(sc,     rowY + h, sc + h, rowY + PAL_SW_SZ, 0xFF2A2A3A);
                        g.fill(sc + h, rowY + h, sc + PAL_SW_SZ, rowY + PAL_SW_SZ, 0xFF444455);
                    } else {
                        g.fill(sc, rowY, sc + PAL_SW_SZ, rowY + PAL_SW_SZ,
                                MirrorPlacementScreen.ICON_PALETTE[c]);
                    }
                }
            }

            // Folder label — field widget is at ct+172
            g.drawString(font, "Folder:", labelX, ct + 162, UITheme.TEXT_MUTED, false);
            int folderFieldY = ct + 172;
            g.fill(pl + 7, folderFieldY - 2, pl + 189, folderFieldY + 14, UITheme.BORDER_ACCENT);
            g.fill(pl + 8, folderFieldY - 1, pl + 188, folderFieldY + 13, 0xFF060022);

            // Expand Pocket label (pocket mirrors only) + Upgrade cost below actions row
            if ("pocket".equals(typeName)) {
                g.drawString(font, "Expand Pocket:", labelX, ct + 264, UITheme.TEXT_LAVENDER, false);
            }
            int costY = "pocket".equals(typeName) ? ct + 290 : ct + 214;
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

    private boolean handleIconEdit(double mouseX, double mouseY, int button) {
        if (activeTab != 2) return false;
        // Canvas hit
        if (mouseX >= iconEditorX && mouseX < iconEditorX + ICON_CANVAS_PX
                && mouseY >= iconEditorY && mouseY < iconEditorY + ICON_CANVAS_PX) {
            int px = ((int)mouseX - iconEditorX) / ICON_CELL;
            int py = ((int)mouseY - iconEditorY) / ICON_CELL;
            if (button == 0) iconPixels[py * 16 + px] = (byte) iconSelectedColor;
            else if (button == 1) iconPixels[py * 16 + px] = 0;
            return true;
        }
        // Palette hit (2 rows)
        int paletteY = iconEditorY + ICON_CANVAS_PX + 16; // canvas+4+strip(8)+4
        int paletteX = panelLeft() + (PANEL_W - PAL_TOTAL_W) / 2;
        for (int palRow = 0; palRow < 2; palRow++) {
            int rowY = paletteY + palRow * (PAL_SW_SZ + PAL_ROW_GAP);
            if (mouseY >= rowY && mouseY < rowY + PAL_SW_SZ) {
                int palCol = ((int)mouseX - paletteX) / (PAL_SW_SZ + PAL_SW_GAP);
                if (palCol >= 0 && palCol < 16 && mouseX >= paletteX + palCol * (PAL_SW_SZ + PAL_SW_GAP)
                        && mouseX < paletteX + palCol * (PAL_SW_SZ + PAL_SW_GAP) + PAL_SW_SZ) {
                    iconSelectedColor = palRow * 16 + palCol;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (activeTab == 2 && mouseX >= iconEditorX && mouseX < iconEditorX + ICON_CANVAS_PX
                && mouseY >= iconEditorY && mouseY < iconEditorY + ICON_CANVAS_PX) {
            iconHoverX = ((int)mouseX - iconEditorX) / ICON_CELL;
            iconHoverY = ((int)mouseY - iconEditorY) / ICON_CELL;
        } else {
            iconHoverX = -1;
            iconHoverY = -1;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleIconEdit(mouseX, mouseY, button)) { iconDragging = true; return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (iconDragging) { handleIconEdit(mouseX, mouseY, button); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        iconDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // C key clears canvas when on Info tab
        if (activeTab == 2 && keyCode == 67 && modifiers == 0) {
            java.util.Arrays.fill(iconPixels, (byte) 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
