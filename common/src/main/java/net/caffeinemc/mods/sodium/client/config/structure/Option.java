package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;

/* TODO:
- storage call after update pass is done
- initial loading and validation
- integration with widget rendering
- proper setup for sodium's own options
- proper binding setup, vanilla bindings/binding generator
- entrypoints for mod scanning and option registration
- figure out changed value/reset value features
 */
// TODO: use update tag to prevent multiple calls to dependencies
public abstract class Option<V> {
    final ResourceLocation id;
    final Collection<ResourceLocation> dependencies;

    final Component name;
    final StorageEventHandler storage;
    final Function<V, Component> tooltipProvider;
    final OptionImpact impact;
    final EnumSet<OptionFlag> flags;
    final DependentValue<V> defaultValue;
    final DependentValue<Boolean> enabled;
    final OptionBinding<V> binding;

    Config state;
    private V value;
    private V modifiedValue;

    Control<V> control;

    Option(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, StorageEventHandler storage, Function<V, Component> tooltipProvider, OptionImpact impact, EnumSet<OptionFlag> flags, DependentValue<V> defaultValue, DependentValue<Boolean> enabled, OptionBinding<V> binding) {
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Option cannot depend on itself");
        }

        this.id = id;
        this.dependencies = dependencies;

        this.name = name;
        this.storage = storage;
        this.tooltipProvider = tooltipProvider;
        this.impact = impact;
        this.flags = flags;
        this.defaultValue = defaultValue;
        this.enabled = enabled;
        this.binding = binding;
    }

    abstract Control<V> createControl();

    public Control<V> getControl() {
        if (this.control == null) {
            this.control = this.createControl();
        }
        return this.control;
    }

    void setParentConfig(Config state) {
        this.state = state;
    }

    public void modifyValue(V value) {
        this.modifiedValue = value;
    }

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

    public boolean hasChanged() {
        return this.modifiedValue != this.value;
    }

    public boolean applyChanges() {
        if (this.hasChanged()) {
            this.value = this.modifiedValue;
            this.binding.save(this.value);
            this.state.notifyStorageWrite(this.storage);
            return true;
        }
        return false;
    }

    boolean isValueValid(V value) {
        return true;
    }

    public boolean isEnabled() {
        return this.enabled.get(this.state);
    }

    public Component getName() {
        return this.name;
    }

    public OptionImpact getImpact() {
        return this.impact;
    }

    public Component getTooltip() {
        return this.tooltipProvider.apply(this.getValidatedValue());
    }

    public Collection<OptionFlag> getFlags() {
        return this.flags;
    }
}

