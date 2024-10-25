package net.caffeinemc.mods.sodium.client.config.structure;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public record ExternalPage(Component name, Consumer<Screen> currentScreenConsumer) implements Page {
    @Override
    public ImmutableList<OptionGroup> groups() {
        return ImmutableList.of();
    }
}
