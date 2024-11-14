package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TopoGraphSorting;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.minecraft.core.SectionPos;

/**
 * A node in the BSP tree. The BSP tree is made up of nodes that split quads
 * into groups on either side of a plane and those that lie on the plane.
 * There's also leaf nodes that contain one or more quads.
 * 
 * Implementation note:
 * - Doing a convex box test doesn't seem to bring a performance boost, even if
 * it does trigger sometimes with man-made structures. The multi partition node
 * probably does most of the work already.
 * - Checking if the given quads are all coplanar doesn't recoup the cost of
 * iterating through all the quads. It also doesn't significantly reduce the
 * number of triggering planes (which would have a performance and memory usage
 * benefit).
 */
public abstract class BSPNode {

    abstract void collectSortedQuads(BSPSortState sortState, Vector3fc cameraPos);

    public void collectSortedQuads(NativeBuffer nativeBuffer, Vector3fc cameraPos) {
        this.collectSortedQuads(new BSPSortState(nativeBuffer), cameraPos);
    }

    public static BSPResult buildBSP(TQuad[] quads, SectionPos sectionPos, BSPNode oldRoot,
            boolean prepareNodeReuse) {
        // throw if there's too many quads
        InnerPartitionBSPNode.validateQuadCount(quads.length);

        // create a workspace and then the nodes figure out the recursive building.
        // throws if the BSP can't be built, null if none is necessary
        var workspace = new BSPWorkspace(quads, sectionPos, prepareNodeReuse);

        // initialize the indexes to all quads
        int[] initialIndexes = new int[quads.length];
        for (int i = 0; i < quads.length; i++) {
            initialIndexes[i] = i;
        }
        var allIndexes = new IntArrayList(initialIndexes);

        var rootNode = BSPNode.build(workspace, allIndexes, -1, oldRoot);
        var result = workspace.result;
        result.setRootNode(rootNode);
        return result;
    }

    private static boolean doubleLeafPossible(TQuad quadA, TQuad quadB) {
        // check for coplanar or mutually invisible quads
        var facingA = quadA.getFacing();
        var facingB = quadB.getFacing();

        // coplanar not aligned
        if (!facingA.isAligned() || !facingB.isAligned()) {
            var packedNormalA = quadA.getPackedNormal();
            var packedNormalB = quadB.getPackedNormal();
            // opposite normal (distance irrelevant)
            if (NormI8.isOpposite(packedNormalA, packedNormalB)
                    // same normal and same distance
                    || packedNormalA == packedNormalB && quadA.getAccurateDotProduct() == quadB.getAccurateDotProduct()) {
                return true;
            }
        }

        // coplanar aligned
        else if (quadA.getExtents()[facingA.ordinal()] == quadB.getExtents()[facingB.ordinal()]) {
            return true;
        }

        // aligned facing away from each other
        else if (facingA == facingB.getOpposite()) {
            return true;
        }

        // aligned otherwise mutually invisible
        else {
            return !TopoGraphSorting.orthogonalQuadVisibleThrough(quadA, quadB)
                    && !TopoGraphSorting.orthogonalQuadVisibleThrough(quadB, quadA);
        }

        return false;
    }

    static BSPNode build(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        depth++;

        // pick which type of node to create for the given workspace
        if (indexes.isEmpty()) {
            return null;
        } else if (indexes.size() == 1) {
            return new LeafSingleBSPNode(indexes.getInt(0));
        } else if (indexes.size() == 2) {
            var quadIndexA = indexes.getInt(0);
            var quadIndexB = indexes.getInt(1);
            var quadA = workspace.quads[quadIndexA];
            var quadB = workspace.quads[quadIndexB];

            if (doubleLeafPossible(quadA, quadB)) {
                return new LeafDoubleBSPNode(quadIndexA, quadIndexB);
            }
        }

        return InnerPartitionBSPNode.build(workspace, indexes, depth, oldNode);
    }
}
