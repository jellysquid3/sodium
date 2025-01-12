package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public interface OptionBuilder {
    OptionBuilder setName(Component name);

    OptionBuilder setTooltip(Component tooltip);

    OptionBuilder setEnabled(boolean available);

    OptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies);
}
