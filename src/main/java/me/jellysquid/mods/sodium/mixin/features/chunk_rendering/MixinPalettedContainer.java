package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import me.jellysquid.mods.sodium.client.level.cloned.PalettedContainerExtended;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PalettedContainer.class)
public class MixinPalettedContainer<T> implements PalettedContainerExtended<T> {
    @Shadow
    private int bits;

    @Shadow
    protected BitStorage storage;

    @Shadow
    private Palette<T> palette;

    @Shadow
    @Final
    private T defaultValue;

    @Override
    public BitStorage getDataArray() {
        return this.storage;
    }

    @Override
    public Palette<T> getPalette() {
        return this.palette;
    }

    @Override
    public T getDefaultValue() {
        return this.defaultValue;
    }

    @Override
    public int getBits() {
        return this.bits;
    }
}
