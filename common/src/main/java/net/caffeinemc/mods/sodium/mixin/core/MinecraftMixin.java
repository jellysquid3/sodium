package net.caffeinemc.mods.sodium.mixin.core;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.checks.ResourcePackScanner;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;
    @Unique
    private final LongArrayFIFOQueue fences = new LongArrayFIFOQueue();

    /**
     * We run this at the beginning of the frame (except for the first frame) to give the previous frame plenty of time
     * to render on the GPU. This allows us to stall on ClientWaitSync for less time.
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(boolean tick, CallbackInfo ci) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("wait_for_gpu");

        while (this.fences.size() > SodiumClientMod.options().advanced.cpuRenderAheadLimit) {
            var fence = this.fences.dequeueLong();
            // We do a ClientWaitSync here instead of a WaitSync to not allow the CPU to get too far ahead of the GPU.
            // This is also needed to make sure that our persistently-mapped staging buffers function correctly, rather
            // than being overwritten by data meant for future frames before the current one has finished rendering on
            // the GPU.
            //
            // Because we use GL_SYNC_FLUSH_COMMANDS_BIT, a flush will be inserted at some point in the command stream
            // (the stream of commands the GPU and/or driver (aka. the "server") is processing).
            // In OpenGL 4.4 contexts and below, the flush will be inserted *right before* the call to ClientWaitSync.
            // In OpenGL 4.5 contexts and above, the flush will be inserted *right after* the call to FenceSync (the
            // creation of the fence).
            // The flush, when the server reaches it in the command stream and processes it, tells the server that it
            // must *finish execution* of all the commands that have already been processed in the command stream,
            // and only after everything before the flush is done is it allowed to start processing and executing
            // commands after the flush.
            // Because we are also waiting on the client for the FenceSync to finish, the flush is effectively treated
            // like a Finish command, where we know that once ClientWaitSync returns, it's likely that everything
            // before it has been completed by the GPU.
            GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
            GL32C.glDeleteSync(fence);
        }

        profiler.pop();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void postRender(boolean tick, CallbackInfo ci) {
        var fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        if (fence == 0) {
            throw new RuntimeException("Failed to create fence object");
        }

        this.fences.enqueue(fence);
    }

    /**
     * Check for problematic core shader resource packs after the initial game launch.
     */
    @Inject(method = "buildInitialScreens", at = @At("TAIL"))
    private void postInit(CallbackInfoReturnable<Runnable> cir) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);

        ConfigManager.registerConfigsLate();
    }

    /**
     * Check for problematic core shader resource packs after every resource reload.
     */
    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("TAIL"))
    private void postResourceReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);
    }

}
