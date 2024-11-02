package net.caffeinemc.mods.sodium.client.config.structure;

import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.Validate;

public abstract class PageBuilderImpl {
    Component name;

    abstract Page build();

    void prepareBuild() {
        Validate.notNull(this.name, "Name must not be null");
    }

    public PageBuilderImpl setName(Component name) {
        this.name = name;
        return this;
    }
}
