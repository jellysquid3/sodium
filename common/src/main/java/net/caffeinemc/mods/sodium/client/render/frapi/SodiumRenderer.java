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

package net.caffeinemc.mods.sodium.client.render.frapi;

import net.caffeinemc.mods.sodium.client.render.frapi.material.MaterialFinderImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.material.RenderMaterialImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableMeshImpl;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;

/**
 * The Sodium renderer implementation.
 */
public class SodiumRenderer implements Renderer {
    public static final SodiumRenderer INSTANCE = new SodiumRenderer();

    public static final RenderMaterial STANDARD_MATERIAL = INSTANCE.materialFinder().find();

    static {
        INSTANCE.registerMaterial(RenderMaterial.STANDARD_ID, STANDARD_MATERIAL);
    }

    private final HashMap<ResourceLocation, RenderMaterial> materialMap = new HashMap<>();

    private SodiumRenderer() { }

    @Override
    public MutableMesh mutableMesh() {
        return new MutableMeshImpl();
    }

    @Override
    public MaterialFinder materialFinder() {
        return new MaterialFinderImpl();
    }

    @Override
    public RenderMaterial materialById(ResourceLocation id) {
        return materialMap.get(id);
    }

    @Override
    public boolean registerMaterial(ResourceLocation id, RenderMaterial material) {
        if (materialMap.containsKey(id)) return false;

        // cast to prevent acceptance of impostor implementations
        materialMap.put(id, (RenderMaterialImpl) material);
        return true;
    }
}
