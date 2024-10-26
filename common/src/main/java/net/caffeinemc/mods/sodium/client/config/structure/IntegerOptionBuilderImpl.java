package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.option.*;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.config.value.DynamicValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class IntegerOptionBuilderImpl extends OptionBuilderImpl<Integer> implements IntegerOptionBuilder {
    DependentValue<Range> rangeProvider;
    ControlValueFormatter valueFormatter;

    IntegerOptionBuilderImpl(ResourceLocation id) {
        super(id);
    }

    @Override
    IntegerOption build() {
        this.prepareBuild();

        Validate.notNull(this.rangeProvider, "Range provider must be set");
        Validate.notNull(this.valueFormatter, "Value formatter must be set");

        return new IntegerOption(this.id, this.getDependencies(), this.name, this.storage, this.tooltipProvider, this.impact, this.flags, this.defaultValue, this.enabled, this.binding, this.rangeProvider, this.valueFormatter);
    }

    @Override
    Collection<ResourceLocation> getDependencies() {
        var deps = super.getDependencies();
        deps.addAll(this.rangeProvider.getDependencies());
        return deps;
    }

    @Override
    public IntegerOptionBuilder setRange(int min, int max, int step) {
        return this.setRange(new Range(min, max, step));
    }

    @Override
    public IntegerOptionBuilder setRange(Range range) {
        this.rangeProvider = new ConstantValue<>(range);
        return this;
    }

    @Override
    public IntegerOptionBuilder setRangeProvider(Function<ConfigState, Range> provider, ResourceLocation... dependencies) {
        this.rangeProvider = new DynamicValue<>(provider, dependencies);
        return this;
    }

    @Override
    public IntegerOptionBuilder setValueFormatter(ControlValueFormatter formatter) {
        this.valueFormatter = formatter;
        return this;
    }

    @Override
    public IntegerOptionBuilder setName(Component name) {
        super.setName(name);
        return this;
    }

    @Override
    public IntegerOptionBuilder setStorageHandler(StorageEventHandler storage) {
        super.setStorageHandler(storage);
        return this;
    }

    @Override
    public IntegerOptionBuilder setTooltip(Component tooltip) {
        super.setTooltip(tooltip);
        return this;
    }

    @Override
    public IntegerOptionBuilder setTooltip(Function<Integer, Component> tooltip) {
        super.setTooltip(tooltip);
        return this;
    }

    @Override
    public IntegerOptionBuilder setImpact(OptionImpact impact) {
        super.setImpact(impact);
        return this;
    }

    @Override
    public IntegerOptionBuilder setFlags(OptionFlag... flags) {
        super.setFlags(flags);
        return this;
    }

    @Override
    public IntegerOptionBuilder setDefaultValue(Integer value) {
        super.setDefaultValue(value);
        return this;
    }

    @Override
    public IntegerOptionBuilder setDefaultProvider(Function<ConfigState, Integer> provider, ResourceLocation... dependencies) {
        super.setDefaultProvider(provider, dependencies);
        return this;
    }

    @Override
    public IntegerOptionBuilder setEnabled(boolean available) {
        super.setEnabled(available);
        return this;
    }

    @Override
    public IntegerOptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        super.setEnabledProvider(provider, dependencies);
        return this;
    }

    @Override
    public IntegerOptionBuilder setBinding(Consumer<Integer> save, Supplier<Integer> load) {
        super.setBinding(save, load);
        return this;
    }

    @Override
    public IntegerOptionBuilder setBinding(OptionBinding<Integer> binding) {
        super.setBinding(binding);
        return this;
    }
}
