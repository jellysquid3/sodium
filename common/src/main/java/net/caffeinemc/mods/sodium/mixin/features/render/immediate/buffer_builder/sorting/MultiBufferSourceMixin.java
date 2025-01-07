package net.caffeinemc.mods.sodium.mixin.features.render.immediate.buffer_builder.sorting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.caffeinemc.mods.sodium.client.util.sorting.VertexSorters;
import net.caffeinemc.mods.sodium.client.util.sorting.VertexSortingExtended;
import net.minecraft.client.renderer.MultiBufferSource;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiBufferSource.BufferSource.class)
public class MultiBufferSourceMixin {
    @Unique
    private static final int VERTICES_PER_QUAD = 6;

    @WrapOperation(
            method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/MeshData;sortQuads(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)Lcom/mojang/blaze3d/vertex/MeshData$SortState;"
            )
    )
    private MeshData.SortState redirectSortQuads(MeshData meshData, ByteBufferBuilder bufferBuilder, VertexSorting sorting, Operation<MeshData.SortState> original) {
        if (sorting instanceof VertexSortingExtended sortingExtended) {
            var sortedPrimitiveIds = VertexSorters.sort(meshData.vertexBuffer(), meshData.drawState().vertexCount(), meshData.drawState().format().getVertexSize(), sortingExtended);
            var sortedIndexBuffer = buildSortedIndexBuffer(meshData, bufferBuilder, sortedPrimitiveIds);
            ((MeshDataAccessor) meshData).setIndexBuffer(sortedIndexBuffer);
        } else {
            original.call(meshData, bufferBuilder, sorting);
        }

        return null;
    }

    @Unique
    private static ByteBufferBuilder.Result buildSortedIndexBuffer(MeshData meshData, ByteBufferBuilder bufferBuilder, int[] primitiveIds) {
        final var indexType = meshData.drawState().indexType();
        final var ptr = bufferBuilder.reserve((primitiveIds.length * VERTICES_PER_QUAD) * indexType.bytes);

        if (indexType == VertexFormat.IndexType.SHORT) {
            writeShortIndexBuffer(ptr, primitiveIds);
        } else if (indexType == VertexFormat.IndexType.INT) {
            writeIntIndexBuffer(ptr, primitiveIds);
        }

        return bufferBuilder.build();
    }

    @Unique
    private static void writeIntIndexBuffer(long ptr, int[] primitiveIds) {
        for (int primitiveId : primitiveIds) {
            MemoryUtil.memPutInt(ptr +  0L, (primitiveId * 4) + 0);
            MemoryUtil.memPutInt(ptr +  4L, (primitiveId * 4) + 1);
            MemoryUtil.memPutInt(ptr +  8L, (primitiveId * 4) + 2);
            MemoryUtil.memPutInt(ptr + 12L, (primitiveId * 4) + 2);
            MemoryUtil.memPutInt(ptr + 16L, (primitiveId * 4) + 3);
            MemoryUtil.memPutInt(ptr + 20L, (primitiveId * 4) + 0);
            ptr += 24L;
        }
    }

    @Unique
    private static void writeShortIndexBuffer(long ptr, int[] primitiveIds) {
        for (int primitiveId : primitiveIds) {
            MemoryUtil.memPutShort(ptr +  0L, (short) ((primitiveId * 4) + 0));
            MemoryUtil.memPutShort(ptr +  2L, (short) ((primitiveId * 4) + 1));
            MemoryUtil.memPutShort(ptr +  4L, (short) ((primitiveId * 4) + 2));
            MemoryUtil.memPutShort(ptr +  6L, (short) ((primitiveId * 4) + 2));
            MemoryUtil.memPutShort(ptr +  8L, (short) ((primitiveId * 4) + 3));
            MemoryUtil.memPutShort(ptr + 10L, (short) ((primitiveId * 4) + 0));
            ptr += 12L;
        }
    }
}
