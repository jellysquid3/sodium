package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.BSPBuildFailureException;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.*;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;

/**
 * The translucent geometry collector collects the data from the renderers and
 * builds data structures for either dynamic triggering or static sorting. It
 * determines the best sort type for the section and constructs various types of
 * translucent data objects that then perform sorting and get registered with
 * GFNI for triggering.
 * <p>
 * An instance of this class is created for each meshing task. It goes through
 * three stages:
 * 1. During meshing, it collects the geometry and calculates some metrics on the
 * fly. These are later used for the sort type heuristic.
 * 2. With {@link #finishRendering()} it finishes the geometry collection,
 * generates the quad list, and calculates additional metrics. Then the sort
 * type is determined with a heuristic based on the collected metrics. This
 * determines if block face culling can be enabled.
 * - Now the {@link BuiltSectionMeshParts} is generated, which yields the vertex
 * counts.
 * 3. The vertex counts and the mesh parts object are used by the collector in
 * the construction of the {@link TranslucentData} object. The data object
 * allocates memory for the index data and performs the first (and for static
 * sort types, only) sort.
 * - The data object is put into the {@link ChunkBuildOutput}.
 * <p>
 * When dynamic sorting is enabled, trigger information from {@link DynamicData}
 * object is integrated into {@link SortTriggering} when main thread receives the
 * task result.
 */
public class TranslucentGeometryCollector {

    private final SectionPos sectionPos;

    // true if there are any unaligned quads
    private boolean hasUnaligned = false;

    // a bitmap of the aligned facings present in the section
    private int alignedFacingBitmap = 0;

    // AABB of the geometry
    private final float[] extents = new float[] {
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY
    };

    // true if one of the extents has more than one plane
    private boolean alignedExtentsMultiple = false;

    // the maximum (or minimum for negative directions) of quads with a particular
    // facing. (Dot product of the normal with a vertex for all aligned facings)
    private final float[] alignedExtremes = new float[] {
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY
    };

    // keep track of two normals with each up to two distances for important special
    // case heuristics
    private int unalignedANormal = -1;
    private float unalignedADistance1 = Float.NaN;
    private float unalignedADistance2 = Float.NaN;
    private int unalignedBNormal = -1;
    private float unalignedBDistance1 = Float.NaN;
    private float unalignedBDistance2 = Float.NaN;

    @SuppressWarnings("unchecked")
    private ReferenceArrayList<TQuad>[] quadLists = new ReferenceArrayList[ModelQuadFacing.COUNT];
    private TQuad[] quads;

    private SortType sortType;

    private boolean quadHashPresent = false;
    private int quadHash = 0;

    public TranslucentGeometryCollector(SectionPos sectionPos) {
        this.sectionPos = sectionPos;
    }

    private static final float INV_QUANTIZE_EPSILON = 256f;
    private static final float QUANTIZE_EPSILON = 1f / INV_QUANTIZE_EPSILON;

    static {
        // ensure it fits with the fluid renderer epsilon and that it's a power-of-two
        // fraction
        var targetEpsilon = DefaultFluidRenderer.EPSILON * 2.1f;
        if (QUANTIZE_EPSILON <= targetEpsilon && Integer.bitCount((int) INV_QUANTIZE_EPSILON) == 1) {
            throw new RuntimeException("epsilon is invalid: " + QUANTIZE_EPSILON);
        }
    }

