package me.jellysquid.mods.sodium.client.render.chunk.compile;

import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.common.util.collections.QueueDrainingIterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkBuilder {
    /**
     * The maximum number of jobs that can be queued for a given worker thread.
     */
    private static final int TASK_QUEUE_LIMIT_PER_WORKER = 2;

    private static final Logger LOGGER = LogManager.getLogger("ChunkBuilder");

    private final Deque<WrappedTask> buildQueue = new ConcurrentLinkedDeque<>();

    private final Object jobNotifier = new Object();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> threads = new ArrayList<>();
    /**
     * Amount of threads which are currently blocked waiting on {@link #jobNotifier}. Synchronized via the same object.
     */
    private int idleThreads;

    private World world;
    private BlockRenderPassManager renderPassManager;

    private final int limitThreads;
    private final ChunkVertexType vertexType;

    private final Queue<ChunkBuildResult> deferredResultQueue = new ConcurrentLinkedDeque<>();

    public ChunkBuilder(ChunkVertexType vertexType) {
        this.vertexType = vertexType;
        this.limitThreads = getOptimalThreadCount();
    }

    /**
     * Returns the remaining number of build tasks which should be scheduled this frame. If an attempt is made to
     * spawn more tasks than the budget allows, it will block until resources become available.
     */
    public int getSchedulingBudget() {
        return Math.max(0, (this.limitThreads * TASK_QUEUE_LIMIT_PER_WORKER) - this.buildQueue.size() - this.deferredResultQueue.size());
    }

    /**
     * Spawns a number of work-stealing threads to process results in the build queue. If the builder is already
     * running, this method does nothing and exits.
     */
    public void startWorkers() {
        if (this.running.getAndSet(true)) {
            return;
        }

        if (!this.threads.isEmpty()) {
            throw new IllegalStateException("Threads are still alive while in the STOPPED state");
        }

        MinecraftClient client = MinecraftClient.getInstance();

        for (int i = 0; i < this.limitThreads; i++) {
            ChunkBuildBuffers buffers = new ChunkBuildBuffers(this.vertexType, this.renderPassManager);
            ChunkRenderCacheLocal pipeline = new ChunkRenderCacheLocal(client, this.world);

            WorkerRunnable worker = new WorkerRunnable(buffers, pipeline);

            Thread thread = new Thread(worker, "Chunk Render Task Executor #" + i);
            thread.setPriority(Math.max(0, Thread.NORM_PRIORITY - 2));
            thread.start();

            this.threads.add(thread);
        }

        LOGGER.info("Started {} worker threads", this.threads.size());
    }

    /**
     * Notifies all worker threads to stop and blocks until all workers terminate. After the workers have been shut
     * down, all tasks are cancelled and the pending queues are cleared. If the builder is already stopped, this
     * method does nothing and exits.
     */
    public void stopWorkers() {
        if (!this.running.getAndSet(false)) {
            return;
        }

        if (this.threads.isEmpty()) {
            throw new IllegalStateException("No threads are alive but the executor is in the RUNNING state");
        }

        LOGGER.info("Stopping worker threads");

        // Notify all worker threads to wake up, where they will then terminate
        synchronized (this.jobNotifier) {
            this.jobNotifier.notifyAll();
        }

        // Wait for every remaining thread to terminate
        for (Thread thread : this.threads) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }

        this.threads.clear();

        // Delete any queued tasks and resources attached to them
        for (WrappedTask job : this.buildQueue) {
            job.future.cancel(true);
            job.task.releaseResources();
        }

        // Delete any results in the deferred queue
        while (!this.deferredResultQueue.isEmpty()) {
            this.deferredResultQueue.remove()
                    .delete();
        }

        this.buildQueue.clear();

        this.world = null;
    }

    public CompletableFuture<ChunkBuildResult> schedule(ChunkRenderBuildTask task) {
        if (!this.running.get()) {
            throw new IllegalStateException("Executor is stopped");
        }

        WrappedTask job = new WrappedTask(task);

        this.buildQueue.add(job);

        synchronized (this.jobNotifier) {
            this.jobNotifier.notify();
        }

        return job.future;
    }

    /**
     * @return True if all background work has been completed
     */
    public boolean isIdle() {
        if (!this.isBuildQueueEmpty()) {
            return false;
        }
        synchronized (this.jobNotifier) {
            return this.idleThreads >= this.threads.size();
        }
    }

    /**
     * @return True if the build queue is empty
     */
    public boolean isBuildQueueEmpty() {
        return this.buildQueue.isEmpty();
    }

    /**
     * Initializes this chunk builder for the given world. If the builder is already running (which can happen during
     * a world teleportation event), the worker threads will first be stopped and all pending tasks will be discarded
     * before being started again.
     * @param world The world instance
     * @param renderPassManager The render pass manager used for the world
     */
    public void init(ClientWorld world, BlockRenderPassManager renderPassManager) {
        if (world == null) {
            throw new NullPointerException("World is null");
        }

        this.stopWorkers();

        this.world = world;
        this.renderPassManager = renderPassManager;

        this.startWorkers();
    }

    /**
     * Returns the "optimal" number of threads to be used for chunk build tasks. This is always at least one thread,
     * but can be up to the number of available processor threads on the system.
     */
    private static int getOptimalThreadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public CompletableFuture<Void> scheduleDeferred(ChunkRenderBuildTask task) {
        return this.schedule(task)
                .thenAccept(this.deferredResultQueue::add);
    }

    public Iterator<ChunkBuildResult> createDeferredBuildResultDrain() {
        return new QueueDrainingIterator<>(this.deferredResultQueue);
    }

    private class WorkerRunnable implements Runnable {
        private final AtomicBoolean running = ChunkBuilder.this.running;

        // The re-useable build buffers used by this worker for building chunk meshes
        private final ChunkBuildBuffers bufferCache;

        // Making this thread-local provides a small boost to performance by avoiding the overhead in synchronizing
        // caches between different CPU cores
        private final ChunkRenderCacheLocal cache;

        public WorkerRunnable(ChunkBuildBuffers bufferCache, ChunkRenderCacheLocal cache) {
            this.bufferCache = bufferCache;
            this.cache = cache;
        }

        @Override
        public void run() {
            // Run until the chunk builder shuts down
            while (this.running.get()) {
                WrappedTask job = this.getNextJob();

                if (job == null) {
                    continue;
                }

                this.processJob(job);
            }

            this.bufferCache.destroy();
        }

        private void processJob(WrappedTask job) {
            ChunkBuildResult result;

            try {
                if (job.isCancelled()) {
                    return;
                }

                // Perform the build task with this worker's local resources and obtain the result
                result = job.task.performBuild(this.cache, this.bufferCache, job);
            } catch (Exception e) {
                // Propagate any exception from chunk building
                job.future.completeExceptionally(e);
                return;
            } finally {
                job.task.releaseResources();
            }

            // The result can be null if the task is cancelled
            if (result != null) {
                // Notify the future that the result is now available
                job.future.complete(result);
            } else if (!job.isCancelled()) {
                // If the job wasn't cancelled and no result was produced, we've hit a bug
                job.future.completeExceptionally(new RuntimeException("No result was produced by the task"));
            }
        }

        /**
         * Returns the next task which this worker can work on or blocks until one becomes available. If no tasks are
         * currently available, it will wait on {@link ChunkBuilder#jobNotifier} field until notified.
         */
        private WrappedTask getNextJob() {
            WrappedTask job = ChunkBuilder.this.buildQueue.poll();

            if (job == null) {
                synchronized (ChunkBuilder.this.jobNotifier) {
                    ChunkBuilder.this.idleThreads++;
                    try {
                        ChunkBuilder.this.jobNotifier.wait();
                    } catch (InterruptedException ignored) {
                    } finally {
                        ChunkBuilder.this.idleThreads--;
                    }
                }
            }

            return job;
        }
    }

    private static class WrappedTask implements CancellationSource {
        private final ChunkRenderBuildTask task;
        private final CompletableFuture<ChunkBuildResult> future;

        private WrappedTask(ChunkRenderBuildTask task) {
            this.task = task;
            this.future = new CompletableFuture<>();
        }

        @Override
        public boolean isCancelled() {
            return this.future.isCancelled();
        }
    }
}
