package me.jellysquid.mods.sodium.client.render.chunk.compile;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

import java.util.Map;

/**
 * The result of a chunk rebuild task which contains any and all data that needs to be processed or uploaded on
 * the main thread. If a task is cancelled after finishing its work and not before the result is processed, the result
 * will instead be discarded.
 */
public class ChunkBuildResult {
    public final RenderSection render;
    public final ChunkRenderData data;
    public final Map<BlockRenderPass, ChunkMeshData> meshes;
    public final int buildTime;

    public ChunkBuildResult(RenderSection render, ChunkRenderData data, Map<BlockRenderPass, ChunkMeshData> meshes, int buildTime) {
        this.render = render;
        this.data = data;
        this.meshes = meshes;
        this.buildTime = buildTime;
    }

    public ChunkMeshData getMesh(BlockRenderPass pass) {
        return this.meshes.get(pass);
    }

    public void delete() {
        for (ChunkMeshData data : this.meshes.values()) {
            data.getVertexData()
                    .delete();
        }
    }
}
