package com.ether.mirrors.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom-styled button for Ether's Mirrors UI.
 * Dark arcane theme: glowing border, corner gems, hover glow.
 */
public class MirrorButton extends Button {

    private final int borderColor;
    private final int activeTextColor;

    MirrorButton(int x, int y, int w, int h, Component text, OnPress onPress,
                 int borderColor, int activeTextColor) {
        super(Button.builder(text, onPress).pos(x, y).size(w, h));
        this.borderColor = borderColor;
        this.activeTextColor = activeTextColor;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static MirrorButton of(int x, int y, int w, int h, Component text,
                                   OnPress onPress, int borderColor, int textColor) {
        return new MirrorButton(x, y, w, h, text, onPress, borderColor, textColor);
    }

    public static MirrorButton purple(int x, int y, int w, int h, Component text, OnPress onPress) {
        return of(x, y, w, h, text, onPress, UITheme.BTN_PURPLE, UITheme.TEXT_WHITE);
    }

    public static MirrorButton gold(int x, int y, int w, int h, Component text, OnPress onPress) {
        return of(x, y, w, h, text, onPress, UITheme.BTN_GOLD, UITheme.TEXT_GOLD);
    }

    public static MirrorButton green(int x, int y, int w, int h, Component text, OnPress onPress) {
        return of(x, y, w, h, text, onPress, UITheme.BTN_GREEN, 0xFFAAFFBB);
    }

    public static MirrorButton red(int x, int y, int w, int h, Component text, OnPress onPress) {
        return of(x, y, w, h, text, onPress, UITheme.BTN_RED, 0xFFFFAAAA);
    }

    public static MirrorButton teal(int x, int y, int w, int h, Component text, OnPress onPress) {
        return of(x, y, w, h, text, onPress, UITheme.BTN_TEAL, 0xFFAAEEEE);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean active = isActive();

        // Outer hover glow
        if (hov && active) {
            int glow = UITheme.withAlpha(borderColor, 0x40);
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, glow);
        }

        // Body
        int body = !active ? 0xAA04000E : (hov ? 0xCC14043A : 0xBB0A0020);
        g.fill(x, y, x + w, y + h, body);

        // Border
        int bc = !active ? 0xFF1A1A28 : (hov ? UITheme.lighten(borderColor, 40) : borderColor);
        g.fill(x,         y,         x + w, y + 1,     bc);
        g.fill(x,         y + h - 1, x + w, y + h,     bc);
        g.fill(x,         y + 1,     x + 1, y + h - 1, bc);
        g.fill(x + w - 1, y + 1,     x + w, y + h - 1, bc);

        // Inner highlight line (top)
        if (active) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, UITheme.withAlpha(UITheme.lighten(borderColor, 60), 0x80));
        }

        // Corner gems
        if (active) {
            int gem = hov ? 0xFFFFDD66 : UITheme.CORNER_GEM;
            g.fill(x,         y,         x + 2, y + 2, gem);
            g.fill(x + w - 2, y,         x + w, y + 2, gem);
            g.fill(x,         y + h - 2, x + 2, y + h, gem);
            g.fill(x + w - 2, y + h - 2, x + w, y + h, gem);
        }

        // Label
        int textColor = !active ? 0xFF444455
                : (hov ? 0xFFFFFFFF : activeTextColor);
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                x + w / 2, y + (h - 8) / 2, textColor);
    }
}
