package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundPermissionRequestPacket;
import com.ether.mirrors.network.packets.ServerboundPermissionResponsePacket;
import com.ether.mirrors.network.packets.ServerboundSetPermissionLevelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PermissionScreen extends Screen {

    // ── Entry types (backward-compatible constructors preserved) ──────────────

    public static class GrantedEntry {
        public final UUID   playerUUID;
        public final String playerName;
        public final boolean hasUse;
        public final boolean hasViewCamera;
        public final boolean hasBreak;
        /** 0=NONE 1=VIEW 2=CALL 3=ENTER — derived from legacy flags on init, mutable via ladder pills */
        public int permLevel;

        public GrantedEntry(UUID playerUUID, String playerName,
                            boolean hasUse, boolean hasViewCamera, boolean hasBreak) {
            this.playerUUID    = playerUUID;
            this.playerName    = playerName;
            this.hasUse        = hasUse;
            this.hasViewCamera = hasViewCamera;
            this.hasBreak      = hasBreak;
            // Map old three-flag model → tier: ENTER if break, CALL if use, VIEW if viewCamera, else NONE
            if (hasBreak)           this.permLevel = 3;
            else if (hasUse)        this.permLevel = 2;
            else if (hasViewCamera) this.permLevel = 1;
            else                    this.permLevel = 0;
        }
    }

    public static class RequestEntry {
        public final UUID   requesterUUID;
        public final String requesterName;

        public RequestEntry(UUID requesterUUID, String requesterName) {
            this.requesterUUID = requesterUUID;
            this.requesterName = requesterName;
        }
    }

    // ── Tab indices ───────────────────────────────────────────────────────────

    private static final int TAB_NETWORK  = 0;
    private static final int TAB_PLAYERS  = 1;
    private static final int TAB_REQUESTS = 2;

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int PANEL_W  = 460;
    private static final int HEADER_H = 32;
    private static final int TABS_H   = 22;
    private static final int SEARCH_H = 38;   // 8 top pad + 22 content + 8 bottom pad
    private static final int EXPAND_H = 90;
    private static final int COL_HDR_H = 16;
    private static final int ROW_H    = 22;
    private static final int REQ_ROW_H = 30;
    private static final int FOOTER_H = 32;
    private static final int MAX_ROWS_NP  = 7;  // My Network / Players
    private static final int MAX_ROWS_REQ = 5;  // Requests

    // Panel heights
    private static final int PANEL_H_NP  = HEADER_H + TABS_H + SEARCH_H + COL_HDR_H + MAX_ROWS_NP * ROW_H + FOOTER_H;   // 294
    private static final int PANEL_H_REQ = HEADER_H + TABS_H + SEARCH_H + COL_HDR_H + MAX_ROWS_REQ * REQ_ROW_H + FOOTER_H; // 290
    private static final int PANEL_H_EXP = HEADER_H + TABS_H + SEARCH_H + EXPAND_H + COL_HDR_H + 4 * ROW_H + FOOTER_H;  // 318

    // ── Column x positions (from panel left pl) ───────────────────────────────
    // Content width = 460 - 10(left) - 14(right) = 436px
    private static final int CX_IDX  = 10;  // offset from pl — 18px wide
    private static final int CX_FACE = 34;  // face draw at pl+35
    private static final int CX_NAME = 58;  // 158px wide
    // Ladder pills (right-justified starting at pl+229, 4 pills × 46px + 3 gaps × 3px = 193px)
    private static final int CX_NONE  = 229;
    private static final int CX_VIEW  = 278;
    private static final int CX_CALL  = 327;
    private static final int CX_ENTER = 376;
    private static final int PILL_W   = 46;
    private static final int CX_X    = 428;  // × button

    // Request tab columns (from pl)
    private static final int CRQ_FACE    = 10;
    private static final int CRQ_TEXT    = 36;
    private static final int CRQ_ACTIONS = 326;
    private static final int CRQ_DENY_W  = 50;
    private static final int CRQ_APPR_W  = 66;

    // ── Design colours ────────────────────────────────────────────────────────

    private static final int DC_PANEL      = 0xFF030012;
    private static final int DC_HEADER_BG  = 0xFF020020;
    private static final int DC_BDR_OUTER  = 0xFF7733CC;
    private static final int DC_BDR_INNER  = 0x8CAA55FF;
    private static final int DC_BDR_DIM    = 0xFF2A1144;
    private static final int DC_TEXT_MUTED = 0xFF9999AA;
    private static final int DC_TEXT_LAV   = 0xFFAA88FF;
    private static final int DC_GOLD       = 0xFFD4AF37;
    private static final int DC_GOLD_DIM   = 0xFF9A7520;
    private static final int DC_ROW_ZEBRA  = 0x0AAAAAFF;
    private static final int DC_ROW_HOVER  = 0x25AA66EE;

    // Tier active colours [bg, border, text]
    private static final int[][] TIER_ACTIVE = {
        { 0xFF8B1A1A, 0xFFC73838, 0xFFFFFFFF },  // 0 NONE  — red
        { 0xFF7733CC, 0xFFAA55FF, 0xFFFFFFFF },  // 1 VIEW  — lavender
        { 0xFF2A9A9A, 0xFF6FE0E0, 0xFFFFFFFF },  // 2 CALL  — teal
        { 0xFFD4AF37, 0xFFFFF8C0, 0xFF1A0A00 },  // 3 ENTER — gold (dark text)
    };

    // Tier inactive hint colours [text, border]
    private static final int[][] TIER_HINT = {
        { 0x66FF8090, 0x4D8B1A1A },  // NONE
        { 0x66AA88FF, 0x4D7733CC },  // VIEW
        { 0x666FE0E0, 0x661A6B6B },  // CALL
        { 0x73D4AF37, 0x669A7520 },  // ENTER
    };

    private static final String[] TIER_LABELS = { "NONE", "VIEW", "CALL", "ENTER" };

    // Privacy chip cycles: 0=LOCKED 1=VIEW 2=CALL 3=ENTER
    private static final String[] CHIP_GLYPHS  = { "\u25A0", "\u25D1", "\u25CE", "\u25C9" };
    private static final String[] CHIP_LABELS  = { "DEFAULT: LOCKED", "DEFAULT: VIEW", "DEFAULT: CALL", "DEFAULT: ENTER" };
    private static final int[] CHIP_TEXT   = { 0xFFFF8090, 0xFFAA88FF, 0xFF6FE0E0, 0xFFD4AF37 };
    private static final int[] CHIP_BORDER = { 0x808B1A1A, 0x807733CC, 0x801A6B6B, 0x809A7520 };
    private static final int[] CHIP_BG     = { 0x2D8B1A1A, 0x1E7733CC, 0x381A6B6B, 0x2D9A7520 };

    // Skin base / hair / eye palette choices
    private static final int[] SKIN_BASE  = { 0xFFE8C9A8, 0xFFC9925E, 0xFF7A5234, 0xFFB27A4A };
    private static final int[] HAIR_COLOR = { 0xFF2A1810, 0xFF8B2A1A, 0xFF4A2A18, 0xFF1A1A1A };
    private static final int[] EYE_COLOR  = { 0xFF2E5B8A, 0xFF2E8A4B, 0xFF4A2A18 };

    // ── State ─────────────────────────────────────────────────────────────────

    private List<GrantedEntry>  networkEntries  = new ArrayList<>();
    private List<GrantedEntry>  playerEntries   = new ArrayList<>(); // online players not yet in network
    private List<RequestEntry>  requestEntries  = new ArrayList<>();

    private int currentTab      = TAB_NETWORK;
    private int networkScroll   = 0;
    private int playerScroll    = 0;
    private int requestScroll   = 0;
    private int defaultLevel    = 0; // privacy chip state
    private boolean addExpanded = false;

    private EditBox searchBox;
    private EditBox addNameBox;
    private String  searchFilter = "";

    // ── Constructor ───────────────────────────────────────────────────────────

    public PermissionScreen() {
        super(Component.literal("Permissions"));
    }

    // ── Data setters (called by packet handler) ───────────────────────────────

    public void setGrantedEntries(List<GrantedEntry> entries) { this.networkEntries = new ArrayList<>(entries); }
    public void setRequestEntries(List<RequestEntry> entries) { this.requestEntries = new ArrayList<>(entries); }

    // ── Panel geometry ────────────────────────────────────────────────────────

    private int panelHeight() {
        if (currentTab == TAB_REQUESTS) return PANEL_H_REQ;
        if (currentTab == TAB_PLAYERS && addExpanded) return PANEL_H_EXP;
        return PANEL_H_NP;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }
    private int panelTop()  { return Math.max(10, (this.height - panelHeight()) / 2); }

    // ── Init / rebuild ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        rebuildUI();
    }

    private void rebuildUI() {
        clearWidgets();
        int pl = panelLeft();
        int pt = panelTop();
        int ph = panelHeight();

        // Search box (always present)
        int searchY = pt + HEADER_H + TABS_H + 8;
        searchBox = new EditBox(this.font, pl + 10, searchY, 230, 22,
                Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search players\u2026"));
        searchBox.setBordered(false);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(val -> { searchFilter = val; });
        addRenderableWidget(searchBox);

        // Add-name box (Players tab, expanded)
        if (currentTab == TAB_PLAYERS && addExpanded) {
            int expTop = pt + HEADER_H + TABS_H + SEARCH_H;
            addNameBox = new EditBox(this.font, pl + 10, expTop + 28, 200, 22,
                    Component.literal("Username"));
            addNameBox.setMaxLength(16);
            addNameBox.setHint(Component.literal("Username\u2026"));
            addNameBox.setBordered(false);
            addRenderableWidget(addNameBox);
        }

        buildListButtons(pl, pt, ph);
        buildFooterButtons(pl, pt, ph);
    }

    /** Build approve/deny/revoke/ladder buttons for the active tab's visible rows. */
    private void buildListButtons(int pl, int pt, int ph) {
        int listTop = listTop(pt);

        if (currentTab == TAB_NETWORK) {
            List<GrantedEntry> visible = filteredNetwork();
            int end = Math.min(visible.size(), networkScroll + MAX_ROWS_NP);
            for (int i = networkScroll; i < end; i++) {
                GrantedEntry e = visible.get(i);
                int row = i - networkScroll;
                int ry  = listTop + row * ROW_H;
                buildLadderPills(pl, ry, e);
                buildRemoveButton(pl, ry, e, visible);
            }
        } else if (currentTab == TAB_PLAYERS) {
            List<GrantedEntry> visible = filteredPlayers();
            int maxRows = addExpanded ? 4 : MAX_ROWS_NP;
            int end = Math.min(visible.size(), playerScroll + maxRows);
            for (int i = playerScroll; i < end; i++) {
                GrantedEntry e = visible.get(i);
                int row = i - playerScroll;
                int ry  = listTop + row * ROW_H;
                buildLadderPills(pl, ry, e);
                buildRemoveButton(pl, ry, e, visible);
            }
            // ADD button (expansion)
            if (addExpanded) {
                int expTop = pt + HEADER_H + TABS_H + SEARCH_H;
                addRenderableWidget(MirrorButton.gold(pl + 216, expTop + 28, 54, 22,
                        Component.literal("ADD"), b -> {
                            if (addNameBox != null) {
                                String name = addNameBox.getValue().trim();
                                if (!name.isEmpty() && name.matches("[a-zA-Z0-9_]{3,16}")) {
                                    MirrorsNetwork.sendToServer(new ServerboundPermissionRequestPacket(name));
                                    addNameBox.setValue("");
                                }
                            }
                        }));
            }
        } else {
            // Requests tab
            List<RequestEntry> visible = filteredRequests();
            int end = Math.min(visible.size(), requestScroll + MAX_ROWS_REQ);
            for (int i = requestScroll; i < end; i++) {
                RequestEntry e = visible.get(i);
                int row = i - requestScroll;
                int ry  = listTop + row * REQ_ROW_H;
                int ay  = ry + (REQ_ROW_H - 18) / 2;
                final RequestEntry captured = e;
                addRenderableWidget(MirrorButton.red(pl + CRQ_ACTIONS, ay, CRQ_DENY_W, 18,
                        Component.literal("DENY"), b -> {
                            MirrorsNetwork.sendToServer(
                                    new ServerboundPermissionResponsePacket(captured.requesterUUID, false, 0));
                            requestEntries.remove(captured);
                            requestScroll = Math.max(0, Math.min(requestScroll, Math.max(0, requestEntries.size() - MAX_ROWS_REQ)));
                            rebuildUI();
                        }));
                addRenderableWidget(MirrorButton.teal(pl + CRQ_ACTIONS + CRQ_DENY_W + 4, ay, CRQ_APPR_W, 18,
                        Component.literal("APPROVE"), b -> {
                            MirrorsNetwork.sendToServer(new ServerboundPermissionResponsePacket(
                                    captured.requesterUUID, true,
                                    ServerboundPermissionResponsePacket.FLAG_USE
                                            | ServerboundPermissionResponsePacket.FLAG_VIEW_CAMERA));
                            requestEntries.remove(captured);
                            requestScroll = Math.max(0, Math.min(requestScroll, Math.max(0, requestEntries.size() - MAX_ROWS_REQ)));
                            rebuildUI();
                        }));
            }
        }
    }

    private void buildLadderPills(int pl, int ry, GrantedEntry e) {
        int[] pillXs = { pl + CX_NONE, pl + CX_VIEW, pl + CX_CALL, pl + CX_ENTER };
        for (int tier = 0; tier < 4; tier++) {
            final int t = tier;
            final GrantedEntry captured = e;
            boolean active = (e.permLevel == tier);
            int bdrColor = active ? TIER_ACTIVE[tier][1] : TIER_HINT[tier][1];
            int textColor = active ? TIER_ACTIVE[tier][2] : TIER_HINT[tier][0];
            addRenderableWidget(MirrorButton.of(
                    pillXs[tier], ry + 2, PILL_W, 18,
                    Component.literal(TIER_LABELS[tier]),
                    b -> {
                        captured.permLevel = t;
                        MirrorsNetwork.sendToServer(
                                new ServerboundSetPermissionLevelPacket(captured.playerUUID, t));
                        rebuildUI();
                    },
                    bdrColor, textColor));
        }
    }

    private void buildRemoveButton(int pl, int ry, GrantedEntry e, List<GrantedEntry> list) {
        final GrantedEntry captured = e;
        addRenderableWidget(MirrorButton.red(pl + CX_X, ry + 2, 18, 18,
                Component.literal("\u00d7"), b -> {
                    MirrorsNetwork.sendToServer(
                            ServerboundPermissionResponsePacket.revoke(captured.playerUUID));
                    list.remove(captured);
                    networkScroll  = Math.max(0, Math.min(networkScroll, Math.max(0, networkEntries.size() - MAX_ROWS_NP)));
                    playerScroll   = Math.max(0, Math.min(playerScroll,  Math.max(0, playerEntries.size()  - MAX_ROWS_NP)));
                    rebuildUI();
                }));
    }

    private void buildFooterButtons(int pl, int pt, int ph) {
        int footerY = pt + ph - FOOTER_H;
        int btnY    = footerY + (FOOTER_H - 22) / 2;

        // RESET (ghost / muted)
        addRenderableWidget(MirrorButton.of(
                pl + PANEL_W - 10 - 80 - 6 - 60, btnY, 60, 22,
                Component.literal("RESET"),
                b -> onClose(),
                DC_BDR_DIM, DC_TEXT_MUTED));

        // DONE (gold)
        addRenderableWidget(MirrorButton.gold(
                pl + PANEL_W - 10 - 80, btnY, 80, 22,
                Component.literal("DONE"),
                b -> onClose()));

        // Requests: extra "DENY ALL" ghost button
        if (currentTab == TAB_REQUESTS && !requestEntries.isEmpty()) {
            addRenderableWidget(MirrorButton.of(
                    pl + 10, btnY, 80, 22,
                    Component.literal("DENY ALL"),
                    b -> {
                        for (RequestEntry re : new ArrayList<>(requestEntries)) {
                            MirrorsNetwork.sendToServer(
                                    new ServerboundPermissionResponsePacket(re.requesterUUID, false, 0));
                        }
                        requestEntries.clear();
                        requestScroll = 0;
                        rebuildUI();
                    },
                    DC_BDR_DIM, DC_TEXT_MUTED));
        }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** Y coordinate of the first data row in the current tab. */
    private int listTop(int pt) {
        int base = pt + HEADER_H + TABS_H + SEARCH_H + COL_HDR_H;
        if (currentTab == TAB_PLAYERS && addExpanded) base += EXPAND_H;
        return base;
    }

    /** X of the privacy chip right edge reference (right-aligned in search row). */
    private int chipRight(int pl) { return pl + PANEL_W - 10; }

    // ── Filtered lists ────────────────────────────────────────────────────────

    private List<GrantedEntry> filteredNetwork() {
        if (searchFilter.isEmpty()) return networkEntries;
        String lc = searchFilter.toLowerCase();
        List<GrantedEntry> out = new ArrayList<>();
        for (GrantedEntry e : networkEntries)
            if (e.playerName.toLowerCase().contains(lc)) out.add(e);
        return out;
    }

    private List<GrantedEntry> filteredPlayers() {
        if (searchFilter.isEmpty()) return playerEntries;
        String lc = searchFilter.toLowerCase();
        List<GrantedEntry> out = new ArrayList<>();
        for (GrantedEntry e : playerEntries)
            if (e.playerName.toLowerCase().contains(lc)) out.add(e);
        return out;
    }

    private List<RequestEntry> filteredRequests() {
        if (searchFilter.isEmpty()) return requestEntries;
        String lc = searchFilter.toLowerCase();
        List<RequestEntry> out = new ArrayList<>();
        for (RequestEntry e : requestEntries)
            if (e.requesterName.toLowerCase().contains(lc)) out.add(e);
        return out;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Dim backdrop
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft();
        int pt = panelTop();
        int ph = panelHeight();
        int pr = pl + PANEL_W;
        int pb = pt + ph;

        drawPanelChrome(g, pl, pt, pr, pb);
        drawHeader(g, pl, pt, pr);
        drawTabStrip(g, pl, pt, pr, mouseX, mouseY);
        drawSearchRow(g, pl, pt, mouseX, mouseY);

        if (currentTab == TAB_PLAYERS && addExpanded) {
            drawAddExpansion(g, pl, pt, mouseX, mouseY);
        }

        if (currentTab != TAB_REQUESTS) {
            drawNetworkPlayerColumnHeaders(g, pl, pt);
        }

        drawListRows(g, pl, pt, mouseX, mouseY);
        drawFooter(g, pl, pt, ph);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Panel chrome (mirrors MirrorCallScreen exactly) ───────────────────────

    private void drawPanelChrome(GuiGraphics g, int pl, int pt, int pr, int pb) {
        g.fill(pl, pt, pr, pb, DC_PANEL);

        // Outer border (1px)
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

        // Gold corner brackets
        drawCornerBracket(g, pl+4,  pt+4,  false, false);
        drawCornerBracket(g, pr-14, pt+4,  true,  false);
        drawCornerBracket(g, pl+4,  pb-14, false, true);
        drawCornerBracket(g, pr-14, pb-14, true,  true);
    }

    private void drawCornerBracket(GuiGraphics g, int x, int y, boolean flipH, boolean flipV) {
        int sz = 10, th = 1;
        int col = UITheme.withAlpha(DC_GOLD, 0xB3);
        if (!flipH && !flipV) {
            g.fill(x, y, x+sz, y+th, col); g.fill(x, y, x+th, y+sz, col);
        } else if (flipH && !flipV) {
            g.fill(x, y, x+sz, y+th, col); g.fill(x+sz-th, y, x+sz, y+sz, col);
        } else if (!flipH) {
            g.fill(x, y+sz-th, x+sz, y+sz, col); g.fill(x, y, x+th, y+sz, col);
        } else {
            g.fill(x, y+sz-th, x+sz, y+sz, col); g.fill(x+sz-th, y, x+sz, y+sz, col);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, int pl, int pt, int pr) {
        g.fill(pl, pt, pr, pt + HEADER_H, DC_HEADER_BG);
        g.fill(pl, pt + HEADER_H, pr, pt + HEADER_H + 1, DC_BDR_DIM);

        int hY = pt + (HEADER_H - 8) / 2;
        // Glyph ⌬ (U+2BC6 is not reliable; use delta ▽ U+25BD or the spec's U+2BC6 mapped; using ⌬ as-is)
        g.drawString(font, "\u2BC6", pl + 14, hY, DC_GOLD, false);
        g.drawString(font, "PERMISSIONS", pl + 28, hY, DC_GOLD, false);

        // Mode pill "MY MIRROR" (lavender, right-aligned)
        drawModePill(g, pr, pt, "MY MIRROR", DC_TEXT_LAV, DC_BDR_OUTER, 0x1E7733CC);
    }

    private void drawModePill(GuiGraphics g, int pr, int pt, String label, int txC, int bdC, int bgC) {
        int pillW = font.width(label) + 16;
        int pillH = 18;
        int px = pr - pillW - 12;
        int py = pt + (HEADER_H - pillH) / 2;

        g.fill(px, py, px+pillW, py+pillH, bgC);
        g.fill(px,         py,         px+pillW, py+1,       bdC);
        g.fill(px,         py+pillH-1, px+pillW, py+pillH,   bdC);
        g.fill(px,         py,         px+1,     py+pillH,   bdC);
        g.fill(px+pillW-1, py,         px+pillW, py+pillH,   bdC);
        g.drawString(font, label, px + 8, py + (pillH - 8) / 2, txC, false);
    }

    // ── Tab strip ─────────────────────────────────────────────────────────────

    private void drawTabStrip(GuiGraphics g, int pl, int pt, int pr, int mx, int my) {
        int tabY = pt + HEADER_H;
        int tabH = TABS_H;
        g.fill(pl, tabY, pr, tabY + tabH, UITheme.withAlpha(DC_HEADER_BG, 0xCC));
        g.fill(pl, tabY + tabH, pr, tabY + tabH + 1, DC_BDR_DIM);

        // Tab widths: "MY NETWORK (N)", "PLAYERS (N)", "REQUESTS [badge]"
        List<GrantedEntry> fn = filteredNetwork();
        List<GrantedEntry> fp = filteredPlayers();
        List<RequestEntry> fr = filteredRequests();

        String[] labels = {
            "MY NETWORK (" + fn.size() + ")",
            "PLAYERS (" + fp.size() + ")",
            "REQUESTS"
        };

        int totalW = pr - pl - 20;
        int tabW   = totalW / 3;
        int[] tabXs = { pl + 10, pl + 10 + tabW + 5, pl + 10 + (tabW + 5) * 2 };

        for (int i = 0; i < 3; i++) {
            boolean active = (currentTab == i);
            int textColor  = active ? DC_GOLD : DC_TEXT_MUTED;
            int tx = tabXs[i] + (tabW - font.width(labels[i])) / 2;
            g.drawString(font, labels[i], tx, tabY + (tabH - 8) / 2, textColor, false);
            // Underline for active tab
            if (active) {
                g.fill(tabXs[i], tabY + tabH - 1, tabXs[i] + tabW, tabY + tabH, DC_GOLD);
            }
        }

        // Request badge
        if (!requestEntries.isEmpty()) {
            String cnt = String.valueOf(requestEntries.size());
            int badgeX = tabXs[2] + tabW - font.width(cnt) - 4;
            int badgeY = tabY + (tabH - 9) / 2;
            g.fill(badgeX - 2, badgeY - 1, badgeX + font.width(cnt) + 2, badgeY + 9, 0xFFFF2222);
            g.drawString(font, cnt, badgeX, badgeY, 0xFFFFFFFF, false);
        }
    }

    // ── Search row ────────────────────────────────────────────────────────────

    private void drawSearchRow(GuiGraphics g, int pl, int pt, int mx, int my) {
        int rowY = pt + HEADER_H + TABS_H;
        g.fill(pl, rowY, pl + PANEL_W, rowY + SEARCH_H, UITheme.withAlpha(DC_HEADER_BG, 0x88));

        // Search box outline
        int sbx = pl + 10, sby = rowY + 8;
        g.fill(sbx - 1, sby - 1, sbx + 232, sby + 24, DC_BDR_OUTER);
        g.fill(sbx,     sby,     sbx + 231, sby + 23, 0xFF060022);

        // Right-side chip
        if (currentTab == TAB_NETWORK) {
            drawPrivacyChip(g, pl, rowY, mx, my);
        } else if (currentTab == TAB_PLAYERS) {
            drawAddToggleChip(g, pl, rowY, mx, my);
        }
    }

    private void drawPrivacyChip(GuiGraphics g, int pl, int rowY, int mx, int my) {
        int dl = defaultLevel;
        String chipText  = CHIP_GLYPHS[dl] + " " + CHIP_LABELS[dl];
        int chipW  = font.width(chipText) + 14;
        int chipH  = 22;
        int chipX  = pl + PANEL_W - 10 - chipW;
        int chipY  = rowY + 8;

        g.fill(chipX, chipY, chipX + chipW, chipY + chipH, CHIP_BG[dl]);
        g.fill(chipX,           chipY,           chipX + chipW, chipY + 1,       CHIP_BORDER[dl]);
        g.fill(chipX,           chipY + chipH-1, chipX + chipW, chipY + chipH,   CHIP_BORDER[dl]);
        g.fill(chipX,           chipY,           chipX + 1,     chipY + chipH,   CHIP_BORDER[dl]);
        g.fill(chipX + chipW-1, chipY,           chipX + chipW, chipY + chipH,   CHIP_BORDER[dl]);
        g.drawString(font, chipText, chipX + 7, chipY + (chipH - 8) / 2, CHIP_TEXT[dl], false);
    }

    private void drawAddToggleChip(GuiGraphics g, int pl, int rowY, int mx, int my) {
        String label = addExpanded ? "- CANCEL" : "+ ADD PLAYER";
        int chipW  = font.width(label) + 14;
        int chipH  = 22;
        int chipX  = pl + PANEL_W - 10 - chipW;
        int chipY  = rowY + 8;
        int bdC    = DC_BDR_OUTER;
        int bgC    = addExpanded ? 0x1E7733CC : 0x2D9A7520;
        int txC    = addExpanded ? DC_TEXT_LAV : DC_GOLD;

        g.fill(chipX, chipY, chipX + chipW, chipY + chipH, bgC);
        g.fill(chipX,           chipY,           chipX + chipW, chipY + 1,       bdC);
        g.fill(chipX,           chipY + chipH-1, chipX + chipW, chipY + chipH,   bdC);
        g.fill(chipX,           chipY,           chipX + 1,     chipY + chipH,   bdC);
        g.fill(chipX + chipW-1, chipY,           chipX + chipW, chipY + chipH,   bdC);
        g.drawString(font, label, chipX + 7, chipY + (chipH - 8) / 2, txC, false);
    }

    // ── Add-player expansion (Players tab) ───────────────────────────────────

    private void drawAddExpansion(GuiGraphics g, int pl, int pt, int mx, int my) {
        int expTop  = pt + HEADER_H + TABS_H + SEARCH_H;
        int expBotY = expTop + EXPAND_H;
        g.fill(pl, expTop, pl + PANEL_W, expBotY, UITheme.withAlpha(DC_PANEL, 0xCC));
        g.fill(pl, expTop, pl + PANEL_W, expTop + 1, DC_BDR_DIM);
        g.fill(pl, expBotY - 1, pl + PANEL_W, expBotY, DC_BDR_DIM);

        // "ADD BY USERNAME" label
        g.drawString(font, "ADD BY USERNAME", pl + 10, expTop + 8, DC_TEXT_MUTED, false);

        // Text box outline (drawn by EditBox widget, but give it a border)
        g.fill(pl + 9, expTop + 27, pl + 272, expTop + 51, DC_BDR_OUTER);
        g.fill(pl + 10, expTop + 28, pl + 271, expTop + 50, 0xFF060022);

        // OR PICK FROM ONLINE label
        int conn = onlinePlayerCount();
        String pickLabel = "OR PICK FROM ONLINE (" + conn + ")";
        g.drawString(font, pickLabel, pl + 10, expTop + 58, DC_TEXT_MUTED, false);

        // Online player chips
        drawOnlinePlayerChips(g, pl, expTop, mx, my);
    }

    private void drawOnlinePlayerChips(GuiGraphics g, int pl, int expTop, int mx, int my) {
        List<String> online = onlinePlayerNames();
        int chipY = expTop + 68;
        int cx    = pl + 10;
        for (String name : online) {
            if (cx + font.width(name) + 26 > pl + PANEL_W - 10) break;
            int cw = font.width(name) + 22;
            g.fill(cx, chipY, cx + cw, chipY + 14, 0x28AAFFAA);
            g.fill(cx, chipY, cx + cw, chipY + 1, 0x80AAFFAA);
            g.fill(cx, chipY + 13, cx + cw, chipY + 14, 0x80AAFFAA);
            g.fill(cx, chipY, cx + 1, chipY + 14, 0x80AAFFAA);
            g.fill(cx + cw - 1, chipY, cx + cw, chipY + 14, 0x80AAFFAA);
            // Green live dot
            g.fill(cx + 3, chipY + 5, cx + 7, chipY + 9, 0xFF44FF77);
            g.drawString(font, name, cx + 9, chipY + 3, DC_TEXT_LAV, false);
            cx += cw + 4;
        }
    }

    private int onlinePlayerCount() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return 0;
        return conn.getOnlinePlayers().size();
    }

    private List<String> onlinePlayerNames() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return List.of();
        List<String> names = new ArrayList<>();
        conn.getOnlinePlayers().stream()
                .map(pi -> pi.getProfile().getName())
                .filter(n -> n != null && !n.isEmpty())
                .limit(8)
                .forEach(names::add);
        return names;
    }

    // ── Column headers ────────────────────────────────────────────────────────

    private void drawNetworkPlayerColumnHeaders(GuiGraphics g, int pl, int pt) {
        int hy = listTop(pt) - COL_HDR_H + 4;
        g.drawString(font, "#",      pl + CX_IDX,  hy, DC_TEXT_MUTED, false);
        g.drawString(font, "PLAYER", pl + CX_NAME, hy, DC_TEXT_MUTED, false);
        // Tier header labels
        g.drawString(font, "NONE",  pl + CX_NONE  + (PILL_W - font.width("NONE"))  / 2, hy, DC_TEXT_MUTED, false);
        g.drawString(font, "VIEW",  pl + CX_VIEW  + (PILL_W - font.width("VIEW"))  / 2, hy, DC_TEXT_MUTED, false);
        g.drawString(font, "CALL",  pl + CX_CALL  + (PILL_W - font.width("CALL"))  / 2, hy, DC_TEXT_MUTED, false);
        g.drawString(font, "ENTER", pl + CX_ENTER + (PILL_W - font.width("ENTER")) / 2, hy, DC_TEXT_MUTED, false);
    }

    // ── List rows ─────────────────────────────────────────────────────────────

    private void drawListRows(GuiGraphics g, int pl, int pt, int mx, int my) {
        int listTop = listTop(pt);
        int pr      = pl + PANEL_W;

        if (currentTab == TAB_NETWORK) {
            List<GrantedEntry> visible = filteredNetwork();
            if (visible.isEmpty()) {
                g.drawCenteredString(font, "No players have network access.",
                        pl + PANEL_W / 2, listTop + 30, DC_TEXT_MUTED);
                return;
            }
            int end = Math.min(visible.size(), networkScroll + MAX_ROWS_NP);
            for (int i = networkScroll; i < end; i++) {
                int row = i - networkScroll;
                int ry  = listTop + row * ROW_H;
                GrantedEntry e = visible.get(i);
                boolean hov = mx >= pl && mx < pr && my >= ry && my < ry + ROW_H;
                drawRowBg(g, pl, ry, pr, ROW_H, i, hov);
                drawGrantedRowContent(g, pl, ry, i + 1, e);
            }
        } else if (currentTab == TAB_PLAYERS) {
            List<GrantedEntry> visible = filteredPlayers();
            int maxRows = addExpanded ? 4 : MAX_ROWS_NP;
            if (visible.isEmpty()) {
                g.drawCenteredString(font, "No additional players found.",
                        pl + PANEL_W / 2, listTop + 30, DC_TEXT_MUTED);
                return;
            }
            int end = Math.min(visible.size(), playerScroll + maxRows);
            for (int i = playerScroll; i < end; i++) {
                int row = i - playerScroll;
                int ry  = listTop + row * ROW_H;
                GrantedEntry e = visible.get(i);
                boolean hov = mx >= pl && mx < pr && my >= ry && my < ry + ROW_H;
                drawRowBg(g, pl, ry, pr, ROW_H, i, hov);
                drawGrantedRowContent(g, pl, ry, i + 1, e);
            }
        } else {
            List<RequestEntry> visible = filteredRequests();
            if (visible.isEmpty()) {
                g.drawCenteredString(font, "No pending requests.",
                        pl + PANEL_W / 2, listTop + 40, DC_TEXT_MUTED);
                return;
            }
            int end = Math.min(visible.size(), requestScroll + MAX_ROWS_REQ);
            for (int i = requestScroll; i < end; i++) {
                int row = i - requestScroll;
                int ry  = listTop + row * REQ_ROW_H;
                RequestEntry e = visible.get(i);
                boolean hov = mx >= pl && mx < pr && my >= ry && my < ry + REQ_ROW_H;
                drawRowBg(g, pl, ry, pr, REQ_ROW_H, i, hov);
                drawRequestRowContent(g, pl, ry, e);
            }
        }
    }

    private void drawRowBg(GuiGraphics g, int pl, int ry, int pr, int rh, int idx, boolean hov) {
        if (hov) {
            g.fill(pl, ry, pr, ry + rh, DC_ROW_HOVER);
        } else if (idx % 2 == 1) {
            g.fill(pl, ry, pr, ry + rh, DC_ROW_ZEBRA);
        }
        g.fill(pl + 4, ry + rh - 1, pr - 4, ry + rh, UITheme.withAlpha(0xFFFFFFFF, 0x0A));
    }

    private void drawGrantedRowContent(GuiGraphics g, int pl, int ry, int idx, GrantedEntry e) {
        int ty = ry + (ROW_H - 8) / 2;
        // Index
        g.drawString(font, String.valueOf(idx), pl + CX_IDX + 4, ty, DC_TEXT_MUTED, false);
        // Mini face
        drawMiniFace(g, pl + CX_FACE + 1, ry + (ROW_H - 16) / 2, e.playerName);
        // Name
        g.drawString(font, e.playerName, pl + CX_NAME, ty, 0xFFE8E8F0, false);
        // Ladder pills are rendered as widgets; overlay active tier highlight on top
        // (active pill gets the full bright color via widget's border, inactive gets hint — already set in buildLadderPills)
    }

    private void drawRequestRowContent(GuiGraphics g, int pl, int ry, RequestEntry e) {
        int ty = ry + (REQ_ROW_H - 8) / 2;
        // Mini face
        drawMiniFace(g, pl + CRQ_FACE, ry + (REQ_ROW_H - 16) / 2, e.requesterName);
        // Text: "[name] wants to ENTER your mirror"
        int tx = pl + CRQ_TEXT;
        g.drawString(font, e.requesterName, tx, ty - 4, DC_GOLD, false);
        int nx = tx + font.width(e.requesterName) + 4;
        g.drawString(font, "wants to", nx, ty - 4, DC_TEXT_MUTED, false);
        int wx = nx + font.width("wants to") + 4;
        g.drawString(font, "ENTER", wx, ty - 4, DC_TEXT_LAV, false);
        int ex = wx + font.width("ENTER") + 4;
        g.drawString(font, "your mirror", ex, ty - 4, DC_TEXT_MUTED, false);
    }

    // ── Mini face (16×16 fill-based, hashed from player name) ────────────────

    private void drawMiniFace(GuiGraphics g, int x, int y, String playerName) {
        int hash = Math.abs(playerName.hashCode());
        int skinIdx = hash % SKIN_BASE.length;
        int hairIdx = (hash / SKIN_BASE.length) % HAIR_COLOR.length;
        int eyeIdx  = (hash / (SKIN_BASE.length * HAIR_COLOR.length)) % EYE_COLOR.length;

        int skin = SKIN_BASE[skinIdx];
        int hair = HAIR_COLOR[hairIdx];
        int eye  = EYE_COLOR[eyeIdx];

        // Skin base (16×16)
        g.fill(x, y, x + 16, y + 16, skin);

        // Hair band: full 16px wide, 4px tall at top
        g.fill(x, y, x + 16, y + 4, hair);
        // Hair sides: 2px wide × 4px at y=4, both sides
        g.fill(x,      y + 4, x + 2,  y + 8, hair);
        g.fill(x + 14, y + 4, x + 16, y + 8, hair);

        // Eyes: 2×2px at y=7, x=4 and x=10
        g.fill(x + 4,  y + 7, x + 6,  y + 9, eye);
        g.fill(x + 10, y + 7, x + 12, y + 9, eye);

        // Subtle shadow outline (top + left edges, 1px)
        int shadow = 0x66000000;
        g.fill(x, y, x + 16, y + 1,  shadow);
        g.fill(x, y, x + 1,  y + 16, shadow);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void drawFooter(GuiGraphics g, int pl, int pt, int ph) {
        int footerY = pt + ph - FOOTER_H;
        g.fill(pl, footerY, pl + PANEL_W, footerY + 1, DC_BDR_DIM);
        g.fill(pl, footerY + 1, pl + PANEL_W, pt + ph, UITheme.withAlpha(0xFF000000, 0x44));

        // Left meta
        int textY = footerY + (FOOTER_H - 8) / 2;
        String meta = networkEntries.size() + " players  \u00b7  " + requestEntries.size() + " pending";
        g.drawString(font, meta, pl + 10, textY, DC_TEXT_MUTED, false);
    }

    // ── Mouse interaction ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int pl = panelLeft();
        int pt = panelTop();
        int pr = pl + PANEL_W;

        // Tab strip clicks
        int tabY  = pt + HEADER_H;
        int totalW = pr - pl - 20;
        int tabW   = totalW / 3;
        if (my >= tabY && my < tabY + TABS_H && mx >= pl + 10 && mx < pr - 10) {
            int relX = (int) mx - (pl + 10);
            int clickedTab = relX / (tabW + 5);
            if (clickedTab >= 0 && clickedTab <= 2) {
                currentTab = clickedTab;
                addExpanded = false;
                networkScroll = playerScroll = requestScroll = 0;
                rebuildUI();
                return true;
            }
        }

        // Privacy chip click (My Network)
        if (currentTab == TAB_NETWORK) {
            int rowY  = pt + HEADER_H + TABS_H;
            int chipText = font.width(CHIP_GLYPHS[defaultLevel] + " " + CHIP_LABELS[defaultLevel]) + 14;
            int chipX = pr - 10 - chipText;
            if (my >= rowY + 8 && my < rowY + 30 && mx >= chipX && mx < pr - 10) {
                defaultLevel = (defaultLevel + 1) % 4;
                rebuildUI();
                return true;
            }
        }

        // Add-player chip click (Players)
        if (currentTab == TAB_PLAYERS) {
            int rowY = pt + HEADER_H + TABS_H;
            String addLabel = addExpanded ? "- CANCEL" : "+ ADD PLAYER";
            int chipW = font.width(addLabel) + 14;
            int chipX = pr - 10 - chipW;
            if (my >= rowY + 8 && my < rowY + 30 && mx >= chipX && mx < pr - 10) {
                addExpanded = !addExpanded;
                rebuildUI();
                return true;
            }
            // Online player chip clicks in expansion
            if (addExpanded) {
                int expTop = pt + HEADER_H + TABS_H + SEARCH_H;
                int chipY  = expTop + 68;
                if (my >= chipY && my < chipY + 14) {
                    List<String> online = onlinePlayerNames();
                    int cx = pl + 10;
                    for (String name : online) {
                        int cw = font.width(name) + 22;
                        if (mx >= cx && mx < cx + cw) {
                            // Add player at ENTER level
                            GrantedEntry ne = new GrantedEntry(UUID.randomUUID(), name, true, true, true);
                            ne.permLevel = 3;
                            playerEntries.add(ne);
                            MirrorsNetwork.sendToServer(new ServerboundPermissionRequestPacket(name));
                            rebuildUI();
                            return true;
                        }
                        cx += cw + 4;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentTab == TAB_NETWORK) {
            List<GrantedEntry> visible = filteredNetwork();
            int max = Math.max(0, visible.size() - MAX_ROWS_NP);
            networkScroll = (int) Math.max(0, Math.min(max, networkScroll - Math.signum(delta)));
            rebuildUI();
            return true;
        }
        if (currentTab == TAB_PLAYERS) {
            List<GrantedEntry> visible = filteredPlayers();
            int maxRows = addExpanded ? 4 : MAX_ROWS_NP;
            int max = Math.max(0, visible.size() - maxRows);
            playerScroll = (int) Math.max(0, Math.min(max, playerScroll - Math.signum(delta)));
            rebuildUI();
            return true;
        }
        if (currentTab == TAB_REQUESTS) {
            List<RequestEntry> visible = filteredRequests();
            int max = Math.max(0, visible.size() - MAX_ROWS_REQ);
            requestScroll = (int) Math.max(0, Math.min(max, requestScroll - Math.signum(delta)));
            rebuildUI();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (addNameBox != null && addNameBox.isFocused()) {
            return addNameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
