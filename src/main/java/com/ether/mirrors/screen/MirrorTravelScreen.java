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

/**
 * Full-screen procedural travel animation shown during mirror teleportation.
 * The teleport packet is sent at tick 80 (end of animation) — player is held
 * in place until the animation completes, then teleported.
 *
 * Phases:
 *   0 CHARGE    ticks  0–15  — rings converge, energy builds at center
 *   1 DISSOLVE  ticks 16–35  — reality fractures into particles
 *   2 TUNNEL    ticks 36–55  — flying through the ether (star field + radial lines)
 *   3 REFORM    ticks 56–70  — burst rings collapse inward as form reassembles
 *   4 ARRIVAL   ticks 71–80  — white flash fades, gold shimmer drifts upward
 */
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

    @Override
    protected void init() {
        super.init();
        JourneyMapCompat.highlightDestination(destination.mirrorId);
    }

    @Override
    public void onClose() {
        JourneyMapCompat.clearHighlight(destination.mirrorId);
        super.onClose();
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        ticks++;
        if (ticks >= TOTAL_TICKS) {
            MirrorsNetwork.sendToServer(
                    new ServerboundTeleportRequestPacket(destination.mirrorId, sourceMirrorPos, isHandheld));
            onClose();
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        int sw = width, sh = height;
        int cx = sw / 2, cy = sh / 2;
        int phase = currentPhase();

        // Near-opaque deep-space base
        g.fill(0, 0, sw, sh, 0xF8000010);
        drawVignette(g, sw, sh);
        drawAmbient(g, sw, sh, cx, cy, phase);
        drawPhaseEffect(g, sw, sh, cx, cy, phase);
        drawHUD(g, sw, sh, phase, (float) ticks / TOTAL_TICKS);
    }

    // ── Ambient mood overlay (full-screen tint + center glow, per phase) ─────

    private void drawAmbient(GuiGraphics g, int sw, int sh, int cx, int cy, int phase) {
        int dim = Math.min(sw, sh);
        switch (phase) {
            case 0 -> {
                // Deep violet builds as energy gathers
                float t = phaseT(0, 15);
                g.fill(0, 0, sw, sh, ((int)(t * 55) << 24) | 0x110033);
                drawGlow(g, cx, cy, (int)(dim * 0.28f * t), 0x6622AA, (int)(t * 90));
            }
            case 1 -> {
                // Purple flash peaks at midpoint then fades
                float peak = (float)Math.sin(phaseT(1, 19) * Math.PI);
                g.fill(0, 0, sw, sh, ((int)(peak * 110) << 24) | 0x9933CC);
                drawGlow(g, cx, cy, (int)(dim * 0.22f), 0xFFEEFF, (int)(peak * 140));
            }
            case 2 -> {
                // Deep void with pulsing center
                g.fill(0, 0, sw, sh, 0x18000018);
                long ms = System.currentTimeMillis();
                float pulse = 0.7f + 0.3f * (float)Math.sin(ms * 0.006);
                drawGlow(g, cx, cy, (int)(dim * 0.16f * pulse), 0x5511BB, 120);
            }
            case 3 -> {
                // Lavender light surges as form returns
                float t = phaseT(3, 14);
                g.fill(0, 0, sw, sh, ((int)(t * 80) << 24) | 0xAA77FF);
                drawGlow(g, cx, cy, (int)(dim * 0.32f * t), 0xFFEEFF, (int)(t * 190));
            }
            case 4 -> {
                // White flash (quadratic ease-out) then clear
                float t = phaseT(4, 9);
                int flashA = (int)((1f - t) * (1f - t) * 210);
                g.fill(0, 0, sw, sh, (flashA << 24) | 0xFFFFFF);
                drawGlow(g, cx, cy, (int)(dim * 0.18f * (1f - t)), 0xFFEEDD, (int)((1f - t) * 150));
            }
        }
    }

    // ── Phase-specific effects ─────────────────────────────────────────────────

    private void drawPhaseEffect(GuiGraphics g, int sw, int sh, int cx, int cy, int phase) {
        long ms = System.currentTimeMillis();
        int dim = Math.min(sw, sh);

        switch (phase) {
            case 0 -> {
                // 7 expanding rings, alternating violet/white — energy gathering
                float t = phaseT(0, 15);
                int maxR = (int)(dim * 0.50f);
                for (int ring = 0; ring < 7; ring++) {
                    float frac  = (t + ring / 7f) % 1f;
                    int   r     = (int)(frac * maxR);
                    int   alpha = (int)((1f - frac) * (50 + ring * 10));
                    int   col   = (ring % 2 == 0) ? 0x6633BB : 0xCCAAFF;
                    drawRing(g, cx, cy, r, (alpha << 24) | col, ring % 2 == 0 ? 2 : 1);
                }
                // Bright inner ring collapses toward center as charge builds
                int innerR = (int)((1f - t) * dim * 0.12f + 6);
                drawRing(g, cx, cy, innerR, (170 << 24) | 0xFFFFFF, 1);
            }
            case 1 -> {
                // Particle scatter — reality fracturing apart
                float t = phaseT(1, 19);
                float envelope = (float)Math.sin(t * Math.PI); // 0 → 1 → 0
                for (int p = 0; p < 200; p++) {
                    java.util.Random rand = new java.util.Random((long)p * 1337L + ticks * 11L);
                    int px = rand.nextInt(sw);
                    int py = rand.nextInt(sh);
                    int pa = (int)(rand.nextFloat() * envelope * 220);
                    if (pa <= 0) continue;
                    boolean large = (p % 6 == 0);
                    int sz  = large ? 3 : 2;
                    int col = (p % 7 == 0) ? 0xFFE080 : (p % 3 == 0) ? 0xFFFFFF : 0xDDBBFF;
                    g.fill(px, py, px + sz, py + sz, (Math.min(255, pa) << 24) | col);
                }
                // Shockwave ring expanding outward
                int shockR = (int)(t * Math.max(sw, sh) * 0.55f);
                drawRing(g, cx, cy, shockR, ((int)((1f - t) * 140) << 24) | 0xCC88FF, 2);
            }
            case 2 -> {
                // Star field (fixed seed = consistent positions, gentle per-star pulse)
                java.util.Random stars = new java.util.Random(0xCAFEBABEL);
                for (int s = 0; s < 80; s++) {
                    int sx = stars.nextInt(sw);
                    int sy = stars.nextInt(sh);
                    float pul = 0.35f + 0.65f * (float)Math.sin(ms * 0.004 + s * 1.3);
                    boolean bright = (s % 7 == 0);
                    int sa = (int)(pul * (bright ? 180 : 110));
                    int sc = bright ? 0xFFEEFF : 0x9966CC;
                    g.fill(sx, sy, sx + (bright ? 2 : 1), sy + (bright ? 2 : 1), (sa << 24) | sc);
                }
                // 18 radial rushing lines — two-tone (dim outer layer + bright inner)
                for (int i = 0; i < 18; i++) {
                    long seed  = i * 137L + ms / 55L;
                    double ang = ((seed * 31L) % 360) * Math.PI / 180.0;
                    float frac = ((seed * 53L) % 1000) / 1000f;
                    float spd  = 0.010f + ((seed * 17L) % 60) / 3500f;
                    float pos  = (frac + (ms % 4000L) * spd / 4000f) % 1f;
                    float pos2 = Math.min(pos + 0.042f, 1f);
                    float start = pos * 0.85f + 0.15f; // lines emerge from ring around center
                    int x1 = cx + (int)(Math.cos(ang) * start * sw * 0.52);
                    int y1 = cy + (int)(Math.sin(ang) * start * sh * 0.52);
                    int x2 = cx + (int)(Math.cos(ang) * pos2  * sw * 0.52);
                    int y2 = cy + (int)(Math.sin(ang) * pos2  * sh * 0.52);
                    int bx1 = Math.min(x1,x2), by1 = Math.min(y1,y2);
                    int bx2 = Math.max(x1,x2)+1, by2 = Math.max(y1,y2)+1;
                    g.fill(bx1, by1, bx2, by2, ((int)(pos *  70) << 24) | 0x220055); // dim shadow
                    g.fill(bx1, by1, bx2, by2, ((int)(pos * 170) << 24) | 0x8844CC); // bright line
                }
                // Layered center orb: white core → violet outer
                drawGlow(g, cx, cy,  5, 0xFFFFFF, 255);
                drawGlow(g, cx, cy, 16, 0xEECCFF, 190);
                drawGlow(g, cx, cy, 36, 0x7733AA, 110);
            }
            case 3 -> {
                // Outward burst rings + inward converging rings
                float t    = phaseT(3, 14);
                int maxR   = (int)(Math.max(sw, sh) * 0.65f);
                for (int ring = 0; ring < 3; ring++) {
                    float frac = (t + ring * 0.18f) % 1f;
                    int r = (int)(frac * maxR);
                    drawRing(g, cx, cy, r, (Math.max(0, 130 - ring*35) << 24) | 0xCC99FF, 2);
                }
                for (int ring = 0; ring < 2; ring++) {
                    float frac = ((1f - t) + ring * 0.25f) % 1f;
                    int r = (int)(frac * maxR * 0.45f);
                    if (r > 2) drawRing(g, cx, cy, r, (Math.max(0, 90 - ring*30) << 24) | 0xFFEEFF, 1);
                }
            }
            case 4 -> {
                // Amber shimmer particles drift upward after the flash recedes
                float t = phaseT(4, 9);
                if (t > 0.25f) {
                    float vis = (t - 0.25f) / 0.75f;
                    for (int p = 0; p < 55; p++) {
                        long seed  = (long)p * 997L;
                        int  baseX = (int)(Math.abs(seed * 31L) % sw);
                        float drift = (ms / 22f + p * 43) % sh;
                        int   px   = (int)(baseX + Math.sin(ms * 0.002 + p) * 14);
                        int   py   = sh - (int)drift;
                        if (py < 0 || py >= sh || px < 0 || px >= sw) continue;
                        float twinkle = 0.4f + 0.6f * (float)Math.sin(ms * 0.006 + p * 0.7);
                        int pa = (int)(vis * twinkle * 190);
                        g.fill(px, py, px + 2, py + 2, (pa << 24) | 0xFFE040);
                        if (p % 4 == 0)
                            g.fill(px, py, px + 1, py + 1, ((pa / 2) << 24) | 0xFFFFFF);
                    }
                }
            }
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHUD(GuiGraphics g, int sw, int sh, int phase, float progress) {
        Font font = Minecraft.getInstance().font;
        int pad = 10;

        // Top-left — source
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
        int rightX = sw - pad - Math.max(Math.max(font.width(destName), font.width(destDim)), font.width("TO"));
        g.drawString(font, "TO",     rightX, pad,      UITheme.TEXT_MUTED, true);
        g.drawString(font, destName, rightX, pad + 10, UITheme.TEXT_GOLD,  true);
        g.drawString(font, destDim,  rightX, pad + 20, UITheme.TEXT_MUTED, true);

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

    /** Normalized [0,1] progress within the current phase. */
    private float phaseT(int phaseIdx, int duration) {
        return Math.min(1f, (float)(ticks - PHASE_START[phaseIdx]) / duration);
    }

    /**
     * Vignette: layers semi-transparent black fills from each edge inward.
     * Closer to edge = more layers overlap = darker.
     */
    private static void drawVignette(GuiGraphics g, int sw, int sh) {
        int[] depths = { 90, 72, 54, 36, 18 };
        int[] alphas = { 18, 28, 38, 50, 62 };
        for (int i = 0; i < depths.length; i++) {
            int d = depths[i], a = alphas[i];
            g.fill(0,      0,      sw,     d,      a << 24);
            g.fill(0,      sh - d, sw,     sh,     a << 24);
            g.fill(0,      d,      d,      sh - d, a << 24);
            g.fill(sw - d, d,      sw,     sh - d, a << 24);
        }
    }

    /**
     * Simulates a soft radial glow using concentric filled squares.
     * Brightest at center, fading linearly to zero at maxR.
     */
    private static void drawGlow(GuiGraphics g, int cx, int cy, int maxR, int colorRGB, int maxAlpha) {
        if (maxR <= 0 || maxAlpha <= 0) return;
        int steps = Math.min(maxR / 5 + 1, 10);
        for (int i = 1; i <= steps; i++) {
            int r = (maxR * i) / steps;
            int a = Math.min(255, maxAlpha * (steps - i + 1) / steps);
            g.fill(cx - r, cy - r, cx + r, cy + r, (a << 24) | colorRGB);
        }
    }

    /** Hollow rectangle ring centered at (cx, cy) with half-size r and line thickness t. */
    private static void drawRing(GuiGraphics g, int cx, int cy, int r, int color, int t) {
        if (r <= 0) return;
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
