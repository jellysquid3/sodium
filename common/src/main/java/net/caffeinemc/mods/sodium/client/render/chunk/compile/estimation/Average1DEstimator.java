package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import net.caffeinemc.mods.sodium.client.util.MathUtil;

public abstract class Average1DEstimator<C> extends Estimator<C, Average1DEstimator.Value<C>, Average1DEstimator.ValueBatch<C>, Void, Long, Average1DEstimator.Average<C>> {
    private final float newDataRatio;
    private final long initialEstimate;

    public Average1DEstimator(float newDataRatio, long initialEstimate) {
        this.newDataRatio = newDataRatio;
        this.initialEstimate = initialEstimate;
    }

    public interface Value<C> extends DataPoint<C> {
        long value();
    }

    protected static class ValueBatch<C> implements Estimator.DataBatch<Value<C>> {
        private long valueSum;
        private long count;

        @Override
        public void addDataPoint(Value<C> input) {
            this.valueSum += input.value();
            this.count++;
        }

        @Override
        public void reset() {
            this.valueSum = 0;
            this.count = 0;
        }

        public float getAverage() {
            return ((float) this.valueSum) / this.count;
        }
    }

    @Override
    protected ValueBatch<C> createNewDataBatch() {
        return new ValueBatch<>();
    }

    protected static class Average<C> implements Estimator.Model<Void, Long, ValueBatch<C>, Average<C>> {
        private final float newDataRatio;
        private boolean hasRealData = false;
        private float average;

        public Average(float newDataRatio, float initialValue) {
            this.average = initialValue;
            this.newDataRatio = newDataRatio;
        }

        @Override
        public Average<C> update(ValueBatch<C> batch) {
            if (batch.count > 0) {
                if (this.hasRealData) {
                    this.average = MathUtil.exponentialMovingAverage(this.average, batch.getAverage(), this.newDataRatio);
                } else {
                    this.average = batch.getAverage();
                    this.hasRealData = true;
                }
            }

            return this;
        }

        @Override
        public Long predict(Void input) {
            return (long) this.average;
        }
    }

    @Override
    protected Average<C> createNewModel() {
        return new Average<>(this.newDataRatio, this.initialEstimate);
    }

    public Long predict(C category) {
        return super.predict(category, null);
    }
}
