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

    // ── Sigils ────────────────────────────────────────────────────────────────
    static final String[] SIGILS = { "✦", "◈", "⚿", "☽", "☾", "⌬", "✧", "⟁", "⌖", "⍟", "⊛", "❂" };

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
    private static final int PANEL_W   = 520;
    private static final int HEADER_H  = 32;
    private static final int META_H    = 22;
    private static final int SEC_PAD_X = 16;
    private static final int SEC_PAD_Y = 14;
    private static final int INPUT_H   = 28;
    private static final int GLYPH_COLS = 6;
    private static final int GLYPH_SIZE = (PANEL_W - SEC_PAD_X * 2 - 5 * 6) / GLYPH_COLS; // ~74
    private static final int PRIV_H    = 30;
    private static final int SWATCH_SZ = (PANEL_W - SEC_PAD_X * 2 - 15 * 4) / 16; // ~26
    private static final int DESC_H    = 48;
    private static final int TOGGLE_H  = 32;
    private static final int PREVIEW_H = 76;
    private static final int FOOTER_H  = 38;
    private static final int LABEL_H   = 20;

    // ── State ─────────────────────────────────────────────────────────────────
    private final BlockPos mirrorPos;
    private final String mirrorType;
    private final String dimension;
    private final int worldX, worldY, worldZ;

    private String name;
    private int sigilIndex = 1; // default ◈
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
    }

    private int computeTotalH() {
        int h = HEADER_H + META_H;
        // Section 1: name
        h += SEC_PAD_Y + LABEL_H + INPUT_H + SEC_PAD_Y;
        // Section 2: sigil
        int glyphRows = (SIGILS.length + GLYPH_COLS - 1) / GLYPH_COLS;
        h += SEC_PAD_Y + LABEL_H + glyphRows * GLYPH_SIZE + (glyphRows - 1) * 6 + SEC_PAD_Y;
        // Section 3: privacy
        h += SEC_PAD_Y + LABEL_H + PRIV_H + SEC_PAD_Y;
        // Section 4: tint
        h += SEC_PAD_Y + LABEL_H + SWATCH_SZ + SEC_PAD_Y;
        // Section 5: description
        h += SEC_PAD_Y + LABEL_H + DESC_H + SEC_PAD_Y;
        // Section 6: pocket toggle (pocket only)
        if ("pocket".equals(mirrorType)) {
            h += SEC_PAD_Y + LABEL_H + TOGGLE_H + SEC_PAD_Y;
        }
        // Preview + footer
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
        cur = drawSigilSection(g, cur);
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

    // ── Section 2: Sigil ─────────────────────────────────────────────────────

    private int drawSigilSection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Sigil", "Choose 1", false);
        top += LABEL_H;

        int gx = pl + SEC_PAD_X;
        int cellW = (PANEL_W - SEC_PAD_X * 2 - (GLYPH_COLS - 1) * 6) / GLYPH_COLS;
        int cellH = cellW;
        for (int i = 0; i < SIGILS.length; i++) {
            int col = i % GLYPH_COLS;
            int row = i / GLYPH_COLS;
            int cx = gx + col * (cellW + 6);
            int cy = top + row * (cellH + 6);
            boolean sel = i == sigilIndex;
            int bg  = sel ? 0x26D4AF37 : 0x66000000;
            int bor = sel ? DC_GOLD : DC_BORDER_DIM;
            g.fill(cx, cy, cx + cellW, cy + cellH, bg);
            hLine(g, cx, cx + cellW - 1, cy,           bor);
            hLine(g, cx, cx + cellW - 1, cy + cellH - 1, bor);
            vLine(g, cx,          cy, cy + cellH - 1, bor);
            vLine(g, cx + cellW - 1, cy, cy + cellH - 1, bor);
            int tc = sel ? DC_GOLD : DC_MUTED;
            int sw = font.width(SIGILS[i]);
            g.drawString(font, SIGILS[i], cx + (cellW - sw) / 2, cy + (cellH - 8) / 2, tc, false);
        }
        int rows = (SIGILS.length + GLYPH_COLS - 1) / GLYPH_COLS;
        top += rows * cellH + (rows - 1) * 6;
        return sectionEnd(g, top);
    }

    // ── Section 3: Privacy ───────────────────────────────────────────────────

    private int drawPrivacySection(GuiGraphics g, int top) {
        top = sectionStart(g, top);
        drawLabel(g, pl + SEC_PAD_X, top, "Default Access", "For unlisted players", false);
        top += LABEL_H;

        int pw = (PANEL_W - SEC_PAD_X * 2 - 3 * 4) / 4;
        int px = pl + SEC_PAD_X;
        for (int i = 0; i < 4; i++) {
            int x = px + i * (pw + 4);
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

        int sw = (PANEL_W - SEC_PAD_X * 2 - 15 * 4) / 16;
        int sx = pl + SEC_PAD_X;
        for (int i = 0; i < 16; i++) {
            int x = sx + i * (sw + 4);
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

        // Glyph box 56×56
        int glyphColor = dyeColorIndex >= 0 ? DYE_COLORS[dyeColorIndex] : DC_GOLD;
        int gx = pl + 16, gy = top + (PREVIEW_H - 56) / 2;
        g.fill(gx, gy, gx + 56, gy + 56, 0x66000000);
        hLine(g, gx, gx + 55, gy,      glyphColor & 0x66FFFFFF | 0x66000000);
        hLine(g, gx, gx + 55, gy + 55, glyphColor & 0x66FFFFFF | 0x66000000);
        vLine(g, gx,      gy, gy + 55, glyphColor & 0x66FFFFFF | 0x66000000);
        vLine(g, gx + 55, gy, gy + 55, glyphColor & 0x66FFFFFF | 0x66000000);
        String sig = SIGILS[sigilIndex];
        int sigW = font.width(sig);
        // Draw sigil larger by scaling — approximate with drawString (no scaling in 1.20.1 easy API)
        g.drawString(font, sig, gx + (56 - sigW) / 2, gy + 24, glyphColor, false);

        // Name + meta text
        int textX = gx + 56 + 14;
        int nameColor = dyeColorIndex >= 0 ? DYE_COLORS[dyeColorIndex] : DC_GOLD;
        g.drawString(font, name.isEmpty() ? "—" : name.toUpperCase(), textX, top + 18, nameColor, false);

        // Access pill
        int metaY = top + 34;
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
        String cost = "pocket".equals(mirrorType)
                ? "Activation cost  1 \u2736 Lapis + 4 \u2736 Ender Pearl"
                : "Activation cost  1 \u2736 Lapis";
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

        // Sigil grid clicks
        int sigilTop = cur + SEC_PAD_Y + LABEL_H;
        int cellW = (PANEL_W - SEC_PAD_X * 2 - (GLYPH_COLS - 1) * 6) / GLYPH_COLS;
        int cellH = cellW;
        int rows = (SIGILS.length + GLYPH_COLS - 1) / GLYPH_COLS;
        int sigilBot = sigilTop + rows * cellH + (rows - 1) * 6;
        if (my >= sigilTop && my <= sigilBot) {
            int gx = pl + SEC_PAD_X;
            for (int i = 0; i < SIGILS.length; i++) {
                int col = i % GLYPH_COLS;
                int row = i / GLYPH_COLS;
                int cx = gx + col * (cellW + 6);
                int cy = sigilTop + row * (cellH + 6);
                if (mx >= cx && mx <= cx + cellW && my >= cy && my <= cy + cellH) {
                    sigilIndex = i;
                    nameActive = false; descActive = false;
                    return true;
                }
            }
        }
        cur = sigilBot + SEC_PAD_Y;

        // Privacy pills
        int privTop = cur + SEC_PAD_Y + LABEL_H;
        int privBot = privTop + PRIV_H;
        if (my >= privTop && my <= privBot) {
            int pw = (PANEL_W - SEC_PAD_X * 2 - 3 * 4) / 4;
            int px = pl + SEC_PAD_X;
            for (int i = 0; i < 4; i++) {
                int x = px + i * (pw + 4);
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
        int sw = (PANEL_W - SEC_PAD_X * 2 - 15 * 4) / 16;
        int tintBot = tintTop + sw;
        if (my >= tintTop && my <= tintBot) {
            int sx = pl + SEC_PAD_X;
            for (int i = 0; i < 16; i++) {
                int x = sx + i * (sw + 4);
                if (mx >= x && mx <= x + sw) {
                    dyeColorIndex = (dyeColorIndex == i) ? -1 : i; // toggle off on re-click
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
                mirrorPos, name.strip(), sigilIndex, privacyLevel, dyeColorIndex, description, pocketBound));
        onClose();
    }
}
