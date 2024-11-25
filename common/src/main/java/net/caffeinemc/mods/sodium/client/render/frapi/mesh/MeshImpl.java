/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.caffeinemc.mods.sodium.client.render.frapi.mesh;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import org.jetbrains.annotations.Range;

import java.util.function.Consumer;

/**
 * Implementation of {@link Mesh}.
 * The way we encode meshes makes it very simple.
 */
public class MeshImpl implements Mesh {
    /** Used to satisfy external calls to {@link #forEach(Consumer)}. */
    private static final ThreadLocal<ObjectArrayList<QuadViewImpl>> CURSOR_POOLS = ThreadLocal.withInitial(ObjectArrayList::new);

    int[] data;
    int limit;

    MeshImpl(int[] data) {
        this.data = data;
        limit = data.length;
    }

    MeshImpl() {}

    @Override
    @Range(from = 0, to = Integer.MAX_VALUE)
    public int size() {
        return limit / EncodingFormat.TOTAL_STRIDE;
    }

    @Override
    public void forEach(Consumer<? super QuadView> action) {
        ObjectArrayList<QuadViewImpl> pool = CURSOR_POOLS.get();
        QuadViewImpl cursor;

        if (pool.isEmpty()) {
            cursor = new QuadViewImpl();
        } else {
            cursor = pool.pop();
        }

        forEach(action, cursor);

        pool.push(cursor);
    }

    /**
     * The renderer can call this with its own cursor
     * to avoid the performance hit of a thread-local lookup.
     * Also means renderer can hold final references to quad buffers.
     */
    <C extends QuadViewImpl> void forEach(Consumer<? super C> action, C cursor) {
        final int limit = this.limit;
        int index = 0;
        cursor.data = this.data;

        while (index < limit) {
            cursor.baseIndex = index;
            cursor.load();
            action.accept(cursor);
            index += EncodingFormat.TOTAL_STRIDE;
        }

        cursor.data = null;
    }

    // TODO: This could be optimized by checking if the emitter is that of a MutableMeshImpl and if
    //  it has no transforms, in which case the entire data array can be copied in bulk.
    @Override
    public void outputTo(QuadEmitter emitter) {
        MutableQuadViewImpl e = (MutableQuadViewImpl) emitter;
        final int[] data = this.data;
        final int limit = this.limit;
        int index = 0;

        while (index < limit) {
            System.arraycopy(data, index, e.data, e.baseIndex, EncodingFormat.TOTAL_STRIDE);
            e.load();
            e.transformAndEmit();
            index += EncodingFormat.TOTAL_STRIDE;
        }

        e.clear();
    }
}
