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
import java.util.UUID;

public class MirrorSelectionScreen extends Screen {

    private List<ClientboundMirrorListPacket.MirrorInfo> mirrors;
    private List<ClientboundMirrorListPacket.MirrorInfo> filtered = new ArrayList<>();
    private final BlockPos sourceMirrorPos;
    private final String sourceMirrorType;
    private final boolean isHandheld;
    private final boolean warpTargetMode;
    private final long cooldownRemainingMs;
    private final long openTimeMs = System.currentTimeMillis();

    private int scrollOffset  = 0;
    private int hoveredRow    = -1;
    private long lastTickCooldown = -1;
    private EditBox searchBox;

    // v2 state
    private int  currentTab       = 2;    // default: RECENT (shows all until recency tracking added)
    private int  sortMode         = 0;    // 0=signal 1=name 2=dim
    private UUID selectedMirrorId = null; // selection persists across tabs
    private int  selectedRow      = -1;   // list index of selection, recomputed after filter

    // ── Layout — Design v2 (460 × 330) ───────────────────────────────────────
    private static final int PANEL_W     = 460;
    private static final int HEADER_H    = 32;
    private static final int TABS_H      = 22;
    private static final int SEARCH_H    = 38;
    private static final int COLHDR_H    = 16;
    private static final int ROW_H       = 22;
    private static final int MAX_ROWS    = 7;
    private static final int INSPECTOR_H = 36;
    private static final int FOOTER_H    = 32;
    // total = 32+22+38+16+7*22+36+32 = 330

    // Column pixel offsets (relative to panelLeft)
    // CSS grid: 18px gap(6) 1fr gap(6) 88px gap(6) 60px gap(6) 56px gap(6) 18px
    // padding: 0 14px 0 10px  →  content=436  →  1fr=166px
    private static final int CX_IDX  = 10;   // col 0 : 18px
    private static final int CX_NAME = 34;   // col 1 : 166px
    private static final int CX_DIM  = 206;  // col 2 : 88px
    private static final int CX_SIG  = 300;  // col 3 : 60px  (centre +30)
    private static final int CX_ACT  = 366;  // col 4 : 56px
    private static final int CX_STAR = 428;  // col 5 : 18px
    private static final int ACT_W   = 56;
    private static final int ACT_H   = 16;
    private static final int STAR_W  = 14;

    // ── Design colours ────────────────────────────────────────────────────────
    private static final int DC_PANEL      = 0xFF030012;
    private static final int DC_HEADER_BG  = 0xFF020020;
    private static final int DC_BDR_OUTER  = 0xFF7733CC;
    private static final int DC_BDR_INNER  = 0x8CAA55FF;
    private static final int DC_BDR_DIM    = 0xFF2A1144;
    private static final int DC_ROW_ZEBRA  = 0x0AAA88FF;
    private static final int DC_ROW_HOVER  = 0x24AA66EE;
    private static final int DC_ROW_SEL    = 0x47AA55FF;   // rgba(170,85,255,0.28)
    private static final int DC_TEXT_MUTED = 0xFF9999AA;
    private static final int DC_TEXT_LAV   = 0xFFAA88FF;
    private static final int DC_GOLD       = 0xFFD4AF37;
    private static final int DC_SIG_FULL   = 0xFF44FF77;
    private static final int DC_SIG_DIM    = 0x2E44FF77;
    private static final int DC_SIG_DEAD   = 0xFFFF3344;

    // Dimension badge colour sets: text | bg | border
    private static final int DC_OW_T = 0xFF4FB870, DC_OW_BG = 0x144FB870, DC_OW_BD = 0x664FB870;
    private static final int DC_NE_T = 0xFFC84A3A, DC_NE_BG = 0x14C84A3A, DC_NE_BD = 0x66C84A3A;
    private static final int DC_EN_T = 0xFFD4AF37, DC_EN_BG = 0x14D4AF37, DC_EN_BD = 0x66D4AF37;
    private static final int DC_PK_T = 0xFFB07AFF, DC_PK_BG = 0x14B07AFF, DC_PK_BD = 0x66B07AFF;
    private static final int DC_XX_T = 0xFFAA88FF, DC_XX_BG = 0x14AA88FF, DC_XX_BD = 0x66AA88FF;

    // Mode pill colours: text | border | bg
    private static final int[] MODE_T  = {0xFFAA88FF, 0xFF6FE0E0, 0xFFD4AF37, 0xFFFF8090};
    private static final int[] MODE_BD = {0xFF7733CC,  0xFF1A6B6B, 0xFF9A7520, 0xFF8B1A1A};
    private static final int[] MODE_BG = {0x2E7733CC,  0x381A6B6B, 0x1E9A7520, 0x388B1A1A};

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

