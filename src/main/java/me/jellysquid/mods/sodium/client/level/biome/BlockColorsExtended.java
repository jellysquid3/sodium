package me.jellysquid.mods.sodium.client.level.biome;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadColorProvider;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockColorsExtended {
    ModelQuadColorProvider<BlockState> getColorProvider(BlockState state);
}
