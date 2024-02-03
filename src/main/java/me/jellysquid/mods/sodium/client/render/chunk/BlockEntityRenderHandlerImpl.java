package me.jellysquid.mods.sodium.client.render.chunk;

import java.util.function.Predicate;

import net.caffeinemc.mods.sodium.api.blockentity.BlockEntityRenderHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;

public class BlockEntityRenderHandlerImpl implements BlockEntityRenderHandler {
    @Override
    public <T extends BlockEntity> void setRenderPredicate(BlockEntityType<T> type, Predicate<? super T> predicate) {
        ExtendedBlockEntityType.setRenderPredicate(type, predicate);
    }
}