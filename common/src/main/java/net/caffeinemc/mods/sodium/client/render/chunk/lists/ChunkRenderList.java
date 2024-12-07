package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteArrayIterator;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.caffeinemc.mods.sodium.client.util.iterator.ReversibleByteArrayIterator;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public class ChunkRenderList {
    private final RenderRegion region;

    private final byte[] sectionsWithGeometry = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithGeometryCount = 0;

    private final byte[] sectionsWithSprites = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithSpritesCount = 0;

    private final byte[] sectionsWithEntities = new byte[RenderRegion.REGION_SIZE];
    private int sectionsWithEntitiesCount = 0;

    private int size;

    private int lastVisibleFrame;

    public ChunkRenderList(RenderRegion region) {
        this.region = region;
    }

    public void reset(int frame) {
        this.sectionsWithGeometryCount = 0;
        this.sectionsWithSpritesCount = 0;
        this.sectionsWithEntitiesCount = 0;

        this.size = 0;
        this.lastVisibleFrame = frame;
    }

    // clamping the relative camera position to the region bounds means there can only be very few different distances
    private static final int SORTING_HISTOGRAM_SIZE = RenderRegion.REGION_WIDTH + RenderRegion.REGION_HEIGHT + RenderRegion.REGION_LENGTH - 2;

    public void sortSections(SectionPos cameraPos, int[] sortItems) {
        var cameraX = Mth.clamp(cameraPos.getX() - this.region.getChunkX(), 0, RenderRegion.REGION_WIDTH - 1);
        var cameraY = Mth.clamp(cameraPos.getY() - this.region.getChunkY(), 0, RenderRegion.REGION_HEIGHT - 1);
        var cameraZ = Mth.clamp(cameraPos.getZ() - this.region.getChunkZ(), 0, RenderRegion.REGION_LENGTH - 1);

        int[] histogram = new int[SORTING_HISTOGRAM_SIZE];

        for (int i = 0; i < this.sectionsWithGeometryCount; i++) {
            var index = this.sectionsWithGeometry[i] & 0xFF; // makes sure the byte -> int conversion is unsigned
            var x = Math.abs(LocalSectionIndex.unpackX(index) - cameraX);
            var y = Math.abs(LocalSectionIndex.unpackY(index) - cameraY);
            var z = Math.abs(LocalSectionIndex.unpackZ(index) - cameraZ);

            var distance = x + y + z;
            histogram[distance]++;
            sortItems[i] = distance << 8 | index;
        }

        // prefix sum to calculate indexes
        for (int i = 1; i < SORTING_HISTOGRAM_SIZE; i++) {
            histogram[i] += histogram[i - 1];
        }

        for (int i = 0; i < this.sectionsWithGeometryCount; i++) {
            var item = sortItems[i];
            var distance = item >>> 8;
            this.sectionsWithGeometry[--histogram[distance]] = (byte) item;
        }
    }

    public void add(RenderSection render) {
        if (this.size >= RenderRegion.REGION_SIZE) {
            throw new ArrayIndexOutOfBoundsException("Render list is full");
        }

        this.size++;

        int index = render.getSectionIndex();
        int flags = render.getFlags();

        this.sectionsWithGeometry[this.sectionsWithGeometryCount] = (byte) index;
        this.sectionsWithGeometryCount += (flags >>> RenderSectionFlags.HAS_BLOCK_GEOMETRY) & 1;

        this.sectionsWithSprites[this.sectionsWithSpritesCount] = (byte) index;
        this.sectionsWithSpritesCount += (flags >>> RenderSectionFlags.HAS_ANIMATED_SPRITES) & 1;

        this.sectionsWithEntities[this.sectionsWithEntitiesCount] = (byte) index;
        this.sectionsWithEntitiesCount += (flags >>> RenderSectionFlags.HAS_BLOCK_ENTITIES) & 1;
    }

    public @Nullable ByteIterator sectionsWithGeometryIterator(boolean reverse) {
        if (this.sectionsWithGeometryCount == 0) {
            return null;
        }

        return new ReversibleByteArrayIterator(this.sectionsWithGeometry, this.sectionsWithGeometryCount, reverse);
    }

    public @Nullable ByteIterator sectionsWithSpritesIterator() {
        if (this.sectionsWithSpritesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithSprites, this.sectionsWithSpritesCount);
    }

    public @Nullable ByteIterator sectionsWithEntitiesIterator() {
        if (this.sectionsWithEntitiesCount == 0) {
            return null;
        }

        return new ByteArrayIterator(this.sectionsWithEntities, this.sectionsWithEntitiesCount);
    }

    public int getSectionsWithGeometryCount() {
        return this.sectionsWithGeometryCount;
    }

    public int getSectionsWithSpritesCount() {
        return this.sectionsWithSpritesCount;
    }

    public int getSectionsWithEntitiesCount() {
        return this.sectionsWithEntitiesCount;
    }

    public int getLastVisibleFrame() {
        return this.lastVisibleFrame;
    }

    public RenderRegion getRegion() {
        return this.region;
    }

    public int size() {
        return this.size;
    }
}
