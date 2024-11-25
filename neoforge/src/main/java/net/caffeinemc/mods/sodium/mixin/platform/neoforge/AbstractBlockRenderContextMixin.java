package net.caffeinemc.mods.sodium.mixin.platform.neoforge;

import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.services.SodiumModelData;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlockRenderContext.BlockEmitter.class)
public abstract class AbstractBlockRenderContextMixin implements QuadEmitter {
    @Unique
    private AbstractBlockRenderContext parent;

    /**
     * @author IMS
     * @reason Access parent
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    public void assignParent(AbstractBlockRenderContext parent, CallbackInfo ci) {
        this.parent = parent;
    }

    @Override
    public ModelData getModelData() {
        return (ModelData) (Object) this.parent.getModelData();
    }

    @Override
    public RenderType getRenderType() {
        return this.parent.getRenderType();
    }
}
