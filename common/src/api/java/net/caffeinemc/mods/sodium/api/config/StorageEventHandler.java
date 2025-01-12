package net.caffeinemc.mods.sodium.api.config;

@FunctionalInterface
public interface StorageEventHandler {
    void afterSave();
}
