package net.caffeinemc.mods.sodium.mixin.features.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.Collections;
import java.util.List;

@Mixin(WeightedBakedModel.class)
public class WeightedBakedModelMixin {
    @Shadow
    @Final
    private List<WeightedEntry.Wrapper<BakedModel>> list;

    @Shadow
    @Final
    private int totalWeight;

    @Unique
    private static <T extends WeightedEntry> T getAt(List<T> pool, int totalWeight) {
        int i = 0;
        int len = pool.size();

        T weighted;

        do {
            if (i >= len) {
                return null;
            }

            weighted = pool.get(i++);
            totalWeight -= weighted.getWeight().asInt();
        } while (totalWeight >= 0);

        return weighted;
    }

    /**
     * @author JellySquid
     * @reason Avoid excessive object allocations
     */
    @Overwrite
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        WeightedEntry.Wrapper<BakedModel> quad = getAt(this.list, Math.abs((int) random.nextLong()) % this.totalWeight);

        if (quad != null) {
            return quad.data()
                    .getQuads(state, face, random);
        }

        return Collections.emptyList();
    }
}
