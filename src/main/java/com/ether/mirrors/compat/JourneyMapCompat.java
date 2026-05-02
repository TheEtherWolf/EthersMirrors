package com.ether.mirrors.compat;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.network.packets.ClientboundSyncWaypointsPacket.WaypointData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Premium JourneyMap 5.10.3 integration — optional soft dependency, pure reflection.
 *
 * Design principles:
 *  — Labels are minimal: sigil + name, owner attribution only when relevant
 *  — Calling mirrors are intentionally excluded (they are for communication, not navigation)
 *  — Range rings only appear for own mirrors with range > 256 blocks (no small-ring noise)
 *  — Signal tinting preserves hue identity — dark shade of type color, never grey
 *  — Travel highlight flips the destination waypoint to bright gold during transit
 */
public final class JourneyMapCompat {

    private static final String MOD_ID = "journeymap";

    // Per-type accent colors  (0xRRGGBB — JM uses no-alpha ints)
    private static final int COL_TELEPORT = 0x8B3FC8;  // arcane violet
    private static final int COL_POCKET   = 0x2EAD52;  // emerald
    private static final int COL_BEACON   = 0xD4AF37;  // antique gold
    private static final int COL_TRAVEL   = 0xFFE040;  // bright amber (travel flash)

    // Minimum range (blocks) before a ring is worth showing on the map
    private static final int RING_MIN_RANGE = 256;

    // Tier default ranges (fallback when packet range field is missing)
    private static final Map<String, Integer> TIER_RANGE = new HashMap<>();
    static {
        TIER_RANGE.put("wood",      64);
        TIER_RANGE.put("stone",     128);
        TIER_RANGE.put("iron",      256);
        TIER_RANGE.put("gold",      512);
        TIER_RANGE.put("diamond",   1024);
        TIER_RANGE.put("netherite", 2048);
    }

    // Mirrors the travel screen is currently animating (kept for instant color restore)
    private static final ConcurrentHashMap<UUID, Boolean> travelHighlights = new ConcurrentHashMap<>();

    private JourneyMapCompat() {}

    // =========================================================================
    //  Public surface
    // =========================================================================

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Full re-sync — atomically replaces all EM elements in JM. */
    public static void syncWaypoints(List<WaypointData> entries) {
        if (!isPresent()) return;
        try {
            Object api = api();
            if (api == null) return;
            removeAll(api);
            Method show = method(api.getClass(), "show", 1);
            if (show == null) return;
            for (WaypointData e : entries) {
                if ("calling".equals(e.typeName)) continue; // not a navigation landmark
                putWaypoint(api, show, e);
                putRangeRing(api, show, e);
            }
        } catch (Exception ex) {
            EthersMirrors.LOGGER.debug("[EthersMirrors] JourneyMap sync error: {}", ex.getMessage());
        }
    }

    /** Flash the destination waypoint gold while MirrorTravelScreen is open. */
    public static void highlightDestination(UUID mirrorId) {
        if (!isPresent()) return;
        travelHighlights.put(mirrorId, Boolean.TRUE);
        recolorLive(mirrorId, COL_TRAVEL);
    }

    /** Restore normal color when travel animation ends. */
    public static void clearHighlight(UUID mirrorId) {
        if (!isPresent()) return;
        travelHighlights.remove(mirrorId);
        // Next syncWaypoints call will re-apply the correct tinted color automatically.
    }

    // =========================================================================
    //  Waypoints
    // =========================================================================

    private static void putWaypoint(Object api, Method show, WaypointData e) throws Exception {
        Class<?>    cls  = Class.forName("journeymap.client.api.display.WaypointDisplay");
        Constructor<?> c = cls.getDeclaredConstructor(
                String.class, String.class, ResourceKey.class, BlockPos.class);
        c.setAccessible(true);

        Object wp = c.newInstance(
                EthersMirrors.MOD_ID,
                wpId(e.mirrorId),
                dimKey(e.dimension),
                new BlockPos(e.x, e.y, e.z));

        String label = label(e);
        set(cls, wp, "setLabel",      label);
        set(cls, wp, "setName",       label);
        set(cls, wp, "setColor",      tintedColor(e));
        set(cls, wp, "setPersistent", true);

        show.invoke(api, wp);
    }

    // =========================================================================
    //  Range rings (PolygonOverlay)
    // =========================================================================

