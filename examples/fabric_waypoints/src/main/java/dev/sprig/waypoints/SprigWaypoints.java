package dev.sprig.waypoints;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric's main entrypoint. The item, its registration and its creative tab
 * entry are Sprig (register_marker in src/main.spr). This class stays Java
 * because fabric.mod.json names the entrypoint by class, and the classes
 * Sprig generates (sprig.user.$...) are compiler output, not names to publish
 * in mod metadata; javac checks this call against them at build time.
 */
public final class SprigWaypoints implements ModInitializer {
    @Override
    public void onInitialize() {
        sprig.user.$M_main.fn$register_marker();
    }
}
