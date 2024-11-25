package net.caffeinemc.mods.sodium.client.gl.shader.uniform;

import org.lwjgl.opengl.GL30C;

public class GlUniformFloat4v extends GlUniform<float[]> {
    public GlUniformFloat4v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 4) {
            throw new IllegalArgumentException("value.length != 4");
        }

        GL30C.glUniform4fv(this.index, value);
    }

    public void set(float x, float y, float z, float w) {
        GL30C.glUniform4f(this.index, x, y, z, w);
    }
}
