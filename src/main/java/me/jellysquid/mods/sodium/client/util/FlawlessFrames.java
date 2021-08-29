package me.jellysquid.mods.sodium.client.util;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements the "Flawless Frames" FREX feature using which third-party mods can instruct Sodium to sacrifice
 * performance (even beyond the point where it can no longer achieve interactive frame rates) in exchange for
 * a noticeable boost to quality.
 *
 * In Sodium's case, this means waiting for all chunks to be fully updated and ready for rendering before each frame.
 *
 * See https://github.com/grondag/frex/pull/9
 */
public class FlawlessFrames {
    private static final Set<Object> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @SuppressWarnings("unchecked")
    public static void onClientInitialization() {
        Function<String, Consumer<Boolean>> provider = name -> {
            Object token = new Object();
            return active -> {
                if (active) {
                    ACTIVE.add(token);
                } else {
                    ACTIVE.remove(token);
                }
            };
        };
        FabricLoader.getInstance()
                .getEntrypoints("frex_flawless_frames", Consumer.class)
                .forEach(api -> api.accept(provider));
    }

    public static boolean isActive() {
        return !ACTIVE.isEmpty();
    }
}
