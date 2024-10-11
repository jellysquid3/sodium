package net.caffeinemc.mods.sodium.client.render.chunk.compile.executor;

public record JobEffort(Class<?> category, long duration, long effort) {
    public static JobEffort untilNowWithEffort(Class<?> effortType, long start, long effort) {
        return new JobEffort(effortType,System.nanoTime() - start, effort);
    }
}
