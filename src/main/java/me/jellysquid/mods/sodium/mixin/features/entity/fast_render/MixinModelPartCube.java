package me.jellysquid.mods.sodium.mixin.features.entity.fast_render;

import me.jellysquid.mods.sodium.client.model.ModelPartCubeAccessor;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ModelPart.Cube.class)
public class MixinModelPartCube implements ModelPartCubeAccessor {
    @Shadow
    @Final
    private ModelPart.Polygon[] polygons;

    @Override
    public ModelPart.Polygon[] getQuads() {
        return this.polygons;
    }
}
