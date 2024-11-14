package net.caffeinemc.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.*;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.gl.shader.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat vertexFormat;

    protected final RenderDevice device;

    protected GlProgram<ChunkShaderInterface> activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();
    }

    protected GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
        GlProgram<ChunkShaderInterface> program = this.programs.get(options);

        if (program == null) {
            this.programs.put(options, program = this.createShader("blocks/block_layer_opaque", options));
        }

        return program;
    }

    private GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        GlShader vertShader = ShaderLoader.loadShader(ShaderType.VERTEX,
                ResourceLocation.fromNamespaceAndPath("sodium", path + ".vsh"), constants);

        GlShader fragShader = ShaderLoader.loadShader(ShaderType.FRAGMENT,
                ResourceLocation.fromNamespaceAndPath("sodium", path + ".fsh"), constants);

        try {
            return GlProgram.builder(ResourceLocation.fromNamespaceAndPath("sodium", "chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Position", ChunkShaderBindingPoints.ATTRIBUTE_POSITION)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE)
                    .bindAttribute("a_LightAndData", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .link((shader) -> new DefaultShaderInterface(shader, options));
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    protected void begin(TerrainRenderPass pass) {
        pass.startDrawing();

        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass, this.vertexType);

        this.activeProgram = this.compileProgram(options);
        this.activeProgram.bind();
        this.activeProgram.getInterface()
                .setupState();
    }

    protected void end(TerrainRenderPass pass) {
        this.activeProgram.getInterface()
                .resetState();
        this.activeProgram.unbind();
        this.activeProgram = null;

        pass.endDrawing();
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values()
                .forEach(GlProgram::delete);
    }

}
