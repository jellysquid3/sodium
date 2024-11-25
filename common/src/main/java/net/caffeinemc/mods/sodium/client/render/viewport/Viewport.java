package net.caffeinemc.mods.sodium.client.render.viewport;

import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.joml.Vector3d;

public final class Viewport {
    private final Frustum frustum;
    private final CameraTransform transform;

    private final SectionPos sectionCoords;
    private final BlockPos blockCoords;

    public Viewport(Frustum frustum, Vector3d position) {
        this.frustum = frustum;
        this.transform = new CameraTransform(position.x, position.y, position.z);

        this.sectionCoords = SectionPos.of(
                SectionPos.posToSectionCoord(position.x),
                SectionPos.posToSectionCoord(position.y),
                SectionPos.posToSectionCoord(position.z)
        );

        this.blockCoords = BlockPos.containing(position.x, position.y, position.z);
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSizeX, float floatSizeY, float floatSizeZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testAab(
                floatOriginX - floatSizeX,
                floatOriginY - floatSizeY,
                floatOriginZ - floatSizeZ,

                floatOriginX + floatSizeX,
                floatOriginY + floatSizeY,
                floatOriginZ + floatSizeZ
        );
    }

    public int getBoxIntersection(int intOriginX, int intOriginY, int intOriginZ, float floatSizeX, float floatSizeY, float floatSizeZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.intersectAab(
                floatOriginX - floatSizeX,
                floatOriginY - floatSizeY,
                floatOriginZ - floatSizeZ,

                floatOriginX + floatSizeX,
                floatOriginY + floatSizeY,
                floatOriginZ + floatSizeZ
        );
    }

    public CameraTransform getTransform() {
        return this.transform;
    }

    public SectionPos getChunkCoord() {
        return this.sectionCoords;
    }

    public BlockPos getBlockCoord() {
        return this.blockCoords;
    }
}
