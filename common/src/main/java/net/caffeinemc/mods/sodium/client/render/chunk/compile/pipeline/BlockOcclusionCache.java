package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockOcclusionCache {
    private static final int CACHE_SIZE = 512;

    private static final int ENTRY_ABSENT = -1;
    private static final int ENTRY_FALSE = 0;
    private static final int ENTRY_TRUE = 1;


    private final Object2IntLinkedOpenCustomHashMap<ShapeComparison> comparisonLookupTable;
    private final ShapeComparison cachedComparisonObject = new ShapeComparison();
    private final BlockPos.MutableBlockPos cachedPositionObject = new BlockPos.MutableBlockPos();

    public BlockOcclusionCache() {
        this.comparisonLookupTable = new Object2IntLinkedOpenCustomHashMap<>(CACHE_SIZE, 0.5F, new ShapeComparison.ShapeComparisonStrategy());
        this.comparisonLookupTable.defaultReturnValue(ENTRY_ABSENT);
    }

    /**
     * @param selfState The state of the block in the level
     * @param view The block view for this render context
     * @param selfPos The position of the block
     * @param facing The facing direction of the side to check
     * @return True if the block side facing {@param dir} is not occluded, otherwise false
     */
    public boolean shouldDrawSide(BlockState selfState, BlockGetter view, BlockPos selfPos, Direction facing) {
        BlockPos.MutableBlockPos otherPos = this.cachedPositionObject;
        otherPos.set(selfPos.getX() + facing.getStepX(), selfPos.getY() + facing.getStepY(), selfPos.getZ() + facing.getStepZ());

        BlockState otherState = view.getBlockState(otherPos);

        // Blocks can define special behavior to control whether faces are rendered.
        // This is mostly used by transparent blocks (Leaves, Glass, etc.) to not render interior faces between blocks
        // of the same type.
        if (selfState.skipRendering(otherState, facing) ||
                PlatformBlockAccess.getInstance().shouldSkipRender(view, selfState, otherState, selfPos, otherPos, facing)) {
            return false;
        }

        // If the other block is transparent, then it is unable to hide any geometry.
        if (!otherState.canOcclude()) {
            return true;
        }

        // The cull shape of the block being rendered
        VoxelShape selfShape = selfState.getFaceOcclusionShape(view, selfPos, facing);

        // If the block being rendered has an empty cull shape, intersection tests will always fail
        if (selfShape.isEmpty()) {
            return true;
        }

        // The cull shape of the block neighboring the one being rendered
        VoxelShape otherShape = otherState.getFaceOcclusionShape(view, otherPos, DirectionUtil.getOpposite(facing));

        // If the other block has an empty cull shape, then it cannot hide any geometry
        if (otherShape.isEmpty()) {
            return true;
        }

        // If both blocks use a full-cube cull shape, then they will always hide the faces between each other
        if (selfShape == Shapes.block() && otherShape == Shapes.block()) {
            return false;
        }

        // No other simplifications apply, so we need to perform a full shape comparison, which is very slow
        return this.lookup(selfShape, otherShape);
    }

    /**
     * Checks if a face of a fluid block should be rendered. It takes into account both occluding fluid face against
     * its own waterlogged block state and the neighboring block state. This is an approximation that doesn't check
     * voxel for shapes between the fluid and the neighboring block since that is handled by the fluid renderer
     * separately and more accurately using actual fluid heights. It only uses voxel shape comparison for checking
     * self-occlusion with the waterlogged block state.
     *
     * @param selfBlockState  The state of the block in the level
     * @param view            The block view for this render context
     * @param selfPos         The position of the fluid
     * @param facing          The facing direction of the side to check
     * @param fluid           The fluid state
     * @param fluidShape      The non-empty shape of the fluid
     * @return True if the fluid side facing {@param dir} is not occluded, otherwise false
     */
    public boolean shouldDrawFullBlockFluidSide(
            BlockState selfBlockState,
            BlockGetter view,
            BlockPos selfPos,
            Direction facing,
            FluidState fluid,
            VoxelShape fluidShape)
    {
        var fluidShapeIsBlock = fluidShape == Shapes.block();

        // only perform self-occlusion if the own block state can't occlude
        if (selfBlockState.canOcclude()) {
            var selfShape = selfBlockState.getFaceOcclusionShape(view, selfPos, facing);

            // only a non-empty self-shape can occlude anything
            if (!selfShape.isEmpty()) {
                // a full self-shape occludes everything
                if (selfShape == Shapes.block() && fluidShapeIsBlock) {
                    return false;
                }

                // perform occlusion of the fluid by the block it's contained in
                if (!this.lookup(fluidShape, selfShape)) {
                    return false;
                }
            }
        }

        // perform occlusion against the neighboring block
        BlockPos.MutableBlockPos otherPos = this.cachedPositionObject;
        otherPos.set(selfPos.getX() + facing.getStepX(), selfPos.getY() + facing.getStepY(), selfPos.getZ() + facing.getStepZ());
        BlockState otherState = view.getBlockState(otherPos);

        // don't render anything if the other blocks is the same fluid
        if (otherState.getFluidState() == fluid) {
            return false;
        }

        // check for special fluid occlusion behavior
        if (PlatformBlockAccess.getInstance().shouldOccludeFluid(facing.getOpposite(), otherState, fluid)) {
            return false;
        }

        // the up direction doesn't do occlusion with other block shapes
        if (facing == Direction.UP) {
            return true;
        }

        // only occlude against blocks that can potentially occlude in the first place
        if (!otherState.canOcclude()) {
            return true;
        }

        var otherShape = otherState.getFaceOcclusionShape(view, otherPos, facing.getOpposite());

        // If the other block has an empty cull shape, then it cannot hide any geometry
        if (otherShape.isEmpty()) {
            return true;
        }

        // If both blocks use a full-cube cull shape, then they will always hide the faces between each other.
        // No voxel shape comparison is done after this point because it's redundant with the later more accurate check.
        return otherShape != Shapes.block() || !fluidShapeIsBlock;
    }

    private boolean lookup(VoxelShape self, VoxelShape other) {
        ShapeComparison comparison = this.cachedComparisonObject;
        comparison.self = self;
        comparison.other = other;

        // Entries at the cache are promoted to the top of the table when accessed
        // The entries at the bottom of the table are removed when it gets too large
        return switch (this.comparisonLookupTable.getAndMoveToFirst(comparison)) {
            case ENTRY_FALSE -> false;
            case ENTRY_TRUE -> true;
            default -> this.calculate(comparison);
        };
    }

    private boolean calculate(ShapeComparison comparison) {
        boolean result = Shapes.joinIsNotEmpty(comparison.self, comparison.other, BooleanOp.ONLY_FIRST);

        // Remove entries while the table is too large
        while (this.comparisonLookupTable.size() >= CACHE_SIZE) {
            this.comparisonLookupTable.removeLastInt();
        }

        this.comparisonLookupTable.putAndMoveToFirst(comparison.copy(), (result ? ENTRY_TRUE : ENTRY_FALSE));

        return result;
    }

    private static boolean isFullShape(VoxelShape selfShape) {
        return selfShape == Shapes.block();
    }

    private static boolean isEmptyShape(VoxelShape voxelShape) {
        return voxelShape == Shapes.empty() || voxelShape.isEmpty();
    }

    private static final class ShapeComparison {
        private VoxelShape self, other;

        private ShapeComparison() {

        }

        private ShapeComparison(VoxelShape self, VoxelShape other) {
            this.self = self;
            this.other = other;
        }

        public static class ShapeComparisonStrategy implements Hash.Strategy<ShapeComparison> {
            @Override
            public int hashCode(ShapeComparison value) {
                int result = System.identityHashCode(value.self);
                result = 31 * result + System.identityHashCode(value.other);

                return result;
            }

            @Override
            public boolean equals(ShapeComparison a, ShapeComparison b) {
                if (a == b) {
                    return true;
                }

                if (a == null || b == null) {
                    return false;
                }

                return a.self == b.self && a.other == b.other;
            }
        }

        public ShapeComparison copy() {
            return new ShapeComparison(this.self, this.other);
        }
    }
}
