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

import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MirrorSelectionScreen extends Screen {

    private List<ClientboundMirrorListPacket.MirrorInfo> mirrors;
    private List<ClientboundMirrorListPacket.MirrorInfo> filtered = new ArrayList<>();
    private final BlockPos sourceMirrorPos;
    private final String sourceMirrorType;
    private final boolean isHandheld;
    private final boolean warpTargetMode;
    private final long cooldownRemainingMs;
    private final long openTimeMs = System.currentTimeMillis();

    private int scrollOffset = 0;
    private int hoveredRow   = -1;
    private long lastTickCooldown = -1;
    private EditBox searchBox;
    private int currentTab = 0; // 0=ALL 1=FAV 2=RECENT 3=NEARBY

    // ── Layout — Design v1 (460 × 332) ───────────────────────────────────────
    private static final int PANEL_W  = 460;
    private static final int HEADER_H = 30;
    private static final int TABS_H   = 24;
    private static final int SEARCH_H = 40;
    private static final int COLHDR_H = 18;
    private static final int ROW_H    = 26;
    private static final int MAX_ROWS = 7;
    private static final int FOOTER_H = 38;
    // total = 30+24+40+18+7*26+38 = 332

    // Column pixel offsets (relative to panelLeft)
    // CSS grid: 16px gap(8) 1fr gap(8) 90px gap(8) 64px gap(8) 38px gap(8) 56px gap(8) 18px
    // padding: 0 14px 0 12px  →  content width = 434  →  1fr = 104px
    private static final int CX_IDX  = 12;   // col 0 : 16px
    private static final int CX_NAME = 36;   // col 1 : 104px
    private static final int CX_DIM  = 148;  // col 2 : 90px
    private static final int CX_SIG  = 246;  // col 3 : 64px  (center +32)
    // col 4 (empty) : 38px at x=318
    private static final int CX_ACT  = 364;  // col 5 : 56px
    private static final int CX_STAR = 428;  // col 6 : 18px
    private static final int ACT_W   = 56;
    private static final int ACT_H   = 18;
    private static final int STAR_W  = 14;

    // ── Design colours ────────────────────────────────────────────────────────
    private static final int DC_PANEL      = 0xFF030012;
    private static final int DC_HEADER_BG  = 0xFF020020;
    private static final int DC_BDR_OUTER  = 0xFF7733CC;
    private static final int DC_BDR_INNER  = 0x8CAA55FF;   // 55% opacity
    private static final int DC_BDR_DIM    = 0xFF2A1144;
    private static final int DC_ROW_ZEBRA  = 0x0AAA88FF;   //  4% tint
    private static final int DC_ROW_HOVER  = 0x24AA66EE;   // 14% tint
    private static final int DC_TEXT_MUTED = 0xFF9999AA;
    private static final int DC_TEXT_LAV   = 0xFFAA88FF;
    private static final int DC_GOLD       = 0xFFD4AF37;
    private static final int DC_SIG_FULL   = 0xFF44FF77;
    private static final int DC_SIG_DIM    = 0x2E44FF77;   // 18% opacity
    private static final int DC_SIG_DEAD   = 0xFFFF3344;

    // Dimension badge colour sets: text | bg | border
    private static final int DC_OW_T = 0xFF4FB870, DC_OW_BG = 0x144FB870, DC_OW_BD = 0x664FB870;
    private static final int DC_NE_T = 0xFFC84A3A, DC_NE_BG = 0x14C84A3A, DC_NE_BD = 0x66C84A3A;
    private static final int DC_EN_T = 0xFFD4AF37, DC_EN_BG = 0x14D4AF37, DC_EN_BD = 0x66D4AF37;
    private static final int DC_PK_T = 0xFFB07AFF, DC_PK_BG = 0x14B07AFF, DC_PK_BD = 0x66B07AFF;
    private static final int DC_XX_T = 0xFFAA88FF, DC_XX_BG = 0x14AA88FF, DC_XX_BD = 0x66AA88FF;

    // ── Constructors ──────────────────────────────────────────────────────────

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

    private long currentCooldownMs() {
        return Math.max(0L, cooldownRemainingMs - (System.currentTimeMillis() - openTimeMs));
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private int panelLeft()   { return (this.width - PANEL_W) / 2; }
    private int panelTop()    { return Math.max(4, (this.height - 332) / 2); }
    private int totalPanelH() { return 332; }
    private int listTop()     { return panelTop() + HEADER_H + TABS_H + SEARCH_H + COLHDR_H; }

    // ── Tab helpers ───────────────────────────────────────────────────────────

    private static final String[] TAB_NAMES = {"ALL", "FAV", "RECENT", "NEARBY"};

    private int tabCount(int tab) {
        return switch (tab) {
            case 1 -> (int) mirrors.stream().filter(m -> m.isFavorite).count();
            case 3 -> (int) mirrors.stream().filter(m -> m.inRange).count();
            default -> mirrors.size();
        };
    }

    private int tabItemW() { return (PANEL_W - 20) / 4; } // 110px each

    // ── Filter ────────────────────────────────────────────────────────────────

    private void applyFilter(String query) {
        filtered.clear();
        String q = query.toLowerCase().trim();
        for (ClientboundMirrorListPacket.MirrorInfo m : mirrors) {
            boolean tabOk = switch (currentTab) {
                case 1 -> m.isFavorite;
                case 3 -> m.inRange;
                default -> true;
            };
            if (!tabOk) continue;
            if (q.isEmpty()
                    || mirrorDisplayName(m).toLowerCase().contains(q)
                    || m.folderName.toLowerCase().contains(q)
                    || m.ownerName.toLowerCase().contains(q)) {
                filtered.add(m);
            }
        }
        scrollOffset = 0;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int pl = panelLeft(), pt = panelTop();
        int sbY = pt + HEADER_H + TABS_H + 9;
        int sbX = pl + 22;
        int sbW = PANEL_W - 22 - 76;
        searchBox = new EditBox(this.font, sbX, sbY, sbW, 14,
                Component.literal("Search"));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.literal("Search mirrors, owners, dimensions\u2026"));
        searchBox.setBordered(false);
        searchBox.setResponder(text -> { applyFilter(text); rebuildButtons(); });
        addRenderableWidget(searchBox);
        applyFilter(searchBox.getValue());
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (searchBox != null) addRenderableWidget(searchBox);

        int pl = panelLeft(), pt = panelTop(), lt = listTop();
        int visCount = Math.min(MAX_ROWS, filtered.size() - scrollOffset);
        long cdMs = currentCooldownMs();
        boolean onCooldown = cdMs > 0;

        for (int i = 0; i < visCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;
            ClientboundMirrorListPacket.MirrorInfo info = filtered.get(idx);

            int ry   = lt + i * ROW_H;
            int btnY = ry + (ROW_H - ACT_H) / 2;
            int btnX = pl + CX_ACT;
            int starX = pl + CX_STAR;
            int starY = ry + (ROW_H - 10) / 2;

            // Star / favorite button
            String starLabel = info.isFavorite ? "\u2605" : "\u2606";
            final java.util.UUID mid = info.mirrorId;
            MirrorButton starBtn = MirrorButton.of(starX, starY, STAR_W, 10,
                    Component.literal(starLabel),
                    b -> {
                        MirrorsNetwork.sendToServer(new ServerboundToggleFavoritePacket(mid));
                        for (int j = 0; j < mirrors.size(); j++) {
                            if (mirrors.get(j).mirrorId.equals(mid)) {
                                ClientboundMirrorListPacket.MirrorInfo old = mirrors.get(j);
                                mirrors.set(j, new ClientboundMirrorListPacket.MirrorInfo(
                                        old.mirrorId, old.ownerUUID, old.pos, old.dimensionName,
                                        old.tierName, old.typeName, old.ownerName, old.isOwn,
                                        old.inRange, old.signalStrength, old.mirrorName,
                                        !old.isFavorite, old.folderName));
                                break;
                            }
                        }
                        mirrors.sort(Comparator
                                .comparing((ClientboundMirrorListPacket.MirrorInfo m) -> m.isFavorite ? 0 : 1)
                                .thenComparing(m -> m.folderName.toLowerCase())
                                .thenComparing(m -> mirrorSortName(m).toLowerCase()));
                        applyFilter(searchBox != null ? searchBox.getValue() : "");
                        rebuildButtons();
                    },
                    UITheme.BORDER_MID,
                    info.isFavorite ? DC_GOLD : UITheme.withAlpha(DC_TEXT_MUTED, 0x4C));
            starBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("Toggle favorite (sorts to top)")));
            addRenderableWidget(starBtn);

            // Action button
            if ("beacon".equals(sourceMirrorType)) {
                // beacon is read-only — no action button
            } else if ("calling".equals(sourceMirrorType)) {
                MirrorButton btn = MirrorButton.teal(btnX, btnY, ACT_W, ACT_H,
                        Component.literal("CALL"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundCallRequestPacket(info.ownerUUID));
                            onClose();
                        });
                btn.active = info.inRange;
                addRenderableWidget(btn);
            } else if ("pocket".equals(sourceMirrorType)) {
                MirrorButton btn = MirrorButton.teal(btnX, btnY, ACT_W, ACT_H,
                        Component.literal("ENTER"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundEnterPocketPacket(info.ownerUUID));
                            onClose();
                        });
                btn.active = true;
                addRenderableWidget(btn);
            } else if (warpTargetMode) {
                String label = onCooldown ? String.format("%.1fs", cdMs / 1000.0) : "SET WARP";
                MirrorButton btn = MirrorButton.gold(btnX, btnY, ACT_W, ACT_H,
                        Component.literal(label), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundTeleportRequestPacket(
                                    info.mirrorId, sourceMirrorPos, isHandheld, true));
                            onClose();
                        });
                btn.active = info.inRange && !onCooldown;
                addRenderableWidget(btn);
            } else {
                String label = onCooldown ? String.format("%.1fs", cdMs / 1000.0) : "ENTER";
                MirrorButton btn = MirrorButton.purple(btnX, btnY, ACT_W, ACT_H,
                        Component.literal(label), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundTeleportRequestPacket(
                                    info.mirrorId, sourceMirrorPos, isHandheld));
                            onClose();
                        });
                btn.active = info.inRange && !onCooldown;
                addRenderableWidget(btn);
            }
        }

        // Scroll arrows (right gutter)
        int gutterX = pl + PANEL_W - 13;
        if (scrollOffset > 0) {
            addRenderableWidget(MirrorButton.purple(
                    gutterX, listTop() - 10, 10, 8,
                    Component.literal("\u25b2"), b -> { scrollOffset--; rebuildButtons(); }));
        }
        if (scrollOffset + MAX_ROWS < filtered.size()) {
            addRenderableWidget(MirrorButton.purple(
                    gutterX, listTop() + MAX_ROWS * ROW_H + 2, 10, 8,
                    Component.literal("\u25bc"), b -> { scrollOffset++; rebuildButtons(); }));
        }

        // Footer buttons
        int footerY  = panelTop() + 332 - FOOTER_H;
        int btnCY    = footerY + (FOOTER_H - 22) / 2;
        int fbW      = 90;
        int closeX   = pl + PANEL_W - fbW - 10;
        int permX    = closeX - fbW - 6;

        addRenderableWidget(MirrorButton.of(
                permX, btnCY, fbW, 22,
                Component.literal("PERMISSIONS"),
                b -> MirrorsNetwork.sendToServer(
                        new com.ether.mirrors.network.packets.ServerboundOpenPermissionsPacket()),
                UITheme.BORDER_MID, DC_TEXT_MUTED));

        addRenderableWidget(MirrorButton.gold(
                closeX, btnCY, fbW, 22,
                Component.literal("CLOSE"), b -> onClose()));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Dim the world behind the panel
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + 332;

        // ── Panel body ────────────────────────────────────────────────────────
        g.fill(pl, pt, pr, pb, DC_PANEL);

        // Outer border (1px)
        g.fill(pl,   pt,   pr,   pt+1, DC_BDR_OUTER);
        g.fill(pl,   pb-1, pr,   pb,   DC_BDR_OUTER);
        g.fill(pl,   pt,   pl+1, pb,   DC_BDR_OUTER);
        g.fill(pr-1, pt,   pr,   pb,   DC_BDR_OUTER);

        // Inner border (1px, inset 2, 55% opacity)
        g.fill(pl+2, pt+2, pr-2, pt+3, DC_BDR_INNER);
        g.fill(pl+2, pb-3, pr-2, pb-2, DC_BDR_INNER);
        g.fill(pl+2, pt+2, pl+3, pb-2, DC_BDR_INNER);
        g.fill(pr-3, pt+2, pr-2, pb-2, DC_BDR_INNER);

        // Pulsing glow on top/bottom border edges
        float pulse = UITheme.pulse();
        int glowA = (int)(pulse * 0x50 + 0x18);
        g.fill(pl+4, pt,   pr-4, pt+1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowA));
        g.fill(pl+4, pb-1, pr-4, pb,   UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowA));

        // Corner brackets (antique gold, 70% opacity)
        drawCornerBracket(g, pl+4,    pt+4,    false, false);
        drawCornerBracket(g, pr-14,   pt+4,    true,  false);
        drawCornerBracket(g, pl+4,    pb-14,   false, true);
        drawCornerBracket(g, pr-14,   pb-14,   true,  true);

        // ── Header (pt → pt+30) ───────────────────────────────────────────────
        g.fill(pl, pt, pr, pt+HEADER_H, DC_HEADER_BG);
        g.fill(pl, pt+HEADER_H, pr, pt+HEADER_H+1, DC_BDR_DIM);

        int hTextY = pt + (HEADER_H - 8) / 2;
        g.drawString(font, "\u2736", pl+14, hTextY, DC_GOLD, false);           // ✶ glyph
        g.drawString(font, "MIRROR NETWORK", pl+28, hTextY, DC_GOLD, false);

        // Pulsing connected-node indicator (right side)
        int nodeCount = mirrors.size();
        String statusTxt = "LINKED \u00b7 " + nodeCount + (nodeCount == 1 ? " NODE" : " NODES");
        int statusX = pr - 14 - font.width(statusTxt);
        int dotAlpha = (int)(pulse * 0xDD + 0x22);
        // dot: 3×3 filled square approximating a circle at MC pixel scale
        int dotX = statusX - 9, dotY = pt + (HEADER_H - 3) / 2;
        g.fill(dotX, dotY, dotX+3, dotY+3, UITheme.withAlpha(DC_SIG_FULL, dotAlpha));
        g.drawString(font, statusTxt, statusX, hTextY, UITheme.withAlpha(DC_TEXT_MUTED, 0xCC), false);

        // ── Tabs (pt+30 → pt+54) ─────────────────────────────────────────────
        int tabsY = pt + HEADER_H;
        g.fill(pl, tabsY, pr, tabsY+TABS_H, UITheme.withAlpha(DC_BDR_OUTER, 0x10));
        g.fill(pl, tabsY+TABS_H, pr, tabsY+TABS_H+1, DC_BDR_DIM);

        int tabW = tabItemW();
        for (int t = 0; t < 4; t++) {
            int tx     = pl + 10 + t * tabW;
            String lbl = TAB_NAMES[t] + " " + tabCount(t);
            int lw     = font.width(lbl);
            int tColor = (t == currentTab) ? DC_GOLD : UITheme.withAlpha(DC_TEXT_MUTED, 0xCC);
            g.drawString(font, lbl, tx + (tabW - lw) / 2, tabsY + (TABS_H - 8) / 2, tColor, false);
            if (t == currentTab) {
                g.fill(tx+8, tabsY+TABS_H-1, tx+tabW-8, tabsY+TABS_H, DC_GOLD);
            }
        }

        // ── Search row (pt+54 → pt+94) ────────────────────────────────────────
        int srY = tabsY + TABS_H;
        // Search box border
        int sbX1 = pl+10, sbY1 = srY+8, sbX2 = pl+PANEL_W-80, sbY2 = srY+32;
        g.fill(sbX1,   sbY1,   sbX2,   sbY2,   UITheme.withAlpha(0xFF000000, 0x66));
        g.fill(sbX1,   sbY1,   sbX2,   sbY1+1, DC_BDR_DIM);
        g.fill(sbX1,   sbY2-1, sbX2,   sbY2,   DC_BDR_DIM);
        g.fill(sbX1,   sbY1,   sbX1+1, sbY2,   DC_BDR_DIM);
        g.fill(sbX2-1, sbY1,   sbX2,   sbY2,   DC_BDR_DIM);
        // ⌕ magnifier icon
        g.drawString(font, "\u2315", sbX1+5, sbY1+(24-8)/2, UITheme.withAlpha(DC_TEXT_LAV, 0x99), false);

        // Sort chip
        int chipX1 = pl+PANEL_W-74, chipY1 = srY+8, chipX2 = chipX1+64, chipY2 = srY+32;
        g.fill(chipX1,   chipY1,   chipX2,   chipY2,   UITheme.withAlpha(DC_BDR_OUTER, 0x1E));
        g.fill(chipX1,   chipY1,   chipX2,   chipY1+1, DC_BDR_DIM);
        g.fill(chipX1,   chipY2-1, chipX2,   chipY2,   DC_BDR_DIM);
        g.fill(chipX1,   chipY1,   chipX1+1, chipY2,   DC_BDR_DIM);
        g.fill(chipX2-1, chipY1,   chipX2,   chipY2,   DC_BDR_DIM);
        String chip = "SORT \u25be";
        g.drawString(font, chip, chipX1+(64-font.width(chip))/2, chipY1+(24-8)/2,
                UITheme.withAlpha(DC_TEXT_LAV, 0xCC), false);

        // ── Column headers (pt+94 → pt+112) ──────────────────────────────────
        int chY = srY + SEARCH_H;
        g.fill(pl, chY, pr, chY+COLHDR_H, UITheme.withAlpha(0xFF000000, 0x40));
        g.fill(pl, chY+COLHDR_H-1, pr, chY+COLHDR_H, DC_BDR_DIM);

        int ctY  = chY + (COLHDR_H - 8) / 2;
        int chC  = UITheme.withAlpha(DC_TEXT_MUTED, 0xCC);
        g.drawString(font, "MIRROR \u00b7 OWNER",
                pl + CX_NAME, ctY, chC, false);
        g.drawString(font, "DIM",
                pl + CX_DIM  + (90 - font.width("DIM")) / 2, ctY, chC, false);
        g.drawString(font, "SIG",
                pl + CX_SIG  + (64 - font.width("SIG")) / 2, ctY, chC, false);
        g.drawString(font, "ACTION",
                pl + CX_ACT  + (ACT_W - font.width("ACTION")) / 2, ctY, chC, false);
        g.drawString(font, "\u2605",
                pl + CX_STAR + (STAR_W - font.width("\u2605")) / 2, ctY, chC, false);

        // ── List rows ─────────────────────────────────────────────────────────
        int lt       = listTop();
        hoveredRow   = -1;
        int visCount = Math.min(MAX_ROWS, filtered.size() - scrollOffset);

        for (int i = 0; i < visCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;
            ClientboundMirrorListPacket.MirrorInfo info = filtered.get(idx);

            int ry  = lt + i * ROW_H;
            boolean hov  = mouseX >= pl && mouseX < pr && mouseY >= ry && mouseY < ry + ROW_H;
            boolean dead = !info.inRange && info.signalStrength <= 0;
            if (hov) hoveredRow = i;

            // Row background
            if (hov) {
                g.fill(pl, ry, pr, ry+ROW_H, DC_ROW_HOVER);
                g.fill(pl, ry, pl+2, ry+ROW_H, DC_BDR_INNER); // left-edge accent
            } else if (i % 2 == 1) {
                g.fill(pl, ry, pr, ry+ROW_H, DC_ROW_ZEBRA);
            }

            int rowA  = dead ? 0x8C : 0xFF;
            int nameY = ry + 4;
            int ownY  = ry + 15;

            // Col 0: row index (right-aligned, muted)
            String idxStr = String.format("%02d", idx + 1);
            g.drawString(font, idxStr,
                    pl + CX_IDX + (16 - font.width(idxStr)), nameY,
                    UITheme.withAlpha(DC_TEXT_MUTED, (int)(rowA * 0.7)), false);

            // Col 1: mirror name (lavender) — truncated to 100px
            int nameColor = dead          ? UITheme.withAlpha(DC_TEXT_LAV, 0x8C)
                          : info.isFavorite ? DC_GOLD
                          : DC_TEXT_LAV;
            String baseName   = mirrorDisplayName(info);
            String dispName   = info.folderName.isEmpty() ? baseName
                              : "[" + info.folderName + "] " + baseName;
            g.drawString(font, truncate(dispName, 100), pl+CX_NAME+2, nameY, nameColor, false);

            // Owner sub-line: ◆ in gold + name in muted
            g.drawString(font, "\u25c6", pl+CX_NAME+2, ownY,
                    UITheme.withAlpha(DC_GOLD, rowA), false);
            int ownerColor = info.isOwn ? UITheme.withAlpha(DC_GOLD, rowA)
                                        : UITheme.withAlpha(DC_TEXT_MUTED, (int)(rowA * 0.8));
            g.drawString(font, truncate(info.ownerName, 88),
                    pl+CX_NAME+2+font.width("\u25c6")+2, ownY, ownerColor, false);

            // Col 2: dimension badge (centered in 90px column)
            drawDesignDimBadge(g, pl+CX_DIM, ry+(ROW_H-11)/2, info.dimensionName, 90, rowA);

            // Col 3: signal bars or dead ✕
            int sigCX = pl + CX_SIG + 32;
            if (!info.inRange || info.signalStrength <= 0) {
                String x = "\u2715";
                g.drawString(font, x, sigCX - font.width(x)/2, ry+(ROW_H-8)/2,
                        UITheme.withAlpha(DC_SIG_DEAD, rowA), false);
            } else {
                drawDesignSignalBars(g, sigCX, ry+ROW_H-3, info.signalStrength);
            }
        }

        // Empty-state messages
        if (filtered.isEmpty()) {
            int cy = lt + MAX_ROWS * ROW_H / 2;
            if (!mirrors.isEmpty()) {
                g.drawCenteredString(font, "No mirrors match your search.",
                        pl + PANEL_W/2, cy-4, DC_TEXT_MUTED);
            } else if ("calling".equals(sourceMirrorType)) {
                g.drawCenteredString(font, "No calling mirrors found.",
                        pl+PANEL_W/2, cy-8, DC_TEXT_MUTED);
                g.drawCenteredString(font, "Other players need calling mirrors.",
                        pl+PANEL_W/2, cy+4, UITheme.withAlpha(DC_TEXT_MUTED, 0xAA));
            } else {
                g.drawCenteredString(font, "No mirrors in your network.",
                        pl+PANEL_W/2, cy-8, DC_TEXT_MUTED);
                g.drawCenteredString(font, "Place mirrors and grant permissions.",
                        pl+PANEL_W/2, cy+4, UITheme.withAlpha(DC_TEXT_MUTED, 0xAA));
            }
        }

        // ── Footer (pt+294 → pt+332) ──────────────────────────────────────────
        int fy = pt + 332 - FOOTER_H;
        g.fill(pl, fy,   pr, fy+1, DC_BDR_DIM);
        g.fill(pl, fy+1, pr, pt+332, UITheme.withAlpha(0xFF000000, 0x4C));

        String showingTxt = "SHOWING " + filtered.size() + " / " + mirrors.size();
        g.drawString(font, showingTxt, pl+12, fy+(FOOTER_H-8)/2,
                UITheme.withAlpha(DC_TEXT_MUTED, 0xCC), false);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Tab click ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int pl = panelLeft(), pt = panelTop();
        int tabsY = pt + HEADER_H;
        if (my >= tabsY && my < tabsY + TABS_H && mx >= pl+10 && mx < pl+PANEL_W-10) {
            int clicked = (int)(mx - (pl+10)) / tabItemW();
            if (clicked >= 0 && clicked < 4 && clicked != currentTab) {
                currentTab = clicked;
                applyFilter(searchBox != null ? searchBox.getValue() : "");
                rebuildButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0)                              { scrollOffset--; rebuildButtons(); }
        else if (delta < 0 && scrollOffset + MAX_ROWS < filtered.size()){ scrollOffset++; rebuildButtons(); }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        long remaining = currentCooldownMs();
        long sec = remaining / 1000;
        if (sec != lastTickCooldown) {
            lastTickCooldown = sec;
            rebuildButtons();
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    /** Antique-gold L-shaped corner bracket, 10×10px. flipH/flipV control which corner. */
    private void drawCornerBracket(GuiGraphics g, int x, int y, boolean flipH, boolean flipV) {
        int sz  = 10, th = 1;
        int col = UITheme.withAlpha(DC_GOLD, 0xB3); // 70%
        if (!flipH && !flipV) { // TL
            g.fill(x,      y,      x+sz,    y+th,   col);
            g.fill(x,      y,      x+th,    y+sz,   col);
        } else if (flipH && !flipV) { // TR
            g.fill(x,      y,      x+sz,    y+th,   col);
            g.fill(x+sz-th,y,      x+sz,    y+sz,   col);
        } else if (!flipH) { // BL
            g.fill(x,      y+sz-th,x+sz,    y+sz,   col);
            g.fill(x,      y,      x+th,    y+sz,   col);
        } else { // BR
            g.fill(x,      y+sz-th,x+sz,    y+sz,   col);
            g.fill(x+sz-th,y,      x+sz,    y+sz,   col);
        }
    }

    /** Dimension badge with per-dimension colour scheme, centered in maxW. */
    private void drawDesignDimBadge(GuiGraphics g, int x, int y,
                                     String dim, int maxW, int rowAlpha) {
        String label;
        int bgC, bdC, txC;
        if (dim == null || dim.isEmpty()) {
            label = "?";          bgC = DC_XX_BG; bdC = DC_XX_BD; txC = DC_XX_T;
        } else if (dim.contains("overworld")) {
            label = "OVERWORLD";  bgC = DC_OW_BG; bdC = DC_OW_BD; txC = DC_OW_T;
        } else if (dim.contains("nether")) {
            label = "NETHER";     bgC = DC_NE_BG; bdC = DC_NE_BD; txC = DC_NE_T;
        } else if (dim.contains("the_end")) {
            label = "THE END";    bgC = DC_EN_BG; bdC = DC_EN_BD; txC = DC_EN_T;
        } else if (dim.contains("pocket")) {
            label = "POCKET";     bgC = DC_PK_BG; bdC = DC_PK_BD; txC = DC_PK_T;
        } else {
            String path = dim.contains(":") ? dim.substring(dim.indexOf(':')+1) : dim;
            label = path.replace("_"," ").toUpperCase();
            if (label.length() > 10) label = label.substring(0, 10);
            bgC = DC_XX_BG; bdC = DC_XX_BD; txC = DC_XX_T;
        }

        int tw  = font.width(label);
        int bw  = Math.min(maxW-4, tw+12);
        int bx  = x + (maxW - bw) / 2;
        int bh  = 11;

        g.fill(bx,    y,    bx+bw, y+bh,   applyAlpha(bgC, rowAlpha));
        g.fill(bx,    y,    bx+bw, y+1,    applyAlpha(bdC, rowAlpha));
        g.fill(bx,    y+bh-1, bx+bw, y+bh, applyAlpha(bdC, rowAlpha));
        g.fill(bx,    y,    bx+1,  y+bh,   applyAlpha(bdC, rowAlpha));
        g.fill(bx+bw-1, y,  bx+bw, y+bh,  applyAlpha(bdC, rowAlpha));
        g.drawString(font, label, bx+(bw-tw)/2, y+2, applyAlpha(txC, rowAlpha), false);
    }

    /**
     * Five 2px-wide signal bars, heights [3,5,7,9,11], 1px gap, centred on centerX.
     * All filled bars are solid green; empty bars at 18% opacity.
     */
    private void drawDesignSignalBars(GuiGraphics g, int centerX, int bottomY, double signal) {
        final int[] BH = {3, 5, 7, 9, 11};
        int barW = 2, gap = 1, bars = 5;
        int totalW  = bars * barW + (bars-1) * gap; // 14px
        int startX  = centerX - totalW / 2;
        int filled  = Math.max(0, Math.min(5, (int)(signal * 5 + 0.001)));

        for (int i = 0; i < bars; i++) {
            int bx = startX + i * (barW + gap);
            int bh = BH[i];
            g.fill(bx, bottomY-bh, bx+barW, bottomY, i < filled ? DC_SIG_FULL : DC_SIG_DIM);
        }
    }

    /** Scale the alpha channel of a colour by a 0–255 multiplier. */
    private static int applyAlpha(int color, int alpha) {
        if (alpha >= 0xFF) return color;
        int a = ((color >>> 24) & 0xFF) * alpha / 0xFF;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private String mirrorDisplayName(ClientboundMirrorListPacket.MirrorInfo info) {
        if (info.mirrorName != null && !info.mirrorName.isEmpty()) return info.mirrorName;
        if ("pocket".equalsIgnoreCase(info.typeName)) return info.ownerName + "'s Pocket";
        return cap(info.tierName) + " " + cap(info.typeName);
    }

    private static String mirrorSortName(ClientboundMirrorListPacket.MirrorInfo info) {
        if (info.mirrorName != null && !info.mirrorName.isEmpty()) return info.mirrorName;
        return info.tierName + " " + info.typeName;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "\u2026";
        int ew = font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (char c : text.toCharArray()) {
            int cw = font.width(String.valueOf(c));
            if (w + cw + ew > maxWidth) break;
            sb.append(c);
            w += cw;
        }
        return sb + ellipsis;
    }
}
