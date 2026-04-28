package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundSetMirrorNamePacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorPacket;
import com.ether.mirrors.network.packets.ServerboundOpenMirrorManagementPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MirrorNamingScreen extends Screen {

    private final BlockPos mirrorPos;
    private final String defaultName;
    private final boolean returnToManagement;
    private EditBox nameField;
    private String tierContext = "";
    private String typeContext = "";

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 110;

    public MirrorNamingScreen(BlockPos mirrorPos, String defaultName) {
        this(mirrorPos, defaultName, false);
    }

    public MirrorNamingScreen(BlockPos mirrorPos, String defaultName, boolean returnToManagement) {
        super(Component.literal("Name Your Mirror"));
        this.mirrorPos = mirrorPos;
        this.defaultName = defaultName != null ? defaultName : "";
        this.returnToManagement = returnToManagement;
    }

    /** Provides tier + type context shown as a subtitle in the screen. */
    public MirrorNamingScreen withContext(String tierName, String typeName) {
        this.tierContext = tierName;
        this.typeContext = typeName;
        return this;
    }

    private int panelLeft()  { return (this.width  - PANEL_W) / 2; }
    private int panelTop()   { return (this.height - PANEL_H) / 2; }

    @Override
    protected void init() {
        super.init();

        int pl = panelLeft(), pt = panelTop();
        int cx = pl + PANEL_W / 2;

        // Styled EditBox
        this.nameField = new EditBox(this.font, cx - 120, pt + 42, 240, 18,
                Component.literal("Mirror Name"));
        this.nameField.setMaxLength(48);
        this.nameField.setValue(defaultName);
        this.nameField.setHint(Component.literal("Enter a name..."));
        this.nameField.setBordered(false);
        this.addRenderableWidget(this.nameField);

        // Confirm
        addRenderableWidget(MirrorButton.green(
                cx - 128, pt + 72, 120, 18,
                Component.literal("Confirm"), b -> confirm()));

        // Skip
        addRenderableWidget(MirrorButton.purple(
                cx + 8, pt + 72, 120, 18,
                Component.literal("Skip"), b -> skip()));

        this.setInitialFocus(this.nameField);
    }

    private void confirm() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) name = defaultName;
        MirrorsNetwork.sendToServer(new ServerboundSetMirrorNamePacket(mirrorPos, name));
        onClose();
        if (returnToManagement) {
            MirrorsNetwork.sendToServer(new ServerboundOpenMirrorManagementPacket(mirrorPos));
        } else {
            MirrorsNetwork.sendToServer(new ServerboundOpenMirrorPacket(mirrorPos));
        }
    }

    private void skip() {
        MirrorsNetwork.sendToServer(new ServerboundSetMirrorNamePacket(mirrorPos, defaultName));
        onClose();
        if (returnToManagement) {
            MirrorsNetwork.sendToServer(new ServerboundOpenMirrorManagementPacket(mirrorPos));
        } else {
            MirrorsNetwork.sendToServer(new ServerboundOpenMirrorPacket(mirrorPos));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // World dim
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + PANEL_H;

        UITheme.drawPanel(g, pl, pt, pr, pb);
        UITheme.drawHeader(g, pl, pt, pr, 28);

        // Pulse glow on border
        float pulse = UITheme.pulse();
        int glowAlpha = (int)(pulse * 0x60 + 0x20);
        g.fill(pl - 1, pt - 1, pr + 1, pt, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha));
        g.fill(pl - 1, pb,     pr + 1, pb + 1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, glowAlpha));

        // Title
        g.drawCenteredString(font, "* Name Your Mirror *", pl + PANEL_W / 2, pt + 5, UITheme.TEXT_MUTED);
        g.drawCenteredString(font, "Mirror Name", pl + PANEL_W / 2, pt + 18, UITheme.TEXT_GOLD);

        // Input field background
        int fx = pl + PANEL_W / 2 - 121;
        int fy = pt + 41;
        g.fill(fx - 1, fy - 1, fx + 243, fy + 20, UITheme.BORDER_ACCENT);
        g.fill(fx, fy, fx + 242, fy + 19, 0xFF060022);

        // Hint label
        g.drawString(font, "Name:", fx + 2, fy - 10, UITheme.TEXT_MUTED, false);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // ENTER
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
