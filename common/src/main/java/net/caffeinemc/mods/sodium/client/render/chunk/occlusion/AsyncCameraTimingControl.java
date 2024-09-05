package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public class AsyncCameraTimingControl {
    private static final double ENTER_SYNC_STEP_THRESHOLD = 16;
    private static final double EXIT_SYNC_STEP_THRESHOLD = 10;

    private Vec3 previousPosition;
    private boolean isSyncRendering = false;

    public boolean getShouldRenderSync(Camera camera) {
        var cameraPosition = camera.getPosition();

        if (this.previousPosition == null) {
            this.previousPosition = cameraPosition;
            return true;
        }

        // if the camera moved too much, use sync rendering until it stops
        var distance = Math.max(
                Math.abs(cameraPosition.x - this.previousPosition.x),
                Math.max(
                        Math.abs(cameraPosition.y - this.previousPosition.y),
                        Math.abs(cameraPosition.z - this.previousPosition.z)
                )
        );
        if (this.isSyncRendering && distance <= EXIT_SYNC_STEP_THRESHOLD) {
            this.isSyncRendering = false;
        } else if (!this.isSyncRendering && distance >= ENTER_SYNC_STEP_THRESHOLD) {
            this.isSyncRendering = true;
        }

        this.previousPosition = cameraPosition;

        return this.isSyncRendering;
    }
}
