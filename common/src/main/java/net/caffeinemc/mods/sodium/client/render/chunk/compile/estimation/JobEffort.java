package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

public record JobEffort(Class<?> category, long duration, long effort) implements Linear2DEstimator.DataPair<Class<?>> {
    public static JobEffort untilNowWithEffort(Class<?> effortType, long start, long effort) {
        return new JobEffort(effortType,System.nanoTime() - start, effort);
    }

    @Override
    public long x() {
        return this.effort;
    }

    @Override
    public long y() {
        return this.duration;
    }
}
