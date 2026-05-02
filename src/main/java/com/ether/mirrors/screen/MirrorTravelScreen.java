package com.ether.mirrors.screen;

import com.ether.mirrors.compat.JourneyMapCompat;
import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientboundMirrorListPacket.MirrorInfo;
import com.ether.mirrors.network.packets.ServerboundTeleportRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MirrorTravelScreen extends Screen {

    private static final int   TOTAL_TICKS = 80;
    private static final int[] PHASE_START = { 0, 16, 36, 56, 71 };
    private static final String[] PHASE_NAMES = {
        "Charging...", "Dissolving...", "Traversing Ether...", "Reforming...", "Arriving..."
    };

    private final MirrorInfo destination;
    private final BlockPos   sourceMirrorPos;
    private final boolean    isHandheld;
    private int ticks = 0;

    public MirrorTravelScreen(MirrorInfo destination, BlockPos sourceMirrorPos, boolean isHandheld) {
        super(Component.empty());
        this.destination     = destination;
        this.sourceMirrorPos = sourceMirrorPos;
        this.isHandheld      = isHandheld;
    }

    @Override protected void init()    { super.init(); JourneyMapCompat.highlightDestination(destination.mirrorId); }
    @Override public void onClose()    { JourneyMapCompat.clearHighlight(destination.mirrorId); super.onClose(); }
    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        ticks++;
        // Send teleport when charging completes — player is whisked away at phase boundary,
        // phases 1-4 play out at the destination.
        if (ticks == PHASE_START[1]) {
            MirrorsNetwork.sendToServer(
                    new ServerboundTeleportRequestPacket(destination.mirrorId, sourceMirrorPos, isHandheld));
        }
        if (ticks >= TOTAL_TICKS) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        int sw = width, sh = height;
        int cx = sw / 2, cy = sh / 2;
        int phase = currentPhase();

        // Phase 0 (charge): world stays visible — only the ring effects are drawn on top.
        // Full overlay begins when the player has already been teleported (phase 1+).
        if (phase > 0) {
            g.fill(0, 0, sw, sh, 0xF8000010);
            drawVignette(g, sw, sh);
        }

        switch (phase) {
            case 0 -> { // Charge — rings expand outward, center glow grows
                float t = phaseT(0, 15);
                int maxR = (int)(Math.min(sw, sh) * 0.48f);
                for (int i = 0; i < 4; i++) {
                    float frac  = (t + i * 0.25f) % 1f;
                    int   r     = (int)(frac * maxR);
                    int   alpha = (int)((1f - frac) * 120);
                    drawRing(g, cx, cy, r, (alpha << 24) | 0x7744CC, 2);
                }
                drawGlow(g, cx, cy, (int)(Math.min(sw, sh) * 0.20f * t), 0x5522AA, (int)(t * 100));
            }
            case 1 -> { // Dissolve — purple wash pulses, shockwave expands
                float t    = phaseT(1, 19);
                float peak = (float)Math.sin(t * Math.PI);
                g.fill(0, 0, sw, sh, ((int)(peak * 100) << 24) | 0x8833BB);
                drawGlow(g, cx, cy, (int)(Math.min(sw, sh) * 0.18f), 0xEECCFF, (int)(peak * 120));
                int shockR = (int)(t * Math.max(sw, sh) * 0.52f);
                drawRing(g, cx, cy, shockR, ((int)((1f - t) * 130) << 24) | 0xCC88FF, 2);
            }
            case 2 -> { // Tunnel — 8 radial lines + bright center orb
                long ms = System.currentTimeMillis();
                for (int i = 0; i < 8; i++) {
                    long  seed  = i * 137L + ms / 60L;
                    double ang  = ((seed * 31L) % 360) * Math.PI / 180.0;
                    float frac  = ((seed * 53L) % 1000) / 1000f;
                    float spd   = 0.010f + ((seed * 17L) % 60) / 4000f;
                    float pos   = (frac + (ms % 4000L) * spd / 4000f) % 1f;
                    float pos2  = Math.min(pos + 0.05f, 1f);
                    int x1 = cx + (int)(Math.cos(ang) * pos  * sw * 0.52);
                    int y1 = cy + (int)(Math.sin(ang) * pos  * sh * 0.52);
                    int x2 = cx + (int)(Math.cos(ang) * pos2 * sw * 0.52);
                    int y2 = cy + (int)(Math.sin(ang) * pos2 * sh * 0.52);
                    int a  = (int)(pos * 160);
                    g.fill(Math.min(x1,x2), Math.min(y1,y2), Math.max(x1,x2)+1, Math.max(y1,y2)+1,
                            (a << 24) | 0x7744CC);
                }
                drawGlow(g, cx, cy,  5, 0xFFFFFF, 255);
                drawGlow(g, cx, cy, 20, 0xCCAAFF, 160);
                drawGlow(g, cx, cy, 44, 0x6633AA,  80);
            }
            case 3 -> { // Reform — rings collapse inward, lavender wash
                float t = phaseT(3, 14);
                g.fill(0, 0, sw, sh, ((int)(t * 70) << 24) | 0xAA77FF);
                int maxR2 = (int)(Math.max(sw, sh) * 0.6f);
                for (int i = 0; i < 3; i++) {
                    float frac = (t + i * 0.22f) % 1f;
                    int r = (int)(frac * maxR2);
                    drawRing(g, cx, cy, r, (Math.max(0, 110 - i * 30) << 24) | 0xCC99FF, 2);
                }
                drawGlow(g, cx, cy, (int)(Math.min(sw, sh) * 0.24f * t), 0xFFEEFF, (int)(t * 180));
            }
            case 4 -> { // Arrival — white flash fades, amber shimmer drifts up
                float t = phaseT(4, 9);
                int flashA = (int)((1f - t) * (1f - t) * 200);
                g.fill(0, 0, sw, sh, (flashA << 24) | 0xFFFFFF);
                if (t > 0.3f) {
                    float vis = (t - 0.3f) / 0.7f;
                    long ms = System.currentTimeMillis();
                    for (int p = 0; p < 28; p++) {
                        long  seed  = (long)p * 997L;
                        int   baseX = (int)(Math.abs(seed * 31L) % sw);
                        float drift = (ms / 24f + p * 47) % sh;
                        int   px    = (int)(baseX + Math.sin(ms * 0.002 + p) * 12);
                        int   py    = sh - (int)drift;
                        if (py < 0 || py >= sh || px < 0 || px >= sw) continue;
                        int pa = (int)(vis * 160);
                        g.fill(px, py, px + 2, py + 2, (pa << 24) | 0xFFE040);
                    }
                }
            }
        }

        drawHUD(g, sw, sh, phase, (float) ticks / TOTAL_TICKS);
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHUD(GuiGraphics g, int sw, int sh, int phase, float progress) {
        Font font = Minecraft.getInstance().font;
        int pad = 10;

        g.drawString(font, "FROM", pad, pad, UITheme.TEXT_MUTED, true);
        String fromLine = isHandheld ? "Handheld Mirror"
                : sourceMirrorPos != null
                        ? sourceMirrorPos.getX() + ", " + sourceMirrorPos.getY() + ", " + sourceMirrorPos.getZ()
                        : "Mirror";
        g.drawString(font, fromLine, pad, pad + 10, UITheme.TEXT_LAVENDER, true);

        String destName = destination.mirrorName.isEmpty()
                ? capitalize(destination.typeName) + " Mirror" : destination.mirrorName;
        String destDim  = destination.dimensionName;
        int rightX = sw - pad - Math.max(Math.max(font.width(destName), font.width(destDim)), font.width("TO"));
        g.drawString(font, "TO",     rightX, pad,      UITheme.TEXT_MUTED, true);
        g.drawString(font, destName, rightX, pad + 10, UITheme.TEXT_GOLD,  true);
        g.drawString(font, destDim,  rightX, pad + 20, UITheme.TEXT_MUTED, true);

        g.drawString(font, PHASE_NAMES[phase], pad, sh - pad - 19, UITheme.TEXT_LAVENDER, true);

        int barW = 90, barH = 5;
        int barX = sw - pad - barW, barY = sh - pad - barH - 12;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, UITheme.BORDER_MID);
        g.fill(barX, barY, barX + barW, barY + barH, 0x40FFFFFF);
        g.fill(barX, barY, barX + (int)(progress * barW), barY + barH, UITheme.BORDER_ACCENT);
        String pct = (int)(progress * 100) + "%";
        g.drawString(font, pct, sw - pad - font.width(pct), sh - pad - 8, UITheme.TEXT_MUTED, true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int currentPhase() {
        for (int i = PHASE_START.length - 1; i >= 0; i--) {
            if (ticks >= PHASE_START[i]) return i;
        }
        return 0;
    }

    private float phaseT(int phaseIdx, int duration) {
        return Math.min(1f, (float)(ticks - PHASE_START[phaseIdx]) / duration);
    }

    private static void drawVignette(GuiGraphics g, int sw, int sh) {
        int[] depths = { 72, 48, 28 };
        int[] alphas = { 22, 36, 52 };
        for (int i = 0; i < depths.length; i++) {
            int d = depths[i], a = alphas[i];
            g.fill(0,      0,      sw,     d,      a << 24);
            g.fill(0,      sh - d, sw,     sh,     a << 24);
            g.fill(0,      d,      d,      sh - d, a << 24);
            g.fill(sw - d, d,      sw,     sh - d, a << 24);
        }
    }

    private static void drawGlow(GuiGraphics g, int cx, int cy, int maxR, int colorRGB, int maxAlpha) {
        if (maxR <= 0 || maxAlpha <= 0) return;
        int steps = Math.min(maxR / 6 + 1, 8);
        for (int i = 1; i <= steps; i++) {
            int r = (maxR * i) / steps;
            int a = Math.min(255, maxAlpha * (steps - i + 1) / steps);
            g.fill(cx - r, cy - r, cx + r, cy + r, (a << 24) | colorRGB);
        }
    }

    private static void drawRing(GuiGraphics g, int cx, int cy, int r, int color, int t) {
        if (r <= 0) return;
        g.fill(cx - r,     cy - r,     cx + r,     cy - r + t, color);
        g.fill(cx - r,     cy + r - t, cx + r,     cy + r,     color);
        g.fill(cx - r,     cy - r,     cx - r + t, cy + r,     color);
        g.fill(cx + r - t, cy - r,     cx + r,     cy + r,     color);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
