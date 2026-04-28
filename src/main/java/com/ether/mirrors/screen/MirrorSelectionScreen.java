package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientboundMirrorListPacket;
import com.ether.mirrors.network.packets.ServerboundCallRequestPacket;
import com.ether.mirrors.network.packets.ServerboundEnterPocketPacket;
import com.ether.mirrors.network.packets.ServerboundToggleFavoritePacket;
import com.ether.mirrors.network.packets.ServerboundTeleportRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MirrorSelectionScreen extends Screen {

    private List<ClientboundMirrorListPacket.MirrorInfo> mirrors;
    private final BlockPos sourceMirrorPos;
    private final String sourceMirrorType;
    private final boolean isHandheld;
    private final boolean warpTargetMode;
    private final long cooldownRemainingMs;
    private final long openTimeMs = System.currentTimeMillis();

    private int scrollOffset = 0;
    private int hoveredRow = -1;
    private long lastTickCooldown = -1;

    private static final int MAX_VISIBLE = 7;
    private static final int ENTRY_H     = 26;
    private static final int PANEL_W     = 460;
    private static final int HEADER_H    = 34;  // taller header — room for type + subtitle
    private static final int COLHDR_H    = 14;  // dedicated row for column labels
    private static final int BTN_W       = 60;
    private static final int BTN_H       = 16;
    private static final int STAR_W      = 14;

    public MirrorSelectionScreen(List<ClientboundMirrorListPacket.MirrorInfo> mirrors,
                                  BlockPos sourceMirrorPos, String sourceMirrorType, boolean isHandheld) {
        this(mirrors, sourceMirrorPos, sourceMirrorType, isHandheld, false, 0L);
    }

    public MirrorSelectionScreen(List<ClientboundMirrorListPacket.MirrorInfo> mirrors,
                                  BlockPos sourceMirrorPos, String sourceMirrorType, boolean isHandheld,
                                  boolean warpTargetMode) {
        this(mirrors, sourceMirrorPos, sourceMirrorType, isHandheld, warpTargetMode, 0L);
    }

    public MirrorSelectionScreen(List<ClientboundMirrorListPacket.MirrorInfo> mirrors,
                                  BlockPos sourceMirrorPos, String sourceMirrorType, boolean isHandheld,
                                  boolean warpTargetMode, long cooldownRemainingMs) {
        super(Component.literal("Mirror Network"));
        // Sort: favorites first, then by folder, then by display name
        this.mirrors = new ArrayList<>(mirrors);
        this.mirrors.sort(Comparator
                .comparing((ClientboundMirrorListPacket.MirrorInfo m) -> m.isFavorite ? 0 : 1)
                .thenComparing(m -> m.folderName.toLowerCase())
                .thenComparing(m -> mirrorSortName(m).toLowerCase()));
        this.sourceMirrorPos = sourceMirrorPos;
        this.sourceMirrorType = sourceMirrorType != null ? sourceMirrorType : "teleport";
        this.isHandheld = isHandheld;
        this.warpTargetMode = warpTargetMode;
        this.cooldownRemainingMs = cooldownRemainingMs;
    }

    /** Returns milliseconds left on the cooldown, accounting for time elapsed since screen open. */
    private long currentCooldownMs() {
        long elapsed = System.currentTimeMillis() - openTimeMs;
        return Math.max(0L, cooldownRemainingMs - elapsed);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private int panelLeft()   { return (this.width - PANEL_W) / 2; }
    private int panelTop()    { return Math.max(16, (this.height - totalPanelH()) / 2); }
    private int totalPanelH() { return HEADER_H + 2 + COLHDR_H + MAX_VISIBLE * ENTRY_H + 10 + 24; }
    private int listTop()     { return panelTop() + HEADER_H + 4 + COLHDR_H; }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        int pl = panelLeft(), pt = panelTop(), lt = listTop();
        int visCount = Math.min(MAX_VISIBLE, mirrors.size() - scrollOffset);

        long cdMs = currentCooldownMs();
        boolean onCooldown = cdMs > 0;

        for (int i = 0; i < visCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= mirrors.size()) break;

            ClientboundMirrorListPacket.MirrorInfo info = mirrors.get(idx);
            int ry = lt + i * ENTRY_H + (ENTRY_H - BTN_H) / 2;
            // Action button shifted left to leave room for the star button
            int bx = pl + PANEL_W - BTN_W - STAR_W - 8;
            int starX = pl + PANEL_W - STAR_W - 4;

            // Star (favorite) toggle button
            String starLabel = info.isFavorite ? "\u2605" : "\u2606"; // ★ or ☆
            final int finalIdx = idx;
            MirrorButton starBtn = MirrorButton.of(starX, ry, STAR_W, BTN_H,
                    Component.literal(starLabel),
                    b -> {
                        MirrorsNetwork.sendToServer(new ServerboundToggleFavoritePacket(info.mirrorId));
                        // Optimistically toggle client-side and resort
                        ClientboundMirrorListPacket.MirrorInfo old = mirrors.get(finalIdx);
                        mirrors.set(finalIdx, new ClientboundMirrorListPacket.MirrorInfo(
                                old.mirrorId, old.ownerUUID, old.pos, old.dimensionName,
                                old.tierName, old.typeName, old.ownerName, old.isOwn,
                                old.inRange, old.signalStrength, old.mirrorName,
                                !old.isFavorite, old.folderName));
                        mirrors.sort(Comparator
                                .comparing((ClientboundMirrorListPacket.MirrorInfo m) -> m.isFavorite ? 0 : 1)
                                .thenComparing(m -> m.folderName.toLowerCase())
                                .thenComparing(m -> mirrorSortName(m).toLowerCase()));
                        rebuildButtons();
                    },
                    UITheme.BORDER_MID,
                    info.isFavorite ? UITheme.TEXT_GOLD : UITheme.TEXT_MUTED);
            addRenderableWidget(starBtn);

            if ("beacon".equals(sourceMirrorType)) {
                // Beacon mirrors are read-only waypoints — no action button
            } else if ("calling".equals(sourceMirrorType)) {
                MirrorButton btn = MirrorButton.teal(bx, ry, BTN_W, BTN_H,
                        Component.literal("Call"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundCallRequestPacket(info.ownerUUID));
                            onClose();
                        });
                btn.active = info.inRange;
                addRenderableWidget(btn);
            } else if ("pocket".equals(sourceMirrorType)) {
                MirrorButton btn = MirrorButton.teal(bx, ry, BTN_W, BTN_H,
                        Component.literal("Enter"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundEnterPocketPacket(info.ownerUUID));
                            onClose();
                        });
                btn.active = true; // Pocket dimension always accessible
                addRenderableWidget(btn);
            } else if (warpTargetMode) {
                // In warp target mode: "Set Warp" button saves this mirror as the warp destination
                String label = onCooldown ? String.format("%.1fs", cdMs / 1000.0) : "Set Warp";
                MirrorButton btn = MirrorButton.gold(bx, ry, BTN_W, BTN_H,
                        Component.literal(label), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundTeleportRequestPacket(
                                    info.mirrorId, sourceMirrorPos, isHandheld, true));
                            onClose();
                        });
                btn.active = info.inRange && !onCooldown;
                addRenderableWidget(btn);
            } else {
                String label = onCooldown ? String.format("%.1fs", cdMs / 1000.0) : "Teleport";
                MirrorButton btn = MirrorButton.purple(bx, ry, BTN_W, BTN_H,
                        Component.literal(label), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundTeleportRequestPacket(
                                    info.mirrorId, sourceMirrorPos, isHandheld));
                            onClose();
                        });
                btn.active = info.inRange && !onCooldown;
                addRenderableWidget(btn);
            }
        }

        // Scroll arrows
        int listBottom = lt + MAX_VISIBLE * ENTRY_H;
        if (scrollOffset > 0) {
            addRenderableWidget(MirrorButton.purple(
                    pl + PANEL_W / 2 - 18, pt + HEADER_H - 18, 36, 14,
                    Component.literal("  ^  "), b -> { scrollOffset--; rebuildButtons(); }));
        }
        if (scrollOffset + MAX_VISIBLE < mirrors.size()) {
            addRenderableWidget(MirrorButton.purple(
                    pl + PANEL_W / 2 - 18, listBottom + 2, 36, 14,
                    Component.literal("  v  "), b -> { scrollOffset++; rebuildButtons(); }));
        }

        // Permissions button
        addRenderableWidget(MirrorButton.teal(
                pl + PANEL_W / 2 - 110, panelTop() + totalPanelH() - 22, 100, 18,
                Component.literal("Permissions"), b -> {
                    MirrorsNetwork.sendToServer(new com.ether.mirrors.network.packets.ServerboundOpenPermissionsPacket());
                }));

        // Close
        addRenderableWidget(MirrorButton.gold(
                pl + PANEL_W / 2 + 10, panelTop() + totalPanelH() - 22, 100, 18,
                Component.literal("Close"), b -> onClose()));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Dim the world
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + totalPanelH();

        UITheme.drawPanel(g, pl, pt, pr, pb);
        UITheme.drawHeader(g, pl, pt, pr, HEADER_H);

        // Pulse accent on border corners
        float pulse = UITheme.pulse();
        int glowAlpha = (int)(pulse * 0x60 + 0x20);
        g.fill(pl - 1, pt - 1, pr + 1, pt, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha));
        g.fill(pl - 1, pb,     pr + 1, pb + 1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha));

        // Header — two clearly separated lines inside the taller header strip
        String typeLabel;
        if (warpTargetMode) {
            typeLabel = "Select Warp Destination";
        } else if ("calling".equals(sourceMirrorType)) {
            typeLabel = "Calling Mirror";
        } else if ("beacon".equals(sourceMirrorType)) {
            typeLabel = "Beacon Network";
        } else if ("pocket".equals(sourceMirrorType)) {
            typeLabel = "Pocket Dimensions";
        } else {
            typeLabel = "Teleport Mirror";
        }
        // Small muted network label
        g.drawCenteredString(font, "Mirror Network", pl + PANEL_W / 2, pt + 6,
                UITheme.withAlpha(UITheme.TEXT_MUTED, 0xAA));
        // Large gold type label with decorative diamonds
        g.drawCenteredString(font, "\u2726 " + typeLabel + " \u2726", pl + PANEL_W / 2, pt + 19,
                UITheme.TEXT_GOLD);

        // Column headers — sit in their own dedicated COLHDR_H row below the separator
        int lt = listTop();
        int colHdrY = lt - COLHDR_H + 3;
        g.drawString(font, "Mirror", pl + 8, colHdrY, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xCC), false);
        g.drawString(font, "Owner", pl + 180, colHdrY, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xCC), false);
        g.drawString(font, "Dim", pl + 275, colHdrY, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xCC), false);
        g.drawString(font, "Signal", pl + 335, colHdrY, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xCC), false);
        // Subtle rule separating column headers from list entries
        g.fill(pl + 4, colHdrY + 10, pr - 4, colHdrY + 11, UITheme.withAlpha(UITheme.BORDER_MID, 0x88));

        // Entry rows
        hoveredRow = -1;
        int visCount = Math.min(MAX_VISIBLE, mirrors.size() - scrollOffset);
        for (int i = 0; i < visCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= mirrors.size()) break;
            ClientboundMirrorListPacket.MirrorInfo info = mirrors.get(idx);

            int ry = lt + i * ENTRY_H;
            boolean hov = mouseX >= pl && mouseX < pr && mouseY >= ry && mouseY < ry + ENTRY_H;
            if (hov) hoveredRow = i;

            UITheme.drawRow(g, pl + 2, ry, pr - 2, ry + ENTRY_H, i % 2 == 1, hov);

            int ty = ry + (ENTRY_H - 8) / 2;

            // Mirror name — truncate to fit column (column ends at pl+180, left edge at pl+8)
            int nameColor = !info.inRange ? UITheme.TEXT_MUTED
                    : info.isFavorite ? UITheme.TEXT_GOLD
                    : (info.isOwn ? UITheme.TEXT_OWN : UITheme.TEXT_OTHER);
            String baseName = mirrorDisplayName(info);
            String displayName = info.folderName.isEmpty() ? baseName
                    : "[" + info.folderName + "] " + baseName;
            g.drawString(font, truncate(displayName, 168), pl + 8, ty, nameColor, false);

            // Owner
            g.drawString(font, info.ownerName, pl + 180, ty, UITheme.TEXT_WHITE, false);

            // Dimension badge
            UITheme.drawDimBadge(g, font, pl + 272, ty, info.dimensionName);

            // Signal bars (shifted left to prevent overlap with range text)
            UITheme.drawSignalBars(g, pl + 295, ry + ENTRY_H - 6, info.signalStrength, info.inRange);

            // Range text (small, beside bars)
            String rangeStr;
            int rangeColor;
            if (!info.inRange) {
                rangeStr = "No Signal";
                rangeColor = UITheme.SIGNAL_DEAD;
            } else if (info.signalStrength > 0.6) {
                rangeStr = "Strong";
                rangeColor = UITheme.SIGNAL_FULL;
            } else if (info.signalStrength > 0.2) {
                rangeStr = "Weak";
                rangeColor = UITheme.SIGNAL_MED;
            } else {
                rangeStr = "Faint";
                rangeColor = UITheme.SIGNAL_LOW;
            }
            // Right-align signal text flush against the action button
            int btnX = pl + PANEL_W - BTN_W - STAR_W - 8;
            g.drawString(font, rangeStr, btnX - font.width(rangeStr) - 4, ty, rangeColor, false);
        }

        // Empty state
        if (mirrors.isEmpty()) {
            int cy = lt + MAX_VISIBLE * ENTRY_H / 2;
            if ("calling".equals(sourceMirrorType)) {
                g.drawCenteredString(font, "No other players' calling mirrors found.", pl + PANEL_W / 2, cy - 8, UITheme.TEXT_MUTED);
                g.drawCenteredString(font, "Other players need to place calling mirrors.", pl + PANEL_W / 2, cy + 4, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xAA));
            } else {
                g.drawCenteredString(font, "No mirrors found in your network.", pl + PANEL_W / 2, cy - 8, UITheme.TEXT_MUTED);
                g.drawCenteredString(font, "Place more mirrors and grant permissions.", pl + PANEL_W / 2, cy + 4, UITheme.withAlpha(UITheme.TEXT_MUTED, 0xAA));
            }
        }

        UITheme.drawRule(g, pl + 8, pr - 8, lt + MAX_VISIBLE * ENTRY_H + 4);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildButtons(); }
        else if (delta < 0 && scrollOffset + MAX_VISIBLE < mirrors.size()) { scrollOffset++; rebuildButtons(); }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        // Rebuild buttons when cooldown crosses a second boundary (label changes) or expires
        long remaining = currentCooldownMs();
        long remainingSec = remaining / 1000;
        if (remainingSec != lastTickCooldown) {
            lastTickCooldown = remainingSec;
            rebuildButtons();
        }
    }

    private String mirrorDisplayName(ClientboundMirrorListPacket.MirrorInfo info) {
        if (info.mirrorName != null && !info.mirrorName.isEmpty()) return info.mirrorName;
        if ("pocket".equalsIgnoreCase(info.typeName)) {
            return info.ownerName + "'s Pocket Dimension";
        }
        return cap(info.tierName) + " " + cap(info.typeName);
    }

    /** Name used purely for sorting (without folder prefix). */
    private static String mirrorSortName(ClientboundMirrorListPacket.MirrorInfo info) {
        if (info.mirrorName != null && !info.mirrorName.isEmpty()) return info.mirrorName;
        return info.tierName + " " + info.typeName;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /** Truncate text to fit within maxWidth pixels, appending "…" if cut. */
    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        int currentWidth = 0;
        for (char c : text.toCharArray()) {
            int charWidth = font.width(String.valueOf(c));
            if (currentWidth + charWidth + ellipsisW > maxWidth) break;
            sb.append(c);
            currentWidth += charWidth;
        }
        return sb + ellipsis;
    }
}
