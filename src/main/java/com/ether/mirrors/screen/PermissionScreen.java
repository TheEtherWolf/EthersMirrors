package com.ether.mirrors.screen;

import com.ether.mirrors.network.MirrorsNetwork;
import com.ether.mirrors.network.packets.ServerboundPermissionRequestPacket;
import com.ether.mirrors.network.packets.ServerboundPermissionResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PermissionScreen extends Screen {

    private int currentTab = 0; // 0=My Network, 1=Requests, 2=Request Access

    public static class GrantedEntry {
        public final UUID playerUUID;
        public final String playerName;
        public final boolean hasUse;
        public final boolean hasViewCamera;
        public final boolean hasBreak;

        public GrantedEntry(UUID playerUUID, String playerName,
                            boolean hasUse, boolean hasViewCamera, boolean hasBreak) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.hasUse = hasUse;
            this.hasViewCamera = hasViewCamera;
            this.hasBreak = hasBreak;
        }
    }

    public static class RequestEntry {
        public final UUID requesterUUID;
        public final String requesterName;

        public RequestEntry(UUID requesterUUID, String requesterName) {
            this.requesterUUID = requesterUUID;
            this.requesterName = requesterName;
        }
    }

    private List<GrantedEntry> grantedEntries = new ArrayList<>();
    private List<RequestEntry> requestEntries = new ArrayList<>();
    private EditBox playerNameInput;
    private int grantedScrollOffset = 0;
    private int requestScrollOffset = 0;

    private static final int PANEL_W  = 420;
    private static final int PANEL_H  = 260;
    private static final int HEADER_H = 28;
    private static final int TAB_H    = 18;
    private static final int ENTRY_H  = 22;
    private static final int MAX_ROWS = 7;

    public PermissionScreen() {
        super(Component.literal("Mirror Permissions"));
    }

    public void setGrantedEntries(List<GrantedEntry> entries) { this.grantedEntries = entries; }
    public void setRequestEntries(List<RequestEntry> entries) { this.requestEntries = entries; }

    private int panelLeft() { return (this.width  - PANEL_W) / 2; }
    private int panelTop()  { return Math.max(16, (this.height - PANEL_H) / 2); }

    @Override
    protected void init() {
        super.init();
        rebuildUI();
    }

    private void rebuildUI() {
        clearWidgets();

        int pl = panelLeft(), pt = panelTop();
        int cx = pl + PANEL_W / 2;
        int tabY = pt + HEADER_H + 4;
        int tabW = (PANEL_W - 12) / 3;

        // Tab buttons
        addRenderableWidget(MirrorButton.of(
                pl + 4, tabY, tabW, TAB_H,
                Component.literal("My Network"),
                b -> { currentTab = 0; rebuildUI(); },
                currentTab == 0 ? UITheme.BTN_PURPLE : UITheme.BORDER_MID,
                currentTab == 0 ? UITheme.TEXT_WHITE : UITheme.TEXT_MUTED));

        String reqLabel = requestEntries.isEmpty() ? "Requests"
                : "Requests  (" + requestEntries.size() + ")";
        addRenderableWidget(MirrorButton.of(
                pl + 4 + tabW + 2, tabY, tabW, TAB_H,
                Component.literal(reqLabel),
                b -> { currentTab = 1; rebuildUI(); },
                currentTab == 1 ? UITheme.BTN_PURPLE : UITheme.BORDER_MID,
                currentTab == 1 ? UITheme.TEXT_WHITE : UITheme.TEXT_MUTED));

        addRenderableWidget(MirrorButton.of(
                pl + 4 + (tabW + 2) * 2, tabY, tabW, TAB_H,
                Component.literal("Request Access"),
                b -> { currentTab = 2; rebuildUI(); },
                currentTab == 2 ? UITheme.BTN_PURPLE : UITheme.BORDER_MID,
                currentTab == 2 ? UITheme.TEXT_WHITE : UITheme.TEXT_MUTED));

        int contentY = tabY + TAB_H + 8;

        switch (currentTab) {
            case 0 -> buildNetworkTab(pl, contentY);
            case 1 -> buildRequestsTab(pl, contentY);
            case 2 -> buildRequestAccessTab(cx, contentY);
        }

        // Close
        addRenderableWidget(MirrorButton.gold(
                cx - 44, panelTop() + PANEL_H - 24, 88, 18,
                Component.literal("Close"), b -> onClose()));
    }

    private void buildNetworkTab(int pl, int startY) {
        int btnX = pl + PANEL_W - 66;
        int end = Math.min(grantedEntries.size(), grantedScrollOffset + MAX_ROWS);
        for (int i = grantedScrollOffset; i < end; i++) {
            GrantedEntry entry = grantedEntries.get(i);
            int row = i - grantedScrollOffset;
            addRenderableWidget(MirrorButton.red(
                    btnX, startY + row * ENTRY_H + 2, 60, 16,
                    Component.literal("Revoke"), b -> {
                        MirrorsNetwork.sendToServer(
                                ServerboundPermissionResponsePacket.revoke(entry.playerUUID));
                        grantedEntries.remove(entry);
                        grantedScrollOffset = Math.max(0, Math.min(grantedScrollOffset, Math.max(0, grantedEntries.size() - MAX_ROWS)));
                        rebuildUI();
                    }));
        }
    }

    private void buildRequestsTab(int pl, int startY) {
        int end = Math.min(requestEntries.size(), requestScrollOffset + MAX_ROWS);
        for (int i = requestScrollOffset; i < end; i++) {
            RequestEntry entry = requestEntries.get(i);
            int row = i - requestScrollOffset;
            int y = startY + row * ENTRY_H + 2;
            int bx = pl + PANEL_W - 130;

            addRenderableWidget(MirrorButton.green(bx, y, 60, 16,
                    Component.literal("Accept"), b -> {
                        MirrorsNetwork.sendToServer(
                                new ServerboundPermissionResponsePacket(entry.requesterUUID, true, 1));
                        requestEntries.remove(entry);
                        requestScrollOffset = Math.max(0, Math.min(requestScrollOffset, Math.max(0, requestEntries.size() - MAX_ROWS)));
                        rebuildUI();
                    }));

            addRenderableWidget(MirrorButton.red(bx + 64, y, 60, 16,
                    Component.literal("Deny"), b -> {
                        MirrorsNetwork.sendToServer(
                                new ServerboundPermissionResponsePacket(entry.requesterUUID, false, 0));
                        requestEntries.remove(entry);
                        requestScrollOffset = Math.max(0, Math.min(requestScrollOffset, Math.max(0, requestEntries.size() - MAX_ROWS)));
                        rebuildUI();
                    }));
        }
    }

    private void buildRequestAccessTab(int cx, int startY) {
        playerNameInput = new EditBox(this.font, cx - 120, startY + 16, 240, 18,
                Component.literal("Player Name"));
        playerNameInput.setMaxLength(16);
        playerNameInput.setHint(Component.literal("Enter player name..."));
        playerNameInput.setBordered(false);
        addRenderableWidget(playerNameInput);

        addRenderableWidget(MirrorButton.teal(
                cx - 60, startY + 44, 120, 18,
                Component.literal("Send Request"), b -> {
                    String name = playerNameInput.getValue().trim();
                    // Validate: Minecraft usernames are 3-16 alphanumeric/underscore characters
                    if (!name.isEmpty() && name.length() >= 3 && name.length() <= 16
                            && name.matches("[a-zA-Z0-9_]+")) {
                        MirrorsNetwork.sendToServer(new ServerboundPermissionRequestPacket(name));
                        playerNameInput.setValue("");
                    }
                }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, this.width, this.height, 0xCC020010);

        int pl = panelLeft(), pt = panelTop();
        int pr = pl + PANEL_W, pb = pt + PANEL_H;

        UITheme.drawPanel(g, pl, pt, pr, pb);
        UITheme.drawHeader(g, pl, pt, pr, HEADER_H);

        // Pulse glow
        float pulse = UITheme.pulse();
        int ga = (int)(pulse * 0x60 + 0x20);
        g.fill(pl - 1, pt - 1, pr + 1, pt, UITheme.withAlpha(UITheme.BORDER_BRIGHT, ga));
        g.fill(pl - 1, pb,     pr + 1, pb + 1, UITheme.withAlpha(UITheme.BORDER_BRIGHT, ga));

        int cx = pl + PANEL_W / 2;
        g.drawCenteredString(font, "Mirror Permissions", cx, pt + 5, UITheme.TEXT_MUTED);
        g.drawCenteredString(font, "* Network Access *", cx, pt + 16, UITheme.TEXT_GOLD);

        // Content area
        int tabY  = pt + HEADER_H + 4;
        int contentY = tabY + TAB_H + 8;

        switch (currentTab) {
            case 0 -> renderNetworkTab(g, pl, contentY, mouseX, mouseY);
            case 1 -> renderRequestsTab(g, pl, contentY, mouseX, mouseY);
            case 2 -> renderRequestAccessTab(g, cx, contentY);
        }

        UITheme.drawRule(g, pl + 8, pr - 8, pb - 28);

        super.render(g, mouseX, mouseY, partial);
    }

    private void renderNetworkTab(GuiGraphics g, int pl, int startY, int mx, int my) {
        int pr = pl + PANEL_W;

        if (grantedEntries.isEmpty()) {
            g.drawCenteredString(font, "No players have access to your mirrors.",
                    pl + PANEL_W / 2, startY + 30, UITheme.TEXT_MUTED);
            return;
        }

        // Column headers
        g.drawString(font, "Player", pl + 10, startY - 8, UITheme.TEXT_MUTED, false);
        g.drawString(font, "Permissions", pl + 170, startY - 8, UITheme.TEXT_MUTED, false);

        String hoveredTooltip = null;

        int gEnd = Math.min(grantedEntries.size(), grantedScrollOffset + MAX_ROWS);
        for (int i = grantedScrollOffset; i < gEnd; i++) {
            GrantedEntry entry = grantedEntries.get(i);
            int ry = startY + (i - grantedScrollOffset) * ENTRY_H;
            boolean hov = mx >= pl && mx < pr && my >= ry && my < ry + ENTRY_H;
            UITheme.drawRow(g, pl + 2, ry, pr - 2, ry + ENTRY_H, i % 2 == 1, hov);

            int ty = ry + (ENTRY_H - 8) / 2;
            g.drawString(font, entry.playerName, pl + 10, ty, UITheme.TEXT_OTHER, false);

            // Permission badges
            int bx = pl + 170;
            if (entry.hasUse) {
                int w = font.width("USE") + 6;
                if (mx >= bx && mx < bx + w && my >= ty - 1 && my < ty + 9)
                    hoveredTooltip = "USE: can teleport through";
                bx = drawBadge(g, bx, ty, "USE", 0xFF44BB55);
            }
            if (entry.hasViewCamera) {
                int w = font.width("CAM") + 6;
                if (mx >= bx && mx < bx + w && my >= ty - 1 && my < ty + 9)
                    hoveredTooltip = "CAM: can view camera";
                bx = drawBadge(g, bx, ty, "CAM", 0xFF5588FF);
            }
            if (entry.hasBreak) {
                int w = font.width("BRK") + 6;
                if (mx >= bx && mx < bx + w && my >= ty - 1 && my < ty + 9)
                    hoveredTooltip = "BRK: can break";
                drawBadge(g, bx, ty, "BRK", 0xFFFF6666);
            }
        }

        if (hoveredTooltip != null) {
            g.renderTooltip(font, net.minecraft.network.chat.Component.literal(hoveredTooltip), mx, my);
        }
    }

    private void renderRequestsTab(GuiGraphics g, int pl, int startY, int mx, int my) {
        int pr = pl + PANEL_W;

        if (requestEntries.isEmpty()) {
            g.drawCenteredString(font, "No pending access requests.",
                    pl + PANEL_W / 2, startY + 30, UITheme.TEXT_MUTED);
            return;
        }

        g.drawString(font, "Player", pl + 10, startY - 8, UITheme.TEXT_MUTED, false);

        int rEnd = Math.min(requestEntries.size(), requestScrollOffset + MAX_ROWS);
        for (int i = requestScrollOffset; i < rEnd; i++) {
            RequestEntry entry = requestEntries.get(i);
            int ry = startY + (i - requestScrollOffset) * ENTRY_H;
            boolean hov = mx >= pl && mx < pr && my >= ry && my < ry + ENTRY_H;
            UITheme.drawRow(g, pl + 2, ry, pr - 2, ry + ENTRY_H, i % 2 == 1, hov);

            int ty = ry + (ENTRY_H - 8) / 2;
            g.drawString(font, entry.requesterName, pl + 10, ty, UITheme.TEXT_GOLD, false);
            g.drawString(font, "requests access", pl + 10 + font.width(entry.requesterName) + 6, ty,
                    UITheme.TEXT_MUTED, false);
        }
    }

    private void renderRequestAccessTab(GuiGraphics g, int cx, int startY) {
        g.drawCenteredString(font, "Request access to another player's mirrors:",
                cx, startY, UITheme.TEXT_MUTED);

        // Input box background
        int fx = cx - 121;
        int fy = startY + 15;
        g.fill(fx - 1, fy - 1, fx + 243, fy + 20, UITheme.BORDER_ACCENT);
        g.fill(fx, fy, fx + 242, fy + 19, 0xFF060022);
    }

    /** Draws a small colored badge and returns the x after it. */
    private int drawBadge(GuiGraphics g, int x, int y, String label, int bg) {
        int w = font.width(label) + 6;
        g.fill(x, y - 1, x + w, y + 9, bg);
        g.fill(x, y - 1, x + w, y, UITheme.lighten(bg, 40));
        g.drawString(font, label, x + 3, y, UITheme.TEXT_WHITE, false);
        return x + w + 3;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Route scroll to the active tab's list
        if (currentTab == 0 && !grantedEntries.isEmpty()) {
            int maxOffset = Math.max(0, grantedEntries.size() - MAX_ROWS);
            grantedScrollOffset = (int) Math.max(0, Math.min(maxOffset, grantedScrollOffset - Math.signum(delta)));
            rebuildUI();
            return true;
        }
        if (currentTab == 1 && !requestEntries.isEmpty()) {
            int maxOffset = Math.max(0, requestEntries.size() - MAX_ROWS);
            requestScrollOffset = (int) Math.max(0, Math.min(maxOffset, requestScrollOffset - Math.signum(delta)));
            rebuildUI();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
