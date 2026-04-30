package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientCallState;
import com.ether.mirrors.network.packets.ServerboundCallEndPacket;
import com.ether.mirrors.network.packets.ServerboundCallResponsePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

public class MirrorCallScreen extends Screen {

    public enum Mode { INCOMING, OUTGOING, ACTIVE }

    @Nullable public static Screen previousScreen = null;

    private final Mode mode;
    private final String otherName;
    private final UUID callId;
    private final long openTimeMs = System.currentTimeMillis();

    // Optional caller/callee detail — populated when available from packets
    @Nullable private final String mirrorName;
    @Nullable private final String dimensionName;
    private final double signalStrength;

    // ── Layout — Design v1 (460 × 172) ───────────────────────────────────────
    private static final int PANEL_W    = 460;
    private static final int HEADER_H   = 32;
    private static final int CONTENT_H  = 100;
    private static final int ACTIONS_H  = 40;
    private static final int PANEL_H    = 172;  // 32+100+40
    private static final int GLYPH_SZ   = 64;
    private static final int PAD        = 18;

    // ── Design colours ────────────────────────────────────────────────────────
    private static final int DC_PANEL      = 0xFF030012;
    private static final int DC_HEADER_BG  = 0xFF020020;
    private static final int DC_BDR_OUTER  = 0xFF7733CC;
    private static final int DC_BDR_INNER  = 0x8CAA55FF;
    private static final int DC_BDR_DIM    = 0xFF2A1144;
    private static final int DC_TEXT_MUTED = 0xFF9999AA;
    private static final int DC_TEXT_LAV   = 0xFFAA88FF;
    private static final int DC_GOLD       = 0xFFD4AF37;
    private static final int DC_SIG_FULL   = 0xFF44FF77;
    private static final int DC_SIG_DIM    = 0x2E44FF77;
    private static final int DC_TEAL       = 0xFF6FE0E0;
    private static final int DC_TEAL_BD    = 0xFF1A6B6B;
    private static final int DC_TEAL_BG    = 0x381A6B6B;
    private static final int DC_RED        = 0xFFFF8090;
    private static final int DC_RED_BD     = 0xFF8B1A1A;
    private static final int DC_RED_BG     = 0x388B1A1A;
    // Countdown ring colours
    private static final int DC_RING_TEAL  = 0xFF2A9A9A;
    private static final int DC_RING_GOLD  = 0xFFD4AF37;
    private static final int DC_RING_RED   = 0xFFC73838;
    private static final int DC_RING_TRACK = 0xFF1A0A30;

    // Dim badge colours (text | bg | border)
    private static final int DC_OW_T = 0xFF4FB870, DC_OW_BG = 0x144FB870, DC_OW_BD = 0x664FB870;
    private static final int DC_NE_T = 0xFFC84A3A, DC_NE_BG = 0x14C84A3A, DC_NE_BD = 0x66C84A3A;
    private static final int DC_EN_T = 0xFFD4AF37, DC_EN_BG = 0x14D4AF37, DC_EN_BD = 0x66D4AF37;
    private static final int DC_PK_T = 0xFFB07AFF, DC_PK_BG = 0x14B07AFF, DC_PK_BD = 0x66B07AFF;
    private static final int DC_XX_T = 0xFFAA88FF, DC_XX_BG = 0x14AA88FF, DC_XX_BD = 0x66AA88FF;

    // ── Constructors ──────────────────────────────────────────────────────────

    public MirrorCallScreen(Mode mode, String otherName, UUID callId) {
        this(mode, otherName, callId, null, null, 0.0);
    }

    public MirrorCallScreen(Mode mode, String otherName, UUID callId,
                             @Nullable String mirrorName, @Nullable String dimensionName,
                             double signalStrength) {
        super(Component.literal("Mirror Call"));
        this.mode           = mode;
        this.otherName      = otherName;
        this.callId         = callId;
        this.mirrorName     = mirrorName;
        this.dimensionName  = dimensionName;
        this.signalStrength = signalStrength;
    }

