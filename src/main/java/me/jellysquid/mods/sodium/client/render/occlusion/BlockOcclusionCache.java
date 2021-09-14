package me.jellysquid.mods.sodium.client.render.occlusion;

import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.SideShapeType;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class BlockOcclusionCache {
    private static final byte UNCACHED_VALUE = (byte) 127;

    private final Object2ByteLinkedOpenHashMap<CachedOcclusionShapeTest> map;
    private final CachedOcclusionShapeTest cachedTest = new CachedOcclusionShapeTest();
    private final BlockPos.Mutable cpos = new BlockPos.Mutable();

    public BlockOcclusionCache() {
        this.map = new Object2ByteLinkedOpenHashMap<>(2048, 0.5F);
        this.map.defaultReturnValue(UNCACHED_VALUE);
    }

    /**
     * @param selfState The state of the block in the world
     * @param view The world view for this render context
     * @param pos The position of the block
     * @param facing The facing direction of the side to check
     * @return True if the block side facing {@param dir} is not occluded, otherwise false
     */
    public boolean shouldDrawSide(BlockState selfState, BlockView view, BlockPos pos, Direction facing) {
        BlockPos.Mutable adjPos = this.cpos;
        adjPos.set(pos.getX() + facing.getOffsetX(), pos.getY() + facing.getOffsetY(), pos.getZ() + facing.getOffsetZ());

        BlockState adjState = view.getBlockState(adjPos);

        if (selfState.isSideInvisible(adjState, facing)) {
            return false;
        } else if (adjState.isOpaque()) {
            VoxelShape selfShape = selfState.getCullingFace(view, pos, facing);
            VoxelShape adjShape = adjState.getCullingFace(view, adjPos, facing.getOpposite());

            if (selfShape == VoxelShapes.fullCube() && adjShape == VoxelShapes.fullCube()) {
                return false;
            }

            if (selfShape.isEmpty()) {
                if (adjShape.isEmpty()){
                    return true; //example: top face of potted plants if top slab is placed above
                }
                else if (!adjState.isSideSolid(view,pos,facing.getOpposite(), SideShapeType.FULL)){
                    return true; //example: face of potted plants rendered if top stair placed above
                }
            }

            return this.calculate(selfShape, adjShape);
        } else {
            return true;
        }
    }

    private boolean calculate(VoxelShape selfShape, VoxelShape adjShape) {
        CachedOcclusionShapeTest cache = this.cachedTest;
        cache.a = selfShape;
        cache.b = adjShape;
        cache.updateHash();

        byte cached = this.map.getByte(cache);

        if (cached != UNCACHED_VALUE) {
            return cached == 1;
        }

        boolean ret = VoxelShapes.matchesAnywhere(selfShape, adjShape, BooleanBiFunction.ONLY_FIRST);

        this.map.put(cache.copy(), (byte) (ret ? 1 : 0));

        if (this.map.size() > 2048) {
            this.map.removeLastByte();
        }

        return ret;
    }

    private static final class CachedOcclusionShapeTest {
        private VoxelShape a, b;
        private int hashCode;

        private CachedOcclusionShapeTest() {

        }

        private CachedOcclusionShapeTest(VoxelShape a, VoxelShape b, int hashCode) {
            this.a = a;
            this.b = b;
            this.hashCode = hashCode;
        }

        public void updateHash() {
            int result = System.identityHashCode(this.a);
            result = 31 * result + System.identityHashCode(this.b);

            this.hashCode = result;
        }

        public CachedOcclusionShapeTest copy() {
            return new CachedOcclusionShapeTest(this.a, this.b, this.hashCode);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CachedOcclusionShapeTest that) {
                return this.a == that.a &&
                        this.b == that.b;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
