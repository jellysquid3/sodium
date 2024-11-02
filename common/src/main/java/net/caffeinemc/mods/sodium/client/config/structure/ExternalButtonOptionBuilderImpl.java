package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.structure.ExternalButtonOptionBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.Validate;

import java.util.function.Consumer;
import java.util.function.Function;

class ExternalButtonOptionBuilderImpl extends StaticOptionBuilderImpl implements ExternalButtonOptionBuilder {
    private Consumer<Screen> currentScreenConsumer;

    ExternalButtonOptionBuilderImpl(ResourceLocation id) {
        super(id);
    }

    @Override
    void prepareBuild() {
        super.prepareBuild();

        Validate.notNull(this.currentScreenConsumer, "Screen provider must be set");
    }

    @Override
    Option build() {
        this.prepareBuild();

        return new ExternalButtonOption(this.id, this.getDependencies(), this.name, this.enabled, this.tooltip, this.currentScreenConsumer);
    }

    @Override
    public ExternalButtonOptionBuilder setScreenProvider(Consumer<Screen> currentScreenConsumer) {
        this.currentScreenConsumer = currentScreenConsumer;
        return this;
    }

    @Override
    public ExternalButtonOptionBuilder setName(Component name) {
        super.setName(name);
        return this;
    }

    @Override
    public ExternalButtonOptionBuilder setEnabled(boolean available) {
        super.setEnabled(available);
        return this;
    }

    @Override
    public ExternalButtonOptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        super.setEnabledProvider(provider, dependencies);
        return this;
    }

    @Override
    public ExternalButtonOptionBuilder setTooltip(Component tooltip) {
        super.setTooltip(tooltip);
        return this;
    }
}
