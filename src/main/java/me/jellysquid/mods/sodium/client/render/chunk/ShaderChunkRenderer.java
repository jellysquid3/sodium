package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.shader.*;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<BlockRenderPass, Map<ChunkShaderOptions, ChunkProgram>> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat<ChunkMeshAttribute> vertexFormat;

    protected final RenderDevice device;

    protected ChunkProgram activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getCustomVertexFormat();
    }

    // TODO: Generalize shader options
    protected ChunkProgram compileProgram(BlockRenderPass pass, ChunkShaderOptions options) {
        Map<ChunkShaderOptions, ChunkProgram> programs = this.programs.get(pass);

        if (programs == null) {
            this.programs.put(pass, programs = new Object2ObjectOpenHashMap<>());
        }

        ChunkProgram program = programs.get(options);

        if (program == null) {
            programs.put(options, program = this.createShader(this.device, getShaderName(pass), options));
        }

        return program;
    }

    // TODO: Define these in the render pass itself
    protected String getShaderName(BlockRenderPass pass) {
        return switch (pass) {
            case CUTOUT -> "blocks/block_layer_cutout";
            case CUTOUT_MIPPED -> "blocks/block_layer_cutout_mipped";
            case TRANSLUCENT, TRIPWIRE -> "blocks/block_layer_translucent";
            default -> "blocks/block_layer_solid";
        };
    }

    private ChunkProgram createShader(RenderDevice device, String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        GlShader vertShader = ShaderLoader.loadShader(ShaderType.VERTEX,
                new ResourceLocation("sodium", path + ".vsh"), constants);
        
        GlShader fragShader = ShaderLoader.loadShader(ShaderType.FRAGMENT,
                new ResourceLocation("sodium", path + ".fsh"), constants);

        try {
            return GlProgram.builder(new ResourceLocation("sodium", "chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Pos", ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE)
                    .bindAttribute("a_LightCoord", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .build((name) -> new ChunkProgram(device, name, options));
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    protected void begin(BlockRenderPass pass, PoseStack matrixStack) {
        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH);

        this.activeProgram = this.compileProgram(pass, options);
        this.activeProgram.bind();
        this.activeProgram.setup(matrixStack, this.vertexType);
    }

    protected void end() {
        this.activeProgram.unbind();
        this.activeProgram = null;
    }

    @Override
    public void delete() {
        this.programs.values()
                .stream()
                .flatMap(i -> i.values().stream())
                .forEach(GlProgram::delete);
        this.programs.clear();
    }

    @Override
    public ChunkVertexType getVertexType() {
        return this.vertexType;
    }
}
