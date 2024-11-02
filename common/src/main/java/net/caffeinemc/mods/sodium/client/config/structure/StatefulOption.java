package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;

public abstract class StatefulOption<V> extends Option {
    final StorageEventHandler storage;
    final Function<V, Component> tooltipProvider;
    final OptionImpact impact;
    final EnumSet<OptionFlag> flags;
    final DependentValue<V> defaultValue;
    final OptionBinding<V> binding;

    private V value;
    private V modifiedValue;

    StatefulOption(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, DependentValue<Boolean> enabled, StorageEventHandler storage, Function<V, Component> tooltipProvider, OptionImpact impact, EnumSet<OptionFlag> flags, DependentValue<V> defaultValue, OptionBinding<V> binding) {
        super(id, dependencies, name, enabled);
        this.storage = storage;
        this.tooltipProvider = tooltipProvider;
        this.impact = impact;
        this.flags = flags;
        this.defaultValue = defaultValue;
        this.binding = binding;
    }

    public void modifyValue(V value) {
        this.modifiedValue = value;
    }

    @Override
    void resetFromBinding() {
        this.value = this.binding.load();

        if (!isValueValid(this.value)) {
            var defaultValue = this.defaultValue.get(this.state);
            if (defaultValue != this.value) {
                this.value = defaultValue;
                this.binding.save(this.value);
                this.state.notifyStorageWrite(this.storage);
            }
        }

        this.modifiedValue = this.value;
    }

    public V getValidatedValue() {
        if (!isValueValid(this.modifiedValue)) {
            this.modifiedValue = this.defaultValue.get(this.state);
        }

        return this.modifiedValue;
    }

    @Override
    public boolean hasChanged() {
        return this.modifiedValue != this.value;
    }

    @Override
    boolean applyChanges() {
        if (this.hasChanged()) {
            this.value = this.modifiedValue;
            this.binding.save(this.value);
            this.state.notifyStorageWrite(this.storage);
            return true;
        }
        return false;
    }

    public boolean isValueValid(V value) {
        return true;
    }

    @Override
    public OptionImpact getImpact() {
        return this.impact;
    }

    @Override
    public Component getTooltip() {
        return this.tooltipProvider.apply(this.getValidatedValue());
    }

    @Override
    public Collection<OptionFlag> getFlags() {
        return this.flags;
    }
}