    private static void putRangeRing(Object api, Method show, WaypointData e) {
        if (!e.isOwn) return;
        int range = e.rangeBlocks > 0 ? e.rangeBlocks : TIER_RANGE.getOrDefault(e.tierName, 0);
        if (range < RING_MIN_RANGE) return; // skip small ranges — they're map noise

        try {
            Class<?> polyCls  = Class.forName("journeymap.client.api.display.PolygonOverlay");
            Class<?> propCls  = Class.forName("journeymap.client.api.model.ShapeProperties");
            Class<?> polyCls2 = Class.forName("journeymap.client.api.model.MapPolygon");

            Object props = propCls.newInstance();
            int[] rgb    = rgb(typeColor(e.typeName));
            // Stroke: always visible but translucent; fill: very subtle, signal-driven
            float strokeOpacity = 0.45f + (float)(e.signalStrength * 0.40f);
            float fillOpacity   = (float)(e.signalStrength * 0.07f);
            call(props, "setStrokeWidth",   new Class[]{float.class},                     1.5f);
            call(props, "setStrokeColor",   new Class[]{int.class, int.class, int.class}, rgb[0], rgb[1], rgb[2]);
            call(props, "setStrokeOpacity", new Class[]{float.class},                     strokeOpacity);
            call(props, "setFillColor",     new Class[]{int.class, int.class, int.class}, rgb[0], rgb[1], rgb[2]);
            call(props, "setFillOpacity",   new Class[]{float.class},                     fillOpacity);

            Constructor<?> polyC;
            try { polyC = polyCls2.getDeclaredConstructor(List.class); }
            catch (NoSuchMethodException ignored) { return; }
            polyC.setAccessible(true);
            Object poly = polyC.newInstance(circle(e.x, e.y, e.z, range, 48));

            Constructor<?> overlayC = polyCls.getDeclaredConstructor(
                    String.class, String.class, ResourceKey.class, propCls, polyCls2);
            overlayC.setAccessible(true);
            Object overlay = overlayC.newInstance(
                    EthersMirrors.MOD_ID,
                    EthersMirrors.MOD_ID + "_ring_" + e.mirrorId,
                    dimKey(e.dimension),
                    props, poly);
            show.invoke(api, overlay);

        } catch (Exception ex) {
            EthersMirrors.LOGGER.debug("[EthersMirrors] JM range ring skipped: {}", ex.getMessage());
        }
    }

    // =========================================================================
    //  Label construction
    // =========================================================================

    /**
     * Clean two-part label:
     *   own mirror:    "▶ Mirror Name"
     *   others':       "▶ Mirror Name  ·  Owner"
     *   cross-dim own: "▶ Mirror Name  (Nether)"
     */
    private static String label(WaypointData e) {
        String name = e.name.isEmpty() ? capitalize(e.typeName) + " Mirror" : e.name;
        StringBuilder sb = new StringBuilder(sigil(e.typeName)).append(name);

        // Cross-dimension annotation (compact, lowercase)
        String dim = dimShort(e.dimension);
        if (!dim.isEmpty()) sb.append("  (").append(dim).append(")");

        // Owner attribution — only for others' mirrors, after a mid-dot separator
        if (!e.isOwn && !e.ownerName.isEmpty()) {
            sb.append("  \u00B7  ").append(e.ownerName); // ·
        }

        return sb.toString();
    }

