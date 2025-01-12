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

public interface BooleanOptionBuilder extends StatefulOptionBuilder<Boolean> {
    @Override
    BooleanOptionBuilder setName(Component name);

    @Override
    BooleanOptionBuilder setStorageHandler(StorageEventHandler storage);

    @Override
    BooleanOptionBuilder setTooltip(Component tooltip);

    @Override
    BooleanOptionBuilder setTooltip(Function<Boolean, Component> tooltip);

    @Override
    BooleanOptionBuilder setImpact(OptionImpact impact);

    @Override
    BooleanOptionBuilder setFlags(OptionFlag... flags);

    @Override
    BooleanOptionBuilder setDefaultValue(Boolean value);

    @Override
    BooleanOptionBuilder setDefaultProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies);

    @Override
    BooleanOptionBuilder setEnabled(boolean available);

    @Override
    BooleanOptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies);

    @Override
    BooleanOptionBuilder setBinding(Consumer<Boolean> save, Supplier<Boolean> load);

    @Override
    BooleanOptionBuilder setBinding(OptionBinding<Boolean> binding);
}
