package dev.sprig.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SprigCounterClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CounterActions counter = sprig.user.$M_main.fn$new_counter();
        ClientTickEvents.END_CLIENT_TICK.register(client -> counter.tick());
        System.out.println("Sprig Fabric counter initialized: " + counter.count());
    }
}