    public void appendQuad(int packedNormal, ChunkVertexEncoder.Vertex[] vertices, ModelQuadFacing facing) {
        float xSum = 0;
        float ySum = 0;
        float zSum = 0;

        // keep track of distinct vertices to compute the center accurately for
        // degenerate quads
        float lastX = vertices[3].x;
        float lastY = vertices[3].y;
        float lastZ = vertices[3].z;
        int uniqueVertexes = 0;

        float posXExtent = Float.NEGATIVE_INFINITY;
        float posYExtent = Float.NEGATIVE_INFINITY;
        float posZExtent = Float.NEGATIVE_INFINITY;
        float negXExtent = Float.POSITIVE_INFINITY;
        float negYExtent = Float.POSITIVE_INFINITY;
        float negZExtent = Float.POSITIVE_INFINITY;

        for (int i = 0; i < 4; i++) {
            float x = vertices[i].x;
            float y = vertices[i].y;
            float z = vertices[i].z;

            posXExtent = Math.max(posXExtent, x);
            posYExtent = Math.max(posYExtent, y);
            posZExtent = Math.max(posZExtent, z);
            negXExtent = Math.min(negXExtent, x);
            negYExtent = Math.min(negYExtent, y);
            negZExtent = Math.min(negZExtent, z);

            if (x != lastX || y != lastY || z != lastZ) {
                xSum += x;
                ySum += y;
                zSum += z;
                uniqueVertexes++;
            }
            if (i != 3) {
                lastX = x;
                lastY = y;
                lastZ = z;
            }
        }

        // shrink quad in non-normal directions to prevent intersections caused by
        // epsilon offsets applied by FluidRenderer
        if (facing != ModelQuadFacing.POS_X && facing != ModelQuadFacing.NEG_X) {
            posXExtent -= QUANTIZE_EPSILON;
            negXExtent += QUANTIZE_EPSILON;
            if (negXExtent > posXExtent) {
                negXExtent = posXExtent;
            }
        }
        if (facing != ModelQuadFacing.POS_Y && facing != ModelQuadFacing.NEG_Y) {
            posYExtent -= QUANTIZE_EPSILON;
            negYExtent += QUANTIZE_EPSILON;
            if (negYExtent > posYExtent) {
                negYExtent = posYExtent;
            }
        }
        if (facing != ModelQuadFacing.POS_Z && facing != ModelQuadFacing.NEG_Z) {
            posZExtent -= QUANTIZE_EPSILON;
            negZExtent += QUANTIZE_EPSILON;
            if (negZExtent > posZExtent) {
                negZExtent = posZExtent;
            }
        }

        // POS_X, POS_Y, POS_Z, NEG_X, NEG_Y, NEG_Z
        float[] extents = new float[] { posXExtent, posYExtent, posZExtent, negXExtent, negYExtent, negZExtent };

        int direction = facing.ordinal();
        var quadList = this.quadLists[direction];
        if (quadList == null) {
            quadList = new ReferenceArrayList<>();
            this.quadLists[direction] = quadList;
        }

        Vector3fc center = null;
        if (!facing.isAligned() || uniqueVertexes != 4) {
            var centerX = xSum / uniqueVertexes;
            var centerY = ySum / uniqueVertexes;
            var centerZ = zSum / uniqueVertexes;
            center = new Vector3f(centerX, centerY, centerZ);
        }

        // check if we need to store vertex positions for this quad, only necessary if it's unaligned or rotated (yet aligned)
        var needsVertexPositions = uniqueVertexes != 4 || !facing.isAligned();
        if (!needsVertexPositions) {
            for (int i = 0; i < 4; i++) {
                var vertex = vertices[i];
                if (vertex.x != posYExtent && vertex.x != negYExtent ||
                        vertex.y != posZExtent && vertex.y != negZExtent ||
                        vertex.z != posXExtent && vertex.z != negXExtent) {
                    needsVertexPositions = true;
                    break;
                }
            }
        }

        float[] vertexPositions = null;
        if (needsVertexPositions) {
            vertexPositions = new float[12];
            for (int i = 0, itemIndex = 0; i < 4; i++) {
                var vertex = vertices[i];
                vertexPositions[itemIndex++] = vertex.x;
                vertexPositions[itemIndex++] = vertex.y;
                vertexPositions[itemIndex++] = vertex.z;
            }
        }

        if (facing.isAligned()) {
            // only update global extents if there are no unaligned quads since this is only
            // used for the convex box test which doesn't work with unaligned quads anyway
            if (!this.hasUnaligned) {
                this.extents[0] = Math.max(this.extents[0], posXExtent);
                this.extents[1] = Math.max(this.extents[1], posYExtent);
                this.extents[2] = Math.max(this.extents[2], posZExtent);
                this.extents[3] = Math.min(this.extents[3], negXExtent);
                this.extents[4] = Math.min(this.extents[4], negYExtent);
                this.extents[5] = Math.min(this.extents[5], negZExtent);
            }

            var quad = TQuad.fromAligned(facing, extents, vertexPositions, center);
            quadList.add(quad);

            var extreme = this.alignedExtremes[direction];
            var distance = quad.getAccurateDotProduct();

            // check if this is a new dot product for this distance
            var existingExtreme = this.alignedExtremes[direction];
            if (!this.alignedExtentsMultiple && !Float.isInfinite(existingExtreme) && existingExtreme != distance) {
                this.alignedExtentsMultiple = true;
            }

            // update the aligned extremes (which are the direction dependent dot products)
            if (facing.getSign() > 0) {
                this.alignedExtremes[direction] = Math.max(extreme, distance);
            } else {
                this.alignedExtremes[direction] = Math.min(extreme, distance);
            }
        } else {
            this.hasUnaligned = true;

            var quad = TQuad.fromUnaligned(facing, extents, vertexPositions, center, packedNormal);
            quadList.add(quad);

            // update the two unaligned normals that are tracked
            var distance = quad.getAccurateDotProduct();
            if (packedNormal == this.unalignedANormal) {
                if (Float.isNaN(this.unalignedADistance1)) {
                    this.unalignedADistance1 = distance;
                } else {
                    this.unalignedADistance2 = distance;
                }
            } else if (packedNormal == this.unalignedBNormal) {
                if (Float.isNaN(this.unalignedBDistance1)) {
                    this.unalignedBDistance1 = distance;
                } else {
                    this.unalignedBDistance2 = distance;
                }
            } else if (this.unalignedANormal == -1) {
                this.unalignedANormal = packedNormal;
                this.unalignedADistance1 = distance;
            } else if (this.unalignedBNormal == -1) {
                this.unalignedBNormal = packedNormal;
                this.unalignedBDistance1 = distance;
            }
        }
    }

