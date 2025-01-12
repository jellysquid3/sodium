package net.caffeinemc.mods.sodium.client.config.builder;

import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.StatefulOptionBuilder;
import net.caffeinemc.mods.sodium.client.config.AnonymousOptionBinding;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

abstract class StatefulOptionBuilderImpl<V> extends OptionBuilderImpl implements StatefulOptionBuilder<V> {
    StorageEventHandler storage;
    Function<V, Component> tooltipProvider;
    OptionImpact impact;
    EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
    DependentValue<V> defaultValue;
    OptionBinding<V> binding;

    StatefulOptionBuilderImpl(ResourceLocation id) {
        super(id);
    }

    void prepareBuild() {
        super.prepareBuild();

        Validate.notNull(this.storage, "Storage handler must be set");
        Validate.notNull(this.tooltipProvider, "Tooltip provider must be set");
        Validate.notNull(this.defaultValue, "Default value must be set");

        Validate.notNull(this.binding, "Binding must be set");
    }

    Collection<ResourceLocation> getDependencies() {
        var dependencies = super.getDependencies();
        dependencies.addAll(this.defaultValue.getDependencies());
        return dependencies;
    }

    @Override
    public StatefulOptionBuilder<V> setStorageHandler(StorageEventHandler storage) {
        Validate.notNull(storage, "Argument must not be null");

        this.storage = storage;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setTooltip(Component tooltip) {
        Validate.notNull(tooltip, "Argument must not be null");

        this.tooltipProvider = v -> tooltip;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setTooltip(Function<V, Component> tooltip) {
        Validate.notNull(tooltip, "Argument must not be null");

        this.tooltipProvider = tooltip;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setImpact(OptionImpact impact) {
        Validate.notNull(impact, "Argument must not be null");

        this.impact = impact;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setFlags(OptionFlag... flags) {
        Collections.addAll(this.flags, flags);
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setDefaultValue(V value) {
        Validate.notNull(value, "Argument must not be null");

        this.defaultValue = new ConstantValue<>(value);
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setDefaultProvider(Function<ConfigState, V> provider, ResourceLocation... dependencies) {
        Validate.notNull(provider, "Argument must not be null");

        this.defaultValue = new DynamicValue<>(provider, dependencies);
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setBinding(Consumer<V> save, Supplier<V> load) {
        Validate.notNull(save, "Setter must not be null");
        Validate.notNull(load, "Getter must not be null");

        this.binding = new AnonymousOptionBinding<>(save, load);
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setBinding(OptionBinding<V> binding) {
        Validate.notNull(binding, "Argument must not be null");

        this.binding = binding;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setName(Component name) {
        super.setName(name);
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setEnabled(boolean available) {
        super.setEnabled(available);
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        super.setEnabledProvider(provider, dependencies);
        return this;
    }
}
