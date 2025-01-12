package net.caffeinemc.mods.sodium.client.config.builder;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import net.caffeinemc.mods.sodium.client.gui.options.TextProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class EnumOptionBuilderImpl<E extends Enum<E>> extends StatefulOptionBuilderImpl<E> implements EnumOptionBuilder<E> {
    final Class<E> enumClass;

    DependentValue<Set<E>> allowedValues;
    Function<E, Component> elementNameProvider;

    EnumOptionBuilderImpl(ResourceLocation id, Class<E> enumClass) {
        super(id);
        this.enumClass = enumClass;
    }

    @Override
    Option build() {
        this.prepareBuild();

        if (this.allowedValues == null) {
            this.allowedValues = new ConstantValue<>(Set.of(this.enumClass.getEnumConstants()));
        }

        if (this.elementNameProvider == null && TextProvider.class.isAssignableFrom(this.enumClass)) {
            this.elementNameProvider = e -> ((TextProvider) e).getLocalizedName();
        }

        Validate.notNull(this.elementNameProvider, "Element name provider must be set or enum class must implement TextProvider");

        return new EnumOption<>(this.id, this.getDependencies(), this.name, this.enabled, this.storage, this.tooltipProvider, this.impact, this.flags, this.defaultValue, this.binding, this.enumClass, this.allowedValues, this.elementNameProvider);
    }

    @Override
    Collection<ResourceLocation> getDependencies() {
        var deps = super.getDependencies();
        deps.addAll(this.allowedValues.getDependencies());
        return deps;
    }

    @Override
    public EnumOptionBuilder<E> setAllowedValues(Set<E> allowedValues) {
        this.allowedValues = new ConstantValue<>(allowedValues);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setAllowedValuesProvider(Function<ConfigState, Set<E>> provider, ResourceLocation... dependencies) {
        this.allowedValues = new DynamicValue<>(provider, dependencies);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setElementNameProvider(Function<E, Component> provider) {
        this.elementNameProvider = provider;
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setName(Component name) {
        super.setName(name);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setStorageHandler(StorageEventHandler storage) {
        super.setStorageHandler(storage);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setTooltip(Component tooltip) {
        super.setTooltip(tooltip);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setTooltip(Function<E, Component> tooltip) {
        super.setTooltip(tooltip);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setImpact(OptionImpact impact) {
        super.setImpact(impact);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setFlags(OptionFlag... flags) {
        super.setFlags(flags);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setDefaultValue(E value) {
        super.setDefaultValue(value);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setDefaultProvider(Function<ConfigState, E> provider, ResourceLocation... dependencies) {
        super.setDefaultProvider(provider, dependencies);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setEnabled(boolean available) {
        super.setEnabled(available);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        super.setEnabledProvider(provider, dependencies);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setBinding(Consumer<E> save, Supplier<E> load) {
        super.setBinding(save, load);
        return this;
    }

    @Override
    public EnumOptionBuilder<E> setBinding(OptionBinding<E> binding) {
        super.setBinding(binding);
        return this;
    }
}
