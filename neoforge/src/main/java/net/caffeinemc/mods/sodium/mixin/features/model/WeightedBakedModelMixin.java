package net.caffeinemc.mods.sodium.mixin.features.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
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
    public List<BakedQuad> getQuads(@javax.annotation.Nullable BlockState state, @javax.annotation.Nullable Direction face, RandomSource random, ModelData modelData, @org.jetbrains.annotations.Nullable RenderType renderType) {
        WeightedEntry.Wrapper<BakedModel> quad = getAt(this.list, Math.abs((int) random.nextLong()) % this.totalWeight);

        if (quad != null) {
            return quad.data()
                    .getQuads(state, face, random, modelData, renderType);
        }

        return Collections.emptyList();
    }

    /**
     * @author JellySquid
     * @reason Avoid excessive object allocations
     */
    @Overwrite
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        WeightedEntry.Wrapper<BakedModel> quad = getAt(this.list, Math.abs((int) rand.nextLong()) % this.totalWeight);

        if (quad != null) {
            return quad.data()
                    .getRenderTypes(state, rand, data);
        }

        return ChunkRenderTypeSet.none();
    }
}
