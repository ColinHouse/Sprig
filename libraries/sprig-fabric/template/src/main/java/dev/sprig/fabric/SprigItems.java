package dev.sprig.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Registers the wand whose behaviour lives in Sprig ({@code class Wand} in
 * src/main.spr). The Sprig class extends Item, so the registered object is the
 * Sprig object. Registration stays in Java here to keep the starter's Sprig
 * side free of registry calls; Sprig can register too, through the String
 * overload (the ResourceKey and Identifier overloads declare the bound
 * T extends V, which Sprig's interop rejects): see examples/fabric_waypoints.
 */
public final class SprigItems implements ModInitializer {
    public static final ResourceKey<Item> WAND_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("sprig_counter", "wand"));

    @Override
    public void onInitialize() {
        Item wand = sprig.user.$M_main.fn$new_wand(new Item.Properties().setId(WAND_KEY));
        Registry.register(BuiltInRegistries.ITEM, WAND_KEY, wand);
    }
}
