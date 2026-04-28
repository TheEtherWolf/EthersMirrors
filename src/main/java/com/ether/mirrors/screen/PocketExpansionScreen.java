package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundExpandPocketPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PocketExpansionScreen extends Screen {

    /** Describes a single pocket expansion tier option shown in the screen. */
    private record ExpansionOption(int amount, int cost, int borderColor, String material) {}

    private int currentSize;
    private final int maxSize;
    private int pendingAmount = 0; // > 0 means awaiting confirmation

    private static final ExpansionOption[] OPTIONS = {
            new ExpansionOption( 16,  8,  UITheme.BTN_GREEN, "Oak Planks"      ),
            new ExpansionOption( 32,  8,  0xFF4A4A6A,        "Iron Ingots"     ),
            new ExpansionOption( 64,  4,  0xFF1A7B9B,        "Diamonds"        ),
            new ExpansionOption(128,  2,  0xFF8B2A1A,        "Netherite Ingots"),
    };

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 188;

    public PocketExpansionScreen(int currentSize, int maxSize) {
        super(Component.literal("Pocket Dimension"));
        this.currentSize = currentSize;
        this.maxSize = maxSize;
    }

    private int panelLeft() { return (this.width  - PANEL_W) / 2; }
    private int panelTop()  { return (this.height - PANEL_H) / 2; }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int pl = panelLeft(), pt = panelTop();
        int cx = pl + PANEL_W / 2;
        int startY = pt + 78;

        if (pendingAmount > 0) {
            // Show confirm/cancel row
            g_pendingAmount = pendingAmount; // stored for render
            addRenderableWidget(MirrorButton.red(cx - 130, startY, 124, 18,
                    Component.literal("Cancel"), b -> { pendingAmount = 0; rebuildButtons(); }));
            addRenderableWidget(MirrorButton.green(cx + 6, startY, 124, 18,
                    Component.literal("Confirm Expand +%d".formatted(pendingAmount)), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundExpandPocketPacket(pendingAmount));
                        onClose();
                    }));
        } else {
            g_pendingAmount = 0;
            for (int i = 0; i < OPTIONS.length; i++) {
                ExpansionOption opt = OPTIONS[i];
                int amount  = opt.amount();
                int cost    = opt.cost();
                int color   = opt.borderColor();
                String mat  = opt.material();
                boolean can = (currentSize + amount) <= maxSize;

                String label = "+%d blocks  (%d %s)".formatted(amount, cost, mat);
                MirrorButton btn = MirrorButton.of(cx - 130, startY + i * 24, 260, 18,
                        Component.literal(label), b -> { pendingAmount = amount; rebuildButtons(); },
                        color, UITheme.TEXT_WHITE);
                btn.active = can;
                addRenderableWidget(btn);
            }
        }

        // Close
        addRenderableWidget(MirrorButton.gold(
                cx - 44, pt + PANEL_H - 24, 88, 18,
                Component.literal("Close"), b -> onClose()));
    }

    // Used to pass pendingAmount to render without adding a second field
    private int g_pendingAmount = 0;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + PANEL_H;

        UITheme.drawPanel(g, pl, pt, pr, pb);
        UITheme.drawHeader(g, pl, pt, pr, 30);

        // Pulse glow
        float pulse = UITheme.pulse();
        int ga = (int)(pulse * 0x60 + 0x20);
        g.fill(pl - 1, pt - 1, pr + 1, pt, UITheme.withAlpha(UITheme.BORDER_BRIGHT, ga));
        g.fill(pl - 1, pb,     pr + 1, pb + 1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, ga));

        int cx = pl + PANEL_W / 2;

        // Header text
        g.drawCenteredString(font, "Pocket Dimension", cx, pt + 5, UITheme.TEXT_MUTED);
        g.drawCenteredString(font, "* Expand Your Space *", cx, pt + 18, UITheme.TEXT_GOLD);

        // Size progress bar
        int barX  = pl + 20;
        int barY  = pt + 38;
        int barW  = PANEL_W - 40;
        int barH  = 10;
        float pct = maxSize > 0 ? (float) currentSize / maxSize : 0f;

        // Bar track
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF0A0028);
        g.fill(barX, barY, barX, barY + barH, UITheme.BORDER_MID);

        // Filled portion (gradient: teal → purple)
        int fillW = (int)(barW * pct);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, 0xFF1A6B9B);
            g.fill(barX, barY, barX + fillW, barY + 1, UITheme.withAlpha(0xFFAAEEFF, 0x80));
        }

        // Bar border
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY,          UITheme.BORDER_ACCENT);
        g.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, UITheme.BORDER_ACCENT);
        g.fill(barX - 1, barY, barX, barY + barH,              UITheme.BORDER_ACCENT);
        g.fill(barX + barW, barY, barX + barW + 1, barY + barH,    UITheme.BORDER_ACCENT);

        // Size labels
        g.drawString(font, currentSize + "x" + currentSize, barX + 2, barY + 1, UITheme.TEXT_WHITE, false);
        String maxLabel = maxSize + "x" + maxSize;
        g.drawString(font, maxLabel, barX + barW - font.width(maxLabel) - 2, barY + 1, UITheme.TEXT_MUTED, false);

        // Sub-labels
        g.drawCenteredString(font, "Current: " + currentSize + "x" + currentSize
                + "  |  Max: " + maxSize + "x" + maxSize, cx, barY + 14, UITheme.TEXT_MUTED);
        g.drawCenteredString(font, "Sneak + right-click exit mirror to reopen",
                cx, barY + 24, UITheme.withAlpha(UITheme.TEXT_MUTED, 0x99));

        UITheme.drawRule(g, pl + 8, pr - 8, pt + 74);

        // Confirmation prompt
        if (g_pendingAmount > 0) {
            g.drawCenteredString(font,
                    "Expand by +" + g_pendingAmount + " blocks. Are you sure?",
                    cx, pt + 78 - 12, UITheme.TEXT_GOLD);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
