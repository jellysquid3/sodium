package me.jellysquid.mods.sodium.client.util.math;

import com.mojang.math.Quaternion;

public interface Matrix4fExtended {
    /**
     * Applies the specified rotation to this matrix in-place.
     *
     * @param quaternion The quaternion to rotate this matrix by
     */
    void rotate(Quaternion quaternion);

    /**
     * Applies the specified translation to this matrix in-place.
     *
     * @param x The x-component of the translation
     * @param y The y-component of the translation
     * @param z The z-component of the translation
     */
    void translate(float x, float y, float z);

    /**
     * Applies this matrix transformation to the given input vector, returning the x-component. Avoids the lack of
     * struct types in Java and allows for allocation-free return.
     * @param x The x-component of the vector
     * @param y The y-component of the vector
     * @param z The z-component of the vector
     * @return The x-component of the transformed input vector
     */
    float transformVecX(float x, float y, float z);

    /**
     * Applies this matrix transformation to the given input vector, returning the y-component. Avoids the lack of
     * struct types in Java and allows for allocation-free return.
     * @param x The x-component of the vector
     * @param y The y-component of the vector
     * @param z The z-component of the vector
     * @return The y-component of the transformed input vector
     */
    float transformVecY(float x, float y, float z);

    /**
     * Applies this matrix transformation to the given input vector, returning the z-component. Avoids the lack of
     * struct types in Java and allows for allocation-free return.
     * @param x The x-component of the vector
     * @param y The y-component of the vector
     * @param z The z-component of the vector
     * @return The z-component of the transformed input vector
     */
    float transformVecZ(float x, float y, float z);
}