    private int panelLeft() { return (this.width - PANEL_W) / 2; }
    private int panelTop()  { return Math.max(10, (this.height - PANEL_H) / 2); }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() { rebuildWidgets(); }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int pl = panelLeft(), pt = panelTop();
        int actY  = pt + HEADER_H + CONTENT_H;
        int btnCY = actY + (ACTIONS_H - 22) / 2;

        if (mode == Mode.INCOMING) {
            // [ANSWER ENTER] wider, teal — rightmost (primary)
            int answerW = 100, declineW = 80;
            int answerX  = pl + PANEL_W - PAD - answerW;
            int declineX = answerX - declineW - 8;
            addRenderableWidget(MirrorButton.teal(answerX, btnCY, answerW, 22,
                    Component.literal("ANSWER  ENTER"),
                    b -> MirrorsNetwork.sendToServer(new ServerboundCallResponsePacket(callId, true))));
            addRenderableWidget(MirrorButton.red(declineX, btnCY, declineW, 22,
                    Component.literal("DECLINE  ESC"),
                    b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallResponsePacket(callId, false));
                        ClientCallState.clearIncomingCall();
                        Screen prev = previousScreen;
                        previousScreen = null;
                        Minecraft.getInstance().setScreen(prev);
                    }));
        } else if (mode == Mode.OUTGOING) {
            int cancelW = 120;
            int cancelX = pl + PANEL_W - PAD - cancelW;
            addRenderableWidget(MirrorButton.red(cancelX, btnCY, cancelW, 22,
                    Component.literal("CANCEL  ESC"),
                    b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallEndPacket(callId));
                        ClientCallState.clearOutgoingCall();
                        Screen prev = previousScreen;
                        previousScreen = null;
                        Minecraft.getInstance().setScreen(prev);
                    }));
        } else {
            // ACTIVE
            int endW = 120;
            addRenderableWidget(MirrorButton.red(
                    pl + PANEL_W - PAD - endW, btnCY, endW, 22,
                    Component.literal("END CALL"),
                    b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallEndPacket(callId));
                        ClientCallState.clearActiveCall();
                        onClose();
                    }));
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Partial screen dim (call doesn't fully hide world)
        g.fill(0, 0, this.width, this.height, 0x88010008);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + PANEL_H;

        // ── Panel chrome ──────────────────────────────────────────────────────
        g.fill(pl, pt, pr, pb, DC_PANEL);
        // Outer border
        g.fill(pl,   pt,   pr,   pt+1, DC_BDR_OUTER);
        g.fill(pl,   pb-1, pr,   pb,   DC_BDR_OUTER);
        g.fill(pl,   pt,   pl+1, pb,   DC_BDR_OUTER);
        g.fill(pr-1, pt,   pr,   pb,   DC_BDR_OUTER);
        // Inner border (55% opacity, inset 2)
        g.fill(pl+2, pt+2, pr-2, pt+3, DC_BDR_INNER);
        g.fill(pl+2, pb-3, pr-2, pb-2, DC_BDR_INNER);
        g.fill(pl+2, pt+2, pl+3, pb-2, DC_BDR_INNER);
        g.fill(pr-3, pt+2, pr-2, pb-2, DC_BDR_INNER);
        // Pulsing edge glow
        float pulse = UITheme.pulse();
        int gA = (int)(pulse * 0x50 + 0x18);
        g.fill(pl+4, pt,   pr-4, pt+1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, gA));
        g.fill(pl+4, pb-1, pr-4, pb,   UITheme.withAlpha(UITheme.BORDER_BRIGHT, gA));
        // Corner brackets
        drawCornerBracket(g, pl+4,  pt+4,  false, false);
        drawCornerBracket(g, pr-14, pt+4,  true,  false);
        drawCornerBracket(g, pl+4,  pb-14, false, true);
        drawCornerBracket(g, pr-14, pb-14, true,  true);

        // ── Header ────────────────────────────────────────────────────────────
        g.fill(pl, pt, pr, pt+HEADER_H, DC_HEADER_BG);
        g.fill(pl, pt+HEADER_H, pr, pt+HEADER_H+1, DC_BDR_DIM);
        int hY = pt + (HEADER_H - 8) / 2;
        g.drawString(font, "\u2736", pl+14, hY, DC_GOLD, false);
        g.drawString(font, "MIRROR CALL", pl+28, hY, DC_GOLD, false);
        drawModePill(g, pr, pt);

        // ── Content area ──────────────────────────────────────────────────────
        int contentY = pt + HEADER_H;
        int glyphX = pl + PAD;
        int glyphY = contentY + (CONTENT_H - GLYPH_SZ) / 2;

        drawGlyph(g, glyphX, glyphY);

        // Info block — between glyph and right widget
        int infoX  = glyphX + GLYPH_SZ + 16;
        int rightW = (mode == Mode.INCOMING) ? GLYPH_SZ : 80; // countdown or ringing widget
        int infoW  = pr - PAD - rightW - 12 - infoX;
        drawInfoBlock(g, infoX, glyphY, infoW);

        // Right widget
        int rightX = pr - PAD - rightW;
        if (mode == Mode.INCOMING) {
            drawRuneSegments(g, rightX, glyphY);
        } else if (mode == Mode.OUTGOING) {
            drawRinging(g, rightX, glyphY + 8);
        } else {
            drawTimer(g, rightX, glyphY + 8);
        }

        // ── Actions row ───────────────────────────────────────────────────────
        int actY = contentY + CONTENT_H;
        g.fill(pl, actY, pr, actY+1, DC_BDR_DIM);
        g.fill(pl, actY+1, pr, pb, UITheme.withAlpha(0xFF000000, 0x58));
        drawActionsMeta(g, pl, actY);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Mode pill ─────────────────────────────────────────────────────────────

    private void drawModePill(GuiGraphics g, int pr, int pt) {
        boolean incoming = (mode == Mode.INCOMING);
        boolean active   = (mode == Mode.ACTIVE);
        String label = incoming ? "INCOMING" : active ? "CONNECTED" : "OUTGOING";
        int txC  = incoming ? DC_TEAL  : active ? UITheme.SIGNAL_FULL : DC_TEXT_LAV;
        int bdC  = incoming ? DC_TEAL_BD : active ? 0xFF1A7B2B : DC_BDR_OUTER;
        int bgC  = incoming ? DC_TEAL_BG : active ? 0x281A7B2B : 0x1E7733CC;

        int pillW = font.width(label) + 16;
        int pillH = 18;
        int px = pr - pillW - 12;
        int py = pt + (HEADER_H - pillH) / 2;

        g.fill(px,       py,       px+pillW, py+pillH, bgC);
        g.fill(px,       py,       px+pillW, py+1,     bdC);
        g.fill(px,       py+pillH-1, px+pillW, py+pillH, bdC);
        g.fill(px,       py,       px+1,     py+pillH, bdC);
        g.fill(px+pillW-1, py,     px+pillW, py+pillH, bdC);

        // Pulsing dot
        float pulse = UITheme.pulse();
        int dotA = (int)(pulse * 0xCC + 0x33);
        g.fill(px+5, py+(pillH-4)/2, px+9, py+(pillH-4)/2+4, UITheme.withAlpha(txC, dotA));
        g.drawString(font, label, px+12, py+(pillH-8)/2, txC, false);
    }

    // ── Glyph box (64×64) ─────────────────────────────────────────────────────

    private void drawGlyph(GuiGraphics g, int x, int y) {
        boolean active = (mode == Mode.ACTIVE);

        // Box background — tinted by state
        int bgOuter = active ? 0xFF020E09 : 0xFF050018;
        int bgInner = active ? 0xFF031408 : 0xFF0A0025;
        g.fill(x, y, x+GLYPH_SZ, y+GLYPH_SZ, bgOuter);
        g.fill(x+8, y+8, x+GLYPH_SZ-8, y+GLYPH_SZ-8, bgInner);

        // Outer border
        int bdrOuter = active ? UITheme.withAlpha(UITheme.SIGNAL_FULL, 0x66) : DC_BDR_DIM;
        g.fill(x,             y,             x+GLYPH_SZ,   y+1,            bdrOuter);
        g.fill(x,             y+GLYPH_SZ-1,  x+GLYPH_SZ,   y+GLYPH_SZ,    bdrOuter);
        g.fill(x,             y,             x+1,           y+GLYPH_SZ,    bdrOuter);
        g.fill(x+GLYPH_SZ-1, y,             x+GLYPH_SZ,   y+GLYPH_SZ,    bdrOuter);

        // Inner border
        int bdrInner = active
                ? UITheme.withAlpha(UITheme.SIGNAL_FULL, 0x80)
                : UITheme.withAlpha(UITheme.BORDER_BRIGHT, 0x40);
        g.fill(x+1,           y+1,           x+GLYPH_SZ-1, y+2,            bdrInner);
        g.fill(x+1,           y+GLYPH_SZ-2,  x+GLYPH_SZ-1, y+GLYPH_SZ-1,  bdrInner);
        g.fill(x+1,           y+2,           x+2,           y+GLYPH_SZ-2,  bdrInner);
        g.fill(x+GLYPH_SZ-2, y+2,           x+GLYPH_SZ-1, y+GLYPH_SZ-2,  bdrInner);

        // Per-state animation overlay
        if (active) {
            drawGlyphCornerTicks(g, x, y);
        } else if (mode == Mode.INCOMING) {
            drawPulseRings(g, x, y);
        } else {
            drawSpinRing(g, x, y);
        }

        // Player face (32×32 centred in the 64×64 box)
        drawPlayerFace(g, x, y);
    }

    /** ACTIVE: green L-shaped corner ticks. */
    private void drawGlyphCornerTicks(GuiGraphics g, int x, int y) {
        int col = UITheme.withAlpha(UITheme.SIGNAL_FULL, 0xE6);
        int inset = 3, sz = 5, th = 1;
        // TL
        g.fill(x+inset,           y+inset,           x+inset+sz, y+inset+th, col);
        g.fill(x+inset,           y+inset,           x+inset+th, y+inset+sz, col);
        // TR
        g.fill(x+GLYPH_SZ-inset-sz, y+inset,         x+GLYPH_SZ-inset, y+inset+th, col);
        g.fill(x+GLYPH_SZ-inset-th, y+inset,         x+GLYPH_SZ-inset, y+inset+sz, col);
        // BL
        g.fill(x+inset,           y+GLYPH_SZ-inset-th, x+inset+sz, y+GLYPH_SZ-inset, col);
        g.fill(x+inset,           y+GLYPH_SZ-inset-sz, x+inset+th, y+GLYPH_SZ-inset, col);
        // BR
        g.fill(x+GLYPH_SZ-inset-sz, y+GLYPH_SZ-inset-th, x+GLYPH_SZ-inset, y+GLYPH_SZ-inset, col);
        g.fill(x+GLYPH_SZ-inset-th, y+GLYPH_SZ-inset-sz, x+GLYPH_SZ-inset, y+GLYPH_SZ-inset, col);
    }

    /** INCOMING: 3 square ring pulses that expand and fade. */
    private void drawPulseRings(GuiGraphics g, int gx, int gy) {
        long t = System.currentTimeMillis();
        int period = 1600;
        for (int r = 0; r < 3; r++) {
            float phase = ((t + r * (period / 3)) % period) / (float) period;
            int inset = (int)(10 - phase * 24);
            float opacity = phase < 0.2f ? (phase / 0.2f) * 0.7f
                                         : 0.7f * (1f - (phase - 0.2f) / 0.8f);
            int alpha = (int)(opacity * 0xFF);
            if (alpha < 4) continue;
            int x1 = gx + inset, y1 = gy + inset;
            int x2 = gx + GLYPH_SZ - inset, y2 = gy + GLYPH_SZ - inset;
            if (x2 <= x1 || y2 <= y1) continue;
            int col = UITheme.withAlpha(UITheme.BORDER_BRIGHT, alpha);
            g.fill(x1, y1, x2,    y1+1, col);
            g.fill(x1, y2-1, x2,  y2,   col);
            g.fill(x1, y1, x1+1,  y2,   col);
            g.fill(x2-1, y1, x2,  y2,   col);
        }
    }

    /** OUTGOING: single dashed border segment sweeping clockwise around the box. */
    private void drawSpinRing(GuiGraphics g, int gx, int gy) {
        long t = System.currentTimeMillis();
        int perim = GLYPH_SZ * 4;
        int dashLen = 14;
        int offset = (int)((t % 4000L) * perim / 4000L);
        int col    = UITheme.withAlpha(UITheme.BORDER_BRIGHT, 0xB0);
        int colDim = UITheme.withAlpha(UITheme.BORDER_BRIGHT, 0x22);

        g.fill(gx,              gy,              gx+GLYPH_SZ, gy+1,             colDim);
        g.fill(gx,              gy+GLYPH_SZ-1,   gx+GLYPH_SZ, gy+GLYPH_SZ,     colDim);
        g.fill(gx,              gy,              gx+1,         gy+GLYPH_SZ,     colDim);
        g.fill(gx+GLYPH_SZ-1,  gy,              gx+GLYPH_SZ, gy+GLYPH_SZ,     colDim);

        for (int d = 0; d < 2; d++) {
            int dashStart = (offset + d * (perim / 2)) % perim;
            for (int p = 0; p < dashLen; p++) {
                perimPixel(g, gx, gy, (dashStart + p) % perim, col);
            }
        }
    }

    /** Fill one pixel at position `pos` along the clockwise perimeter of the glyph box. */
    private void perimPixel(GuiGraphics g, int gx, int gy, int pos, int col) {
        int sz = GLYPH_SZ;
        int px, py;
        if (pos < sz) {
            px = gx + pos; py = gy;
        } else if (pos < sz * 2) {
            px = gx + sz - 1; py = gy + (pos - sz);
        } else if (pos < sz * 3) {
            px = gx + sz - 1 - (pos - sz * 2); py = gy + sz - 1;
        } else {
            px = gx; py = gy + sz - 1 - (pos - sz * 3);
        }
        g.fill(px, py, px + 1, py + 1, col);
    }

    /** Player skin face — blits the 8×8 face tile (UV 8,8) at 4× scale centred in the glyph box. */
    private void drawPlayerFace(GuiGraphics g, int glyphX, int glyphY) {
        ResourceLocation skin = getSkinTexture();
        int faceX = glyphX + 16; // centre of 64px box: (64-32)/2 = 16
        int faceY = glyphY + 16;
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(faceX, faceY, 0f);
        pose.scale(4f, 4f, 1f);
        // Blit face region (u=8, v=8, w=8, h=8) from 64×64 skin → draws as 32×32 after scale
        g.blit(skin, 0, 0, 8f, 8f, 8, 8, 64, 64);
        pose.popPose();
    }

    // Steve skin fallback — used when the other player's skin isn't loaded yet
    private static final ResourceLocation STEVE_SKIN =
            new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");

    /** Resolve the other player's skin texture; falls back to Steve if not available. */
    private ResourceLocation getSkinTexture() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            PlayerInfo info = conn.getPlayerInfo(otherName);
            if (info != null) {
                ResourceLocation loc = info.getSkinLocation();
                if (loc != null) return loc;
            }
        }
        return STEVE_SKIN;
    }

    // ── Info block ────────────────────────────────────────────────────────────

    private void drawInfoBlock(GuiGraphics g, int x, int glyphY, int maxW) {
        int y = glyphY + 6;

        // Line 1: name / state
        String who = (mode == Mode.OUTGOING) ? "CALLING " + otherName.toUpperCase()
                                              : otherName.toUpperCase();
        g.drawString(font, truncate(who, maxW), x, y, DC_GOLD, false);

        // Line 2: mirror name (if available)
        if (mirrorName != null && !mirrorName.isEmpty()) {
            g.drawString(font, "\u25c8 " + mirrorName, x, y + 16,
                    UITheme.withAlpha(DC_TEXT_LAV, 0xDD), false);
        } else if (mode == Mode.ACTIVE) {
            g.drawString(font, "CALL ACTIVE", x, y + 16,
                    UITheme.withAlpha(UITheme.SIGNAL_FULL, 0xCC), false);
        }

        // Line 3: dim badge + signal (if available)
        if (dimensionName != null && !dimensionName.isEmpty()) {
            int mx = x;
            mx += drawCallDimBadge(g, mx, y + 34, dimensionName);
            if (signalStrength > 0) {
                mx += 10;
                g.drawString(font, "\u00b7", mx, y+34, UITheme.withAlpha(DC_TEXT_MUTED, 0x80), false);
                mx += font.width("\u00b7") + 6;
                drawCallSignalBars(g, mx, y + 34 + 8, signalStrength);
                mx += 18;
                String sigLabel = signalStrength > 0.8 ? "STRONG"
                        : signalStrength > 0.6 ? "GOOD"
                        : signalStrength > 0.4 ? "FAIR"
                        : "WEAK";
                g.drawString(font, sigLabel, mx, y+34, UITheme.withAlpha(DC_TEXT_LAV, 0xCC), false);
            }
        }
    }

    // ── Rune-segment countdown (INCOMING) ────────────────────────────────────
    // 8 arc segments at radius=26, stroke 6px (r 23-28), 45° apart, 30.8° each.
    // Segments deplete from the end as time runs out.
    // Colour: >50% teal, 25-50% gold, ≤25% red (last 1-2 pulse at 0.8s).

    private void drawRuneSegments(GuiGraphics g, int x, int y) {
        int timeoutMs  = com.ether.mirrors.MirrorsConfig.CALL_TIMEOUT_SECONDS.get() * 1000;
        long remaining = Math.max(0, openTimeMs + timeoutMs - System.currentTimeMillis());
        float fraction = (float) remaining / timeoutMs;
        int secsLeft   = (int)(remaining / 1000) + 1;
        long t         = System.currentTimeMillis();

        int SEGS    = 8;
        int litCount = (fraction <= 0f) ? 0 : (int) Math.ceil(SEGS * fraction);

        boolean urgent   = fraction <= 0.25f;
        int segColor = fraction > 0.5f ? DC_RING_TEAL
                     : fraction > 0.25f ? DC_RING_GOLD
                     : DC_RING_RED;

        int cx = x + GLYPH_SZ / 2;
        int cy = y + GLYPH_SZ / 2;

        double sweepRad = Math.toRadians(30.8); // arc span per segment
        double stepRad  = Math.toRadians(45.0); // 360°/8

        for (int i = 0; i < SEGS; i++) {
            double startAngle = -Math.PI / 2.0 + i * stepRad;
            boolean lit = (i < litCount);

            int col;
            if (!lit) {
                col = DC_RING_TRACK;
            } else {
                boolean pulse = urgent && (i >= litCount - 2);
                if (pulse) {
                    float phase = (t % 800L) / 800f;
                    float alpha = 0.35f + 0.65f * (float) Math.abs(Math.cos(phase * Math.PI));
                    col = UITheme.withAlpha(segColor, (int)(alpha * 0xFF));
                } else {
                    col = segColor;
                }
            }

            // Draw pixels along the arc at radii 23-28 (stroke-width 6, centred on r=26)
            int arcSteps = 22;
            for (int s = 0; s <= arcSteps; s++) {
                double angle = startAngle + sweepRad * s / arcSteps;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                for (int r = 23; r <= 28; r++) {
                    int px = cx + (int) Math.round(r * cos);
                    int py = cy + (int) Math.round(r * sin);
                    g.fill(px, py, px + 1, py + 1, col);
                }
            }
        }

        // Centre number
        String numStr  = secsLeft + "s";
        int numColor;
        if (urgent) {
            boolean blink = (t % 800L) < 400L;
            numColor = blink ? DC_RED : UITheme.withAlpha(DC_RED, 0xA0);
        } else {
            numColor = fraction > 0.5f ? DC_TEXT_LAV : DC_GOLD;
        }
        g.drawString(font, numStr, cx - font.width(numStr) / 2, cy - 4, numColor, false);
    }

    // ── Ringing indicator (OUTGOING) ──────────────────────────────────────────

    private void drawRinging(GuiGraphics g, int x, int y) {
        // "RINGING" label
        g.drawString(font, "RINGING", x, y, UITheme.withAlpha(DC_TEXT_LAV, 0xCC), false);

        // 3-dot stagger bounce (y offset out of phase)
        long t = System.currentTimeMillis();
        int dotSize = 4, dotGap = 6;
        int dotsStartX = x;
        for (int d = 0; d < 3; d++) {
            float phase = ((t + d * 200) % 1200) / 1200f;
            // Sine wave: peak at phase=0.5
            float bounce = (float) Math.sin(phase * Math.PI);
            int dy = (int)(bounce * 3);
            int dx = dotsStartX + d * (dotSize + dotGap);
            int alpha = (int)(0x44 + bounce * 0xBB);
            g.fill(dx, y + 14 - dy, dx + dotSize, y + 14 - dy + dotSize,
                    UITheme.withAlpha(DC_TEXT_LAV, alpha));
        }

        // Elapsed time
        long elapsed = System.currentTimeMillis() - openTimeMs;
        int mins = (int)(elapsed / 60000);
        int secs = (int)((elapsed % 60000) / 1000);
        String elapsed_str = mins + ":" + String.format("%02d", secs);
        g.drawString(font, elapsed_str, x, y + 26,
                UITheme.withAlpha(DC_TEXT_MUTED, 0x99), false);
    }

    // ── Active call timer (ACTIVE) ────────────────────────────────────────────
    // Shows: pulsing green dot + "CONNECTED" / large elapsed clock / quality label.
    // x,y = top-left of the 80px wide timer widget.

    private void drawTimer(GuiGraphics g, int x, int y) {
        int w = 80;
        // Row 1: green dot + CONNECTED
        float pulse = UITheme.pulse();
        int dotA = (int)(pulse * 0xCC + 0x33);
        g.fill(x, y + 2, x + 5, y + 7, UITheme.withAlpha(UITheme.SIGNAL_FULL, dotA));
        String connLabel = "CONNECTED";
        g.drawString(font, connLabel, x + 9, y,
                UITheme.withAlpha(UITheme.SIGNAL_FULL, 0xCC), false);

        // Row 2: elapsed clock (gold, centered)
        long elapsed = System.currentTimeMillis() - openTimeMs;
        int mins = (int)(elapsed / 60000);
        int secs = (int)((elapsed % 60000) / 1000);
        String clock = mins + ":" + String.format("%02d", secs);
        g.drawString(font, clock, x + (w - font.width(clock)) / 2, y + 18, DC_GOLD, false);

        // Row 3: quality label (muted, centered)
        String quality = signalStrength > 0.8 ? "CRYSTAL"
                       : signalStrength > 0.5 ? "STABLE"
                       : signalStrength > 0.3 ? "FAIR" : "WEAK";
        g.drawString(font, quality, x + (w - font.width(quality)) / 2, y + 34,
                UITheme.withAlpha(DC_TEXT_MUTED, 0x99), false);
    }

    // ── Actions meta text ─────────────────────────────────────────────────────

    private void drawActionsMeta(GuiGraphics g, int pl, int actY) {
        int textY = actY + (ACTIONS_H - 8) / 2;
        if (mode == Mode.INCOMING) {
            int timeoutMs = com.ether.mirrors.MirrorsConfig.CALL_TIMEOUT_SECONDS.get() * 1000;
            long remaining = Math.max(0, openTimeMs + timeoutMs - System.currentTimeMillis());
            int secsLeft = (int)(remaining / 1000) + 1;
            String meta = "AUTO-DECLINES IN " + secsLeft + "S";
            g.drawString(font, meta, pl + PAD, textY,
                    UITheme.withAlpha(DC_TEXT_MUTED, 0xCC), false);
        } else if (mode == Mode.OUTGOING) {
            float pulse = UITheme.pulse();
            int dotA = (int)(pulse * 0xCC + 0x33);
            g.drawString(font, "AWAITING ANSWER", pl + PAD, textY,
                    UITheme.withAlpha(DC_TEXT_MUTED, 0xCC), false);
            g.drawString(font, "\u2026", pl + PAD + font.width("AWAITING ANSWER") + 3, textY,
                    UITheme.withAlpha(DC_TEXT_LAV, dotA), false);
        } else {
            long elapsed = System.currentTimeMillis() - openTimeMs;
            int mins = (int)(elapsed / 60000);
            int secs = (int)((elapsed % 60000) / 1000);
            g.drawString(font, "CONNECTED \u00b7 " + mins + ":" + String.format("%02d", secs),
                    pl + PAD, textY, UITheme.withAlpha(UITheme.SIGNAL_FULL, 0xCC), false);
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void drawCornerBracket(GuiGraphics g, int x, int y, boolean flipH, boolean flipV) {
        int sz = 10, th = 1;
        int col = UITheme.withAlpha(DC_GOLD, 0xB3);
        if (!flipH && !flipV) {
            g.fill(x, y, x+sz, y+th, col); g.fill(x, y, x+th, y+sz, col);
        } else if (flipH && !flipV) {
            g.fill(x, y, x+sz, y+th, col); g.fill(x+sz-th, y, x+sz, y+sz, col);
        } else if (!flipH) {
            g.fill(x, y+sz-th, x+sz, y+sz, col); g.fill(x, y, x+th, y+sz, col);
        } else {
            g.fill(x, y+sz-th, x+sz, y+sz, col); g.fill(x+sz-th, y, x+sz, y+sz, col);
        }
    }

    /** Returns badge width drawn. */
    private int drawCallDimBadge(GuiGraphics g, int x, int y, String dim) {
        String label;
        int bgC, bdC, txC;
        if (dim.contains("overworld"))      { label = "OVERWORLD"; bgC=DC_OW_BG; bdC=DC_OW_BD; txC=DC_OW_T; }
        else if (dim.contains("nether"))    { label = "NETHER";    bgC=DC_NE_BG; bdC=DC_NE_BD; txC=DC_NE_T; }
        else if (dim.contains("the_end"))   { label = "THE END";   bgC=DC_EN_BG; bdC=DC_EN_BD; txC=DC_EN_T; }
        else if (dim.contains("pocket"))    { label = "POCKET";    bgC=DC_PK_BG; bdC=DC_PK_BD; txC=DC_PK_T; }
        else {
            String path = dim.contains(":") ? dim.substring(dim.indexOf(':')+1) : dim;
            label = path.replace("_"," ").toUpperCase();
            if (label.length() > 10) label = label.substring(0,10);
            bgC=DC_XX_BG; bdC=DC_XX_BD; txC=DC_XX_T;
        }
        int tw = font.width(label), bw = tw + 10, bh = 11;
        g.fill(x,    y,    x+bw, y+bh,  bgC);
        g.fill(x,    y,    x+bw, y+1,   bdC);
        g.fill(x,    y+bh-1, x+bw, y+bh, bdC);
        g.fill(x,    y,    x+1,  y+bh,  bdC);
        g.fill(x+bw-1, y,  x+bw, y+bh, bdC);
        g.drawString(font, label, x+5, y+2, txC, false);
        return bw;
    }

    private void drawCallSignalBars(GuiGraphics g, int x, int bottomY, double signal) {
        final int[] BH = {3, 5, 7, 9, 11};
        int filled = Math.max(0, Math.min(5, (int)(signal * 5 + 0.001)));
        for (int i = 0; i < 5; i++) {
            int bx = x + i * 3;
            g.fill(bx, bottomY-BH[i], bx+2, bottomY, i < filled ? DC_SIG_FULL : DC_SIG_DIM);
        }
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String e = "\u2026";
        int ew = font.width(e);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (char c : text.toCharArray()) {
            int cw = font.width(String.valueOf(c));
            if (w + cw + ew > maxWidth) break;
            sb.append(c); w += cw;
        }
        return sb + e;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Block ESC during active call
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && mode == Mode.ACTIVE) return true;
        // ENTER to answer incoming
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && mode == Mode.INCOMING) {
            MirrorsNetwork.sendToServer(new ServerboundCallResponsePacket(callId, true));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public Mode getMode() { return mode; }
}
