package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.client.render.CompositePass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "doEntityOutline", at = @At("HEAD"), cancellable = true)
    public void cancelEntityOutlineComposite(CallbackInfo ci) {
        // Normally, the entity outline buffer would be blurred and then composited into the
        // main render target, but we want to defer this until our final compositing pass.
        if (CompositePass.ENABLED) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void onEntityRendered(Entity entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, CallbackInfo ci) {
        // If any entities are rendered with an outline effect, mark the entity glow render target
        // as needing to be composited into the final image. Otherwise, we can skip compositing when
        // there are no glowing entities.
        if (multiBufferSource instanceof OutlineBufferSource) {
            CompositePass.ENTITY_GLOW_IS_ACTIVE = true;
        }
    }
}
