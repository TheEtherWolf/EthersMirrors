package com.ether.mirrors.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared visual constants and drawing helpers for Ether's Mirrors UI.
 * Theme: Dark Arcane — deep purples, antique gold, soft glow.
 */
public final class UITheme {

    private UITheme() {}

    // ── Panel & Border ────────────────────────────────────────────────────────
    public static final int PANEL_FILL      = 0xF2030012;
    public static final int PANEL_HEADER    = 0xF5020020;
    public static final int BORDER_OUTER    = 0xFF0E0030;
    public static final int BORDER_MID      = 0xFF3B1070;
    public static final int BORDER_ACCENT   = 0xFF7733CC;
    public static final int BORDER_BRIGHT   = 0xFFAA55FF;
    public static final int GLOW_SOFT       = 0x40883FCC;
    public static final int GLOW_MED        = 0x60993FEE;
    public static final int CORNER_GEM      = 0xFFD4AF37;  // antique gold

    // ── Text ──────────────────────────────────────────────────────────────────
    public static final int TEXT_GOLD       = 0xFFD4AF37;
    public static final int TEXT_LAVENDER   = 0xFFAA88FF;
    public static final int TEXT_WHITE      = 0xFFE8E8F0;
    public static final int TEXT_MUTED      = 0xFF9999AA;
    public static final int TEXT_OWN        = 0xFF55FF88;   // own mirror — mint green
    public static final int TEXT_OTHER      = 0xFF55CCFF;   // others' — sky blue

    // ── Range / Signal ────────────────────────────────────────────────────────
    public static final int SIGNAL_FULL     = 0xFF44FF77;
    public static final int SIGNAL_GOOD     = 0xFF99EE44;
    public static final int SIGNAL_MED      = 0xFFFFCC33;
    public static final int SIGNAL_LOW      = 0xFFFF8833;
    public static final int SIGNAL_DEAD     = 0xFFFF3344;
    public static final int SIGNAL_EMPTY    = 0xFF1C1C2C;

    // ── Row ───────────────────────────────────────────────────────────────────
    public static final int ROW_ALT         = 0x14FFFFFF;
    public static final int ROW_HOVER       = 0x25AA66EE;

    // ── Button themes ─────────────────────────────────────────────────────────
    public static final int BTN_PURPLE      = 0xFF7733CC;
    public static final int BTN_GOLD        = 0xFF9A7520;
    public static final int BTN_GREEN       = 0xFF1A7B2B;
    public static final int BTN_RED         = 0xFF8B1A1A;
    public static final int BTN_TEAL        = 0xFF1A6B6B;

    // =========================================================================
    //  Drawing helpers
    // =========================================================================

