package net.caffeinemc.mods.sodium.client.render.immediate.model;

import java.util.Set;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;

public class ModelCuboid {
    // The ordering needs to be the same as Minecraft, otherwise some core shader replacements
    // will be unable to identify the facing.
    public static final int
            FACE_NEG_Y = 0, // DOWN
            FACE_POS_Y = 1, // UP
            FACE_NEG_X = 2, // WEST
            FACE_NEG_Z = 3, // NORTH
            FACE_POS_X = 4, // EAST
            FACE_POS_Z = 5; // SOUTH

    public final float x1, y1, z1;
    public final float x2, y2, z2;

    public final float u0, u1, u2, u3, u4, u5;
    public final float v0, v1, v2;

    private final int cullBitmask;

    public final boolean mirror;

    public ModelCuboid(int u, int v,
                       float x1, float y1, float z1,
                       float sizeX, float sizeY, float sizeZ,
                       float extraX, float extraY, float extraZ,
                       boolean mirror,
                       float textureWidth, float textureHeight,
                       Set<Direction> renderDirections) {
        float x2 = x1 + sizeX;
        float y2 = y1 + sizeY;
        float z2 = z1 + sizeZ;

        x1 -= extraX;
        y1 -= extraY;
        z1 -= extraZ;

        x2 += extraX;
        y2 += extraY;
        z2 += extraZ;

        if (mirror) {
            float tmp = x2;
            x2 = x1;
            x1 = tmp;
        }

        this.x1 = x1 / 16.0f;
        this.y1 = y1 / 16.0f;
        this.z1 = z1 / 16.0f;

        this.x2 = x2 / 16.0f;
        this.y2 = y2 / 16.0f;
        this.z2 = z2 / 16.0f;

        var scaleU = 1.0f / textureWidth;
        var scaleV = 1.0f / textureHeight;

        this.u0 = scaleU * (u);
        this.u1 = scaleU * (u + sizeZ);
        this.u2 = scaleU * (u + sizeZ + sizeX);
        this.u3 = scaleU * (u + sizeZ + sizeX + sizeX);
        this.u4 = scaleU * (u + sizeZ + sizeX + sizeZ);
        this.u5 = scaleU * (u + sizeZ + sizeX + sizeZ + sizeX);

        this.v0 = scaleV * (v);
        this.v1 = scaleV * (v + sizeZ);
        this.v2 = scaleV * (v + sizeZ + sizeY);

        this.mirror = mirror;

        int cullBitmask = 0;

        for (var direction : renderDirections) {
            cullBitmask |= 1 << getFaceIndex(direction);
        }

        this.cullBitmask = cullBitmask;
    }

    public boolean shouldDrawFace(int faceIndex) {
        return (this.cullBitmask & (1 << faceIndex)) != 0;
    }

    public static int getFaceIndex(@NotNull Direction dir) {
        return switch (dir) {
            case DOWN -> FACE_NEG_Y;
            case UP -> FACE_POS_Y;
            case NORTH -> FACE_NEG_Z;
            case SOUTH -> FACE_POS_Z;
            case WEST -> FACE_NEG_X;
            case EAST -> FACE_POS_X;
        };
    }
}
