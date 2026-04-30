package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundPermissionResponsePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Modal overlay displayed when a {@code ClientboundPermissionNotifyPacket} arrives.
 * Allows the mirror owner to approve or deny an access request in-context,
 * with a 28-second auto-deny countdown.
 */
public class PermissionRequestModal extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────

    private static final int PANEL_W   = 460;
    private static final int HEADER_H  = 32;
    private static final int CONTENT_H = 68;
    private static final int ACTIONS_H = 40;
    private static final int PANEL_H   = 140;   // 32 + 68 + 40
    private static final int GLYPH_SZ  = 48;    // 48×48 (smaller than call screen's 64)
    private static final int PAD       = 18;

    // ── Design colours ────────────────────────────────────────────────────────

    private static final int DC_PANEL      = 0xFF030012;
    private static final int DC_HEADER_BG  = 0xFF020020;
    private static final int DC_BDR_OUTER  = 0xFF7733CC;
    private static final int DC_BDR_INNER  = 0x8CAA55FF;
    private static final int DC_BDR_DIM    = 0xFF2A1144;
    private static final int DC_TEXT_MUTED = 0xFF9999AA;
    private static final int DC_TEXT_LAV   = 0xFFAA88FF;
    private static final int DC_GOLD       = 0xFFD4AF37;

    private static final int DC_TEAL    = 0xFF6FE0E0;
    private static final int DC_TEAL_BD = 0xFF1A6B6B;
    private static final int DC_TEAL_BG = 0x381A6B6B;
    private static final int DC_RED     = 0xFFFF8090;
    private static final int DC_RED_BD  = 0xFF8B1A1A;
    private static final int DC_RED_BG  = 0x388B1A1A;

    private static final int DC_GREEN_LIVE = 0xFF44FF77;

    // Steve fallback skin
    private static final ResourceLocation STEVE_SKIN =
            new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String requesterName;
    private final UUID   requesterUUID;
    private final long   openTimeMs = System.currentTimeMillis();
    private static final int AUTO_DENY_MS = 28_000;

    private boolean resolved = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PermissionRequestModal(String requesterName, UUID requesterUUID) {
        super(Component.literal("Permission Request"));
        this.requesterName = requesterName;
        this.requesterUUID = requesterUUID;
    }

    // ── Panel geometry ────────────────────────────────────────────────────────

    private int panelLeft() { return (this.width  - PANEL_W) / 2; }
    private int panelTop()  { return Math.max(10, (this.height - PANEL_H) / 2); }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        clearWidgets();
        int pl = panelLeft();
        int pt = panelTop();

        int actY  = pt + HEADER_H + CONTENT_H;
        int btnCY = actY + (ACTIONS_H - 22) / 2;
        int pr    = pl + PANEL_W;

        // APPROVE (teal, wider — rightmost, primary action)
        int approveW = 100;
        int denyW    = 80;
        int approveX = pr - PAD - approveW;
        int denyX    = approveX - denyW - 8;

        addRenderableWidget(MirrorButton.teal(approveX, btnCY, approveW, 22,
                Component.literal("APPROVE  ENTER"),
                b -> approve()));

        addRenderableWidget(MirrorButton.red(denyX, btnCY, denyW, 22,
                Component.literal("DENY  ESC"),
                b -> deny()));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Auto-deny check each frame
        if (!resolved && System.currentTimeMillis() - openTimeMs >= AUTO_DENY_MS) {
            deny();
            return;
        }

        // Partial dim (does not fully hide world)
        g.fill(0, 0, this.width, this.height, 0x88010008);

        int pl = panelLeft();
        int pt = panelTop();
        int pr = pl + PANEL_W;
        int pb = pt + PANEL_H;

        drawPanelChrome(g, pl, pt, pr, pb);
        drawHeader(g, pl, pt, pr);
        drawContent(g, pl, pt, pr);
        drawActionsBar(g, pl, pt, pr, pb);

        super.render(g, mouseX, mouseY, partial);
    }

    // ── Panel chrome ──────────────────────────────────────────────────────────

    private void drawPanelChrome(GuiGraphics g, int pl, int pt, int pr, int pb) {
        g.fill(pl, pt, pr, pb, DC_PANEL);

        // Outer border (1px)
        g.fill(pl,   pt,   pr,   pt+1, DC_BDR_OUTER);
        g.fill(pl,   pb-1, pr,   pb,   DC_BDR_OUTER);
        g.fill(pl,   pt,   pl+1, pb,   DC_BDR_OUTER);
        g.fill(pr-1, pt,   pr,   pb,   DC_BDR_OUTER);

        // Inner border (inset 2, 55% opacity)
        g.fill(pl+2, pt+2, pr-2, pt+3, DC_BDR_INNER);
        g.fill(pl+2, pb-3, pr-2, pb-2, DC_BDR_INNER);
        g.fill(pl+2, pt+2, pl+3, pb-2, DC_BDR_INNER);
        g.fill(pr-3, pt+2, pr-2, pb-2, DC_BDR_INNER);

        // Pulsing edge glow
        float pulse = UITheme.pulse();
        int gA = (int)(pulse * 0x50 + 0x18);
        g.fill(pl+4, pt,   pr-4, pt+1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, gA));
        g.fill(pl+4, pb-1, pr-4, pb,   UITheme.withAlpha(UITheme.BORDER_BRIGHT, gA));

        // Gold corner brackets
        drawCornerBracket(g, pl+4,  pt+4,  false, false);
        drawCornerBracket(g, pr-14, pt+4,  true,  false);
        drawCornerBracket(g, pl+4,  pb-14, false, true);
        drawCornerBracket(g, pr-14, pb-14, true,  true);
    }

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

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, int pl, int pt, int pr) {
        g.fill(pl, pt, pr, pt + HEADER_H, DC_HEADER_BG);
        g.fill(pl, pt + HEADER_H, pr, pt + HEADER_H + 1, DC_BDR_DIM);

        int hY = pt + (HEADER_H - 8) / 2;
        g.drawString(font, "\u2BC6", pl + 14, hY, DC_GOLD, false);
        g.drawString(font, "PERMISSION REQUEST", pl + 28, hY, DC_GOLD, false);

        // "LIVE" pill with pulsing green dot
        drawLivePill(g, pr, pt);
    }

    private void drawLivePill(GuiGraphics g, int pr, int pt) {
        String label = "LIVE";
        int pillW = font.width(label) + 22;
        int pillH = 18;
        int px = pr - pillW - 12;
        int py = pt + (HEADER_H - pillH) / 2;

        int bdC = 0xFF1A7B2B;
        int bgC = 0x281A7B2B;

        g.fill(px, py, px+pillW, py+pillH, bgC);
        g.fill(px,         py,         px+pillW, py+1,       bdC);
        g.fill(px,         py+pillH-1, px+pillW, py+pillH,   bdC);
        g.fill(px,         py,         px+1,     py+pillH,   bdC);
        g.fill(px+pillW-1, py,         px+pillW, py+pillH,   bdC);

        // Pulsing green dot
        float pulse = UITheme.pulse();
        int dotA = (int)(pulse * 0xCC + 0x33);
        g.fill(px+5, py+(pillH-4)/2, px+9, py+(pillH-4)/2+4, UITheme.withAlpha(DC_GREEN_LIVE, dotA));
        g.drawString(font, label, px + 12, py + (pillH - 8) / 2, DC_GREEN_LIVE, false);
    }

    // ── Content area ──────────────────────────────────────────────────────────

    private void drawContent(GuiGraphics g, int pl, int pt, int pr) {
        int contentY = pt + HEADER_H;

        // Glyph box (48×48)
        int glyphX = pl + PAD;
        int glyphY = contentY + (CONTENT_H - GLYPH_SZ) / 2;
        drawGlyph(g, glyphX, glyphY);

        // Info block to the right of the glyph
        int infoX = glyphX + GLYPH_SZ + 16;
        int infoW = pr - PAD - infoX;
        drawInfoBlock(g, infoX, glyphY, infoW);
    }

    // ── Glyph (48×48) with pulse rings and face ───────────────────────────────

    private void drawGlyph(GuiGraphics g, int x, int y) {
        // Background: purple radial gradient approximation via layered fills
        g.fill(x,    y,    x+GLYPH_SZ, y+GLYPH_SZ, 0xFF050018);
        g.fill(x+6,  y+6,  x+GLYPH_SZ-6,  y+GLYPH_SZ-6,  0xFF0A0025);
        g.fill(x+12, y+12, x+GLYPH_SZ-12, y+GLYPH_SZ-12, 0xFF0F002E);

        // Outer border
        int bdrDim = 0xFF2A1144;
        g.fill(x,             y,             x+GLYPH_SZ,   y+1,           bdrDim);
        g.fill(x,             y+GLYPH_SZ-1,  x+GLYPH_SZ,   y+GLYPH_SZ,   bdrDim);
        g.fill(x,             y,             x+1,           y+GLYPH_SZ,   bdrDim);
        g.fill(x+GLYPH_SZ-1, y,             x+GLYPH_SZ,   y+GLYPH_SZ,   bdrDim);

        // Inner border
        int bdrInner = UITheme.withAlpha(UITheme.BORDER_BRIGHT, 0x40);
        g.fill(x+1,           y+1,           x+GLYPH_SZ-1, y+2,           bdrInner);
        g.fill(x+1,           y+GLYPH_SZ-2,  x+GLYPH_SZ-1, y+GLYPH_SZ-1, bdrInner);
        g.fill(x+1,           y+2,           x+2,           y+GLYPH_SZ-2, bdrInner);
        g.fill(x+GLYPH_SZ-2, y+2,           x+GLYPH_SZ-1, y+GLYPH_SZ-2, bdrInner);

        // 2 expanding square pulse rings (incoming-style)
        drawPulseRings(g, x, y);

        // Player face (32×32 centred in 48×48 box)
        drawPlayerFace(g, x, y);
    }

    /**
     * Two expanding square ring pulses that grow from the 48×48 glyph boundary
     * and fade as they expand, using a 1600ms period.
     */
    private void drawPulseRings(GuiGraphics g, int gx, int gy) {
        long t = System.currentTimeMillis();
        int period = 1600;
        for (int r = 0; r < 2; r++) {
            float phase = ((t + r * (period / 2)) % period) / (float) period;
            int inset = (int)(8 - phase * 20);
            float opacity = phase < 0.2f
                    ? (phase / 0.2f) * 0.7f
                    : 0.7f * (1f - (phase - 0.2f) / 0.8f);
            int alpha = (int)(opacity * 0xFF);
            if (alpha < 4) continue;
            int x1 = gx + inset;
            int y1 = gy + inset;
            int x2 = gx + GLYPH_SZ - inset;
            int y2 = gy + GLYPH_SZ - inset;
            if (x2 <= x1 || y2 <= y1) continue;
            int col = UITheme.withAlpha(UITheme.BORDER_BRIGHT, alpha);
            g.fill(x1, y1, x2,    y1+1, col);
            g.fill(x1, y2-1, x2,  y2,   col);
            g.fill(x1, y1, x1+1,  y2,   col);
            g.fill(x2-1, y1, x2,  y2,   col);
        }
    }

    /** Blits 32×32 player face (UV 8,8 → 8×8 from 64×64 skin) at 4× scale, centred in the 48×48 glyph box. */
    private void drawPlayerFace(GuiGraphics g, int glyphX, int glyphY) {
        ResourceLocation skin = getSkinTexture();
        // Centre 32×32 in 48×48: (48-32)/2 = 8
        int faceX = glyphX + 8;
        int faceY = glyphY + 8;
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(faceX, faceY, 0f);
        pose.scale(4f, 4f, 1f);
        // 8×8 face tile from 64×64 skin → 32×32 after 4× scale
        g.blit(skin, 0, 0, 8f, 8f, 8, 8, 64, 64);
        pose.popPose();
    }

    /** Resolve the requester's skin; falls back to Steve if not loaded yet. */
    private ResourceLocation getSkinTexture() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            PlayerInfo info = conn.getPlayerInfo(requesterName);
            if (info != null) {
                ResourceLocation loc = info.getSkinLocation();
                if (loc != null) return loc;
            }
        }
        return STEVE_SKIN;
    }

    // ── Info block ────────────────────────────────────────────────────────────

    private void drawInfoBlock(GuiGraphics g, int x, int glyphY, int maxW) {
        int y = glyphY + 4;

        // Line 1: requester name (gold, prominent)
        g.drawString(font, truncate(requesterName.toUpperCase(), maxW), x, y, DC_GOLD, false);

        // Line 2: "WANTS TO ENTER YOUR MIRROR"
        int lx = x;
        g.drawString(font, "WANTS TO ", lx, y + 14, DC_TEXT_MUTED, false);
        lx += font.width("WANTS TO ");
        g.drawString(font, "ENTER", lx, y + 14, DC_TEXT_LAV, false);
        lx += font.width("ENTER");
        g.drawString(font, " YOUR MIRROR", lx, y + 14, DC_TEXT_MUTED, false);

        // Line 3: auto-deny countdown in red
        long remaining = Math.max(0, openTimeMs + AUTO_DENY_MS - System.currentTimeMillis());
        int secsLeft = (int)(remaining / 1000) + 1;
        String countdown = "AUTO-DENIES IN " + secsLeft + "s";
        boolean blink = (System.currentTimeMillis() % 1000) < 500;
        int countdownColor = (secsLeft <= 5 && blink)
                ? UITheme.withAlpha(DC_RED, 0xCC)
                : DC_TEXT_MUTED;
        g.drawString(font, countdown, x, y + 30, countdownColor, false);
    }

    // ── Actions bar ───────────────────────────────────────────────────────────

    private void drawActionsBar(GuiGraphics g, int pl, int pt, int pr, int pb) {
        int actY = pt + HEADER_H + CONTENT_H;
        g.fill(pl, actY, pr, actY + 1, DC_BDR_DIM);
        g.fill(pl, actY + 1, pr, pb, UITheme.withAlpha(0xFF000000, 0x58));

        // Left meta: countdown seconds remaining
        long remaining = Math.max(0, openTimeMs + AUTO_DENY_MS - System.currentTimeMillis());
        int secsLeft = (int)(remaining / 1000) + 1;
        int textY = actY + (ACTIONS_H - 8) / 2;
        String meta = "AUTO-DENIES IN " + secsLeft + "s";
        g.drawString(font, meta, pl + PAD, textY, UITheme.withAlpha(DC_TEXT_MUTED, 0xCC), false);
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            deny();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            approve();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void approve() {
        if (resolved) return;
        resolved = true;
        MirrorsNetwork.sendToServer(new ServerboundPermissionResponsePacket(
                requesterUUID, true,
                ServerboundPermissionResponsePacket.FLAG_USE
                        | ServerboundPermissionResponsePacket.FLAG_VIEW_CAMERA));
        onClose();
    }

    private void deny() {
        if (resolved) return;
        resolved = true;
        MirrorsNetwork.sendToServer(
                new ServerboundPermissionResponsePacket(requesterUUID, false, 0));
        onClose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "\u2026";
        int ew = font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (char c : text.toCharArray()) {
            int cw = font.width(String.valueOf(c));
            if (w + cw + ew > maxWidth) break;
            sb.append(c);
            w += cw;
        }
        return sb + ellipsis;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
