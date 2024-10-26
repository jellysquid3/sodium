package net.caffeinemc.mods.sodium.mixin.platform.neoforge;

import net.minecraft.client.resources.model.SimpleBakedModel;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleBakedModel.class)
public interface SimpleBakedModelAccessor {
    @Accessor
    ChunkRenderTypeSet getBlockRenderTypes();
}