    private int panelLeft()    { return (this.width - PANEL_W) / 2; }
    private int panelTop()     { return Math.max(4, (this.height - totalPanelH()) / 2); }
    private int totalPanelH()  { return HEADER_H + TABS_H + SEARCH_H + COLHDR_H + MAX_ROWS * ROW_H + INSPECTOR_H + FOOTER_H; }
    private int listTop()      { return panelTop() + HEADER_H + TABS_H + SEARCH_H + COLHDR_H; }
    private int inspectorTop() { return listTop() + MAX_ROWS * ROW_H; }
    private int footerTop()    { return inspectorTop() + INSPECTOR_H; }

    // ── Tab helpers ───────────────────────────────────────────────────────────

    private static final String[] TAB_NAMES = {"ALL", "FAV", "RECENT", "NEARBY"};

    private int tabCount(int tab) {
        return switch (tab) {
            case 1 -> (int) mirrors.stream().filter(m -> m.isFavorite).count();
            case 3 -> (int) mirrors.stream().filter(m -> m.inRange).count();
            default -> mirrors.size();
        };
    }

    private int tabItemW() { return (PANEL_W - 20) / 4; }

    // ── Sort chip ─────────────────────────────────────────────────────────────

    private static final String[] SORT_GLYPHS  = {"\u25c9", "A\u2192Z", "\u2b22"}; // ◉ A→Z ⬢
    private static final String[] SORT_LABELS  = {"SIGNAL", "NAME", "DIM"};

