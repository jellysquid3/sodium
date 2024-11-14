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

package net.caffeinemc.mods.sodium.client.render.frapi.material;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.util.TriState;

public class RenderMaterialImpl extends MaterialViewImpl implements RenderMaterial {
    public static final int VALUE_COUNT = 1 << TOTAL_BIT_LENGTH;
    private static final RenderMaterialImpl[] BY_INDEX = new RenderMaterialImpl[VALUE_COUNT];

    static {
        for (int i = 0; i < VALUE_COUNT; i++) {
            if (areBitsValid(i)) {
                BY_INDEX[i] = new RenderMaterialImpl(i);
            }
        }
    }

    private RenderMaterialImpl(int bits) {
        super(bits);
    }

    public int index() {
        return bits;
    }

    public static RenderMaterialImpl byIndex(int index) {
        return BY_INDEX[index];
    }

    public static RenderMaterialImpl setDisableDiffuse(RenderMaterialImpl material, boolean disable) {
        if (material.disableDiffuse() != disable) {
            return byIndex(disable ? (material.bits | DIFFUSE_FLAG) : (material.bits & ~DIFFUSE_FLAG));
        }

        return material;
    }

    public static RenderMaterialImpl setAmbientOcclusion(RenderMaterialImpl material, TriState mode) {
        if (material.ambientOcclusion() != mode) {
            return byIndex((material.bits & ~AO_MASK) | (mode.ordinal() << AO_BIT_OFFSET));
        }

        return material;
    }
}
