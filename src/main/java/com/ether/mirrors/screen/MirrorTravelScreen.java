package com.ether.mirrors.screen;

import com.ether.mirrors.compat.JourneyMapCompat;
import com.ether.mirrors.network.packets.ClientboundMirrorListPacket.MirrorInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Full-screen procedural travel animation shown during mirror teleportation.
 * The teleport packet is sent before this screen opens — the animation is purely cosmetic.
 * Auto-closes after 80 ticks (~4 s at 20 TPS).
 *
 * Phases:
 *   0 CHARGE    ticks  0–15  — concentric rings pulse outward
 *   1 DISSOLVE  ticks 16–35  — purple flash and scanline shatter
 *   2 TUNNEL    ticks 36–55  — rushing lines through the ether
 *   3 REFORM    ticks 56–70  — radial burst from center
 *   4 ARRIVAL   ticks 71–80  — white flash fades to clear
 */
public class MirrorTravelScreen extends Screen {

    private static final int   TOTAL_TICKS  = 80;
    private static final int[] PHASE_START  = { 0, 16, 36, 56, 71 };
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

    @Override
    protected void init() {
        super.init();
        // Pulse the destination waypoint gold on JourneyMap during transit
        JourneyMapCompat.highlightDestination(destination.mirrorId);
    }

    @Override
    public void onClose() {
        JourneyMapCompat.clearHighlight(destination.mirrorId);
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        ticks++;
        if (ticks >= TOTAL_TICKS) onClose();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        int sw = width, sh = height;
        int cx = sw / 2,  cy = sh / 2;
        float progress = (float) ticks / TOTAL_TICKS;
        int phase = currentPhase();

        drawBaseOverlay(g, sw, sh, phase);
        drawPhaseEffect(g, sw, sh, cx, cy, phase);
        drawHUD(g, sw, sh, phase, progress);
    }

    // ── Phase drawing ─────────────────────────────────────────────────────────

    private void drawBaseOverlay(GuiGraphics g, int sw, int sh, int phase) {
        // Always-present dark base so game world is barely visible
        g.fill(0, 0, sw, sh, 0xD8000008);

        switch (phase) {
            case 0 -> { // Charge — deepening purple vignette
                float t = phaseT(0, 15);
                int alpha = (int)(t * 170);
                g.fill(0, 0, sw, sh, (alpha << 24) | 0x1A0040);
            }
            case 1 -> { // Dissolve — sine-shaped purple flash
                float t = phaseT(1, 19);
                int alpha = (int)(Math.sin(t * Math.PI) * 210);
                g.fill(0, 0, sw, sh, (alpha << 24) | 0x8833CC);
            }
            case 2 -> { // Tunnel — near-black deep space
                g.fill(0, 0, sw, sh, 0xBB000018);
            }
            case 3 -> { // Reform — brightening lavender
                float t = phaseT(3, 14);
                int alpha = (int)(t * 150);
                g.fill(0, 0, sw, sh, (alpha << 24) | 0xAA77FF);
            }
            case 4 -> { // Arrival — fade to clear
                float t = phaseT(4, 9);
                int alpha = (int)((1f - t) * 210);
                g.fill(0, 0, sw, sh, (alpha << 24) | 0x000008);
            }
        }
    }

