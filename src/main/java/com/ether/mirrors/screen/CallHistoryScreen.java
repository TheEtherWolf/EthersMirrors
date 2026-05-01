package com.ether.mirrors.screen;

import com.ether.mirrors.data.CallLogData;
import com.ether.mirrors.data.CallLogData.EventType;
import com.ether.mirrors.data.CallLogData.LogEntry;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundCallRequestPacket;
import com.ether.mirrors.network.packets.ServerboundClearCallHistoryPacket;
import com.ether.mirrors.network.packets.ServerboundDeleteCallHistoryEntryPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public class CallHistoryScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PANEL_W  = 520;
    private static final int HEADER_H = 32;
    private static final int TABS_H   = 22;
    private static final int SEARCH_H = 36;
    private static final int LIST_H   = 296;
    private static final int FOOTER_H = 32;
    private static final int PANEL_H  = HEADER_H + TABS_H + SEARCH_H + LIST_H + FOOTER_H;

    private static final int CALL_H = 46;
    private static final int SYS_H  = 22;
    private static final int DAY_H  = 20;

    // column offsets relative to panelLeft (12px side padding each side)
    private static final int CX_DOT_C = 20;   // dot/icon centre
    private static final int CX_FACE  = 34;   // face start  (12+12+10)
    private static final int CX_TEXT  = 60;   // text start  (34+16+10)
    private static final int CX_X_BTN = 484;  // × button left  (520-12-24)
    private static final int CX_CB    = 408;  // Call Back left (484-4-72)
    private static final int CX_RT    = 508;  // right text edge (520-12)

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int DC_BG      = 0xFF030012;
    private static final int DC_HEADER  = 0xFF020020;
    private static final int DC_BDR_OUT = 0xFF7733CC;
    private static final int DC_BDR_IN  = 0x8CAA55FF;
    private static final int DC_BDR_DIM = 0xFF2A1144;
    private static final int DC_TEXT_C  = 0xFFE6DDF5;
    private static final int DC_MUTED   = 0xFF9999AA;
    private static final int DC_LAV     = 0xFFAA88FF;
    private static final int DC_GOLD    = 0xFFD4AF37;
    private static final int DC_GDIM    = 0xFF9A7520;
    private static final int DC_GREEN   = 0xFF3DC356;
    private static final int DC_YELLOW  = 0xFFE6C84B;
    private static final int DC_RED     = 0xFFC73838;
    private static final int DC_TEAL    = 0xFF6FE0E0;
    private static final int DC_TEAL_B  = 0xFF1A6B6B;
    private static final int DC_TEAL_BG = 0x381A6B6B;
    private static final int DC_OW      = 0xFF4FB870;
    private static final int DC_NE      = 0xFFC84A3A;
    private static final int DC_END_C   = 0xFFD4AF37;
    private static final int DC_POC     = 0xFFB07AFF;

    // ── Internals ─────────────────────────────────────────────────────────────
    private record FilteredEntry(int origIndex, LogEntry entry) {}
    private record RenderItem(boolean isDayHdr, String dayLabel, FilteredEntry fe) {}

    private final List<LogEntry>  allEntries;
    private List<FilteredEntry>   filtered   = new ArrayList<>();
    private List<RenderItem>      renderList = new ArrayList<>();

    private int activeTab   = 0; // 0=ALL 1=MISSED 2=INCOMING 3=OUTGOING
    private int scrollPx    = 0;
    private int maxScrollPx = 0;
    private int totalH      = 0;

    private int cAll, cMissed, cIn, cOut;
    private EditBox searchBox;

    private static final String[] TAB_LABELS = {"ALL", "MISSED", "INCOMING", "OUTGOING"};
    private static final String[] MON = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};

    // ── Constructor ───────────────────────────────────────────────────────────
    public CallHistoryScreen(List<LogEntry> entries) {
        super(Component.literal("Memory"));
        this.allEntries = new ArrayList<>(entries);
    }

    // ── Geometry ──────────────────────────────────────────────────────────────
    private int pl()         { return (this.width  - PANEL_W) / 2; }
    private int pt()         { return (this.height - PANEL_H) / 2; }
    private int listTop()    { return pt() + HEADER_H + TABS_H + SEARCH_H; }
    private int listBottom() { return listTop() + LIST_H; }
    private int footTop()    { return listBottom(); }

    // ── Init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        int pl = pl(), pt = pt();
        searchBox = new EditBox(this.font, pl + 44, pt + HEADER_H + TABS_H + 9, PANEL_W - 58, 16,
                Component.literal("Search"));
        searchBox.setMaxLength(48);
        searchBox.setBordered(false);
        searchBox.setTextColor(DC_TEXT_C);
        searchBox.setHint(Component.literal("Search by player name...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        searchBox.setResponder(s -> { scrollPx = 0; applyFilter(); });
        addWidget(searchBox);
        computeCounts();
        applyFilter();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float tick) {
        renderBackground(g);
        int pl = pl(), pt = pt();
        drawPanel(g, pl, pt);
        drawHeader(g, pl, pt);
        drawTabs(g, pl, pt, mx, my);
        drawSearchBar(g, pl, pt);
        drawList(g, pl, mx, my);
        drawFooter(g, pl, pt, mx, my);
        super.render(g, mx, my, tick);
    }

    // ── Panel chrome ──────────────────────────────────────────────────────────
    private void drawPanel(GuiGraphics g, int pl, int pt) {
        g.fill(pl, pt, pl + PANEL_W, pt + PANEL_H, DC_BG);
        // outer border
        g.fill(pl, pt, pl + PANEL_W, pt + 1, DC_BDR_OUT);
        g.fill(pl, pt + PANEL_H - 1, pl + PANEL_W, pt + PANEL_H, DC_BDR_OUT);
        g.fill(pl, pt, pl + 1, pt + PANEL_H, DC_BDR_OUT);
        g.fill(pl + PANEL_W - 1, pt, pl + PANEL_W, pt + PANEL_H, DC_BDR_OUT);
        // inner border
        g.fill(pl + 2, pt + 2, pl + PANEL_W - 2, pt + 3, DC_BDR_IN);
        g.fill(pl + 2, pt + PANEL_H - 3, pl + PANEL_W - 2, pt + PANEL_H - 2, DC_BDR_IN);
        g.fill(pl + 2, pt + 2, pl + 3, pt + PANEL_H - 2, DC_BDR_IN);
        g.fill(pl + PANEL_W - 3, pt + 2, pl + PANEL_W - 2, pt + PANEL_H - 2, DC_BDR_IN);
        // gold corner ornaments
        int c = DC_GOLD;
        g.fill(pl+4, pt+4, pl+10, pt+5, c); g.fill(pl+4, pt+4, pl+5, pt+10, c);
        g.fill(pl+PANEL_W-10, pt+4, pl+PANEL_W-4, pt+5, c); g.fill(pl+PANEL_W-5, pt+4, pl+PANEL_W-4, pt+10, c);
        g.fill(pl+4, pt+PANEL_H-5, pl+10, pt+PANEL_H-4, c); g.fill(pl+4, pt+PANEL_H-10, pl+5, pt+PANEL_H-4, c);
        g.fill(pl+PANEL_W-10, pt+PANEL_H-5, pl+PANEL_W-4, pt+PANEL_H-4, c); g.fill(pl+PANEL_W-5, pt+PANEL_H-10, pl+PANEL_W-4, pt+PANEL_H-4, c);
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private void drawHeader(GuiGraphics g, int pl, int pt) {
        g.fill(pl+1, pt+1, pl+PANEL_W-1, pt+HEADER_H, DC_HEADER);
        g.fill(pl+1, pt+HEADER_H, pl+PANEL_W-1, pt+HEADER_H+1, DC_BDR_DIM);
        int cy = pt + HEADER_H / 2 - 4;
        g.drawString(font, "\u2317", pl + 12, cy, DC_GOLD, false);
        g.drawString(font, "MEMORY", pl + 24, cy, DC_GOLD, false);
        String meta = (activeTab == 1 && cMissed > 0)
                ? cMissed + " MISSED \u00b7 LAST 30 DAYS"
                : "LAST 30 DAYS";
        g.drawString(font, meta, pl + PANEL_W - 12 - font.width(meta), cy, DC_MUTED, false);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private void drawTabs(GuiGraphics g, int pl, int pt, int mx, int my) {
        int ty = pt + HEADER_H;
        g.fill(pl+1, ty, pl+PANEL_W-1, ty+TABS_H, 0x0A7733CC);
        g.fill(pl+1, ty+TABS_H-1, pl+PANEL_W-1, ty+TABS_H, DC_BDR_DIM);
        int x = pl + 10;
        for (int i = 0; i < 4; i++) {
            int count = switch(i){ case 0->cAll; case 1->cMissed; case 2->cIn; default->cOut; };
            boolean active = activeTab == i;
            int tw = font.width(TAB_LABELS[i]) + 20 + font.width(" " + count);
            g.drawString(font, TAB_LABELS[i], x + 10, ty + 7, active ? DC_GOLD : DC_MUTED, false);
            if (i == 1 && cMissed > 0) {
                String badge = String.valueOf(cMissed);
                int bx = x + 10 + font.width(TAB_LABELS[i]) + 4;
                int bw = font.width(badge) + 6;
                g.fill(bx, ty+5, bx+bw, ty+16, DC_RED);
                g.drawString(font, badge, bx+3, ty+7, 0xFFFFFFFF, false);
            } else {
                g.drawString(font, " " + count, x + 10 + font.width(TAB_LABELS[i]), ty+7,
                        active ? DC_GOLD : 0x559999AA, false);
            }
            if (active) g.fill(x+6, ty+TABS_H-1, x+tw-6, ty+TABS_H, DC_GOLD);
            x += tw + 2;
        }
    }

    // ── Search bar ────────────────────────────────────────────────────────────
    private void drawSearchBar(GuiGraphics g, int pl, int pt) {
        int sy = pt + HEADER_H + TABS_H;
        g.fill(pl+1, sy, pl+PANEL_W-1, sy+SEARCH_H, 0x33000000);
        g.fill(pl+1, sy+SEARCH_H-1, pl+PANEL_W-1, sy+SEARCH_H, DC_BDR_DIM);
        // inner box
        g.fill(pl+10, sy+7, pl+PANEL_W-10, sy+SEARCH_H-7, 0x66000000);
        g.fill(pl+10, sy+7, pl+PANEL_W-10, sy+8, DC_BDR_DIM);
        g.fill(pl+10, sy+SEARCH_H-8, pl+PANEL_W-10, sy+SEARCH_H-7, DC_BDR_DIM);
        g.fill(pl+10, sy+7, pl+11, sy+SEARCH_H-7, DC_BDR_DIM);
        g.fill(pl+PANEL_W-11, sy+7, pl+PANEL_W-10, sy+SEARCH_H-7, DC_BDR_DIM);
        g.drawString(font, "\u2315", pl+14, sy+13, DC_LAV, false);
    }

    // ── List ──────────────────────────────────────────────────────────────────
    private void drawList(GuiGraphics g, int pl, int mx, int my) {
        int lt = listTop(), lb = listBottom();
        g.fill(pl+1, lt, pl+PANEL_W-1, lb, DC_BG);

        if (filtered.isEmpty()) { drawEmptyState(g, pl, lt); return; }

        g.enableScissor(pl+1, lt, pl+PANEL_W-1, lb);
        int y = lt - scrollPx;
        for (RenderItem item : renderList) {
            if (item.isDayHdr()) {
                if (y + DAY_H > lt && y < lb) drawDayHeader(g, pl, y, item.dayLabel());
                y += DAY_H;
            } else {
                LogEntry e = item.fe().entry();
                int h = isCallType(e.type) ? CALL_H : SYS_H;
                if (y + h > lt && y < lb) {
                    if (isCallType(e.type)) drawCallCard(g, pl, y, item.fe(), mx, my);
                    else drawSysRow(g, pl, y, e);
                }
                y += h;
            }
        }
        // MISSED tab end divider
        if (activeTab == 1 && !filtered.isEmpty() && y + 60 > lt && y < lb) {
            String msg = "\u254c\u254c\u254c  no older missed calls  \u254c\u254c\u254c";
            g.drawString(font, msg, pl + (PANEL_W - font.width(msg))/2, y + 16, 0x559999AA, false);
        }
        g.disableScissor();

        // scroll thumb
        if (maxScrollPx > 0) {
            int trackH = LIST_H - 4;
            int thumbH = Math.max(14, trackH * LIST_H / Math.max(1, totalH));
            int thumbY = lt + 2 + (int)((long) scrollPx * (trackH - thumbH) / maxScrollPx);
            g.fill(pl+PANEL_W-5, lt+2, pl+PANEL_W-3, lt+2+trackH, 0x22AA88FF);
            g.fill(pl+PANEL_W-5, thumbY, pl+PANEL_W-3, thumbY+thumbH, DC_BDR_OUT);
        }
    }

    // ── Empty state ───────────────────────────────────────────────────────────
    private void drawEmptyState(GuiGraphics g, int pl, int lt) {
        int cx = pl + PANEL_W/2, cy = lt + LIST_H/2 - 20;
        String glyph = "\u25cc  \u25cc  \u25cc";
        g.drawString(font, glyph, cx - font.width(glyph)/2, cy, DC_GDIM, false);
        String l1 = "THE MIRROR HAS", l2 = "NO MEMORY YET";
        g.drawString(font, l1, cx - font.width(l1)/2, cy+18, DC_MUTED, false);
        g.drawString(font, l2, cx - font.width(l2)/2, cy+30, DC_GDIM, false);
        String sub = "PLACE A MIRROR \u00b7 CALL A PLAYER \u00b7 CAST A GLYPH";
        g.drawString(font, sub, cx - font.width(sub)/2, cy+46, 0x559999AA, false);
    }

    // ── Day header ────────────────────────────────────────────────────────────
    private void drawDayHeader(GuiGraphics g, int pl, int y, String label) {
        g.fill(pl+1, y, pl+PANEL_W-1, y+DAY_H, 0x4D000000);
        g.fill(pl+1, y+DAY_H-1, pl+PANEL_W-1, y+DAY_H, DC_BDR_DIM);
        g.drawString(font, label, pl+12, y+6, DC_GDIM, false);
        int lx = pl + 12 + font.width(label) + 8;
        g.fill(lx, y+DAY_H/2, pl+PANEL_W-12, y+DAY_H/2+1, 0x449A7520);
    }

    // ── Call card ─────────────────────────────────────────────────────────────
    private void drawCallCard(GuiGraphics g, int pl, int y, FilteredEntry fe, int mx, int my) {
        LogEntry e = fe.entry();
        boolean hov = mx >= pl && mx < pl+PANEL_W && my >= y && my < y+CALL_H;
        g.fill(pl+1, y, pl+PANEL_W-1, y+CALL_H, hov ? 0x237733CC : 0);
        g.fill(pl+1, y+CALL_H-1, pl+PANEL_W-1, y+CALL_H, 0x14000000);

        int cy = y + CALL_H/2;
        int dotColor = switch(e.type) {
            case CALL_CONNECTED -> DC_GREEN;
            case CALL_MISSED    -> DC_YELLOW;
            default             -> DC_RED;
        };
        g.fill(pl+CX_DOT_C-3, cy-3, pl+CX_DOT_C+3, cy+3, dotColor);

        drawMiniFace(g, pl+CX_FACE, y+(CALL_H-16)/2, e.playerUUID, e.playerName);

        // Line 1: NAME  VERB
        int line1y = y+9, line2y = y+25;
        g.drawString(font, e.playerName.toUpperCase(), pl+CX_TEXT, line1y, DC_GOLD, false);
        String verb = switch(e.type) {
            case CALL_CONNECTED -> e.outgoing ? "OUTGOING" : "INCOMING";
            case CALL_MISSED    -> "MISSED";
            default             -> "DECLINED";
        };
        g.drawString(font, verb, pl+CX_TEXT + font.width(e.playerName.toUpperCase())+8, line1y, DC_MUTED, false);

        // Line 2: duration · dim · time
        int lx = pl+CX_TEXT;
        if (e.type == EventType.CALL_CONNECTED && e.durationSeconds >= 0) {
            String dur = fmtDur(e.durationSeconds);
            g.drawString(font, dur, lx, line2y, DC_GREEN, false);
            lx += font.width(dur)+6;
            g.drawString(font, "\u00b7", lx, line2y, 0x449999AA, false);
            lx += font.width("\u00b7")+6;
        }
        if (!e.dimensionId.isEmpty()) {
            lx = drawDimBadge(g, e.dimensionId, lx, line2y);
            g.drawString(font, "\u00b7", lx+2, line2y, 0x449999AA, false);
            lx += font.width("\u00b7")+8;
        }
        g.drawString(font, relTime(e.timestampMs), lx, line2y, DC_MUTED, false);

        // Action buttons
        if (hov || fe.origIndex() == 0) {
            drawCallBackBtn(g, pl+CX_CB, y+(CALL_H-18)/2, mx, my, e.playerUUID != null);
            drawDeleteBtn(g, pl+CX_X_BTN, y+(CALL_H-18)/2, mx, my);
        }
    }

    private void drawCallBackBtn(GuiGraphics g, int x, int y, int mx, int my, boolean hasTarget) {
        boolean hov = hasTarget && mx>=x && mx<x+72 && my>=y && my<y+18;
        g.fill(x, y, x+72, y+18, DC_TEAL_BG);
        int bdr = hov ? DC_TEAL : DC_TEAL_B;
        g.fill(x, y, x+72, y+1, bdr); g.fill(x, y+17, x+72, y+18, bdr);
        g.fill(x, y, x+1, y+18, bdr); g.fill(x+71, y, x+72, y+18, bdr);
        g.drawString(font, "CALL BACK", x+4, y+5, hov ? 0xFFFFFFFF : DC_TEAL, false);
        g.drawString(font, "[\u21b5]", x+56, y+5, 0x66FFFFFF, false);
    }

    private void drawDeleteBtn(GuiGraphics g, int x, int y, int mx, int my) {
        boolean hov = mx>=x && mx<x+22 && my>=y && my<y+18;
        int bdr = hov ? DC_RED : DC_BDR_DIM;
        if (hov) g.fill(x, y, x+22, y+18, 0x228B1A1A);
        g.fill(x, y, x+22, y+1, bdr); g.fill(x, y+17, x+22, y+18, bdr);
        g.fill(x, y, x+1, y+18, bdr); g.fill(x+21, y, x+22, y+18, bdr);
        g.drawString(font, "\u00d7", x+7, y+5, hov ? DC_RED : DC_MUTED, false);
    }

    // ── System row ────────────────────────────────────────────────────────────
    private void drawSysRow(GuiGraphics g, int pl, int y, LogEntry e) {
        boolean isTp = e.type == EventType.TELEPORT;
        String icon = isTp ? (e.outgoing ? "\u2197" : "\u2198") : "\u26bf";
        g.drawString(font, icon, pl+CX_DOT_C-3, y+7, isTp ? DC_LAV : DC_GDIM, false);

        int tx = pl+CX_TEXT-14;
        if (isTp) {
            g.drawString(font, "YOU", tx, y+7, DC_MUTED, false); tx += font.width("YOU")+4;
            String v = e.outgoing ? "ENTERED" : "CAME FROM";
            g.drawString(font, v, tx, y+7, DC_TEXT_C, false); tx += font.width(v)+4;
            if (!e.mirrorName.isEmpty()) {
                g.drawString(font, e.mirrorName.toUpperCase(), tx, y+7, DC_GDIM, false);
                tx += font.width(e.mirrorName.toUpperCase())+4;
            }
            if (!e.dimensionId.isEmpty()) drawDimBadge(g, e.dimensionId, tx, y+7);
        } else if (e.type == EventType.PERMISSION_GRANT) {
            g.drawString(font, "YOU GRANTED", tx, y+7, DC_MUTED, false); tx += font.width("YOU GRANTED")+4;
            String perm = e.mirrorName.isEmpty() ? "ACCESS" : e.mirrorName.toUpperCase();
            g.drawString(font, perm, tx, y+7, DC_GREEN, false); tx += font.width(perm)+4;
            g.drawString(font, e.playerName.toUpperCase(), tx, y+7, DC_GDIM, false);
        } else {
            g.drawString(font, "YOU REVOKED", tx, y+7, DC_MUTED, false); tx += font.width("YOU REVOKED")+4;
            g.drawString(font, e.playerName.toUpperCase(), tx, y+7, DC_GDIM, false);
        }

        String t = relTime(e.timestampMs);
        g.drawString(font, t, pl+CX_RT-font.width(t), y+7, 0x559999AA, false);
    }

    // ── Footer ────────────────────────────────────────────────────────────────
    private void drawFooter(GuiGraphics g, int pl, int pt, int mx, int my) {
        int fy = footTop();
        g.fill(pl+1, fy, pl+PANEL_W-1, fy+FOOTER_H, 0x4D000000);
        g.fill(pl+1, fy, pl+PANEL_W-1, fy+1, DC_BDR_DIM);

        String meta = activeTab == 1
                ? cMissed + " MISSED"
                : "SHOWING " + filtered.size() + " OF " + cAll + " \u00b7 30-DAY WINDOW";
        g.drawString(font, meta, pl+12, fy+12, DC_MUTED, false);

        boolean empty = filtered.isEmpty();
        String clearLbl = activeTab == 1 ? "CLEAR MISSED" : "CLEAR ALL";
        int btnH = 18;
        int doneW  = font.width("DONE") + 20;
        int clearW = font.width(clearLbl) + 20;
        int doneX  = pl+PANEL_W-12-doneW;
        int clearX = doneX-6-clearW;
        int by = fy + (FOOTER_H-btnH)/2;

        drawBtn(g, doneX, by, doneW, btnH, "DONE", DC_GOLD, DC_GDIM, mx, my);
        if (!empty) drawBtn(g, clearX, by, clearW, btnH, clearLbl, DC_MUTED, DC_BDR_DIM, mx, my);
        else {
            g.fill(clearX, by, clearX+clearW, by+1, 0x33444455);
            g.fill(clearX, by+btnH-1, clearX+clearW, by+btnH, 0x33444455);
            g.fill(clearX, by, clearX+1, by+btnH, 0x33444455);
            g.fill(clearX+clearW-1, by, clearX+clearW, by+btnH, 0x33444455);
            g.drawString(font, clearLbl, clearX+10, by+5, 0x33444455, false);
        }
    }

    private void drawBtn(GuiGraphics g, int x, int y, int w, int h,
                          String lbl, int tc, int bc, int mx, int my) {
        boolean hov = mx>=x && mx<x+w && my>=y && my<y+h;
        if (hov) g.fill(x, y, x+w, y+h, 0x22AA8800);
        g.fill(x, y, x+w, y+1, bc); g.fill(x, y+h-1, x+w, y+h, bc);
        g.fill(x, y, x+1, y+h, bc); g.fill(x+w-1, y, x+w, y+h, bc);
        g.drawString(font, lbl, x+10, y+5, hov ? 0xFFFFFFFF : tc, false);
    }

    // ── Mini face ─────────────────────────────────────────────────────────────
    private static final int[][] SKIN_PAL = {{0xFFE8C9A8},{0xFFB27A4A},{0xFFC9925E},{0xFF7A5234}};
    private static final int[]   HAIR_PAL = {0xFF2A1810,0xFF8B2A1A,0xFF4A2A18,0xFF1A1A1A,0xFFD8D0C0};
    private static final int[]   EYE_PAL  = {0xFF2E5B8A,0xFF2E8A4B,0xFF4A2A18,0xFF8B2A1A};

    private void drawMiniFace(GuiGraphics g, int x, int y, UUID uuid, String name) {
        int h = uuid != null ? uuid.hashCode() : name.hashCode();
        int skin = SKIN_PAL[Math.abs(h)       % SKIN_PAL.length][0];
        int hair = HAIR_PAL[Math.abs(h >> 4)  % HAIR_PAL.length];
        int eye  = EYE_PAL [Math.abs(h >> 8)  % EYE_PAL.length];
        g.fill(x, y, x+16, y+16, skin);
        g.fill(x, y, x+16, y+4,  hair);
        g.fill(x+4,  y+7, x+6,  y+9, eye);
        g.fill(x+10, y+7, x+12, y+9, eye);
        g.fill(x, y, x+16, y+1, 0x80000000);
    }

    // ── Dimension badge ───────────────────────────────────────────────────────
    /** Draws a dim badge and returns x-position after it. */
    private int drawDimBadge(GuiGraphics g, String dimId, int x, int y) {
        String lbl; int col;
        if      (dimId.contains("overworld")) { lbl="OVERWORLD"; col=DC_OW;    }
        else if (dimId.contains("nether"))    { lbl="NETHER";    col=DC_NE;    }
        else if (dimId.contains("end"))       { lbl="THE END";   col=DC_END_C; }
        else if (dimId.contains("pocket"))    { lbl="POCKET";    col=DC_POC;   }
        else { lbl = dimId.contains(":") ? dimId.substring(dimId.lastIndexOf(':')+1).toUpperCase() : dimId.toUpperCase(); col=DC_MUTED; }
        int bw = font.width(lbl)+6;
        int bg  = (col & 0x00FFFFFF) | 0x14000000;
        int bdr = (col & 0x00FFFFFF) | 0x66000000;
        g.fill(x, y-1, x+bw, y+10, bg);
        g.fill(x, y-1, x+bw, y, bdr); g.fill(x, y+9, x+bw, y+10, bdr);
        g.fill(x, y-1, x+1, y+10, bdr); g.fill(x+bw-1, y-1, x+bw, y+10, bdr);
        g.drawString(font, lbl, x+3, y, col, false);
        return x+bw+4;
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        int pl = pl(), pt = pt(), imx=(int)mx, imy=(int)my;

        // Tabs row
        int ty = pt+HEADER_H;
        if (imy>=ty && imy<ty+TABS_H) {
            int x = pl+10;
            for (int i=0; i<4; i++) {
                int count=switch(i){case 0->cAll;case 1->cMissed;case 2->cIn;default->cOut;};
                int tw = font.width(TAB_LABELS[i])+ font.width(" "+count)+20;
                if (imx>=x && imx<x+tw) { activeTab=i; scrollPx=0; applyFilter(); return true; }
                x+=tw+2;
            }
        }

        // Footer buttons
        int fy=footTop();
        if (imy>=fy && imy<fy+FOOTER_H) {
            String clearLbl = activeTab==1 ? "CLEAR MISSED" : "CLEAR ALL";
            int btnH=18, doneW=font.width("DONE")+20, clearW=font.width(clearLbl)+20;
            int doneX=pl+PANEL_W-12-doneW, clearX=doneX-6-clearW;
            int by=fy+(FOOTER_H-btnH)/2;
            if (imx>=doneX && imx<doneX+doneW && imy>=by && imy<by+btnH) { onClose(); return true; }
            if (!filtered.isEmpty() && imx>=clearX && imx<clearX+clearW && imy>=by && imy<by+btnH) {
                MirrorsNetwork.sendToServer(new ServerboundClearCallHistoryPacket(activeTab==1));
                if (activeTab==1) allEntries.removeIf(e->e.type==EventType.CALL_MISSED);
                else allEntries.clear();
                computeCounts(); applyFilter(); return true;
            }
        }

        // List item clicks
        if (imy>=listTop() && imy<listBottom()) {
            int y=listTop()-scrollPx;
            for (RenderItem item : renderList) {
                if (item.isDayHdr()) { y+=DAY_H; continue; }
                LogEntry e=item.fe().entry(); int h=isCallType(e.type)?CALL_H:SYS_H;
                if (isCallType(e.type) && imy>=y && imy<y+h) {
                    int origIdx=item.fe().origIndex();
                    int by2=y+(CALL_H-18)/2;
                    // × delete
                    if (imx>=pl+CX_X_BTN && imx<pl+CX_X_BTN+22 && imy>=by2 && imy<by2+18) {
                        MirrorsNetwork.sendToServer(new ServerboundDeleteCallHistoryEntryPacket(origIdx));
                        allEntries.remove(origIdx); computeCounts(); applyFilter(); return true;
                    }
                    // Call Back
                    if (e.playerUUID!=null && imx>=pl+CX_CB && imx<pl+CX_CB+72 && imy>=by2 && imy<by2+18) {
                        MirrorsNetwork.sendToServer(new ServerboundCallRequestPacket(e.playerUUID));
                        onClose(); return true;
                    }
                }
                y+=h;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if ((int)my>=listTop() && (int)my<listBottom()) {
            scrollPx = Math.max(0, Math.min(maxScrollPx, scrollPx-(int)(delta*12)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key==256) { onClose(); return true; }
        if ((key==257||key==335) && !filtered.isEmpty()) {
            LogEntry e=filtered.get(0).entry();
            if (e.playerUUID!=null && isCallType(e.type)) {
                MirrorsNetwork.sendToServer(new ServerboundCallRequestPacket(e.playerUUID));
                onClose(); return true;
            }
        }
        return super.keyPressed(key,scan,mods) || searchBox.keyPressed(key,scan,mods);
    }

    @Override public boolean charTyped(char c, int mods) { return searchBox.charTyped(c, mods); }
    @Override public boolean isPauseScreen() { return false; }

    // ── Filtering / counting ──────────────────────────────────────────────────
    private void computeCounts() {
        cAll    = allEntries.size();
        cMissed = (int)allEntries.stream().filter(e->e.type==EventType.CALL_MISSED).count();
        cIn     = (int)allEntries.stream().filter(e->isCallType(e.type)&&!e.outgoing).count();
        cOut    = (int)allEntries.stream().filter(e->isCallType(e.type)&&e.outgoing).count();
    }

    private void applyFilter() {
        String q = searchBox!=null ? searchBox.getValue().trim().toLowerCase() : "";
        filtered = new ArrayList<>();
        for (int i=0; i<allEntries.size(); i++) {
            LogEntry e=allEntries.get(i);
            if (!matchesTab(e)) continue;
            if (!q.isEmpty() && !e.playerName.toLowerCase().contains(q)) continue;
            filtered.add(new FilteredEntry(i, e));
        }
        buildRenderList();
    }

    private boolean matchesTab(LogEntry e) {
        return switch(activeTab) {
            case 1 -> e.type==EventType.CALL_MISSED;
            case 2 -> isCallType(e.type)&&!e.outgoing;
            case 3 -> isCallType(e.type)&&e.outgoing;
            default -> true;
        };
    }

    private boolean isCallType(EventType t) {
        return t==EventType.CALL_CONNECTED||t==EventType.CALL_MISSED||t==EventType.CALL_DECLINED;
    }

    private void buildRenderList() {
        renderList=new ArrayList<>(); totalH=0; String curDay=null;
        for (FilteredEntry fe : filtered) {
            String day=dayLabel(fe.entry().timestampMs);
            if (!day.equals(curDay)) { renderList.add(new RenderItem(true,day,null)); totalH+=DAY_H; curDay=day; }
            renderList.add(new RenderItem(false,null,fe));
            totalH += isCallType(fe.entry().type) ? CALL_H : SYS_H;
        }
        if (activeTab==1 && !filtered.isEmpty()) totalH+=60;
        maxScrollPx=Math.max(0,totalH-LIST_H);
        scrollPx=Math.min(scrollPx,maxScrollPx);
    }

    // ── Time helpers ──────────────────────────────────────────────────────────
    private String relTime(long tsMs) {
        long d=System.currentTimeMillis()-tsMs;
        if (d<60_000) return "JUST NOW";
        if (d<3_600_000) return (d/60_000)+"M AGO";
        if (d<86_400_000) return (d/3_600_000)+"H AGO";
        Calendar e=Calendar.getInstance(); e.setTimeInMillis(tsMs);
        Calendar y=Calendar.getInstance(); y.add(Calendar.DAY_OF_YEAR,-1);
        if (sameDay(e,y)) return String.format("YESTERDAY \u00b7 %02d:%02d",e.get(Calendar.HOUR_OF_DAY),e.get(Calendar.MINUTE));
        return String.format("%s %d \u00b7 %02d:%02d",MON[e.get(Calendar.MONTH)],e.get(Calendar.DAY_OF_MONTH),e.get(Calendar.HOUR_OF_DAY),e.get(Calendar.MINUTE));
    }

    private String dayLabel(long tsMs) {
        Calendar now=Calendar.getInstance(), e=Calendar.getInstance(); e.setTimeInMillis(tsMs);
        if (sameDay(now,e)) return "TODAY";
        now.add(Calendar.DAY_OF_YEAR,-1);
        if (sameDay(now,e)) return "YESTERDAY";
        return MON[e.get(Calendar.MONTH)]+" "+e.get(Calendar.DAY_OF_MONTH);
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR)&&a.get(Calendar.YEAR)==b.get(Calendar.YEAR);
    }

    private String fmtDur(int sec) { return String.format("%02d:%02d", sec/60, sec%60); }
}
