package net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
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
     * @param selfBlockState The state of the block in the level
     * @param view The block view for this render context
     * @param selfPos The position of the block
     * @param facing The facing direction of the side to check
     * @return True if the block side facing {@param dir} is not occluded, otherwise false
     */
    public boolean shouldDrawSide(BlockState selfBlockState, BlockGetter view, BlockPos selfPos, Direction facing) {
        BlockPos.MutableBlockPos neighborPos = this.cachedPositionObject;
        neighborPos.setWithOffset(selfPos, facing);

        // The block state of the neighbor
        BlockState neighborBlockState = view.getBlockState(neighborPos);

        // The cull shape of the neighbor between the block being rendered and it
        VoxelShape neighborShape = neighborBlockState.getFaceOcclusionShape(DirectionUtil.getOpposite(facing));

        // Minecraft enforces that if the neighbor has a full-block occlusion shape, the face is always hidden
        if (isFullShape(neighborShape)) {
            return false;
        }

        // Blocks can define special behavior to control whether their faces are rendered.
        // This is mostly used by transparent blocks (Leaves, Glass, etc.) to not render interior faces between blocks
        // of the same type.
        if (selfBlockState.skipRendering(neighborBlockState, facing)) {
            return false;
        } else if (PlatformBlockAccess.getInstance()
                .shouldSkipRender(view, selfBlockState, neighborBlockState, selfPos, neighborPos, facing)) {
            return false;
        }

        // After any custom behavior has been handled, check if the neighbor block is transparent or has an empty
        // cull shape. These blocks cannot hide any geometry.
        if (isEmptyShape(neighborShape) || !neighborBlockState.canOcclude()) {
            return true;
        }

        // The cull shape between of the block being rendered, between it and the neighboring block
        VoxelShape selfShape = selfBlockState.getFaceOcclusionShape(facing);

        // If the block being rendered has an empty cull shape, there will be no intersection with the neighboring
        // block's cull shape, so no geometry can be hidden.
        if (isEmptyShape(selfShape)) {
            return true;
        }

        // No other simplifications apply, so we need to perform a full shape comparison, which is very slow
        return this.lookup(selfShape, neighborShape);
    }

    private static boolean isFullShape(VoxelShape selfShape) {
        return selfShape == Shapes.block();
    }

    private static boolean isEmptyShape(VoxelShape voxelShape) {
        return voxelShape == Shapes.empty() || voxelShape.isEmpty();
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
