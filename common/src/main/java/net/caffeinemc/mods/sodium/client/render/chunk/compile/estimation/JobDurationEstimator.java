package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

public class JobDurationEstimator extends CategoryFactorEstimator<Class<?>> {
    public static final float NEW_DATA_FACTOR = 0.07f;
    private static final long INITIAL_JOB_DURATION_ESTIMATE = 5_000_000L;

    public JobDurationEstimator() {
        super(NEW_DATA_FACTOR, INITIAL_JOB_DURATION_ESTIMATE);
    }

    public long estimateJobDuration(Class<?> jobType, long effort) {
        return this.estimateAWithB(jobType, effort);
    }
}
