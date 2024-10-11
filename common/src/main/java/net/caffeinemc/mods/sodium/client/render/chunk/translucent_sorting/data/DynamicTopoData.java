package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.caffeinemc.mods.sodium.client.util.sorting.RadixSort;
import net.minecraft.core.SectionPos;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * Performs dynamic topo sorting and falls back to distance sorting as
 * necessary. This class implements a number of heuristics to attempt to upgrade
 * distance-based sorting back to topo sorting when possible as topo sorting
 * generally needs to happen far less often.
 * <p>
 * Triggering is performed when the quads' planes crossed along their normal
 * direction (unidirectional).
 * <p>
 * Implementation note:
 * - Reusing the output of previous distance sorting job doesn't make a
 * difference or makes things slower in some cases. It's unclear why exactly
 * this happens, I suspect weird memory behavior or the reuse is not actually
 * that helpful to the sorting algorithm.
 */
public class DynamicTopoData extends DynamicData {
    private static final int MAX_TOPO_SORT_QUADS = 1000;
    private static final int MAX_TOPO_SORT_TIME_NS = 1_000_000;
    private static final int MAX_FAILING_TOPO_SORT_TIME_NS = 750_000;
    private static final int MAX_TOPO_SORT_PATIENT_TIME_NS = 250_000;
    private static final int PATIENT_TOPO_ATTEMPTS = 5;
    private static final int REGULAR_TOPO_ATTEMPTS = 2;

    private boolean GFNITrigger = true;
    private boolean directTrigger = false;
    private int consecutiveTopoSortFailures = 0;

    private double directTriggerKey = -1;
    private boolean pendingTriggerIsDirect;

    private final TQuad[] quads;
    private final Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal;

    private DynamicTopoData(SectionPos sectionPos, int vertexCount, TQuad[] quads,
                            GeometryPlanes geometryPlanes, Vector3dc initialCameraPos,
                            Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal) {
        super(sectionPos, vertexCount, quads.length, geometryPlanes, initialCameraPos);
        this.quads = quads;
        this.distancesByNormal = distancesByNormal;

        if (this.getQuadCount() > MAX_TOPO_SORT_QUADS) {
            this.directTrigger = true;
            this.GFNITrigger = false;
        }
    }

    @Override
    public DynamicSorter getSorter() {
        return new DynamicTopoSorter(this.getQuadCount(), this, this.pendingTriggerIsDirect, this.consecutiveTopoSortFailures, this.GFNITrigger, this.directTrigger);
    }

    public boolean GFNITriggerEnabled() {
        return this.GFNITrigger;
    }

    public boolean directTriggerEnabled() {
        return this.directTrigger;
    }

    public double getDirectTriggerKey() {
        return this.directTriggerKey;
    }

    public void setDirectTriggerKey(double key) {
        this.directTriggerKey = key;
    }

    public boolean isMatchingSorter(DynamicTopoSorter sorter) {
        return sorter.parent == this;
    }

    public boolean checkAndApplyGFNITriggerOff(DynamicTopoSorter sorter) {
        if (this.GFNITrigger && !sorter.GFNITrigger) {
            this.GFNITrigger = false;
            return true;
        }
        return false;
    }

    public boolean checkAndApplyDirectTriggerOff(DynamicTopoSorter sorter) {
        if (this.directTrigger && !sorter.directTrigger) {
            this.directTrigger = false;
            return true;
        }
        return false;
    }

    public boolean checkAndApplyDirectTriggerOn(DynamicTopoSorter sorter) {
        if (!this.directTrigger && sorter.directTrigger) {
            this.directTrigger = true;
            return true;
        }
        return false;
    }

    public void applyTopoSortFailureCounterChange(DynamicTopoSorter sorter) {
        if (sorter.hasSortFailureReset()) {
            this.consecutiveTopoSortFailures = 0;
        } else if (sorter.hasSortFailureIncrement()) {
            this.consecutiveTopoSortFailures++;
        }
    }

    private void copyStateFrom(DynamicTopoSorter sorter) {
        this.GFNITrigger = sorter.GFNITrigger;
        this.directTrigger = sorter.directTrigger;
        this.consecutiveTopoSortFailures = sorter.consecutiveTopoSortFailuresNew;
    }

    @Override
    public void prepareTrigger(boolean isDirectTrigger) {
        this.pendingTriggerIsDirect = isDirectTrigger;
    }

    public class DynamicTopoSorter extends DynamicSorter implements IntConsumer {
        private final DynamicTopoData parent;
        private final boolean isDirectTrigger;
        private final int consecutiveTopoSortFailures;

        private boolean directTrigger;
        private boolean GFNITrigger;
        private int consecutiveTopoSortFailuresNew;

        private IntBuffer intBuffer;

