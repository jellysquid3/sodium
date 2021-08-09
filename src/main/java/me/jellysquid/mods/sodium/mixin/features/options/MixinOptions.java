package me.jellysquid.mods.sodium.mixin.features.options;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Options.class)
public class MixinOptions {
    @Shadow
    public int renderDistance;

    @Shadow
    public GraphicsStatus graphicsMode;

    /**
     * @author JellySquid
     * @reason Make the cloud render mode user-configurable
     */
    @Overwrite
    public CloudStatus getCloudsType() {
        SodiumGameOptions options = SodiumClientMod.options();

        if (this.renderDistance < 4 || !options.quality.enableClouds) {
            return CloudStatus.OFF;
        }

        return options.quality.cloudQuality.isFancy(this.graphicsMode) ? CloudStatus.FANCY : CloudStatus.FAST;
    }
}
