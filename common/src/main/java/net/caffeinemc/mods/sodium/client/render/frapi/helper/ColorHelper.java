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

package net.caffeinemc.mods.sodium.client.render.frapi.helper;

import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;

/**
 * Static routines of general utility for renderer implementations.
 * Renderers are not required to use these helpers, but they were
 * designed to be usable without the default renderer.
 */
public abstract class ColorHelper {
    public static int maxBrightness(int b0, int b1) {
        return  Math.max(b0 & 0x0000FFFF, b1 & 0x0000FFFF) |
                Math.max(b0 & 0xFFFF0000, b1 & 0xFFFF0000);
    }

    /*
    Renderer color format: ARGB (0xAARRGGBB)
    Vanilla color format (little endian): ABGR (0xAABBGGRR)
    Vanilla color format (big endian): RGBA (0xRRGGBBAA)

    Why does the vanilla color format change based on endianness?
    See VertexConsumer#quad. Quad data is loaded as integers into
    a native byte order buffer. Color is read directly from bytes
    12, 13, 14 of each vertex. A different byte order will yield
    different results.

    The renderer always uses ARGB because the API color methods
    always consume and return ARGB. Vanilla block and item colors
    also use ARGB.
     */

    /**
     * Converts from ARGB color to ABGR color. The result will be in the platform's native byte order.
     */
    public static int toVanillaColor(int color) {
        return ColorABGR.toNativeByteOrder(ColorARGB.toABGR(color));
    }

    /**
     * Converts from ABGR color to ARGB color. The input should be in the platform's native byte order.
     */
    public static int fromVanillaColor(int color) {
        return ColorARGB.fromABGR(ColorABGR.fromNativeByteOrder(color));
    }
}