    private void drawPhaseEffect(GuiGraphics g, int sw, int sh, int cx, int cy, int phase) {
        long ms = System.currentTimeMillis();

        switch (phase) {
            case 0 -> { // Charge — concentric expanding rings
                float t = phaseT(0, 15);
                int maxR = (int)(Math.min(sw, sh) * 0.55f);
                for (int ring = 0; ring < 5; ring++) {
                    float frac = (t + ring * 0.2f) % 1f;
                    int r     = (int)(frac * maxR);
                    int alpha = (int)((1f - frac) * 140);
                    drawRing(g, cx, cy, r, (alpha << 24) | 0x7733CC);
                }
            }
            case 1 -> { // Dissolve — scanline shatter
                float t = phaseT(1, 19);
                for (int y = 0; y < sh; y += 3) {
                    // Pseudo-random per scanline using line index + tick
                    long seed = (y * 31L + ticks * 7L) ^ 0xA5A5A5L;
                    int lineAlpha = (int)(((seed & 0x3F) / 63f) * t * 90);
                    g.fill(0, y, sw, y + 2, (lineAlpha << 24) | 0xEECCFF);
                }
            }
            case 2 -> { // Tunnel — radial rushing lines
                for (int i = 0; i < 24; i++) {
                    long seed  = i * 137L + ms / 60;
                    double ang = ((seed * 31L) % 360) * Math.PI / 180.0;
                    float frac = ((seed * 53L) % 1000) / 1000f;
                    float spd  = 0.012f + ((seed * 17L) % 60) / 4000f;
                    float pos  = (frac + (ms % 3000) * spd / 3000f) % 1f;
                    float pos2 = Math.min(pos + 0.05f, 1f);
                    int x1 = cx + (int)(Math.cos(ang) * pos  * sw * 0.52);
                    int y1 = cy + (int)(Math.sin(ang) * pos  * sh * 0.52);
                    int x2 = cx + (int)(Math.cos(ang) * pos2 * sw * 0.52);
                    int y2 = cy + (int)(Math.sin(ang) * pos2 * sh * 0.52);
                    int a  = (int)(pos * 200);
                    g.fill(Math.min(x1,x2), Math.min(y1,y2),
                           Math.max(x1,x2)+1, Math.max(y1,y2)+1,
                           (a << 24) | 0x5522BB);
                }
                // Pulsing center star
                int lum = (int)(160 + Math.sin(ms * 0.01) * 95);
                int col = 0xFF000000 | (lum << 16) | ((lum / 2) << 8) | lum;
                g.fill(cx - 4, cy - 4, cx + 4, cy + 4, col);
                g.fill(cx - 2, cy - 8, cx + 2, cy + 8, col);
                g.fill(cx - 8, cy - 2, cx + 8, cy + 2, col);
            }
            case 3 -> { // Reform — burst rings expanding from center
                float t = phaseT(3, 14);
                int burstR = (int)(t * Math.max(sw, sh));
                for (int ring = 0; ring < 4; ring++) {
                    int r = burstR - ring * 14;
                    if (r > 0) {
                        int alpha = Math.max(0, 160 - ring * 40);
                        drawRing(g, cx, cy, r, (alpha << 24) | 0xCC99FF);
                    }
                }
            }
            case 4 -> { // Arrival — single bright flash at start of phase
                float t = phaseT(4, 9);
                if (t < 0.35f) {
                    int flashA = (int)((0.35f - t) / 0.35f * 255);
                    g.fill(0, 0, sw, sh, (flashA << 24) | 0xFFFFFF);
                }
            }
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHUD(GuiGraphics g, int sw, int sh, int phase, float progress) {
        Font font = Minecraft.getInstance().font;
        int pad = 10;

        // Top-left — origin
        g.drawString(font, "FROM", pad, pad, UITheme.TEXT_MUTED, true);
        String fromLine = isHandheld ? "Handheld Mirror"
                : sourceMirrorPos != null
                        ? sourceMirrorPos.getX() + ", " + sourceMirrorPos.getY() + ", " + sourceMirrorPos.getZ()
                        : "Mirror";
        g.drawString(font, fromLine, pad, pad + 10, UITheme.TEXT_LAVENDER, true);

        // Top-right — destination
        String destName = destination.mirrorName.isEmpty()
                ? capitalize(destination.typeName) + " Mirror"
                : destination.mirrorName;
        String destDim  = destination.dimensionName;
        int dnW = font.width(destName);
        int ddW = font.width(destDim);
        int toW = font.width("TO");
        int rightX = sw - pad - Math.max(Math.max(dnW, ddW), toW);
        g.drawString(font, "TO",     rightX, pad,      UITheme.TEXT_MUTED,    true);
        g.drawString(font, destName, rightX, pad + 10, UITheme.TEXT_GOLD,     true);
        g.drawString(font, destDim,  rightX, pad + 20, UITheme.TEXT_MUTED,    true);

        // Bottom-left — phase name
        g.drawString(font, PHASE_NAMES[phase], pad, sh - pad - 19, UITheme.TEXT_LAVENDER, true);

        // Bottom-right — progress bar + percentage
        int barW = 90, barH = 5;
        int barX = sw - pad - barW;
        int barY = sh - pad - barH - 12;
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

    /** Normalized [0,1] progress within a phase, given its index and duration in ticks. */
    private float phaseT(int phaseIdx, int duration) {
        return Math.min(1f, (float)(ticks - PHASE_START[phaseIdx]) / duration);
    }

    /** Draws a thin hollow rectangle "ring" centered at (cx, cy) with half-size r. */
    private static void drawRing(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int t = 2; // line thickness
        g.fill(cx - r,     cy - r,     cx + r,     cy - r + t, color); // top
        g.fill(cx - r,     cy + r - t, cx + r,     cy + r,     color); // bottom
        g.fill(cx - r,     cy - r,     cx - r + t, cy + r,     color); // left
        g.fill(cx + r - t, cy - r,     cx + r,     cy + r,     color); // right
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
