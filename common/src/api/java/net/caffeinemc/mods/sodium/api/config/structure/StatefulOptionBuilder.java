package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface StatefulOptionBuilder<V> extends OptionBuilder {
    StatefulOptionBuilder<V> setStorageHandler(StorageEventHandler storage);

    StatefulOptionBuilder<V> setTooltip(Function<V, Component> tooltip);

    StatefulOptionBuilder<V> setImpact(OptionImpact impact);

    StatefulOptionBuilder<V> setFlags(OptionFlag... flags);

    StatefulOptionBuilder<V> setDefaultValue(V value);

    StatefulOptionBuilder<V> setDefaultProvider(Function<ConfigState, V> provider, ResourceLocation... dependencies);

    StatefulOptionBuilder<V> setBinding(Consumer<V> save, Supplier<V> load);

    StatefulOptionBuilder<V> setBinding(OptionBinding<V> binding);
}
