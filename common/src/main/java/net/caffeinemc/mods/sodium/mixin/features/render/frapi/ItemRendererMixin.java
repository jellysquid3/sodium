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

package net.caffeinemc.mods.sodium.mixin.features.render.frapi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.render.frapi.render.ItemRenderContext;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entrypoint of the FRAPI pipeline for item rendering, for the baked models that require it.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    private static void renderModelLists(BakedModel bakedModel, int[] is, int i, int j, PoseStack poseStack, VertexConsumer vertexConsumer) {
    }

    @Unique
    private static final ItemRenderContext.VanillaModelBufferer vanillaBufferer = ItemRendererMixin::renderModelLists;

    @Unique
    private static final ThreadLocal<ItemRenderContext> contexts = ThreadLocal.withInitial(() -> new ItemRenderContext(vanillaBufferer));

    @Inject(method = "renderItem", at = @At(value = "HEAD"), cancellable = true)
    private static void beforeRenderItem(ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, int overlay, int[] colors, BakedModel model, RenderType renderType, ItemStackRenderState.FoilType foilType, CallbackInfo ci) {
        if (!((FabricBakedModel) model).isVanillaAdapter()) {
            contexts.get().renderModel(itemDisplayContext, poseStack, multiBufferSource, light, overlay, model, colors, renderType, foilType);
            ci.cancel();
        }
    }
}
