package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.*;
import net.caffeinemc.mods.sodium.api.config.option.*;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;

public class IntegerOption extends StatefulOption<Integer> {
    private final DependentValue<Range> range;
    private final ControlValueFormatter valueFormatter;

    IntegerOption(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, DependentValue<Boolean> enabled, StorageEventHandler storage, Function<Integer, Component> tooltipProvider, OptionImpact impact, EnumSet<OptionFlag> flags, DependentValue<Integer> defaultValue, OptionBinding<Integer> binding, DependentValue<Range> range, ControlValueFormatter valueFormatter) {
        super(id, dependencies, name, enabled, storage, tooltipProvider, impact, flags, defaultValue, binding);
        this.range = range;
        this.valueFormatter = valueFormatter;
    }

    @Override
    public boolean isValueValid(Integer value) {
        return this.range.get(this.state).isValueValid(value);
    }

    @Override
    Control createControl() {
        var range = this.range.get(this.state);
        return new SliderControl(this, range.min(), range.max(), range.step(), this.valueFormatter);
    }

    public Range getRange() {
        return this.range.get(this.state);
    }
}

