package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * A forward-rendering shader program for chunks.
 */
public class ChunkProgram extends GlProgram {
    // Uniform variable binding indexes
    private final int uModelViewProjectionMatrix;
    private final int uModelScale;
    private final int uTextureScale;
    private final int uBlockTex;
    private final int uLightTex;

    public final int uRegionTranslation;

    // The fog shader component used by this program in order to setup the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    public ChunkProgram(RenderDevice owner, int handle, ChunkShaderOptions options) {
        super(owner, handle);

        this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");

        this.uBlockTex = this.getUniformLocation("u_BlockTex");
        this.uLightTex = this.getUniformLocation("u_LightTex");
        this.uModelScale = this.getUniformLocation("u_ModelScale");
        this.uTextureScale = this.getUniformLocation("u_TextureScale");
        this.uRegionTranslation = this.getUniformLocation("u_RegionTranslation");

        this.fogShader = options.fogMode.getFactory().apply(this);
    }

    public void setup(MatrixStack matrixStack, float modelScale, float textureScale) {
        RenderSystem.activeTexture(GL32C.GL_TEXTURE0);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(0));

        RenderSystem.activeTexture(GL32C.GL_TEXTURE2);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(2));

        GL20C.glUniform1i(this.uBlockTex, 0);
        GL20C.glUniform1i(this.uLightTex, 2);

        GL20C.glUniform3f(this.uModelScale, modelScale, modelScale, modelScale);
        GL20C.glUniform2f(this.uTextureScale, textureScale, textureScale);

        this.fogShader.setup();

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            FloatBuffer bufModelViewProjection = memoryStack.mallocFloat(16);

            Matrix4f matrix = RenderSystem.getProjectionMatrix().copy();
            matrix.multiply(matrixStack.peek().getModel());
            matrix.writeColumnMajor(bufModelViewProjection);

            GL20C.glUniformMatrix4fv(this.uModelViewProjectionMatrix, false, bufModelViewProjection);
        }
    }
}
