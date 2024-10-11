package net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.JobEffortEstimator;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.CombinedCameraPos;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;

/**
 * Build tasks are immutable jobs (with optional prioritization) which contain all the necessary state to perform
 * chunk mesh updates or quad sorting off the main thread.
 *
 * When a task is constructed on the main thread, it should copy all the state it requires in order to complete the task
 * without further synchronization. The task will then be scheduled for async execution on a thread pool.
 *
 * After the task completes, it returns a "build result" which contains any computed data that needs to be handled
 * on the main thread.
 */
public abstract class ChunkBuilderTask<OUTPUT extends BuilderTaskOutput> implements CombinedCameraPos {
    protected final RenderSection render;
    protected final int submitTime;
    protected final Vector3dc absoluteCameraPos;
    protected final Vector3fc cameraPos;

    private long estimatedDuration;

    /**
     * Constructs a new build task for the given chunk and converts the absolute camera position to a relative position. While the absolute position is stored as a double vector, the relative position is stored as a float vector.
     * 
     * @param render            The chunk to build
     * @param time              The frame in which this task was created
     * @param absoluteCameraPos The absolute position of the camera
     */
    public ChunkBuilderTask(RenderSection render, int time, Vector3dc absoluteCameraPos) {
        this.render = render;
        this.submitTime = time;
        this.absoluteCameraPos = absoluteCameraPos;
        this.cameraPos = new Vector3f(
                (float) (absoluteCameraPos.x() - (double) render.getOriginX()),
                (float) (absoluteCameraPos.y() - (double) render.getOriginY()),
                (float) (absoluteCameraPos.z() - (double) render.getOriginZ()));
    }

    /**
     * Executes the given build task asynchronously from the calling thread. The implementation should be careful not
     * to access or modify global mutable state.
     *
     * @param context            The context to use for building this chunk
     * @param cancellationToken The cancellation source which can be used to query if the task is cancelled
     * @return The build result of this task, containing any data which needs to be uploaded on the main-thread, or null
     *         if the task was cancelled.
     */
    public abstract OUTPUT execute(ChunkBuildContext context, CancellationToken cancellationToken);

    public abstract long getEffort();

    public void estimateDurationWith(JobEffortEstimator estimator) {
        this.estimatedDuration = estimator.estimateJobDuration(this.getClass(), this.getEffort());
    }

    public long getEstimatedDuration() {
        return this.estimatedDuration;
    }

    @Override
    public Vector3fc getRelativeCameraPos() {
        return this.cameraPos;
    }

    @Override
    public Vector3dc getAbsoluteCameraPos() {
        return this.absoluteCameraPos;
    }
}
