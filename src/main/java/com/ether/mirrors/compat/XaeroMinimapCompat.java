package com.ether.mirrors.compat;

import com.ether.mirrors.EthersMirrors;
import com.ether.mirrors.network.packets.ClientboundSyncWaypointsPacket.WaypointData;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Optional integration with Xaero's Minimap.
 * Uses pure reflection so no compile-time dependency is needed.
 * All methods fail silently if Xaero's is absent or its API changes.
 */
public final class XaeroMinimapCompat {

    private static final String MOD_ID = "xaerominimap";
    /** Prefix used on all waypoints this mod owns — makes cleanup easy. */
    private static final String TAG = "[EM] ";
    /** Key for the default waypoint set in Xaero's waypoint world. */
    private static final String XAERO_DEFAULT_SET = "gui.xaero_default";

    /** Color indices matching Xaero's palette. */
    private static final int COLOR_TELEPORT = 8; // purple
    private static final int COLOR_CALLING  = 6; // cyan
    private static final int COLOR_POCKET   = 4; // green
    private static final int COLOR_BEACON   = 2; // yellow

    private XaeroMinimapCompat() {}

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Syncs the given mirror list into Xaero's current-dimension waypoint world.
     * Clears any previously managed "[EM] " waypoints first, then adds the new ones.
     * Only mirrors whose dimension matches the player's current dimension are added.
     */
    public static void syncWaypoints(List<WaypointData> entries) {
        if (!isPresent()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String currentDim = mc.level.dimension().location().toString();

        try {
            // XaeroMinimap.instance
            Class<?> xaeroClass = Class.forName("xaero.minimap.XaeroMinimap");
            Object xaeroInstance = xaeroClass.getDeclaredField("instance").get(null);

            // .getInterfaces().getMinimapInterface().getWaypointsManager()
            Object interfaces   = call(xaeroInstance, "getInterfaces");
            Object minimapIface = call(interfaces, "getMinimapInterface");
            Object manager      = call(minimapIface, "getWaypointsManager");

            // .getWorldContainer().getCurrentWorld()
            Object container    = call(manager, "getWorldContainer");
            Object world        = call(container, "getCurrentWorld");
            if (world == null) return;

            // .getSets().get("gui.xaero_default")
            @SuppressWarnings("unchecked")
            Map<String, Object> sets = (Map<String, Object>) call(world, "getSets");
            if (sets == null || sets.isEmpty()) return;
            Object defaultSet = sets.get(XAERO_DEFAULT_SET);
            if (defaultSet == null) defaultSet = sets.values().iterator().next();
            if (defaultSet == null) return;

            // .getList() — mutable List<Waypoint>
            @SuppressWarnings("unchecked")
            List<Object> wpList = (List<Object>) call(defaultSet, "getList");
            if (wpList == null) return;

            // Remove old managed waypoints
            Iterator<Object> iter = wpList.iterator();
            while (iter.hasNext()) {
                Object wp = iter.next();
                try {
                    String wpName = (String) call(wp, "getName");
                    if (wpName != null && wpName.startsWith(TAG)) iter.remove();
                } catch (Exception ignored) {}
            }

            // Build Waypoint constructor: (int x, int y, int z, String name, String symbol,
            //                              int color, int type, boolean temporary)
            Class<?> wpClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Constructor<?> ctor = wpClass.getDeclaredConstructor(
                    int.class, int.class, int.class,
                    String.class, String.class,
                    int.class, int.class, boolean.class);
            ctor.setAccessible(true);

            // Add waypoints for mirrors in the current dimension
            for (WaypointData entry : entries) {
                if (!currentDim.equals(entry.dimension)) continue;
                // Skip calling type — not position-based
                if ("calling".equals(entry.typeName)) continue;

                String displayName = TAG + (entry.name.isEmpty()
                        ? capitalize(entry.typeName) + " Mirror"
                        : entry.name);
                int color = colorFor(entry.typeName);
                Object wp = ctor.newInstance(entry.x, entry.y, entry.z,
                        displayName, ".", color, 0, false);
                wpList.add(wp);
            }

        } catch (Exception e) {
            // Xaero's API incompatible or absent — log at DEBUG so server admins can diagnose
            EthersMirrors.LOGGER.debug("[EthersMirrors] XaeroMinimap compat failed: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Object call(Object obj, String methodName) throws Exception {
        Method m = findMethod(obj.getClass(), methodName);
        if (m == null) throw new NoSuchMethodException(obj.getClass().getName() + "." + methodName);
        m.setAccessible(true);
        return m.invoke(obj);
    }

    private static Method findMethod(Class<?> clazz, String name) {
        while (clazz != null) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static int colorFor(String typeName) {
        if (typeName == null) return COLOR_TELEPORT;
        return switch (typeName) {
            case "calling" -> COLOR_CALLING;
            case "pocket"  -> COLOR_POCKET;
            case "beacon"  -> COLOR_BEACON;
            default        -> COLOR_TELEPORT;
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