    /** Full floating panel: dark fill + glowing layered border + corner gems. */
    public static void drawPanel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        // Soft outer glow
        g.fill(x1 - 3, y1 - 3, x2 + 3, y2 + 3, GLOW_SOFT);
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, GLOW_MED);

        // Panel body
        g.fill(x1, y1, x2, y2, PANEL_FILL);

        // Dark outer border
        g.fill(x1 - 1, y1 - 1, x2 + 1, y1,     BORDER_OUTER);
        g.fill(x1 - 1, y2,     x2 + 1, y2 + 1,  BORDER_OUTER);
        g.fill(x1 - 1, y1 - 1, x1,     y2 + 1,  BORDER_OUTER);
        g.fill(x2,     y1 - 1, x2 + 1, y2 + 1,  BORDER_OUTER);

        // Mid-purple frame
        g.fill(x1, y1, x2, y1 + 1,     BORDER_MID);
        g.fill(x1, y2 - 1, x2, y2,     BORDER_MID);
        g.fill(x1, y1, x1 + 1, y2,     BORDER_MID);
        g.fill(x2 - 1, y1, x2, y2,     BORDER_MID);

        // Inner accent highlight
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2,   BORDER_ACCENT);
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1,   BORDER_ACCENT);
        g.fill(x1 + 1, y1 + 2, x1 + 2, y2 - 2,   BORDER_ACCENT);
        g.fill(x2 - 2, y1 + 2, x2 - 1, y2 - 2,   BORDER_ACCENT);

        // Corner gems
        gem(g, x1,     y1);
        gem(g, x2 - 3, y1);
        gem(g, x1,     y2 - 3);
        gem(g, x2 - 3, y2 - 3);
    }

    /** Header strip inside a panel (drawn after drawPanel). */
    public static void drawHeader(GuiGraphics g, int x1, int y1, int x2, int headerH) {
        g.fill(x1 + 2, y1 + 2, x2 - 2, y1 + headerH, PANEL_HEADER);
        // Bottom separator line
        g.fill(x1 + 8,  y1 + headerH,     x2 - 8,  y1 + headerH + 1, BORDER_ACCENT);
        g.fill(x1 + 20, y1 + headerH + 1, x2 - 20, y1 + headerH + 2, BORDER_MID);
    }

    /** Decorative horizontal rule — glows brighter in the middle. */
    public static void drawRule(GuiGraphics g, int x1, int x2, int y) {
        int mid = (x1 + x2) / 2;
        g.fill(mid - 70, y, mid + 70, y + 1, BORDER_ACCENT);
        g.fill(mid - 110, y, mid - 70, y + 1, BORDER_MID);
        g.fill(mid + 70,  y, mid + 110, y + 1, BORDER_MID);
        g.fill(mid - 25, y, mid + 25, y + 1, BORDER_BRIGHT);
    }

    /** Row background — alternating shade + hover highlight. */
    public static void drawRow(GuiGraphics g, int x1, int y1, int x2, int y2,
                                boolean alternate, boolean hovered) {
        if (hovered) {
            g.fill(x1, y1, x2, y2, ROW_HOVER);
        } else if (alternate) {
            g.fill(x1, y1, x2, y2, ROW_ALT);
        }
        // Subtle bottom rule per row
        g.fill(x1 + 2, y2 - 1, x2 - 2, y2, 0x18FFFFFF);
    }

    /**
     * Signal strength bars — 5 bars, varying height and color.
     * x/y is the bottom-left of the bar group.
     */
    public static void drawSignalBars(GuiGraphics g, int x, int y, double signal, boolean inRange) {
        int barW = 3, gap = 1, bars = 5;
        int barsLit = inRange && signal > 0 ? (int) Math.ceil(signal * bars) : 0;

        for (int i = 0; i < bars; i++) {
            int bh = 4 + i * 2;          // 4,6,8,10,12
            int bx = x + i * (barW + gap);
            int by = y - bh;
            boolean lit = i < barsLit;
            int color = lit ? barColor(barsLit) : SIGNAL_EMPTY;
            g.fill(bx, by, bx + barW, y, color);
        }
    }

    private static int barColor(int lit) {
        return switch (lit) {
            case 1 -> SIGNAL_DEAD;
            case 2 -> SIGNAL_LOW;
            case 3 -> SIGNAL_MED;
            case 4 -> SIGNAL_GOOD;
            default -> SIGNAL_FULL;
        };
    }

    /** Small colored dimension pill badge. */
    public static void drawDimBadge(GuiGraphics g, Font font, int x, int y, String dim) {
        String label;
        int bg;
        if (dim == null || dim.isEmpty())    { label = "?";         bg = 0xFF1A2A3A; }
        else if (dim.contains("overworld")) { label = "Overworld";  bg = 0xFF1A5C1A; }
        else if (dim.contains("the_nether")){ label = "Nether";     bg = 0xFF7A2000; }
        else if (dim.contains("the_end"))   { label = "The End";    bg = 0xFF3A1855; }
        else if (dim.contains("pocket"))    { label = "Pocket";     bg = 0xFF2A1060; }
        else {
            String path = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
            path = path.replace("_", " ");
            label = path.length() > 10 ? path.substring(0, 10) : path;
            bg = 0xFF1A2A3A;
        }

        int w = font.width(label) + 6;
        g.fill(x, y - 1, x + w, y + 9, bg);
        g.fill(x, y - 1, x + w, y,     lighten(bg, 50));
        g.drawString(font, label, x + 3, y, 0xFFDDDDDD, false);
    }

    /** 0-1 smooth sine pulse (period ~2 s). */
    public static float pulse() {
        return (float)(Math.sin(System.currentTimeMillis() * Math.PI / 1000.0) * 0.5 + 0.5);
    }

    public static int lighten(int color, int amt) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amt);
        int g = Math.min(255, ((color >> 8)  & 0xFF) + amt);
        int b = Math.min(255, (color & 0xFF) + amt);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int color, int amt) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - amt);
        int g = Math.max(0, ((color >> 8)  & 0xFF) - amt);
        int b = Math.max(0, (color & 0xFF) - amt);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static void gem(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, CORNER_GEM);
    }
}
