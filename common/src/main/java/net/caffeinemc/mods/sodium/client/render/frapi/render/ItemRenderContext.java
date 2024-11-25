/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.caffeinemc.mods.sodium.client.render.frapi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.caffeinemc.mods.sodium.client.render.frapi.helper.ColorHelper;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.EncodingFormat;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.mixin.features.render.frapi.ItemRendererAccessor;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.GlintMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.impl.renderer.VanillaModelEncoder;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * The render context used for item rendering.
 */
public class ItemRenderContext extends AbstractRenderContext {
    /** Value vanilla uses for item rendering.  The only sensible choice, of course.  */
    private static final long ITEM_RANDOM_SEED = 42L;
    private static final int GLINT_COUNT = ItemStackRenderState.FoilType.values().length;

    public class ItemEmitter extends MutableQuadViewImpl {
        {
            data = new int[EncodingFormat.TOTAL_STRIDE];
            clear();
        }

        public void bufferDefaultModel(BakedModel model) {
            ItemRenderContext.this.bufferDefaultModel(this, model, null);
        }


        @Override
        public void emitDirectly() {
            renderQuad(this);
        }

        public boolean hasTransforms() {
            return activeTransform != NO_TRANSFORM;
        }
    }

    private final MutableQuadViewImpl editorQuad = new ItemEmitter();

    private final VanillaModelBufferer vanillaBufferer;

    public ItemRenderContext(VanillaModelBufferer vanillaBufferer) {
        this.vanillaBufferer = vanillaBufferer;
    }

    private final RandomSource random = new SingleThreadedRandomSource(ITEM_RANDOM_SEED);
    private final Supplier<RandomSource> randomSupplier = () -> {
        random.setSeed(ITEM_RANDOM_SEED);
        return random;
    };

    private ItemDisplayContext transformMode;
    private PoseStack poseStack;
    private Matrix4f matPosition;
    private boolean trustedNormals;
    private Matrix3f matNormal;
    private MultiBufferSource bufferSource;
    private int lightmap;
    private int overlay;
    private int[] colors;

    private RenderType defaultLayer;
    private ItemStackRenderState.FoilType defaultGlint;

    private PoseStack.Pose specialGlintEntry;
    private final VertexConsumer[] vertexConsumerCache = new VertexConsumer[3 * GLINT_COUNT];

    @Override
    public QuadEmitter getEmitter() {
        editorQuad.clear();
        return editorQuad;
    }

    public void renderModel(ItemDisplayContext transformMode, PoseStack poseStack, MultiBufferSource bufferSource, int lightmap, int overlay, BakedModel model, int[] colors, RenderType layer, ItemStackRenderState.FoilType glint) {
        this.transformMode = transformMode;
        this.poseStack = poseStack;
        matPosition = poseStack.last().pose();
        trustedNormals = poseStack.last().trustedNormals;
        matNormal = poseStack.last().normal();
        this.bufferSource = bufferSource;
        this.lightmap = lightmap;
        this.overlay = overlay;
        this.colors = colors;

        defaultLayer = layer;
        defaultGlint = glint;

        ((FabricBakedModel) model).emitItemQuads(getEmitter(), randomSupplier);

        this.poseStack = null;
        this.bufferSource = null;
        this.colors = null;

        this.specialGlintEntry = null;
        Arrays.fill(vertexConsumerCache, null);
    }

    private void renderQuad(MutableQuadViewImpl quad) {
        final RenderMaterial mat = quad.material();
        final boolean emissive = mat.emissive();
        final VertexConsumer vertexConsumer = getVertexConsumer(mat.blendMode(), mat.glintMode());

        tintQuad(quad);
        shadeQuad(quad, emissive);
        bufferQuad(quad, vertexConsumer);
    }

    private void tintQuad(MutableQuadViewImpl quad) {
        final int tintIndex = quad.tintIndex();

        if (tintIndex != -1 && tintIndex < colors.length) {
            final int color = colors[tintIndex];

            for (int i = 0; i < 4; i++) {
                quad.color(i, ColorMixer.mulComponentWise(color, quad.color(i)));
            }
        }
    }

