package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class AsyncRenderTask<T> implements Callable<T> {
    protected final Viewport viewport;
    protected final float buildDistance;
    protected final int frame;

    private Future<T> future;
    private volatile boolean started;

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

    public boolean hasStarted() {
        return this.started;
    }

    public int getFrame() {
        return this.frame;
    }

    public void cancelImmediately() {
        this.future.cancel(true);
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
        this.started = true;
        return this.runTask();
    }

    protected abstract T runTask();
}
