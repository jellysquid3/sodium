package net.caffeinemc.mods.sodium.client.render.immediate;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class CloudRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-CloudRenderer");

    private static final ShaderProgram CLOUDS_SHADER = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath("sodium", "clouds"),
            DefaultVertexFormat.POSITION_COLOR,
            ShaderDefines.builder()
                    .build()
    );

    private static final ResourceLocation CLOUDS_TEXTURE_ID =
            ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");

    private static final float CLOUD_HEIGHT = 4.0f; // The height of the cloud cells
    private static final float CLOUD_WIDTH = 12.0f; // The width/length of cloud cells

    // Bitmasks for each cloud face
    private static final int FACE_MASK_NEG_Y = 1 << 0;
    private static final int FACE_MASK_POS_Y = 1 << 1;
    private static final int FACE_MASK_NEG_X = 1 << 2;
    private static final int FACE_MASK_POS_X = 1 << 3;
    private static final int FACE_MASK_NEG_Z = 1 << 4;
    private static final int FACE_MASK_POS_Z = 1 << 5;

    // The brightness of each fac
    // The final color of each vertex is: vec4((texel.rgb * brightness), texel.a * 0.8)
    private static final int BRIGHTNESS_POS_Y = ColorU8.normalizedFloatToByte(1.0F); // used for +Y
    private static final int BRIGHTNESS_NEG_Y = ColorU8.normalizedFloatToByte(0.7F); // used for -Y
    private static final int BRIGHTNESS_X_AXIS = ColorU8.normalizedFloatToByte(0.9F); // used for -X and +X
    private static final int BRIGHTNESS_Z_AXIS = ColorU8.normalizedFloatToByte(0.8F); // used for -Z and +Z

    private @Nullable CloudTextureData textureData;
    private @Nullable CloudGeometry builtGeometry;

    public CloudRenderer(ResourceProvider resourceProvider) {
        this.reload(resourceProvider);
    }

    public void render(Camera camera,
                       ClientLevel level,
                       Matrix4f projectionMatrix,
                       Matrix4f modelView,
                       float ticks,
                       float tickDelta,
                       int color)
    {
        float height = level.effects()
                .getCloudHeight() + 0.33f; // arithmetic against NaN always produces NaN

        // Vanilla uses NaN height as a way to disable cloud rendering
        if (Float.isNaN(height)) {
            return;
        }

        // Skip rendering clouds if the texture data isn't available
        // This can happen if the texture failed to load, or if the texture is completely empty
        if (this.textureData == null) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        int renderDistance = getCloudRenderDistance();
        var renderMode = Minecraft.getInstance().options.getCloudsType();

        // Translation of the clouds texture in world-space
        double worldX = (cameraPos.x + ((ticks + tickDelta) * 0.03D));
        double worldZ = (cameraPos.z + 3.96D);

        double textureWidth = this.textureData.width * CLOUD_WIDTH;
        double textureHeight = this.textureData.height * CLOUD_WIDTH;
        worldX -= Mth.floor(worldX / textureWidth) * textureWidth;
        worldZ -= Mth.floor(worldZ / textureHeight) * textureHeight;

        // The coordinates of the cloud cell which the camera is within
        int cellX = Mth.floor(worldX / CLOUD_WIDTH);
        int cellZ = Mth.floor(worldZ / CLOUD_WIDTH);

        // The orientation of the camera relative to the clouds
        // This is used to cull back-facing geometry
        ViewOrientation orientation;

        if (renderMode == CloudStatus.FANCY) {
            orientation = ViewOrientation.getOrientation(cameraPos, height, height + CLOUD_HEIGHT);
        } else {
            // When fast clouds are used, there is no orientation of faces, since culling is disabled.
            // To avoid unnecessary rebuilds, simply mark a null (undefined) orientation.
            orientation = null;
        }

        var parameters = new CloudGeometryParameters(cellX, cellZ, renderDistance, orientation, renderMode);

        CloudGeometry geometry = this.builtGeometry;

        // Re-generate the cached cloud geometry if necessary
        if (geometry == null || !Objects.equals(geometry.params(), parameters)) {
            this.builtGeometry = (geometry = rebuildGeometry(geometry, parameters, this.textureData));
        }

        VertexBuffer vertexBuffer = geometry.vertexBuffer();

        // The vertex buffer can be empty when there are no clouds to render
        if (vertexBuffer == null) {
            return;
        }

        // Apply world->view transform
        final float viewPosX = (float) (worldX - (cellX * CLOUD_WIDTH));
        final float viewPosY = (float) cameraPos.y() - height;
        final float viewPosZ = (float) (worldZ - (cellZ * CLOUD_WIDTH));

        Matrix4f modelViewMatrix = new Matrix4f(modelView);
        modelViewMatrix.translate(-viewPosX, -viewPosY, -viewPosZ);

        // State setup
        final var prevFogParameters = copyShaderFogParameters(RenderSystem.getShaderFog());
        final var flat = geometry.params()
                .renderMode() == CloudStatus.FAST;

        FogParameters fogParameters = FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, new Vector4f(prevFogParameters.red(), prevFogParameters.green(), prevFogParameters.blue(), prevFogParameters.alpha()), renderDistance * 8, shouldUseWorldFog(level, cameraPos), tickDelta);

        RenderSystem.setShaderColor(ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color), 0.8F);
        RenderSystem.setShaderFog(fogParameters);

        RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.getCloudsTarget();

        if (renderTarget != null) {
            renderTarget.bindWrite(false);
        } else {
            Minecraft.getInstance()
                    .getMainRenderTarget()
                    .bindWrite(false);
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        RenderSystem.setShader(CLOUDS_SHADER);

        if (flat) {
            RenderSystem.disableCull();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL32C.GL_LESS);

        // Draw
        vertexBuffer.bind();
        vertexBuffer.drawWithShader(modelViewMatrix, projectionMatrix, RenderSystem.getShader());
        VertexBuffer.unbind();

        // State teardown
        RenderSystem.depthFunc(GL32C.GL_LEQUAL);
        RenderSystem.disableDepthTest();

        if (flat) {
            RenderSystem.enableCull();
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        if (renderTarget != null) {
            Minecraft.getInstance()
                    .getMainRenderTarget()
                    .bindWrite(false);
        }

        RenderSystem.setShaderFog(prevFogParameters);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static @NotNull CloudGeometry rebuildGeometry(@Nullable CloudGeometry existingGeometry,
                                                          CloudGeometryParameters parameters,
                                                          CloudTextureData textureData)
    {
        BufferBuilder bufferBuilder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        var writer = VertexBufferWriter.of(bufferBuilder);

        final var radius = parameters.radius();
        final var orientation = parameters.orientation();
        final var flat = parameters.renderMode() == CloudStatus.FAST;

        final var slice = textureData.slice(parameters.originX(), parameters.originZ(), radius);

        // Iterate from the center coordinates (0, 0) outwards
        // Since the geometry will be in sorted order, this avoids needing a depth pre-pass
        addCellGeometryToBuffer(writer, slice, 0, 0, orientation, flat);

        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                addCellGeometryToBuffer(writer, slice, x, z, orientation, flat);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                addCellGeometryToBuffer(writer, slice, x, z, orientation, flat);
            }
        }

        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                addCellGeometryToBuffer(writer, slice, x, z, orientation, flat);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                addCellGeometryToBuffer(writer, slice, x, z, orientation, flat);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                addCellGeometryToBuffer(writer, slice, x, z, orientation, flat);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                addCellGeometryToBuffer(writer, slice, x, z, orientation, flat);
            }
        }

        @Nullable MeshData meshData = bufferBuilder.build();
        @Nullable VertexBuffer vertexBuffer = null;

        if (existingGeometry != null) {
            vertexBuffer = existingGeometry.vertexBuffer();
        }

        if (meshData != null) {
            if (vertexBuffer == null) {
                vertexBuffer = new VertexBuffer(BufferUsage.DYNAMIC_WRITE);
            }

            uploadToVertexBuffer(vertexBuffer, meshData);
        } else {
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
        }

        Tesselator.getInstance().clear();

        return new CloudGeometry(vertexBuffer, parameters);
    }

    private static void addCellGeometryToBuffer(VertexBufferWriter writer,
                                                CloudTextureData.Slice textureData,
                                                int x,
                                                int z,
                                                @Nullable CloudRenderer.ViewOrientation orientation,
                                                boolean flat)
    {
        int index = textureData.getCellIndex(x, z);
        int faces = textureData.getCellFaces(index) & getVisibleFaces(x, z, orientation);

        if (faces == 0) {
            return;
        }

        int color = textureData.getCellColor(index);

        if (isTransparent(color)) {
            return;
        }

        if (flat) {
            emitCellGeometryFlat(writer, color, x, z);
        } else {
            emitCellGeometryExterior(writer, faces, color, x, z);

            if (taxicabDistance(x, z) <= 1) {
                emitCellGeometryInterior(writer, color, x, z);
            }
        }
    }

    private static int getVisibleFaces(int x, int z, ViewOrientation orientation) {
        int faces = 0;

        if (x <= 0) {
            faces |= FACE_MASK_POS_X;
        }

        if (z <= 0) {
            faces |= FACE_MASK_POS_Z;
        }

        if (x >= 0) {
            faces |= FACE_MASK_NEG_X;
        }

        if (z >= 0) {
            faces |= FACE_MASK_NEG_Z;
        }

        if (orientation != ViewOrientation.BELOW_CLOUDS) {
            faces |= FACE_MASK_POS_Y;
        }

        if (orientation != ViewOrientation.ABOVE_CLOUDS) {
            faces |= FACE_MASK_NEG_Y;
        }

        return faces;
    }

    private static void emitCellGeometryFlat(VertexBufferWriter writer, int texel, int x, int z) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long vertexBuffer = stack.nmalloc(4 * ColorVertex.STRIDE);
            long ptr = vertexBuffer;

            final float x0 = (x * CLOUD_WIDTH);
            final float x1 = x0 + CLOUD_WIDTH;
            final float z0 = (z * CLOUD_WIDTH);
            final float z1 = z0 + CLOUD_WIDTH;

            {
                final int color = ColorABGR.mulRGB(texel, BRIGHTNESS_POS_Y);
                ptr = writeVertex(ptr, x1, 0.0f, z1, color);
                ptr = writeVertex(ptr, x0, 0.0f, z1, color);
                ptr = writeVertex(ptr, x0, 0.0f, z0, color);
                ptr = writeVertex(ptr, x1, 0.0f, z0, color);
            }

            writer.push(stack, vertexBuffer, 4, ColorVertex.FORMAT);
        }
    }

    private static void emitCellGeometryExterior(VertexBufferWriter writer, int cellFaces, int cellColor, int cellX, int cellZ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long vertexBuffer = stack.nmalloc(6 * 4 * ColorVertex.STRIDE);
            int vertexCount = 0;

            long ptr = vertexBuffer;

            final float x0 = cellX * CLOUD_WIDTH;
            final float y0 = 0.0f;
            final float z0 = cellZ * CLOUD_WIDTH;

            final float x1 = x0 + CLOUD_WIDTH;
            final float y1 = y0 + CLOUD_HEIGHT;
            final float z1 = z0 + CLOUD_WIDTH;

            // -Y
            if ((cellFaces & FACE_MASK_NEG_Y) != 0) {
                final int vertexColor = ColorABGR.mulRGB(cellColor, BRIGHTNESS_NEG_Y);
                ptr = writeVertex(ptr, x1, y0, z1, vertexColor);
                ptr = writeVertex(ptr, x0, y0, z1, vertexColor);
                ptr = writeVertex(ptr, x0, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y0, z0, vertexColor);
                vertexCount += 4;
            }

            // +Y
            if ((cellFaces & FACE_MASK_POS_Y) != 0) {
                final int vertexColor = ColorABGR.mulRGB(cellColor, BRIGHTNESS_POS_Y);
                ptr = writeVertex(ptr, x0, y1, z1, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z1, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z0, vertexColor);
                ptr = writeVertex(ptr, x0, y1, z0, vertexColor);
                vertexCount += 4;
            }

            if ((cellFaces & (FACE_MASK_NEG_X | FACE_MASK_POS_X)) != 0) {
                final int vertexColor = ColorABGR.mulRGB(cellColor, BRIGHTNESS_X_AXIS);

                // -X
                if ((cellFaces & FACE_MASK_NEG_X) != 0) {
                    ptr = writeVertex(ptr, x0, y0, z1, vertexColor);
                    ptr = writeVertex(ptr, x0, y1, z1, vertexColor);
                    ptr = writeVertex(ptr, x0, y1, z0, vertexColor);
                    ptr = writeVertex(ptr, x0, y0, z0, vertexColor);
                    vertexCount += 4;
                }

                // +X
                if ((cellFaces & FACE_MASK_POS_X) != 0) {
                    ptr = writeVertex(ptr, x1, y1, z1, vertexColor);
                    ptr = writeVertex(ptr, x1, y0, z1, vertexColor);
                    ptr = writeVertex(ptr, x1, y0, z0, vertexColor);
                    ptr = writeVertex(ptr, x1, y1, z0, vertexColor);
                    vertexCount += 4;
                }
            }

            if ((cellFaces & (FACE_MASK_NEG_Z | FACE_MASK_POS_Z)) != 0) {
                final int vertexColor = ColorABGR.mulRGB(cellColor, BRIGHTNESS_Z_AXIS);

                // -Z
                if ((cellFaces & FACE_MASK_NEG_Z) != 0) {
                    ptr = writeVertex(ptr, x1, y1, z0, vertexColor);
                    ptr = writeVertex(ptr, x1, y0, z0, vertexColor);
                    ptr = writeVertex(ptr, x0, y0, z0, vertexColor);
                    ptr = writeVertex(ptr, x0, y1, z0, vertexColor);
                    vertexCount += 4;
                }

                // +Z
                if ((cellFaces & FACE_MASK_POS_Z) != 0) {
                    ptr = writeVertex(ptr, x1, y0, z1, vertexColor);
                    ptr = writeVertex(ptr, x1, y1, z1, vertexColor);
                    ptr = writeVertex(ptr, x0, y1, z1, vertexColor);
                    ptr = writeVertex(ptr, x0, y0, z1, vertexColor);
                    vertexCount += 4;
                }
            }

            writer.push(stack, vertexBuffer, vertexCount, ColorVertex.FORMAT);
        }
    }

    private static void emitCellGeometryInterior(VertexBufferWriter writer, int baseColor, int cellX, int cellZ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long vertexBuffer = stack.nmalloc(6 * 4 * ColorVertex.STRIDE);
            long ptr = vertexBuffer;

            final float x0 = cellX * CLOUD_WIDTH;
            final float y0 = 0.0f;
            final float z0 = cellZ * CLOUD_WIDTH;

            final float x1 = x0 + CLOUD_WIDTH;
            final float y1 = y0 + CLOUD_HEIGHT;
            final float z1 = z0 + CLOUD_WIDTH;

            {
                // -Y
                final int vertexColor = ColorABGR.mulRGB(baseColor, BRIGHTNESS_NEG_Y);
                ptr = writeVertex(ptr, x1, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x0, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x0, y0, z1, vertexColor);
                ptr = writeVertex(ptr, x1, y0, z1, vertexColor);
            }

            {
                // +Y
                final int vertexColor = ColorABGR.mulRGB(baseColor, BRIGHTNESS_POS_Y);
                ptr = writeVertex(ptr, x0, y1, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z1, vertexColor);
                ptr = writeVertex(ptr, x0, y1, z1, vertexColor);
            }

            {
                final int vertexColor = ColorABGR.mulRGB(baseColor, BRIGHTNESS_X_AXIS);

                // -X
                ptr = writeVertex(ptr, x0, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x0, y1, z0, vertexColor);
                ptr = writeVertex(ptr, x0, y1, z1, vertexColor);
                ptr = writeVertex(ptr, x0, y0, z1, vertexColor);

                // +X
                ptr = writeVertex(ptr, x1, y1, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y0, z1, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z1, vertexColor);
            }

            {
                final int vertexColor = ColorABGR.mulRGB(baseColor, BRIGHTNESS_Z_AXIS);

                // -Z
                ptr = writeVertex(ptr, x0, y1, z0, vertexColor);
                ptr = writeVertex(ptr, x0, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y0, z0, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z0, vertexColor);

                // +Z
                ptr = writeVertex(ptr, x0, y0, z1, vertexColor);
                ptr = writeVertex(ptr, x0, y1, z1, vertexColor);
                ptr = writeVertex(ptr, x1, y1, z1, vertexColor);
                ptr = writeVertex(ptr, x1, y0, z1, vertexColor);
            }

            writer.push(stack, vertexBuffer, 6 * 4, ColorVertex.FORMAT);
        }
    }

    private static long writeVertex(long buffer, float x, float y, float z, int color) {
        ColorVertex.put(buffer, x, y, z, color);
        return buffer + ColorVertex.STRIDE;
    }

    private static void uploadToVertexBuffer(VertexBuffer vertexBuffer, MeshData builtBuffer) {
        vertexBuffer.bind();
        vertexBuffer.upload(builtBuffer);

        VertexBuffer.unbind();
    }

    public void reload(ResourceProvider resourceProvider) {
        this.destroy();
        this.textureData = loadTextureData(resourceProvider);
    }

    public void destroy() {
        if (this.builtGeometry != null) {
            var vertexBuffer = this.builtGeometry.vertexBuffer();

            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            this.builtGeometry = null;
        }
    }

    private static @Nullable CloudTextureData loadTextureData(ResourceProvider resourceProvider) {
        var resource = resourceProvider.getResource(CloudRenderer.CLOUDS_TEXTURE_ID)
                .orElseThrow(); // always provided by default resource pack

        try (var inputStream = resource.open();
             var nativeImage = NativeImage.read(inputStream))
        {
            return CloudTextureData.load(nativeImage);
        }
        catch (Throwable t) {
            LOGGER.error("Failed to load texture '{}'. The rendering of clouds in the skybox will be disabled. " +
                    "This may be caused by an incompatible resource pack.", CloudRenderer.CLOUDS_TEXTURE_ID, t);
            return null;
        }
    }

    private static boolean shouldUseWorldFog(ClientLevel level, Vec3 pos) {
        return level.effects().isFoggyAt(Mth.floor(pos.x()), Mth.floor(pos.z())) ||
                Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog();
    }

    private static int getCloudRenderDistance() {
        return Math.max(32, (Minecraft.getInstance().options.getEffectiveRenderDistance() * 2) + 9);
    }

    private static boolean isTransparent(int argb) {
        return ColorARGB.unpackAlpha(argb) < 10;
    }

    private static int taxicabDistance(int x, int z) {
        return Math.abs(x) + Math.abs(z);
    }

    private static FogParameters copyShaderFogParameters(FogParameters shaderFog) {
        return new FogParameters(
                shaderFog.start(),
                shaderFog.end(),
                shaderFog.shape(),
                shaderFog.red(),
                shaderFog.green(),
                shaderFog.blue(),
                shaderFog.alpha());
    }

    private static class CloudTextureData {
        private final byte[] faces;
        private final int[] colors;

        private final int width, height;

        private CloudTextureData(int width, int height) {
            this.faces = new byte[width * height];
            this.colors = new int[width * height];

            this.width = width;
            this.height = height;
        }

        public Slice slice(int originX, int originY, int radius) {
            final var src = this;
            final var dst = new CloudTextureData.Slice(radius);

            for (int dstY = 0; dstY < dst.height; dstY++) {
                int srcX = Math.floorMod(originX - radius, this.width);
                int srcY = Math.floorMod(originY - radius + dstY, this.height);

                int dstX = 0;

                while (dstX < dst.width) {
                    final int length = Math.min(src.width - srcX, dst.width - dstX);

                    final int srcPos = getCellIndex(srcX, srcY, src.width);
                    final int dstPos = getCellIndex(dstX, dstY, dst.width);

                    System.arraycopy(this.faces, srcPos, dst.faces, dstPos, length);
                    System.arraycopy(this.colors, srcPos, dst.colors, dstPos, length);

                    srcX = 0;
                    dstX += length;
                }
            }

            return dst;
        }

        public static @Nullable CloudTextureData load(NativeImage image) {
            final int width = image.getWidth();
            final int height = image.getHeight();

            var data = new CloudTextureData(width, height);

            if (!data.loadTextureData(image, width, height)) {
                return null; // The texture is empty, so it isn't necessary to render it
            }

            return data;
        }

        private boolean loadTextureData(NativeImage texture, int width, int height) {
            Validate.isTrue(this.width == width);
            Validate.isTrue(this.height == height);

            boolean containsData = false;

            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    int color = texture.getPixel(x, z);

                    if (isTransparent(color)) {
                        continue;
                    }

                    int index = getCellIndex(x, z, width);
                    this.colors[index] = color;
                    this.faces[index] = (byte) getOpenFaces(texture, color, x, z);

                    containsData = true;
                }
            }

            return containsData;
        }

        private static int getOpenFaces(NativeImage image, int color, int x, int z) {
            // Since the cloud texture is only 2D, nothing can hide the top or bottom faces
            int faces = FACE_MASK_NEG_Y | FACE_MASK_POS_Y;

            // Generate faces where the neighbor cell is a different color
            // Do not generate duplicate faces between two cells
            {
                // -X face
                int neighbor = getNeighborTexel(image, x - 1, z);

                if (color != neighbor) {
                    faces |= FACE_MASK_NEG_X;
                }
            }

            {
                // +X face
                int neighbor = getNeighborTexel(image, x + 1, z);

                if (color != neighbor) {
                    faces |= FACE_MASK_POS_X;
                }
            }

            {
                // -Z face
                int neighbor = getNeighborTexel(image, x, z - 1);

                if (color != neighbor) {
                    faces |= FACE_MASK_NEG_Z;
                }
            }

            {
                // +Z face
                int neighbor = getNeighborTexel(image, x, z + 1);

                if (color != neighbor) {
                    faces |= FACE_MASK_POS_Z;
                }
            }

            return faces;
        }

        private static int getNeighborTexel(NativeImage image, int x, int z) {
            x = wrapTexelCoord(x, 0, image.getWidth() - 1);
            z = wrapTexelCoord(z, 0, image.getHeight() - 1);

            return image.getPixel(x, z);
        }

        private static int wrapTexelCoord(int coord, int min, int max) {
            if (coord < min) {
                coord = max;
            }

            if (coord > max) {
                coord = min;
            }

            return coord;
        }

        private static int getCellIndex(int x, int z, int pitch) {
            return (z * pitch) + x;
        }

        public static class Slice {
            private final int width, height;
            private final int radius;
            private final byte[] faces;
            private final int[] colors;

            public Slice(int radius) {
                this.width = 1 + (radius * 2);
                this.height = 1 + (radius * 2);
                this.radius = radius;
                this.faces = new byte[this.width * this.height];
                this.colors = new int[this.width * this.height];
            }

            public int getCellIndex(int x, int z) {
                return CloudTextureData.getCellIndex(x + this.radius, z + this.radius, this.width);
            }

            public int getCellFaces(int index) {
                return Byte.toUnsignedInt(this.faces[index]);
            }

            public int getCellColor(int index) {
                return this.colors[index];
            }
        }
    }

    public record CloudGeometry(@Nullable VertexBuffer vertexBuffer, CloudGeometryParameters params) {

    }

    public record CloudGeometryParameters(int originX, int originZ, int radius, @Nullable ViewOrientation orientation, CloudStatus renderMode) {

    }

    private enum ViewOrientation {
        BELOW_CLOUDS,   // Top faces should *not* be rendered
        INSIDE_CLOUDS,  // All faces *must* be rendered
        ABOVE_CLOUDS;   // Bottom faces should *not* be rendered

        public static @NotNull ViewOrientation getOrientation(Vec3 camera, float minY, float maxY) {
            if (camera.y() <= minY + 0.125f /* epsilon */) {
                return ViewOrientation.BELOW_CLOUDS;
            } else if (camera.y() >= maxY - 0.125f /* epsilon */) {
                return ViewOrientation.ABOVE_CLOUDS;
            } else {
                return ViewOrientation.INSIDE_CLOUDS;
            }
        }
    }
}
