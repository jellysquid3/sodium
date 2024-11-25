package net.caffeinemc.mods.sodium.mixin.core.model.quad;

import net.caffeinemc.mods.sodium.client.model.quad.BakedQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.caffeinemc.mods.sodium.client.util.ModelQuadUtil;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedQuad.class)
public abstract class BakedQuadMixin implements BakedQuadView {
    @Shadow
    @Final
    protected int[] vertices;

    @Shadow
    @Final
    protected TextureAtlasSprite sprite;

    @Shadow
    @Final
    protected int tintIndex;

    @Shadow
    @Final
    protected Direction direction; // This is really the light face, but we can't rename it.

    @Shadow
    @Final
    private boolean shade;

    @Shadow
    public abstract boolean hasAmbientOcclusion();

    @Shadow
    public abstract int getLightEmission();

    @Unique
    private int flags;

    @Unique
    private int normal = -1;

    @Unique
    private ModelQuadFacing normalFace = null;

    @Inject(method = {
            "<init>([IILnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZIZ)V"
    }, at = @At("RETURN"))
    private void init(int[] is, int i, Direction face, TextureAtlasSprite arg2, boolean bl, int ambientLight, boolean hasAmbientOcclusion, CallbackInfo ci) {
        this.flags = ModelQuadFlags.getQuadFlags(this, face);
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.POSITION_INDEX + 2]);
    }

    @Override
    public int getColor(int idx) {
        return this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.COLOR_INDEX];
    }

    @Override
    public int getVertexNormal(int idx) {
        return this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.NORMAL_INDEX];
    }

    @Override
    public int getLight(int idx) {
        return this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.LIGHT_INDEX];
    }

    @Override
    public TextureAtlasSprite getSprite() {
        return this.sprite;
    }

    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX]);
    }

    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.vertices[ModelQuadUtil.vertexOffset(idx) + ModelQuadUtil.TEXTURE_INDEX + 1]);
    }

    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public int getTintIndex() {
        return this.tintIndex;
    }

    @Override
    public ModelQuadFacing getNormalFace() {
        if (this.normalFace == null) {
            this.normalFace = ModelQuadFacing.fromPackedNormal(this.getFaceNormal());
        }

        return this.normalFace;
    }

    @Override
    public int getFaceNormal() {
        if (this.normal == -1) {
            this.normal = this.calculateNormal();
        }

        return this.normal;
    }

    @Override
    public Direction getLightFace() {
        return this.direction;
    }

    @Override
    public int getMaxLightQuad(int idx) {
        return LightTexture.lightCoordsWithEmission(getLight(idx), getLightEmission());
    }

    @Override
    @Unique(silent = true) // The target class has a function with the same name in a remapped environment
    public boolean hasShade() {
        return this.shade;
    }

    @Override
    public boolean hasAO() {
        return this.hasAmbientOcclusion();
    }
}
