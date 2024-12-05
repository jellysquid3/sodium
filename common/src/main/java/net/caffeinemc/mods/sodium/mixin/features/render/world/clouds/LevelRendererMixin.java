package net.caffeinemc.mods.sodium.mixin.features.render.world.clouds;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow
    private @Nullable ClientLevel level;
    @Shadow
    private int ticks;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private CloudRenderer cloudRenderer;

    /**
     * @author jellysquid3
     * @reason Optimize cloud rendering
     */
    @Group(name = "sodium$cloudsOverride", min = 1, max = 1)
    @Dynamic
    @Inject(
            method = "method_62205",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;render(ILnet/minecraft/client/CloudStatus;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;F)V"
            ),
            require = 0,
            cancellable = true
    )
    public void renderCloudsFabric(ResourceHandle<RenderTarget> resourceHandle, int cloudColor, CloudStatus cloudStatus, float cloudHeight, Matrix4f matModelView, Matrix4f matProjection, Vec3 camera, float partialTicks, CallbackInfo ci) {
        ci.cancel();

        this.sodium$renderClouds(matModelView, matProjection, cloudColor);
    }

    @Group(name = "sodium$cloudsOverride", min = 1, max = 1)
    @Dynamic
    @Inject(method = { "lambda$addCloudsPass$6" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;render(ILnet/minecraft/client/CloudStatus;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;F)V"), cancellable = true, require = 0) // Inject after Forge checks dimension support
    public void renderCloudsNeo(ResourceHandle<?> resourcehandle, float p_365209_, Vec3 p_362985_, Matrix4f modelView, Matrix4f projectionMatrix, int color, CloudStatus p_364196_, float p_362337_, CallbackInfo ci) {
        ci.cancel();

        this.sodium$renderClouds(modelView, projectionMatrix, color);
    }

    @Unique
    private void sodium$renderClouds(Matrix4f matModelView, Matrix4f matProjection, int color) {
        if (this.cloudRenderer == null) {
            this.cloudRenderer = new CloudRenderer(this.minecraft.getResourceManager());
        }

        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        ClientLevel level = Objects.requireNonNull(this.level);

        var ticks = this.ticks;
        var partialTicks = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        this.cloudRenderer.render(camera, level, matProjection, matModelView, ticks, partialTicks, color);
    }

    @Inject(method = "onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("RETURN"))
    private void onReload(ResourceManager manager, CallbackInfo ci) {
        if (this.cloudRenderer != null) {
            this.cloudRenderer.reload(manager);
        }
    }

    @Inject(method = "allChanged()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        // will be re-allocated on next use
        if (this.cloudRenderer != null) {
            this.cloudRenderer.destroy();
            this.cloudRenderer = null;
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        // will never be re-allocated, as the renderer is shutting down
        if (this.cloudRenderer != null) {
            this.cloudRenderer.destroy();
            this.cloudRenderer = null;
        }
    }
}
