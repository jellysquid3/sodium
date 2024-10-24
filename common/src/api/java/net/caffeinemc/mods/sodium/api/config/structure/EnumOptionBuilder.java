package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface EnumOptionBuilder<E extends Enum<E>> extends OptionBuilder<E> {
    @Override
    EnumOptionBuilder<E> setName(Component name);

    @Override
    EnumOptionBuilder<E> setStorageHandler(StorageEventHandler storage);

    @Override
    EnumOptionBuilder<E> setTooltip(Component tooltip);

    @Override
    EnumOptionBuilder<E> setTooltip(Function<E, Component> tooltip);

    @Override
    EnumOptionBuilder<E> setImpact(OptionImpact impact);

    @Override
    EnumOptionBuilder<E> setFlags(OptionFlag... flags);

    @Override
    EnumOptionBuilder<E> setDefaultValue(E value);

    @Override
    EnumOptionBuilder<E> setDefaultProvider(Function<ConfigState, E> provider, ResourceLocation... dependencies);

    @Override
    EnumOptionBuilder<E> setEnabled(boolean available);

    @Override
    EnumOptionBuilder<E> setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies);

    @Override
    EnumOptionBuilder<E> setBinding(Consumer<E> save, Supplier<E> load);

    @Override
    EnumOptionBuilder<E> setBinding(OptionBinding<E> binding);

    EnumOptionBuilder<E> setAllowedValues(Set<E> allowedValues);

    EnumOptionBuilder<E> setAllowedValuesProvider(Function<ConfigState, Set<E>> provider, ResourceLocation... dependencies);

    EnumOptionBuilder<E> setElementNameProvider(Function<E, Component> provider);

    static <E extends Enum<E>> Function<E, Component> nameProviderFrom(Component... names) {
        return e -> names[e.ordinal()];
    }
}
