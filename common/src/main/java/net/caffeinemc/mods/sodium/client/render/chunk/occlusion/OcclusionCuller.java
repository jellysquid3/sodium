package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.collections.DoubleBufferedQueue;
import net.caffeinemc.mods.sodium.client.util.collections.ReadQueue;
import net.caffeinemc.mods.sodium.client.util.collections.WriteQueue;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class OcclusionCuller {
    private final Long2ReferenceMap<RenderSection> sections;
    private final Level level;

    private final DoubleBufferedQueue<RenderSection> queue = new DoubleBufferedQueue<>();

    public OcclusionCuller(Long2ReferenceMap<RenderSection> sections, Level level) {
        this.sections = sections;
        this.level = level;
    }

    public void findVisible(Visitor visitor,
                            Viewport viewport,
                            float searchDistance,
                            boolean useOcclusionCulling,
                            int frame)
    {
        final var queues = this.queue;
        queues.reset();

        this.init(visitor, queues.write(), viewport, searchDistance, useOcclusionCulling, frame);

        while (queues.flip()) {
            processQueue(visitor, viewport, searchDistance, useOcclusionCulling, frame, queues.read(), queues.write());
        }

        this.addNearbySections(visitor, viewport, searchDistance, frame);
    }

    private static void processQueue(Visitor visitor,
                                     Viewport viewport,
                                     float searchDistance,
                                     boolean useOcclusionCulling,
                                     int frame,
                                     ReadQueue<RenderSection> readQueue,
                                     WriteQueue<RenderSection> writeQueue)
    {
        RenderSection section;

        while ((section = readQueue.dequeue()) != null) {
            if (!isSectionVisible(section, viewport, searchDistance)) {
                continue;
            }

            visitor.visit(section);

            int connections;

            {
                if (useOcclusionCulling) {
                    var sectionVisibilityData = section.getVisibilityData();

                    // occlude paths through the section if it's being viewed at an angle where
                    // the other side can't possibly be seen
                    sectionVisibilityData &= getAngleVisibilityMask(viewport, section);

                    // When using occlusion culling, we can only traverse into neighbors for which there is a path of
                    // visibility through this chunk. This is determined by taking all the incoming paths to this chunk and
                    // creating a union of the outgoing paths from those.
                    connections = VisibilityEncoding.getConnections(sectionVisibilityData, section.getIncomingDirections());
                } else {
                    // Not using any occlusion culling, so traversing in any direction is legal.
                    connections = GraphDirectionSet.ALL;
                }

                // We can only traverse *outwards* from the center of the graph search, so mask off any invalid
                // directions.
                connections &= getOutwardDirections(viewport.getChunkCoord(), section);
            }

            visitNeighbors(writeQueue, section, connections, frame);
        }
    }

    private static final long UP_DOWN_OCCLUDED = (1L << VisibilityEncoding.bit(GraphDirection.DOWN, GraphDirection.UP)) | (1L << VisibilityEncoding.bit(GraphDirection.UP, GraphDirection.DOWN));
    private static final long NORTH_SOUTH_OCCLUDED = (1L << VisibilityEncoding.bit(GraphDirection.NORTH, GraphDirection.SOUTH)) | (1L << VisibilityEncoding.bit(GraphDirection.SOUTH, GraphDirection.NORTH));
    private static final long WEST_EAST_OCCLUDED = (1L << VisibilityEncoding.bit(GraphDirection.WEST, GraphDirection.EAST)) | (1L << VisibilityEncoding.bit(GraphDirection.EAST, GraphDirection.WEST));

    private static long getAngleVisibilityMask(Viewport viewport, RenderSection section) {
        var transform = viewport.getTransform();
        var dx = Math.abs(transform.x - section.getCenterX());
        var dy = Math.abs(transform.y - section.getCenterY());
        var dz = Math.abs(transform.z - section.getCenterZ());

        var angleOcclusionMask = 0L;
        if (dx > dy || dz > dy) {
            angleOcclusionMask |= UP_DOWN_OCCLUDED;
        }
        if (dx > dz || dy > dz) {
            angleOcclusionMask |= NORTH_SOUTH_OCCLUDED;
        }
        if (dy > dx || dz > dx) {
            angleOcclusionMask |= WEST_EAST_OCCLUDED;
        }

        return ~angleOcclusionMask;
    }

    private static boolean isSectionVisible(RenderSection section, Viewport viewport, float maxDistance) {
        return isWithinRenderDistance(viewport.getTransform(), section, maxDistance) && isWithinFrustum(viewport, section);
    }

    private static void visitNeighbors(final WriteQueue<RenderSection> queue, RenderSection section, int outgoing, int frame) {
        // Only traverse into neighbors which are actually present.
        // This avoids a null-check on each invocation to enqueue, and since the compiler will see that a null
        // is never encountered (after profiling), it will optimize it away.
        outgoing &= section.getAdjacentMask();

        // Check if there are any valid connections left, and if not, early-exit.
        if (outgoing == GraphDirectionSet.NONE) {
            return;
        }

        // This helps the compiler move the checks for some invariants upwards.
        queue.ensureCapacity(6);

        if (GraphDirectionSet.contains(outgoing, GraphDirection.DOWN)) {
            visitNode(queue, section.adjacentDown, GraphDirectionSet.of(GraphDirection.UP), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.UP)) {
            visitNode(queue, section.adjacentUp, GraphDirectionSet.of(GraphDirection.DOWN), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.NORTH)) {
            visitNode(queue, section.adjacentNorth, GraphDirectionSet.of(GraphDirection.SOUTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.SOUTH)) {
            visitNode(queue, section.adjacentSouth, GraphDirectionSet.of(GraphDirection.NORTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.WEST)) {
            visitNode(queue, section.adjacentWest, GraphDirectionSet.of(GraphDirection.EAST), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.EAST)) {
            visitNode(queue, section.adjacentEast, GraphDirectionSet.of(GraphDirection.WEST), frame);
        }
    }

    private static void visitNode(final WriteQueue<RenderSection> queue, @NotNull RenderSection render, int incoming, int frame) {
        if (render.getLastVisibleFrame() != frame) {
            // This is the first time we are visiting this section during the given frame, so we must
            // reset the state.
            render.setLastVisibleFrame(frame);
            render.setIncomingDirections(GraphDirectionSet.NONE);

            queue.enqueue(render);
        }

        render.addIncomingDirections(incoming);
    }

    private static int getOutwardDirections(SectionPos origin, RenderSection section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.getX() ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.getX() ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.getY() ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.getY() ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.getZ() ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.getZ() ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    private static boolean isWithinRenderDistance(CameraTransform camera, RenderSection section, float maxDistance) {
        // origin point of the chunk's bounding box (in view space)
        int ox = section.getOriginX() - camera.intX;
        int oy = section.getOriginY() - camera.intY;
        int oz = section.getOriginZ() - camera.intZ;

        // coordinates of the point to compare (in view space)
        // this is the closest point within the bounding box to the center (0, 0, 0)
        // the bounding box is expanded by 1 block in each direction due to the maximum allowed size of block models.
        float dx = nearestToZero(ox - 1, ox + 17) - camera.fracX;
        float dy = nearestToZero(oy - 1, oy + 17) - camera.fracY;
        float dz = nearestToZero(oz - 1, oz + 17) - camera.fracZ;

        // vanilla's "cylindrical fog" algorithm
        // max(length(distance.xz), abs(distance.y))
        return (((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance);
    }

    @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
    private static int nearestToZero(int min, int max) {
        // this compiles to slightly better code than Math.min(Math.max(0, min), max)
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }

    // The bounding box of a chunk section must be large enough to contain all possible geometry within it. Block models
    // can extend outside a block volume by +/- 1.0 blocks on all axis. Additionally, we make use of a small epsilon
    // to deal with floating point imprecision during a frustum check (see GH#2132).
    private static final float CHUNK_SECTION_RADIUS = 8.0f /* chunk bounds */;
    private static final float CHUNK_SECTION_SIZE = CHUNK_SECTION_RADIUS + 1.0f /* maximum model extent */ + 0.125f /* epsilon */;

    public static boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
                CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE);
    }

    // this bigger chunk section size is only used for frustum-testing nearby sections with large models
    private static final float CHUNK_SECTION_SIZE_NEARBY = CHUNK_SECTION_RADIUS + 2.0f /* bigger model extent */ + 0.125f /* epsilon */;
    
    public static boolean isWithinNearbySectionFrustum(Viewport viewport, RenderSection section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
                CHUNK_SECTION_SIZE_NEARBY, CHUNK_SECTION_SIZE_NEARBY, CHUNK_SECTION_SIZE_NEARBY);
    }

    // This method visits sections near the origin that are not in the path of the graph traversal
    // but have bounding boxes that may intersect with the frustum. It does this additional check
    // for all neighboring, even diagonally neighboring, sections around the origin to render them
    // if their extended bounding box is visible, and they may render large models that extend
    // outside the 16x16x16 base volume of the section.
    private void addNearbySections(Visitor visitor, Viewport viewport, float searchDistance, int frame) {
        var origin = viewport.getChunkCoord();
        var originX = origin.getX();
        var originY = origin.getY();
        var originZ = origin.getZ();

        for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -1; dy <= 1; dy++) {
                for (var dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    var section = this.getRenderSection(originX + dx, originY + dy, originZ + dz);

                    // additionally render not yet visited but visible sections
                    if (section != null && section.getLastVisibleFrame() != frame && isWithinNearbySectionFrustum(viewport, section)) {
                        // reset state on first visit, but don't enqueue
                        section.setLastVisibleFrame(frame);

                        visitor.visit(section);
                    }
                }
            }
        }
    }

    private void init(Visitor visitor,
                      WriteQueue<RenderSection> queue,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.getY() < this.level.getMinSectionY()) {
            // below the level
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.level.getMinSectionY(), GraphDirection.DOWN);
        } else if (origin.getY() > this.level.getMaxSectionY()) {
            // above the level
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.level.getMaxSectionY(), GraphDirection.UP);
        } else {
            this.initWithinWorld(visitor, queue, viewport, useOcclusionCulling, frame);
        }
    }

    private void initWithinWorld(Visitor visitor, WriteQueue<RenderSection> queue, Viewport viewport, boolean useOcclusionCulling, int frame) {
        var origin = viewport.getChunkCoord();
        var section = this.getRenderSection(origin.getX(), origin.getY(), origin.getZ());

        if (section == null) {
            return;
        }

        section.setLastVisibleFrame(frame);
        section.setIncomingDirections(GraphDirectionSet.NONE);

        visitor.visit(section);

        int outgoing;

        if (useOcclusionCulling) {
            // Since the camera is located inside this chunk, there are no "incoming" directions. So we need to instead
            // find any possible paths out of this chunk and enqueue those neighbors.
            outgoing = VisibilityEncoding.getConnections(section.getVisibilityData());
        } else {
            // Occlusion culling is disabled, so we can traverse into any neighbor.
            outgoing = GraphDirectionSet.ALL;
        }

        visitNeighbors(queue, section, outgoing, frame);
    }

    // Enqueues sections that are inside the viewport using diamond spiral iteration to avoid sorting and ensure a
    // consistent order. Innermost layers are enqueued first. Within each layer, iteration starts at the northernmost
    // section and proceeds counterclockwise (N->W->S->E).
    private void initOutsideWorldHeight(WriteQueue<RenderSection> queue,
                                        Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        var origin = viewport.getChunkCoord();
        var radius = Mth.floor(searchDistance / 16.0f);

        // Layer 0
        this.tryVisitNode(queue, origin.getX(), height, origin.getZ(), direction, frame, viewport);

        // Complete layers, excluding layer 0
        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }

        // Incomplete layers
        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }
    }

    private void tryVisitNode(WriteQueue<RenderSection> queue, int x, int y, int z, int direction, int frame, Viewport viewport) {
        RenderSection section = this.getRenderSection(x, y, z);

        if (section == null || !isWithinFrustum(viewport, section)) {
            return;
        }

        visitNode(queue, section, GraphDirectionSet.of(direction), frame);
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sections.get(SectionPos.asLong(x, y, z));
    }

    public interface Visitor {
        void visit(RenderSection section);
    }
}
