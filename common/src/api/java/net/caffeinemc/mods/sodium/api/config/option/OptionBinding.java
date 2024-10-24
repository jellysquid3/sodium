package net.caffeinemc.mods.sodium.api.config.option;

public interface OptionBinding<V> {
    void save(V value);

    V load();

    // TODO: add shortcuts to generate for vanilla option bindings
}
