package dev.sprig.waypoints;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric's main entrypoint. The item, its registration and its creative tab
 * entry are Sprig (register_marker in src/main.spr). This class stays Java
 * because fabric.mod.json names the entrypoint by class, and the classes
 * Sprig generates (sprig.user.sprig_waypoints.$..., the package named after
 * the project in sprig.toml) are compiler output, not names to publish in mod
 * metadata; javac checks this call against them at build time.
 *
 * <p>No generated main runs in the game. This call, the first from Java, runs
 * the top-level statements of main.spr and of the modules it imports, so their
 * top-level bindings (the file name in store.spr, the list limit in
 * compass.spr) have their values when Minecraft later calls the marker's use.
 */
public final class SprigWaypoints implements ModInitializer {
    @Override
    public void onInitialize() {
        sprig.user.sprig_waypoints.$M_main.fn$register_marker();
    }
}
