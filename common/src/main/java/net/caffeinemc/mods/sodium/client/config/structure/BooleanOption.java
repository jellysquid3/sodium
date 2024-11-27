package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.TickBoxControl;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;

public class BooleanOption extends StatefulOption<Boolean> {
    public BooleanOption(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, DependentValue<Boolean> enabled, StorageEventHandler storage, Function<Boolean, Component> tooltipProvider, OptionImpact impact, EnumSet<OptionFlag> flags, DependentValue<Boolean> defaultValue, OptionBinding<Boolean> binding) {
        super(id, dependencies, name, enabled, storage, tooltipProvider, impact, flags, defaultValue, binding);
    }

    @Override
    Control createControl() {
        return new TickBoxControl(this);
    }
}
