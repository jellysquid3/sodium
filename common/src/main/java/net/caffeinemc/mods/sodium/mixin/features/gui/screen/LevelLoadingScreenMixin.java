package net.caffeinemc.mods.sodium.mixin.features.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.*;

/**
 * Re-implements the loading screen with considerations to reduce draw calls and other sources of overhead. This can
 * improve world load times on slower processors with very few cores.
 */
@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
    @Mutable
    @Shadow
    @Final
    private static Object2IntMap<ChunkStatus> COLORS;

    @Unique
    private static Reference2IntOpenHashMap<ChunkStatus> STATUS_TO_COLOR_FAST;

    @Unique
    private static final int NULL_STATUS_COLOR = ColorABGR.pack(0, 0, 0, 0xFF);

    @Unique
    private static final int DEFAULT_STATUS_COLOR = ColorABGR.pack(0, 0x11, 0xFF, 0xFF);

    /**
     * This implementation differs from vanilla's in the following key ways.
     * - All tiles are batched together in one draw call, reducing CPU overhead by an order of magnitudes.
     * - Reference hashing is used for faster ChunkStatus -> Color lookup.
     * - Colors are stored in ABGR format so conversion is not necessary every tile draw.
     * New optimizations:
     * - Render a rectangle of NULL_STATUS_COLOR as background and any 'null' statuses are ignored.
     * - Iterate in a spiral inwards->outwards and terminate early if all non-null statuses have been drawn.
     * - Tiles are combined into bigger rectangles to massively reduce the amount of quads rendered.
     *
     * @reason Significantly optimized implementation.
     * @author JellySquid, contaria
     */
    @Overwrite
    public static void renderChunks(GuiGraphics graphics, StoringChunkProgressListener tracker, int mapX, int mapY, int mapScale, int mapPadding) {
        if (STATUS_TO_COLOR_FAST == null) {
            STATUS_TO_COLOR_FAST = new Reference2IntOpenHashMap<>(COLORS.size());
            STATUS_TO_COLOR_FAST.put(null, NULL_STATUS_COLOR);
            COLORS.object2IntEntrySet()
                    .forEach(entry -> STATUS_TO_COLOR_FAST.put(entry.getKey(), ColorARGB.toABGR(entry.getIntValue(), 0xFF)));
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();

        Tesselator tessellator = Tesselator.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        var writer = VertexBufferWriter.of(bufferBuilder);

        int centerSize = tracker.getFullDiameter();
        int size = tracker.getDiameter();

        int tileSize = mapScale + mapPadding;

        if (mapPadding != 0) {
            int mapRenderCenterSize = centerSize * tileSize - mapPadding;
            int radius = mapRenderCenterSize / 2 + 1;

            addRect(writer, matrix, mapX - radius, mapY - radius, mapX - radius + 1, mapY + radius, DEFAULT_STATUS_COLOR);
            addRect(writer, matrix, mapX + radius - 1, mapY - radius, mapX + radius, mapY + radius, DEFAULT_STATUS_COLOR);
            addRect(writer, matrix, mapX - radius, mapY - radius, mapX + radius, mapY - radius + 1, DEFAULT_STATUS_COLOR);
            addRect(writer, matrix, mapX - radius, mapY + radius - 1, mapX + radius, mapY + radius, DEFAULT_STATUS_COLOR);
        }

        int mapRenderSize = size * mapScale;
        int mapStartX = mapX - mapRenderSize / 2;
        int mapStartY = mapY - mapRenderSize / 2;

        // Draw one rectangle covering the entire background
        // This allows us to ignore 'null' statuses
        addRect(writer, matrix, mapStartX, mapStartY, mapStartX + size * tileSize, mapStartY + size * tileSize, NULL_STATUS_COLOR);

        // Count drawn 'statuses' and terminate early if 'total' is reached
        int total = ((StoringChunkProgressListenerAccessor) tracker).getStatuses().size();
        int statuses = 0;

        int direction = -1;
        int x = size / 2;
        int z = size / 2;
        int tileX = mapStartX + x * tileSize;
        int tileY = mapStartY + z * tileSize;

        // Try to combine inner tiles into one rectangle
        boolean drawingInnerRect = false;
        ChunkStatus prevStatus = tracker.getStatus(x, z);
        if (prevStatus != null) {
            drawingInnerRect = true;
            statuses++;
        }

        // Tile combining breaks mapPadding, but it's always 0 in vanilla
        // It's original use was probably for debugging by separating tiles visually
        // We can create our own debug view by replacing '+ tileSize' with '+ mapScale'
        // and subtracting 'mapPadding' from x2 and y2 when drawing the inner rectangle
        // This also wouldn't change rendering when mapPadding is 0

        // Iterate over statuses inwards->outwards in a spiral.
        // Travel along the x-axis, then the y-axis, then reverse direction
        for (int i = 1; i <= size && statuses < total; i++) {
            int fromX = tileX;
            for (int j = 0; j < i && statuses < total; j++) {
                x += direction;
                tileX = mapStartX + x * tileSize;

                ChunkStatus status = tracker.getStatus(x, z);
                if (status != null) {
                    statuses++;
                }
                if (prevStatus == status) {
                    // Combine this tile with the previous one
                    continue;
                }
                if (drawingInnerRect) {
                    // Draw a rectangle covering all the iterated tiles except the ones from the current loop
                    int rectStart = (size / 2 - i / 2) * tileSize;
                    int x1 = mapStartX + rectStart;
                    int y1 = mapStartY + rectStart - ((i - 1) % 2) * tileSize;
                    addRect(writer, matrix, x1, y1, x1 + i * tileSize, y1 + (i - 1) * tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
                    drawingInnerRect = false;
                }
                if (prevStatus != null) {
                    int toX = tileX - tileSize * direction;
                    addRect(writer, matrix, Math.min(fromX, toX), tileY, Math.max(fromX, toX) + tileSize, tileY + tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
                }
                prevStatus = status;
                fromX = tileX;
            }
            // Draw on direction change unless the inner rectangle is being combined
            if (prevStatus != null && !drawingInnerRect) {
                addRect(writer, matrix, Math.min(fromX, tileX), tileY, Math.max(fromX, tileX) + tileSize, tileY + tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
                prevStatus = null;
            }

            int fromY = tileY;
            for (int j = 0; j < i && statuses < total; j++) {
                z += direction;
                tileY = mapStartY + z * tileSize;

                ChunkStatus status = tracker.getStatus(x, z);
                if (status != null) {
                    statuses++;
                }
                if (prevStatus == status) {
                    // Combine this tile with the previous one
                    continue;
                }
                if (drawingInnerRect) {
                    // Draw a rectangle covering all the iterated tiles except the ones from the current loop
                    int rectStart = (size / 2 - i / 2) * tileSize;
                    int x1 = mapStartX + rectStart;
                    int y1 = mapStartY + rectStart;
                    addRect(writer, matrix, x1, y1, x1 + i * tileSize, y1 + i * tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
                    drawingInnerRect = false;
                }
                if (prevStatus != null) {
                    int toY = tileY - tileSize * direction;
                    addRect(writer, matrix, tileX, Math.min(fromY, toY), tileX + tileSize, Math.max(fromY, toY) + tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
                }
                prevStatus = status;
                fromY = tileY;
            }
            // Draw on direction change unless the inner rectangle is being combined
            if (prevStatus != null && !drawingInnerRect) {
                addRect(writer, matrix, tileX, Math.min(fromY, tileY), tileX + tileSize, Math.max(fromY, tileY) + tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
                prevStatus = null;
            }

            direction = -direction;
        }
        // This code will only run if the entire chunk map is the same status
        if (drawingInnerRect) {
            addRect(writer, matrix, mapStartX, mapStartY, mapStartX + size * tileSize, mapStartY + size * tileSize, STATUS_TO_COLOR_FAST.getInt(prevStatus));
        }

        MeshData data = bufferBuilder.build();

        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        Tesselator.getInstance().clear();

        RenderSystem.disableBlend();
    }

    @Unique
    private static void addRect(VertexBufferWriter writer, Matrix4f matrix, int x1, int y1, int x2, int y2, int color) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * ColorVertex.STRIDE);
            long ptr = buffer;

            ColorVertex.put(ptr, matrix, x1, y2, 0, color);
            ptr += ColorVertex.STRIDE;

            ColorVertex.put(ptr, matrix, x2, y2, 0, color);
            ptr += ColorVertex.STRIDE;

            ColorVertex.put(ptr, matrix, x2, y1, 0, color);
            ptr += ColorVertex.STRIDE;

            ColorVertex.put(ptr, matrix, x1, y1, 0, color);
            ptr += ColorVertex.STRIDE;

            writer.push(stack, buffer, 4, ColorVertex.FORMAT);
        }
    }
}
