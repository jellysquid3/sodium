package me.jellysquid.mods.sodium.mixin.core.pipeline;

import com.mojang.blaze3d.vertex.VertexFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.BufferVertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VertexFormat.class)
public abstract class MixinVertexFormat implements BufferVertexFormat {
    @Shadow
    public abstract int getVertexSize();

    @Override
    public int getStride() {
        return this.getVertexSize();
    }
}
