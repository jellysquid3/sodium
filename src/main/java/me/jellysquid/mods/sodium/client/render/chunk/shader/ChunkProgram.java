package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
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
    private final int uModelOffset;
    private final int uTextureScale;
    private final int uBlockTex;
    private final int uLightTex;

    public final int uCameraTranslation;
    public final int uboDrawParametersIndex;

    // The fog shader component used by this program in order to setup the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    public ChunkProgram(RenderDevice owner, int handle, ChunkShaderOptions options) {
        super(handle);

        this.uModelViewProjectionMatrix = this.getUniformLocation("u_ModelViewProjectionMatrix");

        this.uBlockTex = this.getUniformLocation("u_BlockTex");
        this.uLightTex = this.getUniformLocation("u_LightTex");
        this.uModelScale = this.getUniformLocation("u_ModelScale");
        this.uModelOffset = this.getUniformLocation("u_ModelOffset");
        this.uTextureScale = this.getUniformLocation("u_TextureScale");
        this.uCameraTranslation = this.getUniformLocation("u_CameraTranslation");

        this.uboDrawParametersIndex = this.getUniformBlockIndex("ubo_DrawParameters");

        this.fogShader = options.fog().getFactory().apply(this);
    }

    public void setup(PoseStack matrixStack, ChunkVertexType vertexType) {
        RenderSystem.activeTexture(GL32C.GL_TEXTURE0);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(0));

        RenderSystem.activeTexture(GL32C.GL_TEXTURE2);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(2));

        GL20C.glUniform1i(this.uBlockTex, 0);
        GL20C.glUniform1i(this.uLightTex, 2);

        GL20C.glUniform1f(this.uModelScale, vertexType.getModelScale());
        GL20C.glUniform1f(this.uModelOffset, vertexType.getModelOffset());
        GL20C.glUniform1f(this.uTextureScale, vertexType.getTextureScale());
        
        this.fogShader.setup();

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            FloatBuffer bufModelViewProjection = memoryStack.mallocFloat(16);

            Matrix4f matrix = RenderSystem.getProjectionMatrix().copy();
            matrix.multiply(matrixStack.last().pose());
            matrix.store(bufModelViewProjection);

            GL20C.glUniformMatrix4fv(this.uModelViewProjectionMatrix, false, bufModelViewProjection);
        }
    }
}
