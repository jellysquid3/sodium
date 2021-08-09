package me.jellysquid.mods.sodium.client.model.vertex.transformers;

import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.GlyphVertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.ParticleVertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Base implementation for a {@link VertexSink} which transforms texture coordinates relative to a sprite's bounds.
 *
 * @param <T> The {@link VertexSink} interface this transformer wraps
 */
public abstract class SpriteTexturedVertexTransformer<T extends VertexSink> extends AbstractVertexTransformer<T> {
    private final float uMin;
    private final float vMin;

    private final float uMaxMin;
    private final float vMaxMin;

    public SpriteTexturedVertexTransformer(T delegate, TextureAtlasSprite sprite) {
        super(delegate);

        this.uMin = sprite.getU0();
        this.vMin = sprite.getV0();

        this.uMaxMin = sprite.getU1() - this.uMin;
        this.vMaxMin = sprite.getV1() - this.vMin;
    }

    protected float transformTextureU(float u) {
        return (this.uMaxMin * u) + this.uMin;
    }

    protected float transformTextureV(float v) {
        return (this.vMaxMin * v) + this.vMin;
    }

    public static class Quad extends SpriteTexturedVertexTransformer<QuadVertexSink> implements QuadVertexSink {
        public Quad(QuadVertexSink delegate, TextureAtlasSprite sprite) {
            super(delegate, sprite);
        }

        @Override
        public void writeQuad(float x, float y, float z, int color, float u, float v, int light, int overlay, int normal) {
            u = this.transformTextureU(u);
            v = this.transformTextureV(v);

            this.delegate.writeQuad(x, y, z, color, u, v, light, overlay, normal);
        }
    }

    public static class Particle extends SpriteTexturedVertexTransformer<ParticleVertexSink> implements ParticleVertexSink {
        public Particle(ParticleVertexSink delegate, TextureAtlasSprite sprite) {
            super(delegate, sprite);
        }

        @Override
        public void writeParticle(float x, float y, float z, float u, float v, int color, int light) {
            u = this.transformTextureU(u);
            v = this.transformTextureV(v);

            this.delegate.writeParticle(x, y, z, u, v, color, light);
        }
    }

    public static class Glyph extends SpriteTexturedVertexTransformer<GlyphVertexSink> implements GlyphVertexSink {
        public Glyph(GlyphVertexSink delegate, TextureAtlasSprite sprite) {
            super(delegate, sprite);
        }

        @Override
        public void writeGlyph(float x, float y, float z, int color, float u, float v, int light) {
            u = this.transformTextureU(u);
            v = this.transformTextureV(v);

            this.delegate.writeGlyph(x, y, z, color, u, v, light);
        }
    }
}