    /**
     * Filters the given sort type to fit within the selected sorting mode. If it
     * doesn't match, then it's set to the NONE sort type.
     *
     * @param sortType the sort type to filter
     */
    private static SortType filterSortType(SortType sortType) {
        SortBehavior sortBehavior = SodiumClientMod.options().performance.getSortBehavior();
        switch (sortBehavior) {
            case OFF:
                return SortType.NONE;
            case STATIC:
                if (sortType == SortType.STATIC_NORMAL_RELATIVE || sortType == SortType.STATIC_TOPO) {
                    return sortType;
                } else {
                    return SortType.NONE;
                }
            default:
                return sortType;
        }
    }

    /**
     * Array of how many quads a section can have with a given number of unique
     * normals so that a static topo sort is attempted on it. -1 means the value is
     * unused and doesn't make sense to give.
     */
    private static final int[] STATIC_TOPO_SORT_ATTEMPT_LIMITS = new int[] { -1, -1, 250, 100, 50, 30 };
    public static final int STATIC_TOPO_UNKNOWN_FALLBACK_LIMIT = STATIC_TOPO_SORT_ATTEMPT_LIMITS[STATIC_TOPO_SORT_ATTEMPT_LIMITS.length - 1];

    /**
     * Determines the sort type for the collected geometry from the section. It
     * determines a sort type, which is either no sorting, a static sort or a
     * dynamic sort (section in GFNI only in this case).
     * <p>
     * See the section on special cases for an explanation of the special sorting
     * cases: <a href="https://hackmd.io/@douira100/sodium-sl-gfni#Special-Sorting-Cases">...</a>
     * <p>
     * A: If there are no or only one normal, this builder can be considered
     * practically empty.
     * <p>
     * B: If there are two face planes with opposing normals at the same distance,
     * then
     * they can't be seen through each other and this section can be ignored.
     * <p>
     * C: If the translucent faces are on the surface of the convex hull of all
     * translucent faces in the section and face outwards, then there is no way to
     * see one through another. Since convex hulls are hard, a simpler case only
     * uses the axis aligned normals: Under the condition that only aligned normals
     * are used in the section, tracking the bounding box of the translucent
     * geometry (the vertices) in the section and then checking if the normal
     * distances line up with the bounding box allows the exclusion of some
     * sections containing a single convex translucent cuboid (of which not all
     * faces need to exist).
     * <p>
     * D: If there are only two normals which are opposites of
     * each other, then a special fixed sort order is always a correct sort order.
     * This ordering sorts the two sets of face planes by their ascending
     * normal-relative distance. The ordering between the two normals is irrelevant
     * as they can't be seen through each other anyway.
     * <p>
     * More heuristics can be performed here to conservatively determine if this
     * section could possibly have more than one translucent sort order.
     *
     * @return the required sort type to ensure this section always looks correct
     */
    private SortType sortTypeHeuristic() {
        if (this.quads.length <= 1) {
            return SortType.NONE;
        }

        SortBehavior sortBehavior = SodiumClientMod.options().performance.getSortBehavior();
        if (sortBehavior.getSortMode() == SortBehavior.SortMode.NONE) {
            return SortType.NONE;
        }

        int alignedNormalCount = Integer.bitCount(this.alignedFacingBitmap);
        int planeCount = getPlaneCount(alignedNormalCount);

        int unalignedNormalCount = 0;
        if (this.unalignedANormal != -1) {
            unalignedNormalCount++;
        }
        if (this.unalignedBNormal != -1) {
            unalignedNormalCount++;
        }

        int normalCount = alignedNormalCount + unalignedNormalCount;

        // special case A
        if (planeCount <= 1) {
            return SortType.NONE;
        }

        if (!this.hasUnaligned) {
            boolean opposingAlignedNormals = ModelQuadFacing.bitmapIsOpposingAligned(this.alignedFacingBitmap);

            // special case B
            // if there are just two normals, they are exact opposites of each other and they
            // each only have one distance, there is no way to see through one face to the
            // other.
            if (planeCount == 2 && opposingAlignedNormals) {
                return SortType.NONE;
            }

            // special case C
            // the more complex test that checks for distances aligned with the bounding box
            if (!this.alignedExtentsMultiple) {
                boolean passesBoundingBoxTest = true;
                for (int direction = 0; direction < ModelQuadFacing.DIRECTIONS; direction++) {
                    var extreme = this.alignedExtremes[direction];
                    if (Float.isInfinite(extreme)) {
                        continue;
                    }

                    // check the distance against the bounding box
                    var sign = direction < 3 ? 1 : -1;
                    if (sign * extreme != this.extents[direction]) {
                        passesBoundingBoxTest = false;
                        break;
                    }
                }
                if (passesBoundingBoxTest) {
                    return SortType.NONE;
                }
            }

            // special case D
            // there are up to two normals that are opposing, this means no dynamic sorting
            // is necessary. Without static sorting, the geometry to trigger on could be
            // reduced but this isn't done here as we assume static sorting is possible.
            if (opposingAlignedNormals || alignedNormalCount == 1) {
                return SortType.STATIC_NORMAL_RELATIVE;
            }
        } else if (alignedNormalCount == 0) {
            // special case D but for one normal or two opposing unaligned normals
            if (unalignedNormalCount == 1
                    || unalignedNormalCount == 2 && NormI8.isOpposite(this.unalignedANormal, this.unalignedBNormal)) {
                return SortType.STATIC_NORMAL_RELATIVE;
            }
        } else if (planeCount == 2) { // implies normalCount == 2
            // special case D with mixed aligned and unaligned normals
            int alignedDirection = Integer.numberOfTrailingZeros(this.alignedFacingBitmap);
            if (NormI8.isOpposite(this.unalignedANormal, ModelQuadFacing.PACKED_ALIGNED_NORMALS[alignedDirection])) {
                return SortType.STATIC_NORMAL_RELATIVE;
            }
        }

        // use the given set of quad count limits to determine if a static topo sort
        // should be attempted

        var attemptLimitIndex = Mth.clamp(normalCount, 2, STATIC_TOPO_SORT_ATTEMPT_LIMITS.length - 1);
        if (this.quads.length <= STATIC_TOPO_SORT_ATTEMPT_LIMITS[attemptLimitIndex]) {
            return SortType.STATIC_TOPO;
        }

        return SortType.DYNAMIC;
    }

