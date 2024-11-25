package net.caffeinemc.mods.sodium.mixin.core.world.biome;

import net.caffeinemc.mods.sodium.client.world.BiomeSeedProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(ClientLevel.class)
public class ClientLevelMixin implements BiomeSeedProvider {
    @Unique
    private long biomeZoomSeed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureSeed(ClientPacketListener packetListener,
                             ClientLevel.ClientLevelData levelData,
                             ResourceKey<Level> dimension,
                             Holder<DimensionType> dimensionType,
                             int loadDistance,
                             int simulationDistance,
                             LevelRenderer renderer,
                             boolean isDebug,
                             long biomeZoomSeed, int k,
                             CallbackInfo ci) {
        this.biomeZoomSeed = biomeZoomSeed;
    }

    @Override
    public long sodium$getBiomeZoomSeed() {
        return this.biomeZoomSeed;
    }
}