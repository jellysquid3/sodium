package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPNode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPResult;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;

/**
 * Constructs a BSP tree of the quads and sorts them dynamically.
 *
 * Triggering is performed when the BSP tree's partition planes are crossed in
 * any direction (bidirectional).
 */
public class DynamicBSPData extends DynamicData {
    private static final int NODE_REUSE_MIN_GENERATION = 1;

    private final BSPNode rootNode;
    private final int generation;

    private DynamicBSPData(SectionPos sectionPos, int vertexCount, BSPResult result, Vector3dc initialCameraPos, TQuad[] quads, int generation) {
        super(sectionPos, vertexCount, quads.length, result, initialCameraPos);
        this.rootNode = result.getRootNode();
        this.generation = generation;
    }

    private class DynamicBSPSorter extends DynamicSorter {
        private DynamicBSPSorter(int quadCount) {
            super(quadCount);
        }

        @Override
        void writeSort(CombinedCameraPos cameraPos, boolean initial) {
            DynamicBSPData.this.rootNode.collectSortedQuads(this.getIndexBuffer(), cameraPos.getRelativeCameraPos());
        }
    }

    @Override
    public DynamicSorter getSorter() {
        return new DynamicBSPSorter(this.getQuadCount());
    }

    public static DynamicBSPData fromMesh(int vertexCount,
                                          CombinedCameraPos cameraPos, TQuad[] quads, SectionPos sectionPos,
                                          TranslucentData oldData) {
        BSPNode oldRoot = null;
        int generation = 0;
        boolean prepareNodeReuse = false;
        if (oldData instanceof DynamicBSPData oldBSPData) {
            generation = oldBSPData.generation + 1;
            oldRoot = oldBSPData.rootNode;

            // only enable partial updates after a certain number of generations
            // (times the section has been built)
            prepareNodeReuse = generation >= NODE_REUSE_MIN_GENERATION;
        }
        var result = BSPNode.buildBSP(quads, sectionPos, oldRoot, prepareNodeReuse);

        var dynamicData = new DynamicBSPData(sectionPos, vertexCount, result, cameraPos.getAbsoluteCameraPos(), quads, generation);

        // prepare geometry planes for integration into GFNI triggering
        result.prepareIntegration();

        return dynamicData;
    }
}
