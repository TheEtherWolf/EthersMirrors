package com.ether.mirrors.screen;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.MirrorsConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EthersMirrors.MOD_ID, value = Dist.CLIENT)
public class CameraOverlayRenderer {

    private static volatile boolean cameraViewActive = false;
    private static volatile String viewingPlayerName = "";
    private static volatile double signalStrength = 1.0;

    private static final java.util.Random STATIC_RANDOM = new java.util.Random();

    public static void startCameraView(String playerName, double signal) {
        cameraViewActive = true;
        viewingPlayerName = playerName;
        CameraOverlayRenderer.signalStrength = Math.max(0.0, Math.min(1.0, signal));
    }

    public static void stopCameraView() {
        cameraViewActive = false;
        viewingPlayerName = "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    public static boolean isCameraViewActive() { return cameraViewActive; }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!cameraViewActive) return;
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        // Reset shader color — AutoHUD / ImmediatelyFast may leave alpha < 1 from batched rendering
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int W = mc.getWindow().getGuiScaledWidth();
        int H = mc.getWindow().getGuiScaledHeight();

        // ── Vignette ──────────────────────────────────────────────────────────

        int top = 38, bot = H - 38, side = 56;

        // Solid bars top/bottom
        g.fill(0, 0,   W, top,     0xEE000010);
        g.fill(0, bot, W, H,       0xEE000010);

        // Side bars with purple tint
        g.fill(0,        top, side,     bot, 0xBB050018);
        g.fill(W - side, top, W,        bot, 0xBB050018);

        // Gradient inner edges (purple glow) — top/bottom
        g.fill(side, top,       W - side, top + 4,  0x33AA55FF);
        g.fill(side, bot - 4,   W - side, bot,      0x33AA55FF);
        // left/right inner edges
        g.fill(side,         top + 4, side + 4,     bot - 4, 0x33AA55FF);
        g.fill(W - side - 4, top + 4, W - side,     bot - 4, 0x33AA55FF);

        // Corner darkening for oval crystal-ball feel
        int c = 90;
        g.fill(0,     top,     c, top + 36, 0x77000000);
        g.fill(W - c, top,     W, top + 36, 0x77000000);
        g.fill(0,     bot - 36, c, bot,     0x77000000);
        g.fill(W - c, bot - 36, W, bot,     0x77000000);

        // Pulsing border glow line
        float pulse = UITheme.pulse();
        int glowAlpha = (int)(pulse * 0x55 + 0x15);
        int glowColor = UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha);
        g.fill(side, top,     W - side, top + 1,  glowColor);
        g.fill(side, bot - 1, W - side, bot,      glowColor);
        g.fill(side,         top + 1, side + 1,     bot - 1, glowColor);
        g.fill(W - side - 1, top + 1, W - side,     bot - 1, glowColor);

        // Gold corner gems on border corners
        int gemColor = UITheme.withAlpha(UITheme.CORNER_GEM, (int)(pulse * 0xBB + 0x44));
        drawGem(g, side, top);
        drawGem(g, W - side - 3, top);
        drawGem(g, side, bot - 3);
        drawGem(g, W - side - 3, bot - 3);

        // ── Static / signal-lost ───────────────────────────────────────────────

        double staticThreshold = 1.0 - (MirrorsConfig.STATIC_THRESHOLD_PERCENT.get() / 100.0);
        if (signalStrength <= 0.0) {
            g.fill(0, 0, W, H, 0xBB111111);
            // Draw "Signal Lost" in styled box
            int bx = W / 2 - 52, by = H / 2 - 8;
            g.fill(bx - 4, by - 4, bx + 108, by + 16, 0xDD030014);
            g.fill(bx - 4, by - 4, bx + 108, by - 3, UITheme.BORDER_ACCENT);
            g.fill(bx - 4, by + 13, bx + 108, by + 14, UITheme.BORDER_ACCENT);
            g.drawCenteredString(mc.font, "Signal Lost", W / 2, by, UITheme.SIGNAL_DEAD);
        } else if (signalStrength < staticThreshold) {
            double intensity = (staticThreshold - signalStrength) / staticThreshold;
            int tileCount = (int)(intensity * 200);
            for (int i = 0; i < tileCount; i++) {
                int x = STATIC_RANDOM.nextInt(Math.max(1, W - 3));
                int y = STATIC_RANDOM.nextInt(Math.max(1, H - 3));
                int alpha = 0x55 + (int)(intensity * 0xAA);
                int grey  = 0x80 + STATIC_RANDOM.nextInt(0x7F);
                int color = (alpha << 24) | (grey << 16) | (grey << 8) | grey;
                g.fill(x, y, x + 3, y + 3, color);
            }
        }

        // ── HUD — top label ────────────────────────────────────────────────────

        // "Viewing: Name" centered in top bar
        String viewText = "Viewing:  " + viewingPlayerName;
        int labelW = mc.font.width(viewText) + 16;
        int lx = W / 2 - labelW / 2;
        int ly = 6;
        g.fill(lx, ly, lx + labelW, ly + 12, 0xCC030014);
        g.fill(lx, ly, lx + labelW, ly + 1,  UITheme.BORDER_ACCENT);
        g.fill(lx, ly + 11, lx + labelW, ly + 12, UITheme.BORDER_ACCENT);
        // "Viewing:" muted, name in gold
        int prefixW = mc.font.width("Viewing:  ");
        g.drawString(mc.font, "Viewing:", lx + 8, ly + 2, UITheme.TEXT_MUTED, false);
        g.drawString(mc.font, viewingPlayerName, lx + 8 + prefixW, ly + 2, UITheme.TEXT_GOLD, false);

        // ── HUD — signal bars (top-right) ─────────────────────────────────────

        UITheme.drawSignalBars(g, W - side + 8, top - 4, signalStrength, signalStrength > 0.0);

        // ── HUD — bottom exit hint ─────────────────────────────────────────────

        String exitText = "Sneak  or  ESC  to exit";
        int ew = mc.font.width(exitText) + 16;
        int ex = W / 2 - ew / 2;
        int ey = H - 14;
        g.fill(ex, ey - 1, ex + ew, ey + 10, 0xAA030014);
        g.drawCenteredString(mc.font, exitText, W / 2, ey, UITheme.TEXT_MUTED);
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (!cameraViewActive) return;
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            stopCameraView();
        }
    }

    private static void drawGem(GuiGraphics g, int x, int y) {
        float pulse = UITheme.pulse();
        int alpha = (int)(pulse * 0xBB + 0x44);
        g.fill(x, y, x + 3, y + 3, UITheme.withAlpha(UITheme.CORNER_GEM, alpha));
    }
}
