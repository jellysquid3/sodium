package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;

import java.util.Map;

public class JobDurationEstimator extends Linear2DEstimator<Class<?>> {
    public static final int INITIAL_SAMPLE_TARGET = 100;
    public static final float NEW_DATA_RATIO = 0.05f;
    private static final long INITIAL_JOB_DURATION_ESTIMATE = 5_000_000L; // 5ms

    public JobDurationEstimator() {
        super(NEW_DATA_RATIO, INITIAL_SAMPLE_TARGET, INITIAL_JOB_DURATION_ESTIMATE);
    }

    public long estimateJobDuration(Class<?> jobType, long effort) {
        return this.predict(jobType, effort);
    }

    @Override
    protected <T> Map<Class<?>, T> createMap() {
        return new Reference2ReferenceArrayMap<>();
    }
}
