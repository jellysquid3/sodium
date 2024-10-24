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

class EnumOption<E extends Enum<E>> extends Option<E> {
    final Class<E> enumClass;

    private final DependentValue<Set<E>> allowedValues;
    private final Function<E, Component> elementNameProvider;

    EnumOption(ResourceLocation id, Collection<ResourceLocation> dependencies, Class<E> enumClass, Component name, StorageEventHandler storage, Function<E, Component> tooltipProvider, OptionImpact impact, EnumSet<OptionFlag> flags, DependentValue<E> defaultValue, DependentValue<Boolean> enabled, OptionBinding<E> binding, DependentValue<Set<E>> allowedValues, Function<E, Component> elementNameProvider) {
        super(id, dependencies, name, storage, tooltipProvider, impact, flags, defaultValue, enabled, binding);
        this.enumClass = enumClass;
        this.allowedValues = allowedValues;
        this.elementNameProvider = elementNameProvider;
    }

    @Override
    boolean isValueValid(E value) {
        return this.allowedValues.get(this.state).contains(value);
    }

    @Override
    Control<E> createControl() {
        // TODO: doesn't update allowed values when dependencies change
        return new CyclingControl<>(this, this.enumClass, this.elementNameProvider, this.allowedValues.get(this.state).toArray(this.enumClass.getEnumConstants()));
    }
}
