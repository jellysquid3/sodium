package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.ExternalButtonControl;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.function.Consumer;

public class ExternalButtonOption extends StaticOption {
    final Consumer<Screen> currentScreenConsumer;

    public ExternalButtonOption(ResourceLocation id, Collection<ResourceLocation> dependencies, Component name, DependentValue<Boolean> enabled, Component tooltip, Consumer<Screen> currentScreenConsumer) {
        super(id, dependencies, name, enabled, tooltip);
        this.currentScreenConsumer = currentScreenConsumer;
    }

    @Override
    Control createControl() {
        return new ExternalButtonControl(this, this.currentScreenConsumer);
    }
}
