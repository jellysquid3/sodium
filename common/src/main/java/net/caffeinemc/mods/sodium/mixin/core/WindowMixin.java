package net.caffeinemc.mods.sodium.mixin.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.caffeinemc.mods.sodium.client.platform.NativeWindowHandle;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Window.class)
public class WindowMixin implements NativeWindowHandle {
    @Shadow
    @Final
    private long window;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), require = 0)
    public long setAdditionalWindowHints(int titleEncoded, int width, CharSequence height, long title, long monitor, Operation<Long> original) {
        if (!PlatformRuntimeInformation.getInstance().platformHasEarlyLoadingScreen()) {
            if (SodiumClientMod.options().performance.useNoErrorGLContext) {
                if (!Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED)) {
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
                }
            }
        }

        return original.call(titleEncoded, width, height, title, monitor);
    }

    @Override
    public long getWin32Handle() {
        return GLFWNativeWin32.glfwGetWin32Window(this.window);
    }
}
