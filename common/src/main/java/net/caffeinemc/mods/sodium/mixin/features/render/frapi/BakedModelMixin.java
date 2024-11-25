package net.caffeinemc.mods.sodium.mixin.features.render.frapi;

import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.frapi.render.ItemRenderContext;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(BakedModel.class)
public interface BakedModelMixin extends FabricBakedModel {
    @Override
    default void emitItemQuads(QuadEmitter emitter, Supplier<RandomSource> randomSupplier) {
        if (emitter instanceof ItemRenderContext.ItemEmitter itemE && !itemE.hasTransforms()) {
            itemE.bufferDefaultModel((BakedModel) this);
        } else {
            FabricBakedModel.super.emitItemQuads(emitter, randomSupplier);
        }
    }

    @Override
    default void emitBlockQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, Predicate<@Nullable Direction> cullTest) {
        if (emitter instanceof AbstractBlockRenderContext.BlockEmitter) {
            ((AbstractBlockRenderContext.BlockEmitter) emitter).bufferDefaultModel((BakedModel) this, state, cullTest);
        } else if (emitter instanceof ItemRenderContext.ItemEmitter itemE && !itemE.hasTransforms()) {
            itemE.bufferDefaultModel((BakedModel) this);
        } else {
            FabricBakedModel.super.emitBlockQuads(emitter, blockView, state, pos, randomSupplier, cullTest);
        }
    }
}
