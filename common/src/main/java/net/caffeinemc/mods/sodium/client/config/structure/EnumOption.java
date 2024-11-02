package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

public class EnumOption<E extends Enum<E>> extends StatefulOption<E> {
    final Class<E> enumClass;

    private final DependentValue<Set<E>> allowedValues;
    private final Function<E, Component> elementNameProvider;

    EnumOption(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, DependentValue<Boolean> enabled, StorageEventHandler storage, Function<E, Component> tooltipProvider, OptionImpact impact, EnumSet<OptionFlag> flags, DependentValue<E> defaultValue, OptionBinding<E> binding, Class<E> enumClass, DependentValue<Set<E>> allowedValues, Function<E, Component> elementNameProvider) {
        super(id, dependencies, name, enabled, storage, tooltipProvider, impact, flags, defaultValue, binding);
        this.enumClass = enumClass;
        this.allowedValues = allowedValues;
        this.elementNameProvider = elementNameProvider;
    }

    @Override
    public boolean isValueValid(E value) {
        return this.allowedValues.get(this.state).contains(value);
    }

    @Override
    Control createControl() {
        // TODO: doesn't update allowed values when dependencies change
        return new CyclingControl<>(this, this.enumClass, this.elementNameProvider, this.allowedValues.get(this.state).toArray(this.enumClass.getEnumConstants()));
    }
}
