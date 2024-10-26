package net.caffeinemc.mods.sodium.mixin.features.gui.screen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StoringChunkProgressListener.class)
public interface StoringChunkProgressListenerAccessor {
    @Accessor
    Long2ObjectOpenHashMap<ChunkStatus> getStatuses();
}
