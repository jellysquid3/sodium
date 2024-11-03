package net.caffeinemc.mods.sodium.client.render.immediate;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class CloudRenderer {
    private static final ResourceLocation CLOUDS_TEXTURE_ID = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    public static final ShaderProgram CLOUDS = new ShaderProgram(ResourceLocation.fromNamespaceAndPath("sodium", "clouds"), DefaultVertexFormat.POSITION_COLOR, ShaderDefines.builder().build());

    private CloudTextureData textureData;

    private @Nullable CloudRenderer.CloudGeometry cachedGeometry;

    public CloudRenderer(ResourceProvider resourceProvider) {
        this.reloadTextures(resourceProvider);
    }

    public void render(Camera camera,
                       ClientLevel level,
                       Matrix4f projectionMatrix,
                       Matrix4f modelView,
                       float ticks,
                       float tickDelta, int color)
    {
        float cloudHeight = level.effects().getCloudHeight();

        // Vanilla uses NaN height as a way to disable cloud rendering
        if (Float.isNaN(cloudHeight)) {
            return;
        }

        // Skip rendering clouds if texture is completely blank
        if (this.textureData.isBlank) {
            return;
        }

        Vec3 pos = camera.getPosition();

        double cloudTime = (ticks + tickDelta) * 0.03F;
        double cloudCenterX = (pos.x() + cloudTime);
        double cloudCenterZ = (pos.z()) + 0.33D;

        int cloudDistance = getCloudRenderDistance();

        int centerCellX = (int) (Math.floor(cloudCenterX / 12.0));
        int centerCellZ = (int) (Math.floor(cloudCenterZ / 12.0));

        // -1 if below clouds, +1 if above clouds
        var cloudType = Minecraft.getInstance().options.getCloudsType();
        var relativeCloudY = cloudHeight - (float) pos.y() + 0.33F;
        int orientation = cloudType == CloudStatus.FANCY ? (int) Math.signum(-relativeCloudY) : 0;
        var parameters = new CloudGeometryParameters(centerCellX, centerCellZ, cloudDistance, orientation, cloudType);

        CloudGeometry geometry = this.cachedGeometry;

        if (geometry == null || !Objects.equals(geometry.params(), parameters)) {
            this.cachedGeometry = (geometry = rebuildGeometry(geometry, parameters, this.textureData));
        }

        VertexBuffer vertexBuffer = geometry.vertexBuffer();
        if (vertexBuffer == null) {
            return;
        }

        final float translateX = (float) (cloudCenterX - (centerCellX * 12));
        final float translateZ = (float) (cloudCenterZ - (centerCellZ * 12));

        Matrix4f modelViewMatrix = new Matrix4f(modelView);
        modelViewMatrix.translate(-translateX, relativeCloudY, -translateZ);

        final var prevShaderFog = copyFog(RenderSystem.getShaderFog());

        Vector4f fogColor = FogRenderer.computeFogColor(camera, tickDelta, level, cloudDistance * 8, Minecraft.getInstance().gameRenderer.getDarkenWorldAmount(tickDelta));
        FogParameters fogParameters = FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, fogColor, cloudDistance * 8, shouldUseWorldFog(level, pos), tickDelta);
        RenderSystem.setShaderFog(fogParameters);

        boolean fastClouds = geometry.params().renderMode() == CloudStatus.FAST;
        boolean fabulous = Minecraft.useShaderTransparency();

        if (fastClouds) {
            RenderSystem.disableCull();
        }

        RenderSystem.setShaderColor(ARGB.from8BitChannel(ARGB.red(color)), ARGB.from8BitChannel(ARGB.green(color)), ARGB.from8BitChannel(ARGB.blue(color)), 0.8F);

        vertexBuffer.bind();

        RenderType.clouds().setupRenderState();
        RenderSystem.depthFunc(GL32C.GL_LESS);

        RenderSystem.setShader(CLOUDS);

        vertexBuffer.drawWithShader(modelViewMatrix, projectionMatrix, RenderSystem.getShader());

        RenderSystem.depthFunc(GL32C.GL_LEQUAL);

        VertexBuffer.unbind();

        if (fastClouds) {
            RenderSystem.enableCull();
        }

        RenderType.clouds().clearRenderState();


        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.setShaderFog(prevShaderFog);
    }

    private FogParameters copyFog(FogParameters shaderFog) {
        return new FogParameters(shaderFog.start(), shaderFog.end(), shaderFog.shape(), shaderFog.red(), shaderFog.green(), shaderFog.blue(), shaderFog.alpha());
    }

    private static @NotNull CloudGeometry rebuildGeometry(@Nullable CloudGeometry existingGeometry,
                                                          CloudGeometryParameters parameters,
                                                          CloudTextureData textureData)
    {
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        var writer = VertexBufferWriter.of(bufferBuilder);

        var originCellX = parameters.originX();
        var originCellZ = parameters.originZ();

        var orientation = parameters.orientation();

        var radius = parameters.radius();
        var useFastGraphics = parameters.renderMode() == CloudStatus.FAST;

        addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, 0, 0, orientation, useFastGraphics);

        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, x, z, orientation, useFastGraphics);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, x, z, orientation, useFastGraphics);
            }
        }

        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, x, z, orientation, useFastGraphics);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, x, z, orientation, useFastGraphics);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, x, z, orientation, useFastGraphics);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                addCellGeometryToBuffer(writer, textureData, originCellX, originCellZ, x, z, orientation, useFastGraphics);
            }
        }

        MeshData builtBuffer = bufferBuilder.build();

        VertexBuffer vertexBuffer = null;

        if (builtBuffer != null) {
            if (existingGeometry != null) {
                vertexBuffer = existingGeometry.vertexBuffer();
            }
            if (vertexBuffer == null) {
                vertexBuffer = new VertexBuffer(BufferUsage.DYNAMIC_WRITE);
            }

            uploadToVertexBuffer(vertexBuffer, builtBuffer);
        }

        Tesselator.getInstance().clear();

        return new CloudGeometry(vertexBuffer, parameters);
    }

    private static void addCellGeometryToBuffer(VertexBufferWriter writer,
                                                CloudTextureData textureData,
                                                int originX,
                                                int originZ,
                                                int offsetX,
                                                int offsetZ,
                                                int orientation,
                                                boolean useFastGraphics) {
        int cellX = originX + offsetX;
        int cellZ = originZ + offsetZ;

        int cellIndex = textureData.getCellIndexWrapping(cellX, cellZ);
        int cellFaces = textureData.getCellFaces(cellIndex) & getVisibleFaces(offsetX, offsetZ, orientation);

        if (cellFaces == 0) {
            return;
        }

        int cellColor = textureData.getCellColor(cellIndex);

        if (ColorABGR.unpackAlpha(cellColor) == 0) {
            return;
        }

        float x = offsetX * 12;
        float z = offsetZ * 12;

        if (useFastGraphics) {
            emitCellGeometry2D(writer, cellFaces, cellColor, x, z);
        } else {
            emitCellGeometry3D(writer, cellFaces, cellColor, x, z, false);

            int distance = Math.abs(offsetX) + Math.abs(offsetZ);

            if (distance <= 1) {
                emitCellGeometry3D(writer, CloudFaceSet.all(), cellColor, x, z, true);
            }
        }
    }

    private static int getVisibleFaces(int x, int z, int orientation) {
        int faces = CloudFaceSet.all();

        if (x > 0) {
            faces = CloudFaceSet.remove(faces, CloudFace.POS_X);
        }

        if (z > 0) {
            faces = CloudFaceSet.remove(faces, CloudFace.POS_Z);
        }

        if (x < 0) {
            faces = CloudFaceSet.remove(faces, CloudFace.NEG_X);
        }

        if (z < 0) {
            faces = CloudFaceSet.remove(faces, CloudFace.NEG_Z);
        }

        if (orientation < 0) {
            faces = CloudFaceSet.remove(faces, CloudFace.POS_Y);
        }

        if (orientation > 0) {
            faces = CloudFaceSet.remove(faces, CloudFace.NEG_Y);
        }

        return faces;
    }

    private static final Vector3f[][] VERTICES = new Vector3f[CloudFace.COUNT][];

    static {
        VERTICES[CloudFace.NEG_Y.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 0.0f, 12.0f),
                new Vector3f( 0.0f, 0.0f, 12.0f),
                new Vector3f( 0.0f, 0.0f,  0.0f),
                new Vector3f(12.0f, 0.0f,  0.0f)
        };

        VERTICES[CloudFace.POS_Y.ordinal()] = new Vector3f[] {
                new Vector3f( 0.0f, 4.0f, 12.0f),
                new Vector3f(12.0f, 4.0f, 12.0f),
                new Vector3f(12.0f, 4.0f,  0.0f),
                new Vector3f( 0.0f, 4.0f,  0.0f)
        };

        VERTICES[CloudFace.NEG_X.ordinal()] = new Vector3f[] {
                new Vector3f( 0.0f, 0.0f, 12.0f),
                new Vector3f( 0.0f, 4.0f, 12.0f),
                new Vector3f( 0.0f, 4.0f,  0.0f),
                new Vector3f( 0.0f, 0.0f,  0.0f)
        };

        VERTICES[CloudFace.POS_X.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 4.0f, 12.0f),
                new Vector3f(12.0f, 0.0f, 12.0f),
                new Vector3f(12.0f, 0.0f,  0.0f),
                new Vector3f(12.0f, 4.0f,  0.0f)
        };

        VERTICES[CloudFace.NEG_Z.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 4.0f,  0.0f),
                new Vector3f(12.0f, 0.0f,  0.0f),
                new Vector3f( 0.0f, 0.0f,  0.0f),
                new Vector3f( 0.0f, 4.0f,  0.0f)
        };

        VERTICES[CloudFace.POS_Z.ordinal()] = new Vector3f[] {
                new Vector3f(12.0f, 0.0f, 12.0f),
                new Vector3f(12.0f, 4.0f, 12.0f),
                new Vector3f( 0.0f, 4.0f, 12.0f),
                new Vector3f( 0.0f, 0.0f, 12.0f)
        };
    }

    private static void emitCellGeometry2D(VertexBufferWriter writer, int faces, int color, float x, float z) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long buffer = stack.nmalloc(4 * ColorVertex.STRIDE);

            long ptr = buffer;
            int count = 0;

            int mixedColor = ColorMixer.mul(color, CloudFace.POS_Y.getColor());

            ptr = writeVertex(ptr, x + 12.0f, 0.0f, z + 12.0f, mixedColor);
            ptr = writeVertex(ptr, x +  0.0f, 0.0f, z + 12.0f, mixedColor);
            ptr = writeVertex(ptr, x +  0.0f, 0.0f, z +  0.0f, mixedColor);
            ptr = writeVertex(ptr, x + 12.0f, 0.0f, z +  0.0f, mixedColor);

            count += 4;

            writer.push(stack, buffer, count, ColorVertex.FORMAT);
        }
    }

    private static void emitCellGeometry3D(VertexBufferWriter writer, int visibleFaces, int baseColor, float posX, float posZ, boolean interior) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final long buffer = stack.nmalloc(6 * 4 * ColorVertex.STRIDE);

            long ptr = buffer;
            int count = 0;

            for (var face : CloudFace.VALUES) {
                if (!CloudFaceSet.contains(visibleFaces, face)) {
                    continue;
                }

                final var vertices = VERTICES[face.ordinal()];
                final int color = ColorMixer.mul(baseColor, face.getColor());

                for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                    Vector3f vertex = vertices[interior ? 3 - vertexIndex : vertexIndex];

                    final float x = vertex.x + posX;
                    final float y = vertex.y;
                    final float z = vertex.z + posZ;

                    ptr = writeVertex(ptr, x, y, z, color);
                }

                count += 4;
            }

            if (count > 0) {
                writer.push(stack, buffer, count, ColorVertex.FORMAT);
            }
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

    public void reloadTextures(ResourceProvider resourceProvider) {
        this.destroy();

        this.textureData = loadTextureData();
    }

    public void destroy() {
        if (this.cachedGeometry != null) {
            var vertexBuffer = this.cachedGeometry.vertexBuffer();
            vertexBuffer.close();

            this.cachedGeometry = null;
        }
    }

    private static CloudTextureData loadTextureData() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Resource resource = resourceManager.getResource(CLOUDS_TEXTURE_ID)
                .orElseThrow();

        try (InputStream inputStream = resource.open()){
            try (NativeImage nativeImage = NativeImage.read(inputStream)) {
                return new CloudTextureData(nativeImage);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load texture data", ex);
        }
    }

    private static boolean shouldUseWorldFog(ClientLevel level, Vec3 pos) {
        return level.effects().isFoggyAt(Mth.floor(pos.x()), Mth.floor(pos.z())) ||
                Minecraft.getInstance().gui.getBossOverlay().shouldCreateWorldFog();
    }

    private static int getCloudRenderDistance() {
        return Math.max(32, (Minecraft.getInstance().options.getEffectiveRenderDistance() * 2) + 9);
    }

    private enum CloudFace {
        NEG_Y(ColorABGR.pack(0.7F, 0.7F, 0.7F, 1.0f)),
        POS_Y(ColorABGR.pack(1.0f, 1.0f, 1.0f, 1.0f)),
        NEG_X(ColorABGR.pack(0.9F, 0.9F, 0.9F, 1.0f)),
        POS_X(ColorABGR.pack(0.9F, 0.9F, 0.9F, 1.0f)),
        NEG_Z(ColorABGR.pack(0.8F, 0.8F, 0.8F, 1.0f)),
        POS_Z(ColorABGR.pack(0.8F, 0.8F, 0.8F, 1.0f));

        public static final CloudFace[] VALUES = CloudFace.values();
        public static final int COUNT = VALUES.length;

        private final int color;

        CloudFace(int color) {
            this.color = color;
        }

        public int getColor() {
            return this.color;
        }
    }

    private static class CloudFaceSet {
        public static int empty() {
            return 0;
        }

        public static boolean contains(int set, CloudFace face) {
            return (set & (1 << face.ordinal())) != 0;
        }

        public static int add(int set, CloudFace face) {
            return set | (1 << face.ordinal());
        }

        public static int remove(int set, CloudFace face) {
            return set & ~(1 << face.ordinal());
        }

        public static int all() {
            return (1 << CloudFace.COUNT) - 1;
        }
    }

    private static boolean isTransparentCell(int color) {
        return ColorARGB.unpackAlpha(color) <= 1;
    }

    private static class CloudTextureData {
        private final byte[] faces;
        private final int[] colors;
        private boolean isBlank;

        private final int width, height;

        public CloudTextureData(NativeImage texture) {
            int width = texture.getWidth();
            int height = texture.getHeight();

            this.faces = new byte[width * height];
            this.colors = new int[width * height];
            this.isBlank = true;

            this.width = width;
            this.height = height;

            this.loadTextureData(texture, width, height);
        }

        private void loadTextureData(NativeImage texture, int width, int height) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    int index = this.getCellIndex(x, z);
                    int color = texture.getPixel(x, z);

                    this.colors[index] = color;

                    if (!isTransparentCell(color)) {
                        this.faces[index] = (byte) getOpenFaces(texture, color, x, z);
                        this.isBlank = false;
                    }
                }
            }
        }

        private static int getOpenFaces(NativeImage image, int color, int x, int z) {
            // Since the cloud texture is only 2D, nothing can hide the top or bottom faces
            int faces = CloudFaceSet.empty();
            faces = CloudFaceSet.add(faces, CloudFace.NEG_Y);
            faces = CloudFaceSet.add(faces, CloudFace.POS_Y);

            // Generate faces where the neighbor cell is a different color
            // Do not generate duplicate faces between two cells
            {
                // -X face
                int neighbor = getNeighborTexel(image, x - 1, z);

                if (color != neighbor) {
                    faces = CloudFaceSet.add(faces, CloudFace.NEG_X);
                }
            }

            {
                // +X face
                int neighbor = getNeighborTexel(image, x + 1, z);

                if (color != neighbor) {
                    faces = CloudFaceSet.add(faces, CloudFace.POS_X);
                }
            }

            {
                // -Z face
                int neighbor = getNeighborTexel(image, x, z - 1);

                if (color != neighbor) {
                    faces = CloudFaceSet.add(faces, CloudFace.NEG_Z);
                }
            }

            {
                // +Z face
                int neighbor = getNeighborTexel(image, x, z + 1);

                if (color != neighbor) {
                    faces = CloudFaceSet.add(faces, CloudFace.POS_Z);
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

        public int getCellFaces(int index) {
            return this.faces[index];
        }

        public int getCellColor(int index) {
            return this.colors[index];
        }

        private int getCellIndexWrapping(int x, int z) {
            return this.getCellIndex(
                    Math.floorMod(x, this.width),
                    Math.floorMod(z, this.height)
            );
        }

        private int getCellIndex(int x, int z) {
            return (x * this.width) + z;
        }
    }

    public record CloudGeometry(VertexBuffer vertexBuffer, CloudGeometryParameters params) {

    }

    public record CloudGeometryParameters(int originX, int originZ, int radius, int orientation, CloudStatus renderMode) {

    }
}
