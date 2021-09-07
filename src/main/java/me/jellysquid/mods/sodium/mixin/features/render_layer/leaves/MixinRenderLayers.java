package me.jellysquid.mods.sodium.mixin.features.render_layer.leaves;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(RenderLayers.class)
public class MixinRenderLayers {
    @Mutable
    @Shadow
    @Final
    private static Map<Block, RenderLayer> BLOCKS;

    @Mutable
    @Shadow
    @Final
    private static Map<Fluid, RenderLayer> FLUIDS;

    static {
        // Replace the backing collection types with something a bit faster, since this is a hot spot in chunk rendering.
        BLOCKS = new Reference2ReferenceOpenHashMap<>(BLOCKS);
        FLUIDS = new Reference2ReferenceOpenHashMap<>(FLUIDS);
    }
    @Inject(method = "getBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/RenderLayer;", at = @At(value = "RETURN"), cancellable = true)
    private static void redirectLeavesGraphics(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {
        if (state.getBlock() instanceof LeavesBlock) {
            boolean fancyLeaves = SodiumClientMod.options().quality.leavesQuality.isFancy(MinecraftClient.getInstance().options.graphicsMode);
            cir.setReturnValue(fancyLeaves ? RenderLayer.getCutoutMipped() : RenderLayer.getSolid());
        }
    }
}
