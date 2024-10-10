package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class AsyncRenderTask<T> implements Callable<T>, CancellationToken {
    protected final Viewport viewport;
    protected final float buildDistance;
    protected final int frame;

    private Future<T> future;
    private volatile int state;

    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int CANCELLED = 2;

    protected AsyncRenderTask(Viewport viewport, float buildDistance, int frame) {
        this.viewport = viewport;
        this.buildDistance = buildDistance;
        this.frame = frame;
    }

    public void submitTo(ExecutorService executor) {
        this.future = executor.submit(this);
    }

    public boolean isDone() {
        return this.future.isDone();
    }

    public int getFrame() {
        return this.frame;
    }

    public boolean isCancelled() {
        return this.state == CANCELLED;
    }

    @Override
    public void setCancelled() {
        this.state = CANCELLED;
    }

    public boolean cancelIfNotStarted() {
        if (this.state == PENDING) {
            this.setCancelled();
            return true;
        }
        return false;
    }

    public T getResult() {
        try {
            return this.future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get result of render task", e);
        }
    }

    @Override
    public T call() throws Exception {
        if (this.state == CANCELLED) {
            return null;
        }
        this.state = RUNNING;
        return this.runTask();
    }

    protected abstract T runTask();
}
