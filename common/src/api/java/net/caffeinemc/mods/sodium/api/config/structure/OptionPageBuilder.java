package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.network.chat.Component;

public interface OptionPageBuilder extends PageBuilder {
    OptionPageBuilder setName(Component name);

    OptionPageBuilder addOptionGroup(OptionGroupBuilder group);
}
