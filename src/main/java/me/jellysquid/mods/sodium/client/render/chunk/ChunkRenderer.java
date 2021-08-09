package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

/**
 * The chunk render backend takes care of managing the graphics resource state of chunk render containers. This includes
 * the handling of uploading their data to the graphics card and rendering responsibilities.
 */
public interface ChunkRenderer {
    /**
     * Renders the given chunk render list to the active framebuffer.
     * @param matrixStack The current matrix stack to be used for rendering
     * @param commandList The command list which OpenGL commands should be serialized to
     * @param renders An iterator over the list of chunks to be rendered
     * @param pass The block render pass to execute
     * @param camera The camera context containing chunk offsets for the current render
     */
    void render(PoseStack matrixStack, CommandList commandList, ChunkRenderList renders, BlockRenderPass pass, ChunkCameraContext camera);

    /**
     * Deletes this render backend and any resources attached to it.
     */
    void delete();

    /**
     * Returns the vertex format used by this chunk render backend for rendering meshes.
     */
    ChunkVertexType getVertexType();
}
