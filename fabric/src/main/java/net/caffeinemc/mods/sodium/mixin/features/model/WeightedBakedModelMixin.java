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
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        WeightedEntry.Wrapper<BakedModel> model = ((WeightedRandomListExtension<WeightedEntry.Wrapper<BakedModel>>) list).sodium$getQuick(random);

        if (model != null) {
            return model.data()
                    .getQuads(state, face, random);
        }

        return Collections.emptyList();
    }
}
