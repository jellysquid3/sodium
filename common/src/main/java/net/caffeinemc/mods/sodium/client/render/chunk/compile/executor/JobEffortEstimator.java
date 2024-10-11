package net.caffeinemc.mods.sodium.client.render.chunk.compile.executor;

import it.unimi.dsi.fastutil.objects.*;

// TODO: deal with maximum number of uploads per frame
// TODO: implement per-thread pending upload size limit with a simple semaphore? also see discussion about more complicated allocation scheme with small and large threads: https://discord.com/channels/602796788608401408/651120262129123330/1294158402859171870
public class JobEffortEstimator {
    public static final float NEW_DATA_FACTOR = 0.01f;

    Reference2FloatMap<Class<?>> durationPerEffort = new Reference2FloatArrayMap<>();
    Reference2ReferenceMap<Class<?>, FrameDataAggregation> newData = new Reference2ReferenceArrayMap<>();

    private static class FrameDataAggregation {
        private long durationSum;
        private long effortSum;

        public void addDataPoint(long duration, long effort) {
            this.durationSum += duration;
            this.effortSum += effort;
        }

        public void reset() {
            this.durationSum = 0;
            this.effortSum = 0;
        }

        public float getEffortFactor() {
            return (float) this.durationSum / this.effortSum;
        }
    }

    public void addJobEffort(JobEffort jobEffort) {
        var category = jobEffort.category();
        if (this.newData.containsKey(category)) {
            this.newData.get(category).addDataPoint(jobEffort.duration(), jobEffort.effort());
        } else {
            var frameData = new FrameDataAggregation();
            frameData.addDataPoint(jobEffort.duration(), jobEffort.effort());
            this.newData.put(category, frameData);
        }
    }

    public void flushNewData() {
        this.newData.forEach((category, frameData) -> {
            var newFactor = frameData.getEffortFactor();
            if (Float.isNaN(newFactor)) {
                return;
            }
            if (this.durationPerEffort.containsKey(category)) {
                var oldFactor = this.durationPerEffort.getFloat(category);
                var newValue = oldFactor * (1 - NEW_DATA_FACTOR) + newFactor * NEW_DATA_FACTOR;
                this.durationPerEffort.put(category, newValue);
            } else {
                this.durationPerEffort.put(category, newFactor);
            }
            frameData.reset();
        });
    }

    public long estimateJobDuration(Class<?> category, long effort) {
        if (this.durationPerEffort.containsKey(category)) {
            return (long) (this.durationPerEffort.getFloat(category) * effort);
        } else {
            return 10_000_000L; // 10ms as initial guess
        }
    }
}
