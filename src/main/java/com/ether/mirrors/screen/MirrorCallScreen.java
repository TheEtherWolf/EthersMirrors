package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ClientCallState;
import com.ether.mirrors.network.packets.ServerboundCallEndPacket;
import com.ether.mirrors.network.packets.ServerboundCallResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.UUID;

public class MirrorCallScreen extends Screen {

    public enum Mode { INCOMING, OUTGOING, ACTIVE }

    /**
     * Screen to restore when an incoming or outgoing call is cancelled/declined.
     * Set by the incoming/ringing packet handlers before opening this screen.
     * Cleared on accept (call becomes active) or on close after decline/cancel.
     */
    @Nullable
    public static Screen previousScreen = null;

    private Mode mode;
    private final String otherName;
    private final UUID callId;
    private final long openTimeMs = System.currentTimeMillis();

    public MirrorCallScreen(Mode mode, String otherName, UUID callId) {
        super(Component.literal("Mirror Call"));
        this.mode = mode;
        this.otherName = otherName;
        this.callId = callId;
    }

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 90;

    private int panelLeft() { return (this.width - PANEL_W) / 2; }
    private int panelTop()  { return this.height - PANEL_H - 20; }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int pl = panelLeft(), pt = panelTop();
        int cx = pl + PANEL_W / 2;
        int btnY = pt + PANEL_H - 26;

        if (mode == Mode.INCOMING) {
            // Accept button
            addRenderableWidget(MirrorButton.green(cx - 100, btnY, 90, 16,
                    Component.literal("Accept"), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallResponsePacket(callId, true));
                        // previousScreen is cleared by ClientboundCallEstablishedPacket handler
                        // Don't transition to ACTIVE here — wait for ClientboundCallEstablishedPacket
                    }));
            // Decline button
            addRenderableWidget(MirrorButton.red(cx + 10, btnY, 90, 16,
                    Component.literal("Decline"), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallResponsePacket(callId, false));
                        ClientCallState.clearIncomingCall();
                        Screen prev = previousScreen;
                        previousScreen = null;
                        Minecraft.getInstance().setScreen(prev); // restore management screen if it was open
                    }));
        } else if (mode == Mode.OUTGOING) {
            // Cancel Call button
            addRenderableWidget(MirrorButton.red(cx - 50, btnY, 100, 16,
                    Component.literal("Cancel Call"), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallEndPacket(callId));
                        ClientCallState.clearOutgoingCall();
                        Screen prev = previousScreen;
                        previousScreen = null;
                        Minecraft.getInstance().setScreen(prev);
                    }));
        } else {
            // End Call button
            addRenderableWidget(MirrorButton.red(cx - 50, btnY, 100, 16,
                    Component.literal("End Call"), b -> {
                        MirrorsNetwork.sendToServer(new ServerboundCallEndPacket(callId));
                        ClientCallState.clearActiveCall();
                        onClose();
                    }));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + PANEL_H;

        UITheme.drawPanel(g, pl, pt, pr, pb);
        UITheme.drawHeader(g, pl, pt, pr, 22);

        float pulse = UITheme.pulse();
        int ga = (int)(pulse * 0x80 + 0x20);
        g.fill(pl - 1, pt - 1, pr + 1, pt, UITheme.withAlpha(UITheme.BORDER_BRIGHT, ga));
        g.fill(pl - 1, pb, pr + 1, pb + 1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, ga));

        int cx = pl + PANEL_W / 2;

        if (mode == Mode.INCOMING) {
            g.drawCenteredString(font, "Incoming Mirror Call", cx, pt + 4, UITheme.TEXT_MUTED);
            g.drawCenteredString(font, "* " + otherName + " *", cx, pt + 14, UITheme.TEXT_GOLD);
            g.drawCenteredString(font, "is calling you", cx, pt + 36, UITheme.TEXT_LAVENDER);
            // Countdown timer
            int timeoutSecs = com.ether.mirrors.MirrorsConfig.CALL_TIMEOUT_SECONDS.get();
            long remaining = (openTimeMs + timeoutSecs * 1000L) - System.currentTimeMillis();
            if (remaining > 0) {
                int secs = (int)(remaining / 1000) + 1;
                g.drawCenteredString(font, "Expires in " + secs + "s", cx, pt + 48,
                        UITheme.withAlpha(UITheme.TEXT_MUTED, 0xAA));
            }
        } else if (mode == Mode.OUTGOING) {
            g.drawCenteredString(font, "Calling...", cx, pt + 4, UITheme.TEXT_MUTED);
            g.drawCenteredString(font, "* " + otherName + " *", cx, pt + 14, UITheme.TEXT_GOLD);
            // Pulsing waiting indicator
            int dotAlpha = (int)(pulse * 0xFF);
            g.fill(cx - 4, pt + 36, cx + 4, pt + 44, UITheme.withAlpha(UITheme.TEXT_LAVENDER, dotAlpha));
            g.drawCenteredString(font, "Waiting for answer...", cx, pt + 38, UITheme.TEXT_MUTED);
        } else {
            g.drawCenteredString(font, "Mirror Call Active", cx, pt + 4, UITheme.TEXT_MUTED);
            g.drawCenteredString(font, "* " + otherName + " *", cx, pt + 14, UITheme.TEXT_GOLD);
            // Pulsing indicator dot
            int dotAlpha = (int)(pulse * 0xFF);
            g.fill(cx - 4, pt + 36, cx + 4, pt + 44, UITheme.withAlpha(UITheme.BTN_GREEN, dotAlpha));
            g.drawCenteredString(font, "Connected", cx, pt + 36, UITheme.TEXT_WHITE);
        }

        UITheme.drawRule(g, pl + 8, pr - 8, pt + PANEL_H - 30);

        super.render(g, mouseX, mouseY, partial);
    }

    public Mode getMode() { return mode; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Block ESC during an active call so players can't accidentally dismiss the HUD
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && mode == Mode.ACTIVE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
