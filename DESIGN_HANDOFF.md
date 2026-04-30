# EthersMirrors — Design Handoff

This file keeps Claude Code (CC) and Claude Design (CD) in sync.
Update it after every screen iteration.

---

## Workflow

1. **CC** describes what's needed → prompts **CD**
2. **CD** produces an HTML prototype bundle
3. **CC** fetches the bundle URL, extracts it, implements in Java (Minecraft Forge 1.20.1)
4. **CC** builds, deploys, commits, pushes
5. User tests in-game, feeds back to CD or CC

**Key constraints CC always enforces:**
- Pixel rendering only — `GuiGraphics.fill`, `drawString`, `drawCenteredString`
- No CSS/HTML in runtime — all animations via `System.currentTimeMillis()`
- Panel width locked at **460px** across all screens
- Shared chrome: `UITheme`, `MirrorButton` factory methods
- No AI attribution in commits, ever

---

## Screen Status

| # | Screen | CD Version | CC Status | Notes |
|---|--------|-----------|-----------|-------|
| 01 | `MirrorSelectionScreen` | v2 | **Shipped** `190f01f` | v2 full rewrite |
| 02 | `MirrorCallScreen` | v2 | **Shipped** `431e69d` | Skin face + rune segments + green corner ticks |
| 03 | `PermissionScreen` | v1 | **Shipped** `277325b` | 3-tab, ladder, modal; needs SetPermissionLevel packet |
| 04 | `MirrorManagementScreen` | — | Exists (legacy) | Needs CD pass |
| 05 | `PocketExpansionScreen` | — | Exists (legacy) | Needs CD pass |
| 06 | `MirrorNamingScreen` | — | Exists (legacy) | Needs CD pass |

---

## MirrorSelectionScreen v2 — What was built

**Panel:** 460×330px, 6 tabs (FAV / ALL / RECENT / NEARBY / CONTEXT / SEARCH)

**Key features:**
- Default tab = RECENT (falls back to ALL if empty)
- Selected row: dark green fill + inset border + 2px gold left tab
- Inspector strip (36px): shows mirror name, coords, tier on selection
- Sort chip: cycles Name / Signal / Dimension (3 modes)
- Per-tab empty states with unique glyphs + messages
- Scroll gutter with track + thumb, arrow buttons
- Context mode pills: TELEPORT / CALL / POCKET / WARP switch entire screen behavior
- Footer: PERMISSIONS (ghost) + CLOSE (gold)

**Column grid** (content=436px, 1fr=166px):
```
IDX=10  NAME=34  DIM=206  SIG=300  ACT=366  STAR=428
```

---

## MirrorCallScreen v2 — What was built

**Panel:** 460×172px, centered vertically (partial screen dim 0x88010008)

**States:**
- `INCOMING` — pulse rings on glyph box + 8-segment rune countdown
- `OUTGOING` — spinning dashed perimeter on glyph box + bouncing-dot ringing indicator
- `ACTIVE` — green corner ticks on glyph box + timer widget (elapsed + quality)

**Glyph box:** 64×64px, per-state tinted bg, player skin face 32×32 centred
- Face: `PlayerInfo.getSkinLocation()` blitted at 4× via pose scale; Steve fallback
- Incoming: 3 expanding square outline rings, 1600ms, offset by period/3
- Outgoing: 2 bright 14px dashes sweeping clockwise, 4000ms
- Active: green L-shaped 5px corner ticks (no rings)

**Rune segment countdown:** 8 arc segments, r=26, stroke 6px (r 23–28), 45° apart, 30.8° each
- Depletes from the end (ceil(8 × fraction) segments lit)
- Teal >50%, Gold 25–50%, Red ≤25%; last 2 segments pulse at 0.8s when red

**Keyboard:** ENTER answers INCOMING, ESC blocked during ACTIVE

**Data note:** `mirrorName`, `dimensionName`, `signalStrength` are nullable — packet
changes needed to populate them (future pass).

---

## PermissionScreen v1 — What was built

**Panel:** 460×(variable)px, 3 tabs (MY NETWORK / PLAYERS / REQUESTS)

**Key features:**
- MY NETWORK tab: lists all players with granted access; 4-tier ladder pills (NONE/VIEW/CALL/ENTER); mini 16×16 hash-palette faces; × remove button sends `ServerboundPermissionResponsePacket.revoke()`
- PLAYERS tab: online player list with expand-to-add; clicking a player row grants USE+VIEW_CAMERA
- REQUESTS tab: pending access requests; APPROVE sends `(uuid, true, FLAG_USE|FLAG_VIEW_CAMERA)`; DENY sends `(uuid, false, 0)`
- Privacy chip top-right: cycles default permission level 0→1→2→3→0
- `PermissionRequestModal`: live incoming request overlay, 460×140px; 28s auto-deny countdown; pulse rings + 32×32 skin face; DENY(ESC) / APPROVE(ENTER)
- `ClientboundPermissionNotifyPacket` now opens modal instead of action bar toast

**Known gap:** clicking ladder pills on MY NETWORK rows only updates local state — a `ServerboundSetPermissionLevelPacket` is needed to persist the change server-side (marked TODO in code).

---

## What to tell CD next

When prompting CD for the next screen, include:

> "This is for a Minecraft mod GUI rendered in pure Java pixel ops (no CSS at runtime).
> Panel width is always 460px. Use the same header chrome as MirrorSelectionScreen:
> dark panel background, 32px header with mode pill left + title center + close right,
> teal/gold/purple/red accent palette, `MirrorButton` rounded pill buttons.
> Animate with time-based phases only (no CSS transitions). Output as a self-contained
> HTML prototype I can preview."

Then describe the specific screen.

---

## Palette reference (CSS → Java ARGB)

| Role | CSS | Java |
|------|-----|------|
| Panel bg | `#0D0F14` | `0xFF0D0F14` |
| Header bg | `#13161E` | `0xFF13161E` |
| Border dim | `rgba(255,255,255,0.08)` | `0x14FFFFFF` |
| Border bright | `rgba(255,255,255,0.18)` | `0x2EFFFFFF` |
| Teal accent | `#00D4C8` | `0xFF00D4C8` |
| Gold accent | `#F5C842` | `0xFFF5C842` |
| Purple accent | `#9B7FE8` | `0xFF9B7FE8` |
| Red accent | `#E85F5F` | `0xFFE85F5F` |
| Row selected | `rgba(170,85,255,0.28)` | `0x47AA55FF` |
| Text primary | `#E8EAF0` | `0xFFE8EAF0` |
| Text muted | `rgba(232,234,240,0.45)` | `0x72E8EAF0` |
