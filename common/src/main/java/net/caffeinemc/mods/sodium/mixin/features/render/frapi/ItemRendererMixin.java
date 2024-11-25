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
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
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
    @Final
    @Shadow
    private ItemColors itemColors;

    @Shadow
    protected abstract void renderModelLists(BakedModel bakedModel, ItemStack itemStack, int i, int j, PoseStack poseStack, VertexConsumer vertexConsumer);

    @Unique
    private final ItemRenderContext.VanillaModelBufferer vanillaBufferer = this::renderModelLists;

    @Unique
    private final ThreadLocal<ItemRenderContext> contexts = ThreadLocal.withInitial(() -> new ItemRenderContext(itemColors, vanillaBufferer));

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;isCustomRenderer()Z"), cancellable = true)
    private void beforeRenderItem(ItemStack stack, ItemDisplayContext transformMode, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light, int overlay, BakedModel model, boolean invert, CallbackInfo ci) {
        if (!((FabricBakedModel) model).isVanillaAdapter()) {
            contexts.get().renderModel(stack, transformMode, invert, matrixStack, vertexConsumerProvider, light, overlay, model);
            ci.cancel();
        }
    }
}
