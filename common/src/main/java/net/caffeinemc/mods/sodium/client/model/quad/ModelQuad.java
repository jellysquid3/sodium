package net.caffeinemc.mods.sodium.client.model.quad;

import net.caffeinemc.mods.sodium.client.util.ModelQuadUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * A simple implementation of the {@link ModelQuadViewMutable} interface which can provide an on-heap scratch area
 * for storing quad vertex data.
 */
public class ModelQuad implements ModelQuadViewMutable {
    private final int[] data = new int[ModelQuadUtil.VERTEX_SIZE * 4];
    private int flags;

    private TextureAtlasSprite sprite;
    private Direction direction;

    private int tintIdx;
    private int faceNormal;

    @Override
    public void setX(int idx, float x) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX] = Float.floatToRawIntBits(x);
    }

    @Override
    public void setY(int idx, float y) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 1] = Float.floatToRawIntBits(y);
    }

    @Override
    public void setZ(int idx, float z) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 2] = Float.floatToRawIntBits(z);
    }

    @Override
    public void setColor(int idx, int color) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.COLOR_INDEX] = color;
    }

    @Override
    public void setTexU(int idx, float u) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX] = Float.floatToRawIntBits(u);
    }

    @Override
    public void setTexV(int idx, float v) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX + 1] = Float.floatToRawIntBits(v);
    }

    @Override
    public void setLight(int idx, int light) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.LIGHT_INDEX] = light;
    }

    @Override
    public void setNormal(int idx, int normal) {
        this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.NORMAL_INDEX] = normal;
    }

    @Override
    public void setFaceNormal(int normal) {
        this.faceNormal = normal;
    }

    @Override
    public void setFlags(int flags) {
        this.flags = flags;
    }

    @Override
    public void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    @Override
    public void setTintIndex(int index) {
        this.tintIdx = index;
    }

    @Override
    public void setLightFace(Direction direction) {
        this.direction = direction;
    }

    @Override
    public int getTintIndex() {
        return this.tintIdx;
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 2]);
    }

    @Override
    public int getColor(int idx) {
        return this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.COLOR_INDEX];
    }

    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX]);
    }

    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX + 1]);
    }

    @Override
    public int getVertexNormal(int idx) {
        return this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.NORMAL_INDEX];
    }

    @Override
    public int getFaceNormal() {
        return faceNormal;
    }

    @Override
    public int getLight(int idx) {
        return this.data[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.LIGHT_INDEX];
    }

    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public TextureAtlasSprite getSprite() {
        return this.sprite;
    }

    @Override
    public Direction getLightFace() {
        return this.direction;
    }

    @Override
    public int getMaxLightQuad(int idx) {
        return getLight(idx);
    }
}
