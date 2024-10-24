package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface OptionBuilder<V> {
    OptionBuilder<V> setName(Component name);

    OptionBuilder<V> setStorageHandler(StorageEventHandler storage);

    OptionBuilder<V> setTooltip(Component tooltip);

    OptionBuilder<V> setTooltip(Function<V, Component> tooltip);

    OptionBuilder<V> setImpact(OptionImpact impact);

    OptionBuilder<V> setFlags(OptionFlag... flags);

    OptionBuilder<V> setDefaultValue(V value);

    OptionBuilder<V> setDefaultProvider(Function<ConfigState, V> provider, ResourceLocation... dependencies);

    OptionBuilder<V> setEnabled(boolean available);

    OptionBuilder<V> setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies);

    OptionBuilder<V> setBinding(Consumer<V> save, Supplier<V> load);

    OptionBuilder<V> setBinding(OptionBinding<V> binding);
}
