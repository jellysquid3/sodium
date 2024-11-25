package net.caffeinemc.mods.sodium.mixin.features.model;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.mixin.platform.neoforge.ChunkRenderTypeSetAccessor;
import net.caffeinemc.mods.sodium.mixin.platform.neoforge.SimpleBakedModelAccessor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.MultipartModelData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Predicate;

@Mixin(MultiPartBakedModel.class)
public class MultiPartBakedModelMixin {
    @Unique
    private final Map<BlockState, BakedModel[]> stateCacheFast = new Reference2ReferenceOpenHashMap<>();
    @Unique
    private final StampedLock lock = new StampedLock();

    @Shadow
    @Final
    private List<MultiPartBakedModel.Selector> selectors;

    @Unique
    private boolean canSkipRenderTypeCheck;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void storeClassInfo(List<MultiPartBakedModel.Selector> selectors, CallbackInfo ci) {
        this.canSkipRenderTypeCheck = this.selectors.stream().allMatch(model -> (model.model() instanceof SimpleBakedModel simpleModel && ((SimpleBakedModelAccessor) simpleModel).getBlockRenderTypes() == null));
    }

    /**
     * @author JellySquid
     * @reason Avoid expensive allocations and replace bitfield indirection
     */
    @Overwrite
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random, ModelData modelData, @org.jetbrains.annotations.Nullable RenderType renderType) {
        if (state == null) {
            return Collections.emptyList();
        }

        BakedModel[] models;

        long readStamp = this.lock.readLock();
        try {
            models = this.stateCacheFast.get(state);
        } finally {
            this.lock.unlockRead(readStamp);
        }

        if (models == null) {
            long writeStamp = this.lock.writeLock();
            try {
                List<BakedModel> modelList = new ArrayList<>(this.selectors.size());

                for (MultiPartBakedModel.Selector selector : this.selectors) {
                    if (selector.condition().test(state)) {
                        modelList.add(selector.model());
                    }
                }

                models = modelList.toArray(BakedModel[]::new);
                this.stateCacheFast.put(state, models);
            } finally {
                this.lock.unlockWrite(writeStamp);
            }
        }

        List<BakedQuad> quads = new ArrayList<>();
        long seed = random.nextLong();

        for (BakedModel model : models) {
            random.setSeed(seed);

            if (canSkipRenderTypeCheck || renderType == null || model.getRenderTypes(state, random, modelData).contains(renderType)) {
                quads.addAll(model.getQuads(state, direction, random, MultipartModelData.resolve(modelData, model), renderType));
            }
        }

        return quads;
    }

    /**
     * @author embeddedt, IMS
     * @reason Optimize render type lookup using existing cache
     */
    @Overwrite
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
        long seed = random.nextLong();

        if (canSkipRenderTypeCheck) {
            return ItemBlockRenderTypes.getRenderLayers(state);
        }

        BakedModel[] models;

        long readStamp = this.lock.readLock();
        try {
            models = this.stateCacheFast.get(state);
        } finally {
            this.lock.unlockRead(readStamp);
        }

        if (models == null) {
            long writeStamp = this.lock.writeLock();
            try {
                List<BakedModel> modelList = new ArrayList<>(this.selectors.size());

                for (MultiPartBakedModel.Selector selector : this.selectors) {
                    if (selector.condition().test(state)) {
                        modelList.add(selector.model());
                    }
                }

                models = modelList.toArray(BakedModel[]::new);
                this.stateCacheFast.put(state, models);
            } finally {
                this.lock.unlockWrite(writeStamp);
            }
        }

        BitSet bits = new BitSet();

        for (BakedModel model : models) {
            random.setSeed(seed);

            bits.or((((ChunkRenderTypeSetAccessor) (Object) model.getRenderTypes(state, random, data)).getBits()));
        }

        return ChunkRenderTypeSetAccessor.create(bits);
    }
}