    private int getPlaneCount(int alignedNormalCount) {
        int alignedPlaneCount = alignedNormalCount;
        if (this.alignedExtentsMultiple) {
            alignedPlaneCount = 100;
        }

        int unalignedPlaneCount = 0;
        if (!Float.isNaN(this.unalignedADistance1)) {
            unalignedPlaneCount++;
        }
        if (!Float.isNaN(this.unalignedADistance2)) {
            unalignedPlaneCount++;
        }
        if (!Float.isNaN(this.unalignedBDistance1)) {
            unalignedPlaneCount++;
        }
        if (!Float.isNaN(this.unalignedBDistance2)) {
            unalignedPlaneCount++;
        }

        return alignedPlaneCount + unalignedPlaneCount;
    }

    public SortType finishRendering() {
        // combine the quads into one array
        int totalQuadCount = 0;
        for (var quadList : this.quadLists) {
            if (quadList != null) {
                totalQuadCount += quadList.size();
            }
        }
        this.quads = new TQuad[totalQuadCount];
        int quadIndex = 0;
        for (int direction = 0; direction < ModelQuadFacing.COUNT; direction++) {
            var quadList = this.quadLists[direction];
            if (quadList != null) {
                for (var quad : quadList) {
                    this.quads[quadIndex++] = quad;
                }
                if (direction < ModelQuadFacing.DIRECTIONS) {
                    this.alignedFacingBitmap |= 1 << direction;
                }
            }
        }
        this.quadLists = null; // they're not needed anymore

        this.sortType = filterSortType(sortTypeHeuristic());
        return this.sortType;
    }

