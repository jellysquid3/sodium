package me.jellysquid.mods.sodium.client.model.light.flat;

import me.jellysquid.mods.sodium.client.model.light.AbstractLightPipeline;
import me.jellysquid.mods.sodium.client.model.light.QuadLightData;
import me.jellysquid.mods.sodium.client.model.light.cache.LightDataCache;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Arrays;

import static me.jellysquid.mods.sodium.client.model.light.cache.LightDataCache.unpackLM;

/**
 * A light pipeline which implements "classic-style" lighting through simply using the light value of the adjacent
 * block to a face.
 */
public class FlatLightPipeline extends AbstractLightPipeline {
    public FlatLightPipeline(LightDataCache cache) {
        super(cache);
    }

    @Override
    public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction face, boolean shade) {
        // No ambient occlusion exists when using flat shading.
        Arrays.fill(out.br, 1.0f);

        // If the face is aligned, use the light data above it
        if ((quad.getFlags() & ModelQuadFlags.IS_ALIGNED) != 0) {
            Arrays.fill(out.lm, unpackLM(this.lightCache.get(pos, face)));
        } else {
            Arrays.fill(out.lm, unpackLM(this.lightCache.get(pos)));
        }

        this.applySidedBrightnessModifier(out.br, face, shade);
    }
}
