package net.caffeinemc.mods.sodium.mixin.platform.neoforge;


import net.caffeinemc.mods.sodium.client.services.SodiumModelData;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.SodiumAuxiliaryLightManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * This is a self-mixin to implement Forge interfaces into LevelSlice.
 */
@Mixin(LevelSlice.class)
public abstract class LevelSliceMixin implements BlockAndTintGetter {
    @Shadow
    @Final
    private SodiumAuxiliaryLightManager[] auxLightManager;

    @Shadow
    @Final
    private ClientLevel level;

    @Shadow
    private int originBlockX, originBlockY, originBlockZ;

    @Shadow
    public static int getLocalSectionIndex(int sectionX, int sectionY, int sectionZ) {
        throw new IllegalStateException("Not shadowed!");
    }

    @Shadow
    public SodiumModelData getPlatformModelData(BlockPos pos) {
        throw new IllegalStateException("Not shadowed!");
    }

    @Override
    public ModelData getModelData(BlockPos pos) {
        SodiumModelData modelData = getPlatformModelData(pos);
        return modelData != null ? (ModelData) (Object) modelData : null;
    }

    @Override
    public @Nullable AuxiliaryLightManager getAuxLightManager(ChunkPos pos) {
        int relChunkX = pos.x - (this.originBlockX >> 4);
        int relChunkZ = pos.z - (this.originBlockZ >> 4);

        return (AuxiliaryLightManager) auxLightManager[getLocalSectionIndex(relChunkX, 0, relChunkZ)];
    }

    @Override
    public @Nullable AuxiliaryLightManager getAuxLightManager(BlockPos pos) {
        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        return (AuxiliaryLightManager) auxLightManager[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];
    }

    @Override
    public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
        return this.level.getShade(normalX, normalY, normalZ, shade);
    }
}
