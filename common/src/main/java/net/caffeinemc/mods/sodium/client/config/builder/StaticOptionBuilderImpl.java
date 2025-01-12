package net.caffeinemc.mods.sodium.client.config.builder;

import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

abstract class StaticOptionBuilderImpl extends OptionBuilderImpl {
    Component tooltip;

    StaticOptionBuilderImpl(ResourceLocation id) {
        super(id);
    }

    @Override
    void prepareBuild() {
        super.prepareBuild();

        Validate.notNull(this.tooltip, "Tooltip must be set");
    }

    @Override
    public OptionBuilder setTooltip(Component tooltip) {
        Validate.notNull(tooltip, "Argument must not be null");

        this.tooltip = tooltip;
        return this;
    }
}
