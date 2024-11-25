package net.caffeinemc.mods.sodium.mixin.features.gui.hooks.console;


import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.gui.console.ConsoleHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.util.profiling.ProfilerFiller;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Unique
    private static boolean HAS_RENDERED_OVERLAY_ONCE = false;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void onRender(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci, @Local ProfilerFiller profiler) {
        // Do not start updating the console overlay until the font renderer is ready
        // This prevents the console from using tofu boxes for everything during early startup
        if (Minecraft.getInstance().getOverlay() != null) {
            if (!HAS_RENDERED_OVERLAY_ONCE) {
                return;
            }
        }

        profiler.push("sodium_console_overlay");

        GuiGraphics drawContext = new GuiGraphics(this.minecraft, this.renderBuffers.bufferSource());

        ConsoleHooks.render(drawContext, GLFW.glfwGetTime());

        drawContext.flush();

        profiler.pop();

        HAS_RENDERED_OVERLAY_ONCE = true;
    }
}