    private static int ensureUnassignedVertexCount(int[] vertexCounts) {
        int vertexCount = vertexCounts[ModelQuadFacing.UNASSIGNED.ordinal()];

        if (vertexCount == 0) {
            throw new IllegalStateException("No unassigned data in mesh");
        }

        return vertexCount;
    }

    private TranslucentData makeNewTranslucentData(int[] vertexCounts, CombinedCameraPos cameraPos,
                                                   TranslucentData oldData) {
        if (this.sortType == SortType.NONE) {
            return AnyOrderData.fromMesh(vertexCounts, this.quads, this.sectionPos);
        }

        if (this.sortType == SortType.STATIC_NORMAL_RELATIVE) {
            var isDoubleUnaligned = this.alignedFacingBitmap == 0;
            return StaticNormalRelativeData.fromMesh(vertexCounts, this.quads, this.sectionPos, isDoubleUnaligned);
        }

        // from this point on we know the estimated sort type requires direction mixing
        // (no backface culling) and all vertices are in the UNASSIGNED direction.
        if (this.sortType == SortType.STATIC_TOPO) {
            var vertexCount = ensureUnassignedVertexCount(vertexCounts);
            var result = StaticTopoData.fromMesh(vertexCount, this.quads, this.sectionPos);
            if (result != null) {
                return result;
            }
            this.sortType = SortType.DYNAMIC;
        }

        // filter the sort type with the user setting and re-evaluate
        this.sortType = filterSortType(this.sortType);

        if (this.sortType == SortType.NONE) {
            return AnyOrderData.fromMesh(vertexCounts, this.quads, this.sectionPos);
        }

        if (this.sortType == SortType.DYNAMIC) {
            var vertexCount = ensureUnassignedVertexCount(vertexCounts);
            try {
                return DynamicBSPData.fromMesh(
                        vertexCount, cameraPos, this.quads, this.sectionPos, oldData);
            } catch (BSPBuildFailureException e) {
                var geometryPlanes = GeometryPlanes.fromQuadLists(this.sectionPos, this.quads);
                return DynamicTopoData.fromMesh(
                        vertexCount, cameraPos, this.quads, this.sectionPos,
                        geometryPlanes);
            }
        }

        throw new IllegalStateException("Unknown sort type: " + this.sortType);
    }

    private int getQuadHash(TQuad[] quads) {
        if (this.quadHashPresent) {
            return this.quadHash;
        }

        for (int i = 0; i < quads.length; i++) {
            var quad = quads[i];
            this.quadHash = this.quadHash * 31 + quad.getQuadHash() + i * 3;
        }
        this.quadHashPresent = true;
        return this.quadHash;
    }

    public TranslucentData getTranslucentData(
            TranslucentData oldData, BuiltSectionMeshParts translucentMesh, CombinedCameraPos cameraPos) {
        // means there is no translucent geometry
        if (translucentMesh == null) {
            return NoData.forNoTranslucent(this.sectionPos);
        }

        var vertexCounts = translucentMesh.computeVertexCounts();

        // re-use the original translucent data if it's the same. This reduces the
        // amount of generated and uploaded index data when sections are rebuilt without
        // relevant changes to translucent geometry. Rebuilds happen when any part of
        // the section changes, including the here irrelevant cases of changes to opaque
        // geometry or light levels.
        if (oldData != null) {
            // for the NONE sort type the ranges need to be the same, the actual geometry
            // doesn't matter
            if (this.sortType == SortType.NONE && oldData instanceof AnyOrderData oldAnyData
                    && oldAnyData.getQuadCount() == this.quads.length
                    && Arrays.equals(oldAnyData.getVertexCounts(), vertexCounts)) {
                return oldAnyData;
            }

            // for the other sort types the geometry needs to be the same (checked with
            // length and hash)
            if (oldData instanceof PresentTranslucentData oldPresentData) {
                if (oldPresentData.getQuadCount() == this.quads.length
                        && oldPresentData.getQuadHash() == getQuadHash(this.quads)) {
                    return oldPresentData;
                }
            }
        }

        var newData = makeNewTranslucentData(vertexCounts, cameraPos, oldData);
        if (newData instanceof PresentTranslucentData presentData) {
            presentData.setQuadHash(getQuadHash(this.quads));
        }
        return newData;
    }
}
