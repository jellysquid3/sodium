package net.caffeinemc.mods.sodium.client.config.value;

import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public interface DependentValue<V> {
    V get(Config state);

    default Collection<ResourceLocation> getDependencies() {
        return Set.of();
    }
}
