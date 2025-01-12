package net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation;

import java.util.Map;

/**
 * This generic model learning class that can be used to estimate values based on a set of data points. It performs batch-wise model updates. The actual data aggregation and model updates are delegated to the implementing classes. The estimator stores multiple models in a map, one for each category.
 *
 * @param <C> The type of the category key
 * @param <D> A data point contains a category and one piece of data
 * @param <B> A data batch contains multiple data points
 * @param <I> The input to the model
 * @param <O> The output of the model
 * @param <M> The model that is used to predict values
 */
public abstract class Estimator<
        C,
        D extends Estimator.DataPoint<C>,
        B extends Estimator.DataBatch<D>,
        I,
        O,
        M extends Estimator.Model<I, O, B, M>> {
    protected final Map<C, M> models = createMap();
    protected final Map<C, B> batches = createMap();

    protected interface DataBatch<D> {
        void addDataPoint(D input);

        void reset();
    }

    protected interface DataPoint<C> {
        C category();
    }

    protected interface Model<I, O, B, M extends Model<I, O, B, M>> {
        M update(B batch);

        O predict(I input);
    }

    protected abstract B createNewDataBatch();

    protected abstract M createNewModel();

    protected abstract <T> Map<C, T> createMap();

    public void addData(D data) {
        var category = data.category();
        var batch = this.batches.get(category);
        if (batch == null) {
            batch = this.createNewDataBatch();
            this.batches.put(category, batch);
        }
        batch.addDataPoint(data);
    }

    private M ensureModel(C category) {
        var model = this.models.get(category);
        if (model == null) {
            model = this.createNewModel();
            this.models.put(category, model);
        }
        return model;
    }

    public void updateModels() {
        this.batches.forEach((category, aggregator) -> {
            var oldModel = this.ensureModel(category);

            // update the model and store it back if it returned a new model
            var newModel = oldModel.update(aggregator);
            if (newModel != oldModel) {
                this.models.put(category, newModel);
            }

            aggregator.reset();
        });
    }

    public O predict(C category, I input) {
        return this.ensureModel(category).predict(input);
    }

    public String toString(C category) {
        var model = this.models.get(category);
        if (model == null) {
            return "-";
        }
        return model.toString();
    }
}
