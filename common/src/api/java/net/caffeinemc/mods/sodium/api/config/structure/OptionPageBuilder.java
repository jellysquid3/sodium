package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

public interface OptionPageBuilder {
    OptionPageBuilder setName(Component name);

    OptionPageBuilder addOptionGroup(OptionGroupBuilder group);
}
