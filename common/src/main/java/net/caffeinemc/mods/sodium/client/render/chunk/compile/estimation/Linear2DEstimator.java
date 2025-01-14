package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Locale;

public abstract class Linear2DEstimator<C> extends Estimator<C, Linear2DEstimator.DataPair<C>, Linear2DEstimator.LinearRegressionBatch<C>, Long, Long, Linear2DEstimator.LinearFunction<C>> {
    private final float newDataRatio;
    private final int initialSampleTarget;
    private final long initialOutput;

    public Linear2DEstimator(float newDataRatio, int initialSampleTarget, long initialOutput) {
        this.newDataRatio = newDataRatio;
        this.initialSampleTarget = initialSampleTarget;
        this.initialOutput = initialOutput;
    }

    public interface DataPair<C> extends DataPoint<C> {
        long x();

        long y();
    }

    protected static class LinearRegressionBatch<C> extends ObjectArrayList<DataPair<C>> implements Estimator.DataBatch<DataPair<C>> {
        @Override
        public void addDataPoint(DataPair<C> input) {
            this.add(input);
        }

        @Override
        public void reset() {
            this.clear();
        }
    }

    @Override
    protected LinearRegressionBatch<C> createNewDataBatch() {
        return new LinearRegressionBatch<>();
    }

    protected static class LinearFunction<C> implements Model<Long, Long, LinearRegressionBatch<C>, LinearFunction<C>> {
        // the maximum fraction of the total weight that new data can have
        private final float newDataRatioInv;
        // how many samples we want to have at least before we start diminishing the new data's weight
        private final int initialSampleTarget;
        private final long initialOutput;

        private float yIntercept;
        private float slope;

        private int gatheredSamples = 0;
        private float xMeanOld = 0;
        private float yMeanOld = 0;
        private float covarianceOld = 0;
        private float varianceOld = 0;

        public LinearFunction(float newDataRatio, int initialSampleTarget, long initialOutput) {
            this.newDataRatioInv = 1.0f / newDataRatio;
            this.initialSampleTarget = initialSampleTarget;
            this.initialOutput = initialOutput;
        }

        @Override
        public LinearFunction<C> update(LinearRegressionBatch<C> batch) {
            if (batch.isEmpty()) {
                return this;
            }

            // condition the weight to gather at least the initial sample target, and then weight the new data with a ratio
            var newDataSize = batch.size();
            var totalSamples = this.gatheredSamples + newDataSize;
            float oldDataWeight;
            float totalWeight;
            if (totalSamples <= this.initialSampleTarget) {
                totalWeight = totalSamples;
                oldDataWeight = this.gatheredSamples;
                this.gatheredSamples = totalSamples;
            } else {
                oldDataWeight = newDataSize * this.newDataRatioInv - newDataSize;
                totalWeight = oldDataWeight + newDataSize;
            }

            var totalWeightInv = 1.0f / totalWeight;

            // calculate the weighted mean along both axes
            long xSum = 0;
            long ySum = 0;
            for (var data : batch) {
                xSum += data.x();
                ySum += data.y();
            }
            var xMean = (this.xMeanOld * oldDataWeight + xSum) * totalWeightInv;
            var yMean = (this.yMeanOld * oldDataWeight + ySum) * totalWeightInv;

            // the covariance and variance are calculated from the differences to the mean
            var covarianceSum = 0.0f;
            var varianceSum = 0.0f;
            for (var data : batch) {
                var xDelta = data.x() - xMean;
                var yDelta = data.y() - yMean;
                covarianceSum += xDelta * yDelta;
                varianceSum += xDelta * xDelta;
            }

            if (varianceSum == 0) {
                return this;
            }

            covarianceSum += this.covarianceOld * oldDataWeight;
            varianceSum += this.varianceOld * oldDataWeight;

            // negative slopes are clamped to produce a flat line if necessary
            this.slope = Math.max(0, covarianceSum / varianceSum);
            this.yIntercept = yMean - this.slope * xMean;
            
            this.xMeanOld = xMean;
            this.yMeanOld = yMean;
            this.covarianceOld = covarianceSum * totalWeightInv;
            this.varianceOld = varianceSum * totalWeightInv;

            return this;
        }

        @Override
        public Long predict(Long input) {
            if (this.gatheredSamples == 0) {
                return this.initialOutput;
            }

            return (long) (this.yIntercept + this.slope * input);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "s=%.2f,y=%.0f", this.slope, this.yIntercept);
        }
    }

    @Override
    protected LinearFunction<C> createNewModel() {
        return new LinearFunction<>(this.newDataRatio, this.initialSampleTarget, this.initialOutput);
    }
}
