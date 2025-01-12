package net.caffeinemc.mods.sodium.client.config;

import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class AnonymousOptionBinding<V> implements OptionBinding<V> {
    private final Consumer<V> save;
    private final Supplier<V> load;

    public AnonymousOptionBinding(Consumer<V> save, Supplier<V> load) {
        this.save = save;
        this.load = load;
    }

    @Override
    public void save(V value) {
        this.save.accept(value);
    }

    @Override
    public V load() {
        return this.load.get();
    }
}