    private static String dimShort(String dim) {
        if (dim == null || dim.contains("overworld")) return "";
        if (dim.contains("the_nether")) return "Nether";
        if (dim.contains("the_end"))    return "End";
        int colon = dim.lastIndexOf(':');
        String path = colon >= 0 ? dim.substring(colon + 1) : dim;
        path = path.replace('_', ' ');
        if (path.length() > 12) path = path.substring(0, 11) + "\u2026"; // …
        return path.isEmpty() ? "" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    // =========================================================================
    //  Color logic
    // =========================================================================

    /**
     * Signal-tints by darkening the type color — preserves hue identity at any signal level.
     * Full signal → vivid type color.  Zero signal → 28% brightness (dark but still typed).
     */
    private static int tintedColor(WaypointData e) {
        if (travelHighlights.containsKey(e.mirrorId)) return COL_TRAVEL;
        int base = typeColor(e.typeName);
        float brightness = 0.28f + (float)(e.signalStrength * 0.72f);
        return scale(base, brightness);
    }

    private static int typeColor(String t) {
        if (t == null) return COL_TELEPORT;
        return switch (t) {
            case "pocket"  -> COL_POCKET;
            case "beacon"  -> COL_BEACON;
            default        -> COL_TELEPORT; // teleport + unknown
        };
    }

    private static String sigil(String t) {
        if (t == null) return "\u25B6 "; // ▶
        return switch (t) {
            case "pocket"  -> "\u25A3 "; // ▣
            case "beacon"  -> "\u2605 "; // ★
            default        -> "\u25B6 "; // ▶
        };
    }

    /** Scale all channels of a color by a [0,1] factor. */
    private static int scale(int color, float factor) {
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((color >>  8) & 0xFF) * factor));
        int b = Math.min(255, (int)((color & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    // =========================================================================
    //  Reflection utilities
    // =========================================================================

    private static Object api() throws Exception {
        Class<?> cls   = Class.forName("journeymap.client.api.ClientAPI");
        Field    field = cls.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        return field.get(null);
    }

    private static void removeAll(Object api) {
        Method m = method(api.getClass(), "removeAll", 1);
        if (m != null) {
            try { m.invoke(api, EthersMirrors.MOD_ID); return; } catch (Exception ignored) {}
        }
        // Fallback — iterate and remove individually
        try {
            Method get    = method(api.getClass(), "getShownWaypoints", 1);
            Method remove = method(api.getClass(), "remove", 1);
            if (get == null || remove == null) return;
            @SuppressWarnings("unchecked")
            java.util.Collection<Object> list =
                    (java.util.Collection<Object>) get.invoke(api, EthersMirrors.MOD_ID);
            if (list != null)
                for (Object wp : new ArrayList<>(list))
                    try { remove.invoke(api, wp); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void recolorLive(UUID mirrorId, int color) {
        try {
            Object api = api();
            if (api == null) return;
            Object wp = findById(api, wpId(mirrorId));
            if (wp == null) return;
            Method m = method(wp.getClass(), "setColor", 1);
            if (m != null) m.invoke(wp, color);
        } catch (Exception ex) {
            EthersMirrors.LOGGER.debug("[EthersMirrors] JourneyMap recolor failed: {}", ex.getMessage());
        }
    }

    private static Object findById(Object api, String id) {
        try {
            Method get = method(api.getClass(), "getShownWaypoints", 1);
            if (get == null) return null;
            @SuppressWarnings("unchecked")
            java.util.Collection<Object> list =
                    (java.util.Collection<Object>) get.invoke(api, EthersMirrors.MOD_ID);
            if (list == null) return null;
            for (Object wp : list) {
                try {
                    Method getId = method(wp.getClass(), "getId", 0);
                    if (getId != null && id.equals(getId.invoke(wp))) return wp;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Invoke a setter on {@code obj} if it exists, swallowing all exceptions. */
    private static void set(Class<?> cls, Object obj, String name, Object value) {
        try {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    m.invoke(obj, value); return;
                }
            }
        } catch (Exception ignored) {}
    }

    /** Call a method by name + exact param types, swallowing all exceptions. */
    private static void call(Object obj, String name, Class<?>[] types, Object... args) {
        try {
            obj.getClass().getMethod(name, types).invoke(obj, args);
        } catch (Exception ignored) {}
    }

    /** Find a declared or public method by name and arity, walking the class hierarchy. */
    private static Method method(Class<?> cls, String name, int arity) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == arity) { m.setAccessible(true); return m; }
            for (Method m : c.getMethods())
                if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        return null;
    }

    // =========================================================================
    //  Geometry / misc
    // =========================================================================

    private static ResourceKey<Level> dimKey(String dim) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
    }

    private static String wpId(UUID id) {
        return EthersMirrors.MOD_ID + "_wp_" + id;
    }

    /** Approximate a horizontal circle as a closed BlockPos polygon. */
    private static List<BlockPos> circle(int cx, int cy, int cz, int radius, int segments) {
        List<BlockPos> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double a = 2 * Math.PI * i / segments;
            pts.add(new BlockPos(cx + (int)(Math.cos(a) * radius), cy, cz + (int)(Math.sin(a) * radius)));
        }
        return pts;
    }

    private static int[] rgb(int hex) {
        return new int[]{ (hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF };
    }

    private static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
