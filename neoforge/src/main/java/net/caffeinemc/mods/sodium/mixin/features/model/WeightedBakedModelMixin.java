package net.caffeinemc.mods.sodium.mixin.features.model;

import net.caffeinemc.mods.sodium.client.util.WeightedRandomListExtension;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.Collections;
import java.util.List;

@Mixin(WeightedBakedModel.class)
public class WeightedBakedModelMixin {
    @Shadow
    @Final
    private SimpleWeightedRandomList<BakedModel> list;

    /**
     * @author JellySquid
     * @reason Avoid excessive object allocations
     */
    @Overwrite
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random, net.neoforged.neoforge.client.model.data.ModelData modelData, @org.jetbrains.annotations.Nullable net.minecraft.client.renderer.RenderType renderType) {
        WeightedEntry.Wrapper<BakedModel> model = ((WeightedRandomListExtension<WeightedEntry.Wrapper<BakedModel>>) list).sodium$getQuick(random);

        if (model != null) {
            return model.data()
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
        WeightedEntry.Wrapper<BakedModel> model = ((WeightedRandomListExtension<WeightedEntry.Wrapper<BakedModel>>) list).sodium$getQuick(rand);

        if (model != null) {
            return model.data()
                    .getRenderTypes(state, rand, data);
        }

        return ChunkRenderTypeSet.none();
    }
}