        private DynamicTopoSorter(int quadCount, DynamicTopoData parent, boolean isDirectTrigger, int consecutiveTopoSortFailures, boolean GFNITrigger, boolean directTrigger) {
            super(quadCount);
            this.parent = parent;
            this.isDirectTrigger = isDirectTrigger;
            this.consecutiveTopoSortFailures = consecutiveTopoSortFailures;
            this.consecutiveTopoSortFailuresNew = consecutiveTopoSortFailures;
            this.GFNITrigger = GFNITrigger;
            this.directTrigger = directTrigger;
        }

        private static int getAttemptsForTime(long ns) {
            return ns <= MAX_TOPO_SORT_PATIENT_TIME_NS ? PATIENT_TOPO_ATTEMPTS : REGULAR_TOPO_ATTEMPTS;
        }

        private boolean hasSortFailureReset() {
            return this.consecutiveTopoSortFailuresNew < this.consecutiveTopoSortFailures;
        }

        private boolean hasSortFailureIncrement() {
            return this.consecutiveTopoSortFailuresNew > this.consecutiveTopoSortFailures;
        }

        @Override
        public void accept(int value) {
            TranslucentData.writeQuadVertexIndexes(this.intBuffer, value);
        }

        @Override
        void writeSort(CombinedCameraPos cameraPos, boolean initial) {
            // uses a topo sort or a distance sort depending on what is enabled
            IntBuffer indexBuffer = this.getIntBuffer();

            if (this.GFNITrigger && !this.isDirectTrigger) {
                this.intBuffer = indexBuffer;
                var sortStart = initial ? 0 : System.nanoTime();
                var result = TopoGraphSorting.topoGraphSort(this, DynamicTopoData.this.quads, DynamicTopoData.this.distancesByNormal, cameraPos.getRelativeCameraPos());
                this.intBuffer = null;

                var sortTime = initial ? 0 : System.nanoTime() - sortStart;

                // if we've already failed, there's reduced patience for sorting since the
                // probability of failure and wasted compute time is higher. Initial sorting is
                // often very slow when the cpu is loaded and the JIT isn't ready yet, so it's
                // ignored here.
                if (!initial && sortTime > (this.consecutiveTopoSortFailuresNew > 0
                        ? MAX_FAILING_TOPO_SORT_TIME_NS
                        : MAX_TOPO_SORT_TIME_NS)) {
                    this.directTrigger = true;
                    this.GFNITrigger = false;
                } else if (result) {
                    // disable distance sorting because topo sort seems to be possible.
                    this.directTrigger = false;
                    this.consecutiveTopoSortFailuresNew = 0;
                } else {
                    // topo sort failure, the topo sort algorithm doesn't work on all cases

                    // gives up after a certain number of failures. it keeps GFNI triggering with
                    // topo sort on while the angle triggering is also active to maybe get a topo
                    // sort success from a different angle.
                    this.consecutiveTopoSortFailuresNew++;
                    this.directTrigger = true;
                    if (this.consecutiveTopoSortFailuresNew >= getAttemptsForTime(sortTime)) {
                        this.GFNITrigger = false;
                    }
                }
            }

            if (this.directTrigger) {
                indexBuffer.rewind();
                distanceSortDirect(indexBuffer, DynamicTopoData.this.quads, cameraPos.getRelativeCameraPos());
            }

            if (initial) {
                DynamicTopoData.this.copyStateFrom(this);
            }
        }
    }

    /**
     * Sorts the given quads by descending center distance to the camera and writes
     * the resulting order to the given index buffer.
     */
    static void distanceSortDirect(IntBuffer indexBuffer, TQuad[] quads, Vector3fc cameraPos) {
        if (quads.length <= 1) {
            TranslucentData.writeQuadVertexIndexes(indexBuffer, 0);
        } else if (RadixSort.useRadixSort(quads.length)) {
            final var keys = new int[quads.length];

            for (int q = 0; q < quads.length; q++) {
                keys[q] = ~Float.floatToRawIntBits(quads[q].getCenter().distanceSquared(cameraPos));
            }

            var indices = RadixSort.sort(keys);

            for (int i = 0; i < quads.length; i++) {
                TranslucentData.writeQuadVertexIndexes(indexBuffer, indices[i]);
            }
        } else {
            final var data = new long[quads.length];
            for (int q = 0; q < quads.length; q++) {
                float distance = quads[q].getCenter().distanceSquared(cameraPos);
                data[q] = (long) ~Float.floatToRawIntBits(distance) << 32 | q;
            }

            Arrays.sort(data);

            for (int i = 0; i < quads.length; i++) {
                TranslucentData.writeQuadVertexIndexes(indexBuffer, (int) data[i]);
            }
        }
    }

    public static DynamicTopoData fromMesh(int vertexCount,
                                           CombinedCameraPos cameraPos, TQuad[] quads, SectionPos sectionPos,
                                           GeometryPlanes geometryPlanes) {
        var distancesByNormal = geometryPlanes.prepareAndGetDistances();

        return new DynamicTopoData(sectionPos, vertexCount, quads, geometryPlanes,
                cameraPos.getAbsoluteCameraPos(), distancesByNormal);
    }
}
