package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

public interface OptionGroupBuilder {
    OptionGroupBuilder setName(Component name);

    OptionGroupBuilder addOption(OptionBuilder<?> option);
}
