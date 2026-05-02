package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundInscribeMirrorPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MirrorPlacementScreen extends Screen {

    // ── Icon palette ──────────────────────────────────────────────────────────
    public static final int[] ICON_PALETTE = {
        // Row 1 — neutrals + primaries
        0x00000000, // 0:  transparent / erase
        0xFFFFFFFF, // 1:  white
        0xFFCCCCCC, // 2:  light grey
        0xFF888888, // 3:  grey
        0xFF444444, // 4:  dark grey
        0xFF111111, // 5:  black
        0xFFFF4444, // 6:  red
        0xFFFF8C00, // 7:  orange
        0xFFFFDD00, // 8:  yellow
        0xFF44CC44, // 9:  lime
        0xFF00AACC, // 10: cyan
        0xFF4488FF, // 11: blue
        0xFF8844FF, // 12: purple
        0xFFFF44CC, // 13: pink
        0xFFFFD700, // 14: gold
        0xFFAA77FF, // 15: lavender
        // Row 2 — expanded tones
        0xFF87CEEB, // 16: sky
        0xFF008080, // 17: teal
        0xFF228B22, // 18: forest
        0xFF808000, // 19: olive
        0xFF8B4513, // 20: brown
        0xFF8B0000, // 21: maroon
        0xFFB22222, // 22: rust
        0xFFFF7F50, // 23: coral
        0xFFFF00FF, // 24: magenta
        0xFFFFB6C1, // 25: rose
        0xFFFFF8DC, // 26: cream
        0xFFD2B48C, // 27: tan
        0xFF000080, // 28: navy
        0xFF4B0082, // 29: indigo
        0xFF98FF98, // 30: mint
        0xFFCB9ED2, // 31: lilac
    };
    public static final int ICON_W = 16;
    public static final int ICON_H = 16;
    public static final int ICON_BYTES = ICON_W * ICON_H; // 256
    public static final String[] PAL_NAMES = {
        // Row 1
        "Erase","White","L.Grey","Grey","D.Grey","Black",
        "Red","Orange","Yellow","Lime","Cyan","Blue",
        "Purple","Pink","Gold","Lav.",
        // Row 2
        "Sky","Teal","Forest","Olive","Brown","Maroon",
        "Rust","Coral","Magenta","Rose","Cream","Tan",
        "Navy","Indigo","Mint","Lilac"
    };

    // ── Dye swatches ──────────────────────────────────────────────────────────
    private static final int[] DYE_COLORS = {
        0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA,
        0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
        0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA,
        0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21
    };
    private static final String[] DYE_NAMES = {
        "White", "Orange", "Magenta", "Light Blue",
        "Yellow", "Lime", "Pink", "Gray",
        "Light Gray", "Cyan", "Purple", "Blue",
        "Brown", "Green", "Red", "Black"
    };

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int DC_BG          = 0xFF030012;
    private static final int DC_HEADER      = 0xFF020020;
    private static final int DC_BORDER_ACC  = 0xFF7733CC;
    private static final int DC_BORDER_BR   = 0xFFAA55FF;
    private static final int DC_BORDER_DIM  = 0xFF2A1144;
    private static final int DC_BORDER_DMR  = 0xFF1A0A30;
    private static final int DC_TEXT        = 0xFFE6DDF5;
    private static final int DC_MUTED       = 0xFF9999AA;
    private static final int DC_LAV         = 0xFFAA88FF;
    private static final int DC_GOLD        = 0xFFD4AF37;
    private static final int DC_GOLD_DIM    = 0xFF9A7520;

    // Privacy pill bg / border per level
    private static final int[] PRIV_BG  = { 0xFF8B1A1A, 0xFF7733CC, 0xFF1A6B6B, 0xFF9A7520 };
    private static final int[] PRIV_BR  = { 0xFFC73838, 0xFFAA55FF, 0xFF6FE0E0, 0xFFFFF8C0 };
    private static final String[] PRIV_LABELS = { "Locked", "View", "Call", "Enter" };
    private static final String[] PRIV_ICONS  = { "🔒", "◉", "☎", "➤" };

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int PANEL_W    = 360;
    private static final int HEADER_H   = 28;
    private static final int META_H     = 18;
    private static final int SEC_PAD_X  = 14;
    private static final int SEC_PAD_Y  = 8;
    private static final int LABEL_H    = 16;
    private static final int INPUT_H    = 22;
    private static final int PRIV_H     = 26;
    private static final int PRIV_GAP   = 3;
    private static final int SWATCH_GAP = 2;
    private static final int SWATCH_SZ  = (PANEL_W - SEC_PAD_X * 2 - 15 * SWATCH_GAP) / 16; // ~18
    private static final int DESC_H     = 40;
    private static final int ICON_CELL      = 4;
    private static final int ICON_CANVAS_PX = ICON_CELL * ICON_W; // 64
    private static final int PAL_ROWS       = 2;
    private static final int PAL_SW_SZ      = 14;
    private static final int PAL_SW_GAP     = 2;
    private static final int PAL_ROW_GAP    = 3;
    private static final int PAL_TOTAL_W    = 16 * PAL_SW_SZ + 15 * PAL_SW_GAP; // 254
    private static final int PAL_H          = PAL_ROWS * PAL_SW_SZ + (PAL_ROWS - 1) * PAL_ROW_GAP; // 31
    private static final int ICON_EDITOR_H  = ICON_CANVAS_PX + 4 + 8 + 4 + PAL_H; // 111
    private static final int TOGGLE_H   = 24;
    private static final int PREVIEW_H  = 44;
    private static final int FOOTER_H   = 30;

    // ── State ─────────────────────────────────────────────────────────────────
    private final BlockPos mirrorPos;
    private final String mirrorType;
    private final String dimension;
    private final int worldX, worldY, worldZ;

    private String name;
    private byte[] iconPixels = new byte[256];
    private int iconSelectedColor = 1;
    private boolean iconDragging = false;
    private int iconEditorX, iconEditorY;
    private int iconHoverX = -1, iconHoverY = -1;
    private int privacyLevel = 0; // 0=LOCKED
    private int dyeColorIndex = -1; // -1 = none
    private String description = "";
    private boolean pocketBound = true;

    // text cursor / edit state
    private boolean nameActive = false;
    private boolean descActive = false;
    private long cursorBlink = 0;

    // computed panel top-left
    private int pl, pt;
    private int totalH;

    public MirrorPlacementScreen(BlockPos mirrorPos, String mirrorType, String dimension, int x, int y, int z) {
        super(Component.literal("Inscribe Mirror"));
        this.mirrorPos = mirrorPos;
        this.mirrorType = mirrorType;
        this.dimension = dimension;
        this.worldX = x;
        this.worldY = y;
        this.worldZ = z;
        this.name = "Mirror " + Integer.toHexString(mirrorPos.hashCode() & 0xFFFF).toUpperCase();
    }

    @Override
    protected void init() {
        totalH = computeTotalH();
        pl = (width - PANEL_W) / 2;
        pt = (height - totalH) / 2;
        // Compute icon editor position: section starts after header+meta+name sections
        int iconSectionTop = pt + HEADER_H + META_H + (SEC_PAD_Y + LABEL_H + INPUT_H + SEC_PAD_Y) + SEC_PAD_Y + LABEL_H;
        iconEditorX = pl + SEC_PAD_X;
        iconEditorY = iconSectionTop;
    }

    private int computeTotalH() {
        int h = HEADER_H + META_H;
        h += SEC_PAD_Y + LABEL_H + INPUT_H  + SEC_PAD_Y; // name
        h += SEC_PAD_Y + LABEL_H + ICON_EDITOR_H + SEC_PAD_Y; // icon editor
        h += SEC_PAD_Y + LABEL_H + PRIV_H   + SEC_PAD_Y; // privacy
        h += SEC_PAD_Y + LABEL_H + SWATCH_SZ + SEC_PAD_Y; // tint
        h += SEC_PAD_Y + LABEL_H + DESC_H   + SEC_PAD_Y; // description
        if ("pocket".equals(mirrorType)) {
            h += SEC_PAD_Y + LABEL_H + TOGGLE_H + SEC_PAD_Y;
        }
        h += PREVIEW_H + FOOTER_H;
        return h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt2) {
        // Dim background
        g.fill(0, 0, width, height, 0xB0000000);

        int cur = pt;
        drawPanel(g, cur);
        cur += HEADER_H;
        cur = drawMeta(g, cur);
        cur = drawNameSection(g, cur);
        cur = drawIconSection(g, cur);
        cur = drawPrivacySection(g, cur);
        cur = drawTintSection(g, cur);
        cur = drawDescSection(g, cur);
        if ("pocket".equals(mirrorType)) {
            cur = drawPocketSection(g, cur);
        }
        drawPreview(g, cur);
        drawFooter(g, cur + PREVIEW_H);
        cursorBlink++;
    }

    // ── Panel chrome ─────────────────────────────────────────────────────────

    private void drawPanel(GuiGraphics g, int top) {
        g.fill(pl, top, pl + PANEL_W, top + totalH, DC_BG);
        // outer border
        hLine(g, pl, pl + PANEL_W - 1, top,              DC_BORDER_ACC);
        hLine(g, pl, pl + PANEL_W - 1, top + totalH - 1, DC_BORDER_ACC);
        vLine(g, pl,              top, top + totalH - 1, DC_BORDER_ACC);
        vLine(g, pl + PANEL_W - 1, top, top + totalH - 1, DC_BORDER_ACC);
        // inner border
        hLine(g, pl + 1, pl + PANEL_W - 2, top + 1,            DC_BORDER_BR);
        hLine(g, pl + 1, pl + PANEL_W - 2, top + totalH - 2,   DC_BORDER_BR);
        vLine(g, pl + 1,              top + 1, top + totalH - 2, DC_BORDER_BR);
        vLine(g, pl + PANEL_W - 2, top + 1, top + totalH - 2,   DC_BORDER_BR);
        // header bg
        g.fill(pl, top, pl + PANEL_W, top + HEADER_H, DC_HEADER);
        // header separator
        hLine(g, pl, pl + PANEL_W - 1, top + HEADER_H - 1, DC_BORDER_DIM);
        // header glyph + title
        g.drawString(font, "⌬", pl + 14, top + (HEADER_H - 8) / 2, DC_GOLD, false);
        g.drawString(font, "INSCRIBE MIRROR", pl + 30, top + (HEADER_H - 8) / 2, DC_GOLD, false);
        // header right tag
        String tag = "pocket".equals(mirrorType) ? "Pocket · New" : "New";
        int tw = font.width(tag);
        g.drawString(font, tag, pl + PANEL_W - 14 - tw, top + (HEADER_H - 8) / 2, DC_MUTED, false);
    }

    private int drawMeta(GuiGraphics g, int top) {
        g.fill(pl, top, pl + PANEL_W, top + META_H, 0x4C000000);
        hLine(g, pl, pl + PANEL_W - 1, top + META_H - 1, DC_BORDER_DIM);
        String dimLabel = dimDisplayName();
        String coords = "X " + worldX + "  Y " + worldY + "  Z " + worldZ;
        int x = pl + SEC_PAD_X;
        int y = top + (META_H - 8) / 2;
        x = drawMutedPart(g, x, y, "Coords ", DC_MUTED);
        x = drawMutedPart(g, x, y, coords, DC_LAV);
        x = drawMutedPart(g, x, y, "  ·  ", DC_BORDER_DIM);
        x = drawMutedPart(g, x, y, dimLabel, DC_GOLD);
        x = drawMutedPart(g, x, y, "  ·  ", DC_BORDER_DIM);
        drawMutedPart(g, x, y, "Owner  You", DC_MUTED);
        return top + META_H;
    }

    private String dimDisplayName() {
        if (dimension.contains("nether")) return "Nether";
        if (dimension.contains("end")) return "The End";
        if (dimension.contains("pocket")) return "Pocket";
        return "Overworld";
    }

    private int drawMutedPart(GuiGraphics g, int x, int y, String s, int color) {
        g.drawString(font, s, x, y, color, false);
        return x + font.width(s);
    }

    // ── Section 1: Name ──────────────────────────────────────────────────────

    private int drawNameSection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Name", "Required · 32 max", true);
        top += LABEL_H;

        int ix = pl + SEC_PAD_X, iw = PANEL_W - SEC_PAD_X * 2;
        int borderCol = nameActive ? DC_BORDER_BR : DC_BORDER_DIM;
        g.fill(ix, top, ix + iw, top + INPUT_H, 0x66000000);
        hLine(g, ix, ix + iw - 1, top,             borderCol);
        hLine(g, ix, ix + iw - 1, top + INPUT_H - 1, borderCol);
        vLine(g, ix,      top, top + INPUT_H - 1, borderCol);
        vLine(g, ix + iw - 1, top, top + INPUT_H - 1, borderCol);

        int ty = top + (INPUT_H - 8) / 2;
        String display = name;
        boolean showCursor = nameActive && (cursorBlink / 10) % 2 == 0;
        if (showCursor) display += "|";
        g.drawString(font, display, ix + 10, ty, DC_GOLD, false);

        String counter = name.length() + "/32";
        int cw = font.width(counter);
        int cCol = name.length() >= 32 ? 0xFFC73838 : name.length() >= 28 ? DC_GOLD : DC_MUTED;
        g.drawString(font, counter, ix + iw - 10 - cw, ty, cCol, false);

        top += INPUT_H;
        return sectionEnd(g, top);
    }

    // ── Section 2: Icon ───────────────────────────────────────────────────────

    private int drawIconSection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Icon", "Draw your icon", false);
        top += LABEL_H;

        // Center canvas in panel
        iconEditorY = top;
        iconEditorX = pl + (PANEL_W - ICON_CANVAS_PX) / 2;

        // Draw grid cells
        for (int py2 = 0; py2 < 16; py2++) {
            for (int px2 = 0; px2 < 16; px2++) {
                int col = ICON_PALETTE[iconPixels[py2 * 16 + px2] & 0xFF];
                int cx2 = iconEditorX + px2 * ICON_CELL;
                int cy2 = iconEditorY + py2 * ICON_CELL;
                if ((col >>> 24) == 0) {
                    g.fill(cx2, cy2, cx2 + ICON_CELL, cy2 + ICON_CELL,
                            ((px2 + py2) % 2 == 0) ? 0xFF2A2A3A : 0xFF222232);
                } else {
                    g.fill(cx2, cy2, cx2 + ICON_CELL, cy2 + ICON_CELL, col);
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

        // Double border: inner dim, outer accent
        int ce = iconEditorX + ICON_CANVAS_PX, cb = iconEditorY + ICON_CANVAS_PX;
        g.fill(iconEditorX - 1, iconEditorY - 1, ce + 1, iconEditorY,     DC_BORDER_DIM);
        g.fill(iconEditorX - 1, cb,              ce + 1, cb + 1,           DC_BORDER_DIM);
        g.fill(iconEditorX - 1, iconEditorY - 1, iconEditorX, cb + 1,     DC_BORDER_DIM);
        g.fill(ce,              iconEditorY - 1, ce + 1, cb + 1,           DC_BORDER_DIM);
        g.fill(iconEditorX - 2, iconEditorY - 2, ce + 2, iconEditorY - 1, DC_BORDER_ACC);
        g.fill(iconEditorX - 2, cb + 1,          ce + 2, cb + 2,           DC_BORDER_ACC);
        g.fill(iconEditorX - 2, iconEditorY - 2, iconEditorX - 1, cb + 2, DC_BORDER_ACC);
        g.fill(ce + 1,          iconEditorY - 2, ce + 2, cb + 2,           DC_BORDER_ACC);

        // Info strip: hint left, selected color name right
        int stripY = iconEditorY + ICON_CANVAS_PX + 4;
        g.drawString(font, "LMB paint \u00b7 RMB erase \u00b7 C clear",
                pl + SEC_PAD_X, stripY, DC_MUTED, false);
        String palName = PAL_NAMES[iconSelectedColor];
        int nameW = font.width(palName);
        int swX2 = pl + PANEL_W - SEC_PAD_X - nameW - 12;
        int swCol = (iconSelectedColor == 0) ? 0xFF444455 : ICON_PALETTE[iconSelectedColor];
        g.fill(swX2, stripY, swX2 + 8, stripY + 8, swCol);
        g.drawString(font, palName, swX2 + 10, stripY, DC_TEXT, false);

        // Palette: 2 rows of 16, centered
        int palY = stripY + 8 + 4;
        int palX = pl + (PANEL_W - PAL_TOTAL_W) / 2;
        for (int row = 0; row < PAL_ROWS; row++) {
            int rowY = palY + row * (PAL_SW_SZ + PAL_ROW_GAP);
            for (int col2 = 0; col2 < 16; col2++) {
                int c = row * 16 + col2;
                int sc = palX + col2 * (PAL_SW_SZ + PAL_SW_GAP);
                // Selected: gold halo
                if (c == iconSelectedColor) {
                    g.fill(sc - 2, rowY - 2, sc + PAL_SW_SZ + 2, rowY + PAL_SW_SZ + 2, DC_GOLD_DIM);
                    g.fill(sc - 1, rowY - 1, sc + PAL_SW_SZ + 1, rowY + PAL_SW_SZ + 1, DC_GOLD);
                }
                if (c == 0) {
                    int h = PAL_SW_SZ / 2;
                    g.fill(sc,     rowY,     sc + h, rowY + h, 0xFF444455);
                    g.fill(sc + h, rowY,     sc + PAL_SW_SZ, rowY + h, 0xFF2A2A3A);
                    g.fill(sc,     rowY + h, sc + h, rowY + PAL_SW_SZ, 0xFF2A2A3A);
                    g.fill(sc + h, rowY + h, sc + PAL_SW_SZ, rowY + PAL_SW_SZ, 0xFF444455);
                } else {
                    g.fill(sc, rowY, sc + PAL_SW_SZ, rowY + PAL_SW_SZ, ICON_PALETTE[c]);
                }
            }
        }

        top += ICON_EDITOR_H;
        return sectionEnd(g, top);
    }

    // ── Section 3: Privacy ───────────────────────────────────────────────────

    private int drawPrivacySection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Default Access", "For unlisted players", false);
        top += LABEL_H;

        int pw = (PANEL_W - SEC_PAD_X * 2 - 3 * PRIV_GAP) / 4;
        int px = pl + SEC_PAD_X;
        for (int i = 0; i < 4; i++) {
            int x = px + i * (pw + PRIV_GAP);
            boolean sel = i == privacyLevel;
            int bg  = sel ? (PRIV_BG[i]  | 0xFF000000) : 0x00000000;
            int bor = sel ? PRIV_BR[i] : DC_BORDER_DIM;
            g.fill(x, top, x + pw, top + PRIV_H, bg);
            hLine(g, x, x + pw - 1, top,           bor);
            hLine(g, x, x + pw - 1, top + PRIV_H - 1, bor);
            vLine(g, x,      top, top + PRIV_H - 1, bor);
            vLine(g, x + pw - 1, top, top + PRIV_H - 1, bor);
            int tc = sel ? DC_TEXT : DC_MUTED;
            String label = PRIV_ICONS[i] + " " + PRIV_LABELS[i];
            int lw = font.width(label);
            g.drawString(font, label, x + (pw - lw) / 2, top + (PRIV_H - 8) / 2, tc, false);
        }
        top += PRIV_H;
        return sectionEnd(g, top);
    }

    // ── Section 4: Tint ──────────────────────────────────────────────────────

    private int drawTintSection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Tint", "Frame color", false);
        top += LABEL_H;

        int sw = SWATCH_SZ;
        int sx = pl + SEC_PAD_X;
        for (int i = 0; i < 16; i++) {
            int x = sx + i * (sw + SWATCH_GAP);
            boolean sel = i == dyeColorIndex;
            g.fill(x, top, x + sw, top + sw, DYE_COLORS[i]);
            int bor = sel ? DC_GOLD : (DYE_COLORS[i] & 0x00FFFFFF) | 0x66000000;
            hLine(g, x, x + sw - 1, top,      bor);
            hLine(g, x, x + sw - 1, top + sw - 1, bor);
            vLine(g, x,      top, top + sw - 1, bor);
            vLine(g, x + sw - 1, top, top + sw - 1, bor);
            if (sel) {
                hLine(g, x - 1, x + sw, top - 1,    DC_GOLD);
                hLine(g, x - 1, x + sw, top + sw,   DC_GOLD);
                vLine(g, x - 1,     top - 1, top + sw, DC_GOLD);
                vLine(g, x + sw, top - 1, top + sw, DC_GOLD);
            }
        }
        top += sw;
        return sectionEnd(g, top);
    }

    // ── Section 5: Description ───────────────────────────────────────────────

    private int drawDescSection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Description", "Optional · 128 max", false);
        top += LABEL_H;

        int dx = pl + SEC_PAD_X, dw = PANEL_W - SEC_PAD_X * 2;
        int borderCol = descActive ? DC_BORDER_BR : DC_BORDER_DIM;
        g.fill(dx, top, dx + dw, top + DESC_H, 0x66000000);
        hLine(g, dx, dx + dw - 1, top,           borderCol);
        hLine(g, dx, dx + dw - 1, top + DESC_H - 1, borderCol);
        vLine(g, dx,       top, top + DESC_H - 1, borderCol);
        vLine(g, dx + dw - 1, top, top + DESC_H - 1, borderCol);

        String dispDesc = description.isEmpty() ? (descActive ? "" : "Notes about this mirror…") : description;
        int descColor = description.isEmpty() ? (DC_MUTED & 0x00FFFFFF) | 0x99000000 : DC_TEXT;
        String descLine = dispDesc;
        boolean showCursor = descActive && (cursorBlink / 10) % 2 == 0;
        if (showCursor && !description.isEmpty()) descLine += "|";
        g.drawString(font, descLine, dx + 10, top + 6, descColor, false);

        // Bottom meta row
        int metaY = top + DESC_H - 12;
        g.drawString(font, "Visible to players with VIEW or higher", dx + 10, metaY, DC_MUTED, false);
        String dCounter = description.length() + "/128";
        int dcol = description.length() >= 128 ? 0xFFC73838 : description.length() >= 112 ? DC_GOLD : DC_MUTED;
        int dcw = font.width(dCounter);
        g.drawString(font, dCounter, dx + dw - 10 - dcw, metaY, dcol, false);

        top += DESC_H;
        return sectionEnd(g, top);
    }

    // ── Section 6: Pocket toggle ─────────────────────────────────────────────

    private int drawPocketSection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Pocket Dimension", "Mirror type · pocket only", false);
        top += LABEL_H;

        int tx = pl + SEC_PAD_X;
        int ty = top + (TOGGLE_H - 18) / 2;
        // Toggle box 36×18
        int tbg = pocketBound ? 0x40B07AFF : 0x66000000;
        int tbr = pocketBound ? DC_LAV : DC_BORDER_DIM;
        g.fill(tx, ty, tx + 36, ty + 18, tbg);
        hLine(g, tx, tx + 35, ty,      tbr);
        hLine(g, tx, tx + 35, ty + 17, tbr);
        vLine(g, tx,      ty, ty + 17, tbr);
        vLine(g, tx + 35, ty, ty + 17, tbr);
        // Knob 14×14
        int kx = pocketBound ? tx + 21 : tx + 1;
        int kc = pocketBound ? DC_LAV : DC_MUTED;
        g.fill(kx, ty + 2, kx + 14, ty + 16, kc);

        int labelX = tx + 46;
        g.drawString(font, "Bind to Pocket", labelX, top + (TOGGLE_H - 16) / 2, DC_TEXT, false);
        g.drawString(font, "Generates a 32\u00D732 personal pocket on activation",
                labelX, top + (TOGGLE_H - 16) / 2 + 10, DC_MUTED, false);

        // "Pocket" tag chip
        String chip = "Pocket";
        int chipW = font.width(chip) + 12;
        int chipX = pl + PANEL_W - SEC_PAD_X - chipW;
        int chipY = top + (TOGGLE_H - 14) / 2;
        g.fill(chipX, chipY, chipX + chipW, chipY + 14, 0x14B07AFF);
        hLine(g, chipX, chipX + chipW - 1, chipY,      0x66B07AFF);
        hLine(g, chipX, chipX + chipW - 1, chipY + 13, 0x66B07AFF);
        vLine(g, chipX,           chipY, chipY + 13, 0x66B07AFF);
        vLine(g, chipX + chipW - 1, chipY, chipY + 13, 0x66B07AFF);
        g.drawString(font, chip, chipX + 6, chipY + 3, DC_LAV, false);

        top += TOGGLE_H;
        return sectionEnd(g, top);
    }

    // ── Preview row ──────────────────────────────────────────────────────────

    private void drawPreview(GuiGraphics g, int top) {
        g.fill(pl, top, pl + PANEL_W, top + PREVIEW_H, 0x4C000000);
        hLine(g, pl, pl + PANEL_W - 1, top,              DC_BORDER_DIM);
        hLine(g, pl, pl + PANEL_W - 1, top + PREVIEW_H - 1, DC_BORDER_DIM);

        // Icon preview box 36x36 (icon centered within)
        int glyphColor = dyeColorIndex >= 0 ? DYE_COLORS[dyeColorIndex] : DC_GOLD;
        int gx = pl + 12, gy = top + (PREVIEW_H - 36) / 2;
        g.fill(gx, gy, gx + 36, gy + 36, 0x66000000);
        hLine(g, gx, gx + 35, gy,      glyphColor & 0x66FFFFFF | 0x66000000);
        hLine(g, gx, gx + 35, gy + 35, glyphColor & 0x66FFFFFF | 0x66000000);
        vLine(g, gx,      gy, gy + 35, glyphColor & 0x66FFFFFF | 0x66000000);
        vLine(g, gx + 35, gy, gy + 35, glyphColor & 0x66FFFFFF | 0x66000000);
        // Draw icon at 1:1 scale centered in 36x36 box
        int iconOffX = gx + (36 - 16) / 2;
        int iconOffY = gy + (36 - 16) / 2;
        boolean hasAnyPixel = false;
        for (byte b : iconPixels) if (b != 0) { hasAnyPixel = true; break; }
        if (hasAnyPixel) {
            for (int py = 0; py < 16; py++) {
                for (int px = 0; px < 16; px++) {
                    int col = ICON_PALETTE[iconPixels[py * 16 + px] & 0xFF];
                    if ((col >>> 24) != 0) {
                        g.fill(iconOffX + px, iconOffY + py, iconOffX + px + 1, iconOffY + py + 1, col);
                    }
                }
            }
        }

        // Name + meta text
        int textX = gx + 36 + 10;
        int nameColor = dyeColorIndex >= 0 ? DYE_COLORS[dyeColorIndex] : DC_GOLD;
        g.drawString(font, name.isEmpty() ? "—" : name.toUpperCase(), textX, top + 10, nameColor, false);

        // Access pill
        int metaY = top + 24;
        int px2 = textX;
        String privLabel = PRIV_LABELS[privacyLevel];
        int pillW = font.width(privLabel) + 10;
        g.fill(px2, metaY - 1, px2 + pillW, metaY + 9, PRIV_BG[privacyLevel] | 0xFF000000);
        hLine(g, px2, px2 + pillW - 1, metaY - 1, PRIV_BR[privacyLevel]);
        hLine(g, px2, px2 + pillW - 1, metaY + 9,  PRIV_BR[privacyLevel]);
        vLine(g, px2,           metaY - 1, metaY + 9, PRIV_BR[privacyLevel]);
        vLine(g, px2 + pillW - 1, metaY - 1, metaY + 9, PRIV_BR[privacyLevel]);
        g.drawString(font, privLabel, px2 + 5, metaY, DC_TEXT, false);
        px2 += pillW + 6;

        // Tint name
        if (dyeColorIndex >= 0) {
            g.drawString(font, "· Tint ", px2, metaY, DC_MUTED, false);
            px2 += font.width("· Tint ");
            g.drawString(font, DYE_NAMES[dyeColorIndex], px2, metaY, DYE_COLORS[dyeColorIndex], false);
            px2 += font.width(DYE_NAMES[dyeColorIndex]) + 6;
        }

        // Pocket-bound label
        if ("pocket".equals(mirrorType) && pocketBound) {
            g.drawString(font, "· Pocket-bound", px2, metaY, DC_LAV, false);
        } else {
            g.drawString(font, "· Owner  You", px2, metaY, DC_MUTED, false);
        }
    }

    // ── Footer ───────────────────────────────────────────────────────────────

    private void drawFooter(GuiGraphics g, int top) {
        g.fill(pl, top, pl + PANEL_W, top + FOOTER_H, 0x66000000);
        hLine(g, pl, pl + PANEL_W - 1, top, DC_BORDER_DIM);

        // Cost text (left)
        String cost = "pocket".equals(mirrorType) ? "1 Lapis + 4 Pearls" : "1 Lapis";
        g.drawString(font, cost, pl + 14, top + (FOOTER_H - 8) / 2, DC_MUTED, false);

        // CANCEL button
        int btnY = top + (FOOTER_H - 20) / 2;
        String cancelStr = "Cancel [ESC]";
        int cancelW = font.width(cancelStr) + 16;
        int cancelX = pl + PANEL_W - 14 - cancelW;
        drawButton(g, cancelX, btnY, cancelW, 20, cancelStr, DC_MUTED, DC_BORDER_DIM, false);

        // ACTIVATE button
        boolean canActivate = !name.isEmpty();
        String activateStr = "Activate [\u21B5]";
        int activateW = font.width(activateStr) + 24;
        int activateX = cancelX - 8 - activateW;
        int actColor = canActivate ? DC_GOLD : DC_MUTED;
        int actBorder = canActivate ? DC_GOLD_DIM : DC_BORDER_DIM;
        drawButton(g, activateX, btnY, activateW, 20, activateStr, actColor, actBorder, canActivate);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int color, int border, boolean gold) {
        int bg = gold ? 0x2AD4AF37 : 0x00000000;
        g.fill(x, y, x + w, y + h, bg);
        hLine(g, x, x + w - 1, y,      border);
        hLine(g, x, x + w - 1, y + h - 1, border);
        vLine(g, x,      y, y + h - 1, border);
        vLine(g, x + w - 1, y, y + h - 1, border);
        int lw = font.width(label);
        g.drawString(font, label, x + (w - lw) / 2, y + (h - 8) / 2, color, false);
    }

    // ── Section helpers ──────────────────────────────────────────────────────

    private int sectionStart(GuiGraphics g, int top) {
        return top + SEC_PAD_Y;
    }

    private int sectionEnd(GuiGraphics g, int top) {
        int y = top + SEC_PAD_Y;
        hLine(g, pl, pl + PANEL_W - 1, y - 1, DC_BORDER_DMR);
        return y;
    }

    private void drawLabel(GuiGraphics g, int x, int y, String label, String hint, boolean required) {
        g.drawString(font, label.toUpperCase(), x, y + (LABEL_H - 8) / 2, DC_GOLD_DIM, false);
        if (required) {
            int lw = font.width(label.toUpperCase());
            g.drawString(font, "*", x + lw + 3, y + (LABEL_H - 8) / 2, 0xFFC73838, false);
        }
        int hw = font.width(hint);
        g.drawString(font, hint, pl + SEC_PAD_X + PANEL_W - SEC_PAD_X * 2 - hw,
                y + (LABEL_H - 8) / 2, DC_MUTED, false);
    }

    // ── Line drawing helpers ──────────────────────────────────────────────────

    private static void hLine(GuiGraphics g, int x1, int x2, int y, int color) {
        if (x2 < x1) { int t = x1; x1 = x2; x2 = t; }
        g.fill(x1, y, x2 + 1, y + 1, color);
    }

    private static void vLine(GuiGraphics g, int x, int y1, int y2, int color) {
        if (y2 < y1) { int t = y1; y1 = y2; y2 = t; }
        g.fill(x, y1, x + 1, y2 + 1, color);
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cur = pt + HEADER_H + META_H;

        // Name input click zone
        int nameTop = cur + SEC_PAD_Y + LABEL_H;
        int nameBot = nameTop + INPUT_H;
        if (mx >= pl + SEC_PAD_X && mx <= pl + PANEL_W - SEC_PAD_X
                && my >= nameTop && my <= nameBot) {
            nameActive = true;
            descActive = false;
            return true;
        }
        cur = nameBot + SEC_PAD_Y;

        // Icon editor clicks
        int iconTop = cur + SEC_PAD_Y + LABEL_H;
        int iconBot = iconTop + ICON_EDITOR_H;
        if (my >= iconTop && my <= iconBot) {
            if (handleIconEditPlacement(mx, my, button)) {
                iconDragging = true;
                nameActive = false; descActive = false;
                return true;
            }
        }
        cur = iconBot + SEC_PAD_Y;

        // Privacy pills
        int privTop = cur + SEC_PAD_Y + LABEL_H;
        int privBot = privTop + PRIV_H;
        if (my >= privTop && my <= privBot) {
            int pw = (PANEL_W - SEC_PAD_X * 2 - 3 * PRIV_GAP) / 4;
            int px = pl + SEC_PAD_X;
            for (int i = 0; i < 4; i++) {
                int x = px + i * (pw + PRIV_GAP);
                if (mx >= x && mx <= x + pw) {
                    privacyLevel = i;
                    nameActive = false; descActive = false;
                    return true;
                }
            }
        }
        cur = privBot + SEC_PAD_Y;

        // Tint swatches
        int tintTop = cur + SEC_PAD_Y + LABEL_H;
        int tintBot = tintTop + SWATCH_SZ;
        if (my >= tintTop && my <= tintBot) {
            int sx = pl + SEC_PAD_X;
            for (int i = 0; i < 16; i++) {
                int x = sx + i * (SWATCH_SZ + SWATCH_GAP);
                if (mx >= x && mx <= x + SWATCH_SZ) {
                    dyeColorIndex = (dyeColorIndex == i) ? -1 : i;
                    nameActive = false; descActive = false;
                    return true;
                }
            }
        }
        cur = tintBot + SEC_PAD_Y;

        // Description box
        int descTop = cur + SEC_PAD_Y + LABEL_H;
        int descBot = descTop + DESC_H;
        if (mx >= pl + SEC_PAD_X && mx <= pl + PANEL_W - SEC_PAD_X
                && my >= descTop && my <= descBot) {
            descActive = true;
            nameActive = false;
            return true;
        }
        cur = descBot + SEC_PAD_Y;

        // Pocket toggle
        if ("pocket".equals(mirrorType)) {
            int togTop = cur + SEC_PAD_Y + LABEL_H;
            int togBot = togTop + TOGGLE_H;
            int tx = pl + SEC_PAD_X;
            if (mx >= tx && mx <= tx + 36 && my >= togTop + (TOGGLE_H - 18) / 2
                    && my <= togTop + (TOGGLE_H - 18) / 2 + 18) {
                pocketBound = !pocketBound;
                return true;
            }
            cur = togBot + SEC_PAD_Y;
        }

        // Footer buttons
        int footerTop = pt + totalH - FOOTER_H;
        if (my >= footerTop && my <= footerTop + FOOTER_H) {
            // ACTIVATE
            if (!name.isEmpty()) {
                int btnY = footerTop + (FOOTER_H - 20) / 2;
                String cancelStr = "Cancel [ESC]";
                int cancelW = font.width(cancelStr) + 16;
                int cancelX = pl + PANEL_W - 14 - cancelW;
                String activateStr = "Activate [\u21B5]";
                int activateW = font.width(activateStr) + 24;
                int activateX = cancelX - 8 - activateW;
                if (mx >= activateX && mx <= activateX + activateW && my >= btnY && my <= btnY + 20) {
                    sendActivate();
                    return true;
                }
            }
        }

        nameActive = false;
        descActive = false;
        return super.mouseClicked(mx, my, button);
    }

    private boolean handleIconEditPlacement(double mouseX, double mouseY, int button) {
        // Canvas hit
        if (mouseX >= iconEditorX && mouseX < iconEditorX + ICON_CANVAS_PX
                && mouseY >= iconEditorY && mouseY < iconEditorY + ICON_CANVAS_PX) {
            int px2 = ((int)mouseX - iconEditorX) / ICON_CELL;
            int py2 = ((int)mouseY - iconEditorY) / ICON_CELL;
            if (button == 0) iconPixels[py2 * 16 + px2] = (byte) iconSelectedColor;
            else if (button == 1) iconPixels[py2 * 16 + px2] = 0;
            return true;
        }
        // Palette hit (2 rows)
        int palY = iconEditorY + ICON_CANVAS_PX + 16; // canvas + 4 + strip(8) + 4
        int palX = pl + (PANEL_W - PAL_TOTAL_W) / 2;
        for (int row = 0; row < PAL_ROWS; row++) {
            int rowY = palY + row * (PAL_SW_SZ + PAL_ROW_GAP);
            if (mouseY >= rowY && mouseY < rowY + PAL_SW_SZ) {
                int col2 = ((int)mouseX - palX) / (PAL_SW_SZ + PAL_SW_GAP);
                if (col2 >= 0 && col2 < 16 && mouseX >= palX + col2 * (PAL_SW_SZ + PAL_SW_GAP)
                        && mouseX < palX + col2 * (PAL_SW_SZ + PAL_SW_GAP) + PAL_SW_SZ) {
                    iconSelectedColor = row * 16 + col2;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (mouseX >= iconEditorX && mouseX < iconEditorX + ICON_CANVAS_PX
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (iconDragging) { handleIconEditPlacement(mouseX, mouseY, button); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        iconDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // C key clears canvas
        if (keyCode == 67 && modifiers == 0) {
            java.util.Arrays.fill(iconPixels, (byte) 0);
            return true;
        }
        // ESC
        if (keyCode == 256) { onClose(); return true; }
        // Enter → activate
        if (keyCode == 257 || keyCode == 335) {
            if (!name.isEmpty()) { sendActivate(); return true; }
            return true;
        }
        if (nameActive) {
            if (keyCode == 259 && !name.isEmpty()) { // Backspace
                name = name.substring(0, name.length() - 1);
                return true;
            }
        }
        if (descActive) {
            if (keyCode == 259 && !description.isEmpty()) {
                description = description.substring(0, description.length() - 1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (nameActive && font.width(name) < 400 && name.length() < 32) {
            if (c >= 32) { name += c; return true; }
        }
        if (descActive && description.length() < 128) {
            if (c >= 32 || c == '\n') { description += c; return true; }
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Activate ─────────────────────────────────────────────────────────────

    private void sendActivate() {
        MirrorsNetwork.sendToServer(new ServerboundInscribeMirrorPacket(
                mirrorPos, name.strip(), iconPixels, privacyLevel, dyeColorIndex, description, pocketBound));
        onClose();
    }
}
