package net.caffeinemc.mods.sodium.mixin.features.render.compositing;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", shift = At.Shift.BEFORE))
    private void preCreateWindow(CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_RED_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_GREEN_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_BLUE_BITS, 8);
        GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, 0); // Alpha channel is not needed for default framebuffer
    }
}
