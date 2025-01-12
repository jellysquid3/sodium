package net.caffeinemc.mods.sodium.client.config.value;

import net.caffeinemc.mods.sodium.client.config.structure.Config;

public class ConstantValue<V> implements DependentValue<V> {
    private final V value;

    public ConstantValue(V value) {
        this.value = value;
    }

    @Override
    public V get(Config state) {
        return this.value;
    }
}