    private void shadeQuad(MutableQuadViewImpl quad, boolean emissive) {
        if (emissive) {
            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, LightTexture.FULL_BRIGHT);
            }
        } else {
            final int lightmap = this.lightmap;

            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), lightmap));
            }
        }
    }

    private void bufferQuad(MutableQuadViewImpl quad, VertexConsumer vertexConsumer) {
        QuadEncoder.writeQuadVertices(quad, vertexConsumer, overlay, matPosition, trustedNormals, matNormal);
        SpriteUtil.markSpriteActive(quad.sprite(SpriteFinderCache.forBlockAtlas()));
    }

    /**
     * Caches custom blend mode / vertex consumers and mimics the logic
     * in {@code RenderLayers.getEntityBlockLayer}. Layers other than
     * translucent are mapped to cutout.
     */
    private VertexConsumer getVertexConsumer(BlendMode blendMode, GlintMode glintMode) {
        RenderType type;
        ItemStackRenderState.FoilType glint;

        if (blendMode == BlendMode.DEFAULT) {
            type = defaultLayer;
        } else {
            type = blendMode == BlendMode.TRANSLUCENT ? Sheets.translucentItemSheet() : Sheets.cutoutBlockSheet();
        }

        if (glintMode == GlintMode.DEFAULT) {
            glint = defaultGlint;
        } else {
            glint = glintMode.glint;
        }

        int cacheIndex;

        if (type == Sheets.translucentItemSheet()) {
            cacheIndex = 0;
        } else if (type == Sheets.cutoutBlockSheet()) {
            cacheIndex = GLINT_COUNT;
        } else {
            cacheIndex = 2 * GLINT_COUNT;
        }

        cacheIndex += glint.ordinal();
        VertexConsumer vertexConsumer = vertexConsumerCache[cacheIndex];

        if (vertexConsumer == null) {
            vertexConsumer = createVertexConsumer(type, glint);
            vertexConsumerCache[cacheIndex] = vertexConsumer;
        }

        return vertexConsumer;
    }

    private VertexConsumer createVertexConsumer(RenderType type, ItemStackRenderState.FoilType glint) {
        if (glint == ItemStackRenderState.FoilType.SPECIAL) {
            if (specialGlintEntry == null) {
                specialGlintEntry = poseStack.last().copy();

                if (transformMode == ItemDisplayContext.GUI) {
                    MatrixUtil.mulComponentWise(specialGlintEntry.pose(), 0.5F);
                } else if (transformMode.firstPerson()) {
                    MatrixUtil.mulComponentWise(specialGlintEntry.pose(), 0.75F);
                }
            }

            return ItemRendererAccessor.sodium$getCompassFoilBuffer(bufferSource, type, specialGlintEntry);
        }

        return ItemRenderer.getFoilBuffer(bufferSource, type, true, glint != ItemStackRenderState.FoilType.NONE);
    }

    public void bufferDefaultModel(QuadEmitter quadEmitter, BakedModel model, @Nullable BlockState state) {
        if (vanillaBufferer == null) {
            VanillaModelEncoder.emitItemQuads(quadEmitter, model, null, randomSupplier);
        } else {
            VertexConsumer vertexConsumer;
            if (defaultGlint == ItemStackRenderState.FoilType.SPECIAL) {
                PoseStack.Pose pose = poseStack.last().copy();
                if (transformMode == ItemDisplayContext.GUI) {
                    MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
                } else if (transformMode.firstPerson()) {
                    MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
                }

                vertexConsumer = ItemRendererAccessor.sodium$getCompassFoilBuffer(bufferSource, defaultLayer, pose);
            } else {
                vertexConsumer = ItemRenderer.getFoilBuffer(bufferSource, defaultLayer, true, defaultGlint != ItemStackRenderState.FoilType.NONE);
            }

            vanillaBufferer.accept(model, colors, lightmap, overlay, poseStack, vertexConsumer);
        }
    }

    /** used to accept a method reference from the ItemRenderer. */
    @FunctionalInterface
    public interface VanillaModelBufferer {
        void accept(BakedModel model, int[] colirs, int color, int overlay, PoseStack matrixStack, VertexConsumer buffer);
    }
}
