package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.option.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface IntegerOptionBuilder extends OptionBuilder<Integer> {
    IntegerOptionBuilder setRange(int min, int max, int step);

    IntegerOptionBuilder setRange(Range range);

    IntegerOptionBuilder setRangeProvider(Function<ConfigState, Range> provider, ResourceLocation... dependencies);

    IntegerOptionBuilder setValueFormatter(ControlValueFormatter formatter);

    @Override
    IntegerOptionBuilder setName(Component name);

    @Override
    IntegerOptionBuilder setStorageHandler(StorageEventHandler storage);

    @Override
    IntegerOptionBuilder setTooltip(Component tooltip);

    @Override
    IntegerOptionBuilder setTooltip(Function<Integer, Component> tooltip);

    @Override
    IntegerOptionBuilder setImpact(OptionImpact impact);

    @Override
    IntegerOptionBuilder setFlags(OptionFlag... flags);

    @Override
    IntegerOptionBuilder setDefaultValue(Integer value);

    @Override
    IntegerOptionBuilder setDefaultProvider(Function<ConfigState, Integer> provider, ResourceLocation... dependencies);

    @Override
    IntegerOptionBuilder setEnabled(boolean available);

    @Override
    IntegerOptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies);

    @Override
    IntegerOptionBuilder setBinding(Consumer<Integer> save, Supplier<Integer> load);

    @Override
    IntegerOptionBuilder setBinding(OptionBinding<Integer> binding);
}