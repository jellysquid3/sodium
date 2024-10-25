package net.caffeinemc.mods.sodium.client.config.structure;

import net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.Validate;

import java.util.function.Consumer;

public class ExternalPageBuilderImpl extends PageBuilderImpl implements ExternalPageBuilder {
    private Component name;
    private Consumer<Screen> currentScreenConsumer;

    @Override
    ExternalPage build() {
        Validate.notNull(this.name, "Name must not be null");
        Validate.notNull(this.currentScreenConsumer, "Screen consumer must not be null");

        return new ExternalPage(this.name, this.currentScreenConsumer);
    }

    @Override
    public ExternalPageBuilder setName(Component name) {
        this.name = name;
        return this;
    }

    @Override
    public ExternalPageBuilder setScreenProvider(Consumer<Screen> currentScreenConsumer) {
        this.currentScreenConsumer = currentScreenConsumer;
        return this;
    }
}
