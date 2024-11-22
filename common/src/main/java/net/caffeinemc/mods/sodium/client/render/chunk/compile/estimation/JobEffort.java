package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

public record JobEffort(Class<?> category, long duration, long effort) implements CategoryFactorEstimator.BatchEntry<Class<?>> {
    public static JobEffort untilNowWithEffort(Class<?> effortType, long start, long effort) {
        return new JobEffort(effortType,System.nanoTime() - start, effort);
    }

    @Override
    public Class<?> getCategory() {
        return this.category;
    }

    @Override
    public long getA() {
        return this.duration;
    }

    @Override
    public long getB() {
        return this.effort;
    }
}
