package net.caffeinemc.mods.sodium.mixin.features.world.biome;

import net.caffeinemc.mods.sodium.client.world.biome.BiomeColorMaps;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Shadow
    @Final
    private Biome.ClimateSettings climateSettings;

    @Shadow
    @Final
    private BiomeSpecialEffects specialEffects;
    @Unique
    private boolean hasCustomGrassColor;

    @Unique
    private int customGrassColor;

    @Unique
    private boolean hasCustomFoliageColor;

    @Unique
    private int customFoliageColor;

    @Unique
    private int defaultColorIndex;

    @Unique
    private BiomeSpecialEffects cachedSpecialEffects;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        setupColors();
    }

    @Unique
    private void setupColors() {
        this.cachedSpecialEffects = specialEffects;

        var grassColor = this.cachedSpecialEffects.getGrassColorOverride();

        if (grassColor.isPresent()) {
            this.hasCustomGrassColor = true;
            this.customGrassColor = grassColor.get();
        } else {
            this.hasCustomGrassColor = false;
        }

        var foliageColor = this.cachedSpecialEffects.getFoliageColorOverride();

        if (foliageColor.isPresent()) {
            this.hasCustomFoliageColor = true;
            this.customFoliageColor = foliageColor.get();
        } else {
            this.hasCustomFoliageColor = false;
        }

        this.defaultColorIndex = this.getDefaultColorIndex();
    }

    /**
     * @author JellySquid
     * @reason Avoid unnecessary pointer de-references and allocations
     */
    @Overwrite
    public int getGrassColor(double x, double z) {
        if (this.specialEffects != this.cachedSpecialEffects) {
            setupColors();
        }

        int color;

        if (this.hasCustomGrassColor) {
            color = this.customGrassColor;
        } else {
            color = BiomeColorMaps.getGrassColor(this.defaultColorIndex);
        }

        var modifier = this.cachedSpecialEffects.getGrassColorModifier();

        if (modifier != BiomeSpecialEffects.GrassColorModifier.NONE) {
            color = modifier.modifyColor(x, z, color);
        }

        return color;
    }

    /**
     * @author JellySquid
     * @reason Avoid unnecessary pointer de-references and allocations
     */
    @Overwrite
    public int getFoliageColor() {
        if (this.specialEffects != this.cachedSpecialEffects) {
            setupColors();
        }

        int color;

        if (this.hasCustomFoliageColor) {
            color = this.customFoliageColor;
        } else {
            color = BiomeColorMaps.getFoliageColor(this.defaultColorIndex);
        }

        return color;
    }

    @Unique
    private int getDefaultColorIndex() {
        double temperature = Mth.clamp(this.climateSettings.temperature(), 0.0F, 1.0F);
        double humidity = Mth.clamp(this.climateSettings.downfall(), 0.0F, 1.0F);

        return BiomeColorMaps.getIndex(temperature, humidity);
    }
}