    // ── Filter + sort ─────────────────────────────────────────────────────────

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
        // Apply sort
        switch (sortMode) {
            case 1 -> filtered.sort(Comparator.comparing(m -> mirrorDisplayName(m).toLowerCase()));
            case 2 -> filtered.sort(Comparator.comparing(m -> m.dimensionName != null ? m.dimensionName : ""));
            default -> filtered.sort(Comparator
                    .comparingInt((ClientboundMirrorListPacket.MirrorInfo m) -> m.isFavorite ? 0 : 1)
                    .thenComparingDouble(m -> -m.signalStrength));
        }
        scrollOffset = 0;
        // Recompute selected row index
        selectedRow = -1;
        if (selectedMirrorId != null) {
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).mirrorId.equals(selectedMirrorId)) {
                    selectedRow = i;
                    break;
                }
            }
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int pl = panelLeft(), pt = panelTop();
        int srY = pt + HEADER_H + TABS_H;
        // Search box sits inside the search row (8px top padding)
        searchBox = new EditBox(this.font, pl + 22, srY + 9, PANEL_W - 22 - 76, 14,
                Component.literal("Search"));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.literal(searchHint()));
        searchBox.setBordered(false);
        searchBox.setResponder(text -> { applyFilter(text); rebuildButtons(); });
        addRenderableWidget(searchBox);
        applyFilter(searchBox.getValue());
        rebuildButtons();
    }

    private String searchHint() {
        return switch (sourceMirrorType) {
            case "calling" -> "Search by player or mirror\u2026";
            case "pocket"  -> "Search pockets\u2026";
            default        -> "Search mirrors, owners, dimensions\u2026";
        };
    }

    private void rebuildButtons() {
        clearWidgets();
        if (searchBox != null) addRenderableWidget(searchBox);

        int pl = panelLeft(), lt = listTop();
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
            final UUID mid = info.mirrorId;
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
                                        !old.isFavorite, old.folderName, old.iconPixels));
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
                // read-only, no action
            } else if ("calling".equals(sourceMirrorType)) {
                MirrorButton btn = MirrorButton.teal(btnX, btnY, ACT_W, ACT_H,
                        Component.literal("CALL"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundCallRequestPacket(info.ownerUUID));
                            onClose();
                        });
                btn.active = info.inRange;
                addRenderableWidget(btn);
            } else if ("pocket".equals(sourceMirrorType)) {
                MirrorButton btn = MirrorButton.gold(btnX, btnY, ACT_W, ACT_H,
                        Component.literal("OPEN"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundEnterPocketPacket(info.ownerUUID));
                            onClose();
                        });
                btn.active = true;
                addRenderableWidget(btn);
            } else if (warpTargetMode) {
                String label = onCooldown ? String.format("%.1fs", cdMs / 1000.0) : "BIND";
                MirrorButton btn = MirrorButton.red(btnX, btnY, ACT_W, ACT_H,
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
                            // Packet is sent at the END of the travel animation, not here.
                            minecraft.setScreen(new MirrorTravelScreen(info, sourceMirrorPos, isHandheld));
                        });
                btn.active = info.inRange && !onCooldown;
                addRenderableWidget(btn);
            }
        }

        // Scroll arrows in right gutter — only when list overflows
        if (filtered.size() > MAX_ROWS) {
            int gx = panelLeft() + PANEL_W - 12;
            if (scrollOffset > 0) {
                addRenderableWidget(MirrorButton.purple(gx, listTop(), 10, 10,
                        Component.literal("\u25b2"), b -> { scrollOffset--; rebuildButtons(); }));
            }
            if (scrollOffset + MAX_ROWS < filtered.size()) {
                addRenderableWidget(MirrorButton.purple(
                        gx, listTop() + MAX_ROWS * ROW_H - 10, 10, 10,
                        Component.literal("\u25bc"), b -> { scrollOffset++; rebuildButtons(); }));
            }
        }

        // Footer buttons
        int fy   = footerTop();
        int btnCY = fy + (FOOTER_H - 22) / 2;
        int fbW  = 90;
        int closeX = panelLeft() + PANEL_W - fbW - 10;
        int permX  = closeX - fbW - 6;

        addRenderableWidget(MirrorButton.of(permX, btnCY, fbW, 22,
                Component.literal("PERMISSIONS"),
                b -> MirrorsNetwork.sendToServer(
                        new com.ether.mirrors.network.packets.ServerboundOpenPermissionsPacket()),
                UITheme.BORDER_MID, DC_TEXT_MUTED));

        addRenderableWidget(MirrorButton.gold(closeX, btnCY, fbW, 22,
                Component.literal("CLOSE"), b -> onClose()));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + totalPanelH();

        // ── Panel ─────────────────────────────────────────────────────────────
        g.fill(pl, pt, pr, pb, DC_PANEL);
        // Outer border
        g.fill(pl,   pt,   pr,   pt+1, DC_BDR_OUTER);
        g.fill(pl,   pb-1, pr,   pb,   DC_BDR_OUTER);
        g.fill(pl,   pt,   pl+1, pb,   DC_BDR_OUTER);
        g.fill(pr-1, pt,   pr,   pb,   DC_BDR_OUTER);
        // Inner border (55% opacity, inset 2)
        g.fill(pl+2, pt+2, pr-2, pt+3, DC_BDR_INNER);
        g.fill(pl+2, pb-3, pr-2, pb-2, DC_BDR_INNER);
        g.fill(pl+2, pt+2, pl+3, pb-2, DC_BDR_INNER);
        g.fill(pr-3, pt+2, pr-2, pb-2, DC_BDR_INNER);
        // Pulsing edge glow
        float pulse = UITheme.pulse();
        int gA = (int)(pulse * 0x50 + 0x18);
        g.fill(pl+4, pt,   pr-4, pt+1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, gA));
        g.fill(pl+4, pb-1, pr-4, pb,   UITheme.withAlpha(UITheme.BORDER_BRIGHT, gA));
        // Corner brackets
        drawCornerBracket(g, pl+4,  pt+4,  false, false);
        drawCornerBracket(g, pr-14, pt+4,  true,  false);
        drawCornerBracket(g, pl+4,  pb-14, false, true);
        drawCornerBracket(g, pr-14, pb-14, true,  true);

        // ── Header (pt → pt+32) ───────────────────────────────────────────────
        g.fill(pl, pt, pr, pt+HEADER_H, DC_HEADER_BG);
        g.fill(pl, pt+HEADER_H, pr, pt+HEADER_H+1, DC_BDR_DIM);

        int hY = pt + (HEADER_H - 8) / 2;
        // Glyph + title — varies by mode
        int modeIdx = modeIndex();
        g.drawString(font, modeGlyph(), pl+14, hY, DC_GOLD, false);
        g.drawString(font, modeTitle(), pl+28, hY, DC_GOLD, false);
        // Mode pill (right side)
        drawModePill(g, pr, pt, modeIdx);

        // ── Tabs (pt+32 → pt+54) ─────────────────────────────────────────────
        int tabsY = pt + HEADER_H;
        g.fill(pl, tabsY, pr, tabsY+TABS_H, UITheme.withAlpha(DC_BDR_OUTER, 0x10));
        g.fill(pl, tabsY+TABS_H, pr, tabsY+TABS_H+1, DC_BDR_DIM);

        int tabW = tabItemW();
        for (int t = 0; t < 4; t++) {
            int tx   = pl + 10 + t * tabW;
            String lbl = TAB_NAMES[t] + " " + tabCount(t);
            int lw   = font.width(lbl);
            int tc   = (t == currentTab) ? DC_GOLD : UITheme.withAlpha(DC_TEXT_MUTED, 0xCC);
            g.drawString(font, lbl, tx + (tabW - lw) / 2, tabsY + (TABS_H - 8) / 2, tc, false);
            if (t == currentTab) {
                g.fill(tx+6, tabsY+TABS_H-1, tx+tabW-6, tabsY+TABS_H, DC_GOLD);
            }
        }

        // ── Search row (pt+54 → pt+92) ────────────────────────────────────────
        int srY = tabsY + TABS_H;
        int sbX1 = pl+10, sbY1 = srY+8, sbX2 = pl+PANEL_W-80, sbY2 = srY+30;
        g.fill(sbX1,   sbY1,   sbX2,   sbY2,   UITheme.withAlpha(0xFF000000, 0x66));
        g.fill(sbX1,   sbY1,   sbX2,   sbY1+1, DC_BDR_DIM);
        g.fill(sbX1,   sbY2-1, sbX2,   sbY2,   DC_BDR_DIM);
        g.fill(sbX1,   sbY1,   sbX1+1, sbY2,   DC_BDR_DIM);
        g.fill(sbX2-1, sbY1,   sbX2,   sbY2,   DC_BDR_DIM);
        g.drawString(font, "\u2315", sbX1+5, sbY1+(22-8)/2, UITheme.withAlpha(DC_TEXT_LAV, 0x99), false);

        // Sort chip
        int chipX1 = pl+PANEL_W-74, chipY1 = srY+8, chipX2 = chipX1+64, chipY2 = srY+30;
        g.fill(chipX1,   chipY1,   chipX2,   chipY2,   UITheme.withAlpha(DC_BDR_OUTER, 0x1E));
        g.fill(chipX1,   chipY1,   chipX2,   chipY1+1, DC_BDR_DIM);
        g.fill(chipX1,   chipY2-1, chipX2,   chipY2,   DC_BDR_DIM);
        g.fill(chipX1,   chipY1,   chipX1+1, chipY2,   DC_BDR_DIM);
        g.fill(chipX2-1, chipY1,   chipX2,   chipY2,   DC_BDR_DIM);
        String chip = SORT_GLYPHS[sortMode] + " " + SORT_LABELS[sortMode];
        g.drawString(font, chip, chipX1+(64-font.width(chip))/2, chipY1+(22-8)/2,
                UITheme.withAlpha(DC_TEXT_LAV, 0xCC), false);

        // ── Column headers (pt+92 → pt+108) ──────────────────────────────────
        int chY = srY + SEARCH_H;
        g.fill(pl, chY, pr, chY+COLHDR_H, UITheme.withAlpha(0xFF000000, 0x4C));
        g.fill(pl, chY+COLHDR_H-1, pr, chY+COLHDR_H, DC_BDR_DIM);
        int ctY = chY + (COLHDR_H - 8) / 2;
        int chC = UITheme.withAlpha(DC_TEXT_MUTED, 0xCC);
        g.drawString(font, "MIRROR \u00b7 OWNER", pl+CX_NAME, ctY, chC, false);
        g.drawString(font, "DIM",    pl+CX_DIM+(88-font.width("DIM"))/2,    ctY, chC, false);
        g.drawString(font, "SIG",    pl+CX_SIG+(60-font.width("SIG"))/2,    ctY, chC, false);
        g.drawString(font, "ACTION", pl+CX_ACT+(ACT_W-font.width("ACTION"))/2, ctY, chC, false);
        g.drawString(font, "\u2605", pl+CX_STAR+(STAR_W-font.width("\u2605"))/2, ctY, chC, false);

        // ── List rows ─────────────────────────────────────────────────────────
        int lt       = listTop();
        hoveredRow   = -1;
        int visCount = Math.min(MAX_ROWS, filtered.size() - scrollOffset);

        if (filtered.isEmpty()) {
            drawEmptyState(g, pl, lt, pr);
        } else {
            for (int i = 0; i < visCount; i++) {
                int idx = i + scrollOffset;
                if (idx >= filtered.size()) break;
                ClientboundMirrorListPacket.MirrorInfo info = filtered.get(idx);

                int ry  = lt + i * ROW_H;
                boolean hov  = mouseX >= pl && mouseX < pr-10 && mouseY >= ry && mouseY < ry+ROW_H;
                boolean sel  = (i == selectedRow);
                boolean dead = !info.inRange && info.signalStrength <= 0;
                if (hov) hoveredRow = i;

                // Row background
                if (sel) {
                    g.fill(pl,    ry, pr,    ry+ROW_H, DC_ROW_SEL);
                    // Inset 1px border
                    g.fill(pl,    ry,       pr,   ry+1,      DC_BDR_INNER);
                    g.fill(pl,    ry+ROW_H-1, pr, ry+ROW_H,  DC_BDR_INNER);
                    g.fill(pl,    ry,       pl+1, ry+ROW_H,  DC_BDR_INNER);
                    g.fill(pr-1,  ry,       pr,   ry+ROW_H,  DC_BDR_INNER);
                    // 2px gold left tab
                    g.fill(pl, ry, pl+2, ry+ROW_H, UITheme.withAlpha(DC_GOLD, 0xCC));
                } else if (hov) {
                    g.fill(pl, ry, pr, ry+ROW_H, DC_ROW_HOVER);
                    g.fill(pl, ry, pl+1, ry+ROW_H, DC_BDR_INNER);
                } else if (i % 2 == 1) {
                    g.fill(pl, ry, pr, ry+ROW_H, DC_ROW_ZEBRA);
                }

                int rowA = dead ? 0x8C : 0xFF;
                int ty   = ry + (ROW_H - 8) / 2;

                // Col 0: index (right-aligned)
                String idxStr = String.format("%02d", idx+1);
                g.drawString(font, idxStr,
                        pl+CX_IDX+(18-font.width(idxStr)), ty,
                        UITheme.withAlpha(DC_TEXT_MUTED, (int)(rowA * 0.65)), false);

                // Col 1: Icon + Name · ◆ Owner (single line)
                // Draw mirror icon if non-empty
                boolean iconDrawn = false;
                if (info.iconPixels != null) {
                    boolean hasAnyPixel = false;
                    for (byte b : info.iconPixels) if (b != 0) { hasAnyPixel = true; break; }
                    if (hasAnyPixel) {
                        drawMirrorIcon(g, pl + CX_NAME + 2, ry + (ROW_H - 16) / 2, info.iconPixels, 1);
                        iconDrawn = true;
                    }
                }
                int nameOffsetX = iconDrawn ? 20 : 2;
                String name    = mirrorDisplayName(info);
                String nameStr = info.folderName.isEmpty() ? name : "["+info.folderName+"] "+name;
                int nameColor  = dead ? UITheme.withAlpha(DC_TEXT_LAV, 0x8C)
                               : sel  ? UITheme.withAlpha(0xFFF0E2FF, 0xFF)
                               : info.isFavorite ? DC_GOLD
                               : DC_TEXT_LAV;
                // Compute available width for name vs owner
                String ownerPart = " \u00b7 \u25c6" + info.ownerName;
                int ownerW = font.width(ownerPart);
                int nameAvail = 166 - nameOffsetX - ownerW;
                String nameDisplay = nameAvail > 20 ? truncate(nameStr, nameAvail)
                                                    : truncate(nameStr, 80);
                g.drawString(font, nameDisplay, pl+CX_NAME+nameOffsetX, ty, nameColor, false);
                int ownerX = pl+CX_NAME+nameOffsetX+font.width(nameDisplay);
                // " · " separator in muted
                g.drawString(font, " \u00b7 ", ownerX, ty, UITheme.withAlpha(DC_TEXT_MUTED, 0x80), false);
                ownerX += font.width(" \u00b7 ");
                // ◆ in gold
                g.drawString(font, "\u25c6", ownerX, ty, UITheme.withAlpha(DC_GOLD, rowA), false);
                ownerX += font.width("\u25c6")+1;
                int ownerColor = info.isOwn ? UITheme.withAlpha(DC_GOLD, rowA)
                                            : UITheme.withAlpha(DC_TEXT_MUTED, (int)(rowA * 0.8));
                g.drawString(font, truncate(info.ownerName, pl+CX_DIM-ownerX-4),
                        ownerX, ty, ownerColor, false);

                // Col 2: dimension badge
                drawDesignDimBadge(g, pl+CX_DIM, ry+(ROW_H-11)/2, info.dimensionName, 88, rowA);

                // Col 3: signal bars or dead ✕
                int sigCX = pl+CX_SIG+30;
                if (!info.inRange || info.signalStrength <= 0) {
                    String x = "\u2715";
                    g.drawString(font, x, sigCX-font.width(x)/2, ty,
                            UITheme.withAlpha(DC_SIG_DEAD, rowA), false);
                } else {
                    drawDesignSignalBars(g, sigCX, ry+ROW_H-3, info.signalStrength);
                }
            }
        }

        // Scroll gutter visual (only when list overflows)
        if (filtered.size() > MAX_ROWS) {
            int gx = pr-10, gy = lt;
            int gh = MAX_ROWS * ROW_H;
            int trackY1 = gy+12, trackY2 = gy+gh-12;
            int trackH = trackY2 - trackY1;
            // Track
            g.fill(gx+1, trackY1, gx+5, trackY2, UITheme.withAlpha(0xFF000000, 0x66));
            g.fill(gx+1, trackY1, gx+5, trackY1+1, DC_BDR_DIM);
            g.fill(gx+1, trackY2-1, gx+5, trackY2, DC_BDR_DIM);
            // Thumb
            int totalItems = filtered.size();
            int thumbH  = Math.max(12, trackH * MAX_ROWS / totalItems);
            int maxScroll = totalItems - MAX_ROWS;
            int thumbY  = trackY1 + (maxScroll > 0 ? (trackH - thumbH) * scrollOffset / maxScroll : 0);
            g.fill(gx, thumbY, gx+4, thumbY+thumbH, DC_BDR_INNER);
        }

        // ── Inspector strip ───────────────────────────────────────────────────
        int inspY = inspectorTop();
        g.fill(pl, inspY,   pr, inspY+1,          DC_BDR_DIM);
        g.fill(pl, inspY+1, pr, inspY+INSPECTOR_H, UITheme.withAlpha(0xFF000000, 0x72));

        drawInspector(g, pl, inspY, pr);

        // ── Footer ────────────────────────────────────────────────────────────
        int fy = footerTop();
        g.fill(pl, fy,   pr, fy+1,         DC_BDR_DIM);
        g.fill(pl, fy+1, pr, fy+FOOTER_H,  UITheme.withAlpha(0xFF000000, 0x4C));

        String footerLeft = footerMeta();
        g.drawString(font, footerLeft, pl+12, fy+(FOOTER_H-8)/2,
                UITheme.withAlpha(DC_TEXT_MUTED, 0xCC), false);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Mode helpers ──────────────────────────────────────────────────────────

    /** 0=teleport 1=call 2=pocket 3=warp */
    private int modeIndex() {
        if (warpTargetMode)              return 3;
        if ("calling".equals(sourceMirrorType)) return 1;
        if ("pocket".equals(sourceMirrorType))  return 2;
        return 0;
    }

    private String modeGlyph() {
        return switch (modeIndex()) {
            case 2  -> "\u25c8";  // ◈
            case 3  -> "\u2316";  // ⌖
            default -> "\u2736";  // ✶
        };
    }

    private String modeTitle() {
        return switch (modeIndex()) {
            case 1  -> "PLACE A CALL";
            case 2  -> "POCKET DIMENSIONS";
            case 3  -> "BIND WARP TARGET";
            default -> "MIRROR NETWORK";
        };
    }

    private String modePillLabel() {
        return switch (modeIndex()) {
            case 1  -> "CALL";
            case 2  -> "OPEN";
            case 3  -> "BIND";
            default -> "TELEPORT";
        };
    }

    private void drawModePill(GuiGraphics g, int pr, int pt, int modeIdx) {
        String label = modePillLabel();
        int pillW = font.width(label) + 16;
        int pillH = 18;
        int px = pr - pillW - 12;
        int py = pt + (HEADER_H - pillH) / 2;

        g.fill(px,     py,     px+pillW, py+pillH, MODE_BG[modeIdx]);
        g.fill(px,     py,     px+pillW, py+1,     MODE_BD[modeIdx]);
        g.fill(px,     py+pillH-1, px+pillW, py+pillH, MODE_BD[modeIdx]);
        g.fill(px,     py,     px+1,     py+pillH, MODE_BD[modeIdx]);
        g.fill(px+pillW-1, py, px+pillW, py+pillH, MODE_BD[modeIdx]);

        // Pulsing dot (4×4 px)
        float pulse = UITheme.pulse();
        int dotA = (int)(pulse * 0xCC + 0x33);
        g.fill(px+5, py+(pillH-4)/2, px+9, py+(pillH-4)/2+4,
                UITheme.withAlpha(MODE_T[modeIdx], dotA));
        g.drawString(font, label, px+12, py+(pillH-8)/2, MODE_T[modeIdx], false);
    }

    private String footerMeta() {
        if (selectedMirrorId != null && selectedRow >= 0 && selectedRow < filtered.size()) {
            String sn = mirrorDisplayName(filtered.get(selectedRow));
            return filtered.size() + " / " + mirrors.size() + "  \u00b7  " + sn.toUpperCase();
        }
        return switch (modeIndex()) {
            case 1  -> filtered.size() + " / " + mirrors.size() + "  \u00b7  POCKET DIMS HIDDEN";
            case 3  -> filtered.size() + " / " + mirrors.size() + "  \u00b7  WARP SLOT EMPTY";
            default -> filtered.size() + " / " + mirrors.size();
        };
    }

    // ── Inspector strip ───────────────────────────────────────────────────────

    private void drawInspector(GuiGraphics g, int pl, int inspY, int pr) {
        int iy = inspY + (INSPECTOR_H - 8) / 2 - 3; // top of 2-line area

        if (selectedMirrorId == null || selectedRow < 0 || selectedRow >= filtered.size()) {
            // Empty hint
            String hint = switch (modeIndex()) {
                case 3  -> "\u203a Bind a mirror to fast-warp here";
                case 2  -> "\u203a Select a pocket to inspect";
                default -> "\u203a Select a mirror to inspect";
            };
            g.drawString(font, hint, pl+12, inspY+(INSPECTOR_H-8)/2,
                    UITheme.withAlpha(DC_TEXT_MUTED, 0x99), false);
            return;
        }

        ClientboundMirrorListPacket.MirrorInfo sel = filtered.get(selectedRow);
        int x = pl+12;

        // Mirror name in gold (truncated)
        String selName = truncate(mirrorDisplayName(sel).toUpperCase(), 110);
        g.drawString(font, selName, x, inspY+(INSPECTOR_H-8)/2, DC_GOLD, false);
        x += font.width(selName) + 8;

        // Divider
        g.fill(x, inspY+7, x+1, inspY+INSPECTOR_H-7, DC_BDR_DIM);
        x += 8;

        // COORDS
        if (sel.pos != null) {
            g.drawString(font, "COORDS", x, iy,
                    UITheme.withAlpha(DC_TEXT_MUTED, 0x99), false);
            String coords = sel.pos.getX()+", "+sel.pos.getY()+", "+sel.pos.getZ();
            g.drawString(font, coords, x, iy+11, DC_TEXT_LAV, false);
            x += Math.max(font.width("COORDS"), font.width(coords)) + 8;
            g.fill(x, inspY+7, x+1, inspY+INSPECTOR_H-7, DC_BDR_DIM);
            x += 8;
        }

        // TIER
        if (sel.tierName != null && !sel.tierName.isEmpty()) {
            g.drawString(font, "TIER", x, iy,
                    UITheme.withAlpha(DC_TEXT_MUTED, 0x99), false);
            g.drawString(font, sel.tierName.toUpperCase(), x, iy+11, DC_GOLD, false);
            x += Math.max(font.width("TIER"), font.width(sel.tierName)) + 8;
            // Extra stat in pocket mode
            if ("pocket".equals(sourceMirrorType) && x < pr - 40) {
                g.fill(x, inspY+7, x+1, inspY+INSPECTOR_H-7, DC_BDR_DIM);
                x += 8;
                g.drawString(font, "TYPE", x, iy,
                        UITheme.withAlpha(DC_TEXT_MUTED, 0x99), false);
                g.drawString(font, "POCKET", x, iy+11, DC_TEXT_LAV, false);
            }
        }
    }

    // ── Per-tab empty state ───────────────────────────────────────────────────

    private void drawEmptyState(GuiGraphics g, int pl, int lt, int pr) {
        int cx = pl + PANEL_W / 2;
        int cy = lt + MAX_ROWS * ROW_H / 2;
        boolean hasAny = !mirrors.isEmpty();

        String glyph, title, sub;
        if (hasAny) {
            // Mirrors exist but nothing matches search / tab
            glyph = switch (currentTab) {
                case 1  -> "\u2606";        // ☆
                case 3  -> "\u25d0";        // ◐
                case 2  -> "\u231b";        // ⌛
                default -> "\u2315";        // ⌕
            };
            title = switch (currentTab) {
                case 1  -> "NO FAVORITES YET";
                case 3  -> "NO MIRRORS IN RANGE";
                case 2  -> "NO RECENT ACTIVITY";
                default -> "NO MATCHES";
            };
            sub = switch (currentTab) {
                case 1  -> "TAP THE \u2605 BESIDE ANY MIRROR TO SAVE IT HERE";
                case 3  -> "MOVE CLOSER OR BOOST SIGNAL WITH AN AETHER CRYSTAL";
                case 2  -> "MIRRORS YOU'VE RECENTLY VISITED WILL APPEAR HERE";
                default -> "TRY A DIFFERENT SEARCH TERM";
            };
        } else if ("calling".equals(sourceMirrorType)) {
            glyph = "\u2315";
            title = "NO CALLING MIRRORS FOUND";
            sub   = "OTHER PLAYERS NEED CALLING MIRRORS";
        } else {
            glyph = "\u2736";
            title = "NO MIRRORS IN YOUR NETWORK";
            sub   = "PLACE MIRRORS AND GRANT PERMISSIONS";
        }

        g.drawCenteredString(font, glyph, cx, cy-16, UITheme.withAlpha(DC_GOLD, 0x80));
        g.drawCenteredString(font, title, cx, cy-2,  DC_TEXT_LAV);
        g.drawCenteredString(font, sub,   cx, cy+10, UITheme.withAlpha(DC_TEXT_MUTED, 0xAA));
    }

    // ── Mouse input ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int pl = panelLeft(), pt = panelTop();

        // Tab clicks
        int tabsY = pt + HEADER_H;
        if (my >= tabsY && my < tabsY+TABS_H && mx >= pl+10 && mx < pl+PANEL_W-10) {
            int clicked = (int)(mx - (pl+10)) / tabItemW();
            if (clicked >= 0 && clicked < 4 && clicked != currentTab) {
                currentTab = clicked;
                applyFilter(searchBox != null ? searchBox.getValue() : "");
                rebuildButtons();
                return true;
            }
        }

        // Sort chip clicks
        int srY     = tabsY + TABS_H;
        int chipX1  = pl+PANEL_W-74;
        if (mx >= chipX1 && mx < chipX1+64 && my >= srY+8 && my < srY+30) {
            sortMode = (sortMode + 1) % 3;
            applyFilter(searchBox != null ? searchBox.getValue() : "");
            rebuildButtons();
            return true;
        }

        // Row selection (click to select; if already selected, action button handles confirm)
        int lt       = listTop();
        int visCount = Math.min(MAX_ROWS, filtered.size() - scrollOffset);
        for (int i = 0; i < visCount; i++) {
            int ry = lt + i * ROW_H;
            if (my >= ry && my < ry+ROW_H && mx >= pl && mx < pl+PANEL_W-10) {
                int idx = i + scrollOffset;
                if (idx < filtered.size()) {
                    UUID clickedId = filtered.get(idx).mirrorId;
                    selectedMirrorId = clickedId;
                    selectedRow      = i;
                    // Don't consume — let super() fire the button widget if the click lands on it
                }
                break;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0)                               { scrollOffset--; rebuildButtons(); }
        else if (delta < 0 && scrollOffset+MAX_ROWS < filtered.size())  { scrollOffset++; rebuildButtons(); }
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

    private void drawMirrorIcon(GuiGraphics g, int x, int y, byte[] pixels, int scale) {
        if (pixels == null || pixels.length < 256) return;
        for (int py = 0; py < 16; py++) {
            for (int px = 0; px < 16; px++) {
                int col = MirrorPlacementScreen.ICON_PALETTE[pixels[py * 16 + px] & 0xFF];
                if ((col >>> 24) == 0) continue; // transparent: skip
                g.fill(x + px * scale, y + py * scale, x + (px + 1) * scale, y + (py + 1) * scale, col);
            }
        }
    }

    private void drawCornerBracket(GuiGraphics g, int x, int y, boolean flipH, boolean flipV) {
        int sz = 10, th = 1;
        int col = UITheme.withAlpha(DC_GOLD, 0xB3);
        if (!flipH && !flipV) {
            g.fill(x,      y,      x+sz,    y+th,  col);
            g.fill(x,      y,      x+th,    y+sz,  col);
        } else if (flipH && !flipV) {
            g.fill(x,      y,      x+sz,    y+th,  col);
            g.fill(x+sz-th,y,      x+sz,    y+sz,  col);
        } else if (!flipH) {
            g.fill(x,      y+sz-th,x+sz,    y+sz,  col);
            g.fill(x,      y,      x+th,    y+sz,  col);
        } else {
            g.fill(x,      y+sz-th,x+sz,    y+sz,  col);
            g.fill(x+sz-th,y,      x+sz,    y+sz,  col);
        }
    }

    private void drawDesignDimBadge(GuiGraphics g, int x, int y,
                                     String dim, int maxW, int rowAlpha) {
        String label;
        int bgC, bdC, txC;
        if (dim == null || dim.isEmpty()) {
            label = "?";         bgC = DC_XX_BG; bdC = DC_XX_BD; txC = DC_XX_T;
        } else if (dim.contains("overworld")) {
            label = "OVERWORLD"; bgC = DC_OW_BG; bdC = DC_OW_BD; txC = DC_OW_T;
        } else if (dim.contains("nether")) {
            label = "NETHER";    bgC = DC_NE_BG; bdC = DC_NE_BD; txC = DC_NE_T;
        } else if (dim.contains("the_end")) {
            label = "THE END";   bgC = DC_EN_BG; bdC = DC_EN_BD; txC = DC_EN_T;
        } else if (dim.contains("pocket")) {
            label = "POCKET";    bgC = DC_PK_BG; bdC = DC_PK_BD; txC = DC_PK_T;
        } else {
            String path = dim.contains(":") ? dim.substring(dim.indexOf(':')+1) : dim;
            label = path.replace("_"," ").toUpperCase();
            if (label.length() > 10) label = label.substring(0, 10);
            bgC = DC_XX_BG; bdC = DC_XX_BD; txC = DC_XX_T;
        }
        int tw = font.width(label);
        int bw = Math.min(maxW-4, tw+10);
        int bx = x + (maxW - bw) / 2;
        int bh = 11;
        g.fill(bx,      y,      bx+bw,  y+bh,   applyAlpha(bgC, rowAlpha));
        g.fill(bx,      y,      bx+bw,  y+1,    applyAlpha(bdC, rowAlpha));
        g.fill(bx,      y+bh-1, bx+bw,  y+bh,   applyAlpha(bdC, rowAlpha));
        g.fill(bx,      y,      bx+1,   y+bh,   applyAlpha(bdC, rowAlpha));
        g.fill(bx+bw-1, y,      bx+bw,  y+bh,   applyAlpha(bdC, rowAlpha));
        g.drawString(font, label, bx+(bw-tw)/2, y+2, applyAlpha(txC, rowAlpha), false);
    }

    private void drawDesignSignalBars(GuiGraphics g, int centerX, int bottomY, double signal) {
        final int[] BH = {3, 5, 7, 9, 11};
        int barW = 2, gap = 1, bars = 5;
        int startX = centerX - (bars*barW + (bars-1)*gap) / 2;
        int filled = Math.max(0, Math.min(5, (int)(signal * 5 + 0.001)));
        for (int i = 0; i < bars; i++) {
            int bx = startX + i*(barW+gap);
            g.fill(bx, bottomY-BH[i], bx+barW, bottomY, i < filled ? DC_SIG_FULL : DC_SIG_DIM);
        }
    }

    private static int applyAlpha(int color, int alpha) {
        if (alpha >= 0xFF) return color;
        return (color & 0x00FFFFFF) | ((((color >>> 24) & 0xFF) * alpha / 0xFF) << 24);
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
            sb.append(c); w += cw;
        }
        return sb + ellipsis;
    }
}
