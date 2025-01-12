package net.caffeinemc.mods.sodium.client.config.builder;

import net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalPage;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.Validate;

import java.util.function.Consumer;

public class ExternalPageBuilderImpl extends PageBuilderImpl implements ExternalPageBuilder {
    private Consumer<Screen> currentScreenConsumer;

    @Override
    void prepareBuild() {
        super.prepareBuild();

        Validate.notNull(this.currentScreenConsumer, "Screen consumer must not be null");
    }

    @Override
    ExternalPage build() {
        this.prepareBuild();
        return new ExternalPage(this.name, this.currentScreenConsumer);
    }

    @Override
    public ExternalPageBuilder setScreenProvider(Consumer<Screen> currentScreenConsumer) {
        this.currentScreenConsumer = currentScreenConsumer;
        return this;
    }

    @Override
    public ExternalPageBuilderImpl setName(Component name) {
        super.setName(name);
        return this;
    }
}
