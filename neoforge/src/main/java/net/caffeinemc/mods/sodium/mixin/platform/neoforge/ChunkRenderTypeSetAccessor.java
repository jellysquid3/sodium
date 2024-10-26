package net.caffeinemc.mods.sodium.mixin.platform.neoforge;

import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.BitSet;

@Mixin(ChunkRenderTypeSet.class)
public interface ChunkRenderTypeSetAccessor {
    @Invoker("<init>")
    static ChunkRenderTypeSet create(BitSet set) {
        throw new IllegalStateException("Not shadowed");
    }

    @Accessor
    BitSet getBits();
}
