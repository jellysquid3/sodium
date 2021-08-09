package me.jellysquid.mods.sodium.mixin.features.matrix_stack;

import me.jellysquid.mods.sodium.client.util.math.Matrix3fExtended;
import me.jellysquid.mods.sodium.client.util.math.Matrix4fExtended;
import me.jellysquid.mods.sodium.client.util.math.MatrixUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import java.util.Deque;

@Mixin(PoseStack.class)
public class MixinPoseStack {
    @Shadow
    @Final
    private Deque<PoseStack.Pose> poseStack;

    /**
     * @reason Use our faster specialized function
     * @author JellySquid
     */
    @Overwrite
    public void translate(double x, double y, double z) {
        PoseStack.Pose entry = this.poseStack.getLast();

        Matrix4fExtended mat = MatrixUtil.getExtendedMatrix(entry.pose());
        mat.translate((float) x, (float) y, (float) z);
    }

    /**
     * @reason Use our faster specialized function
     * @author JellySquid
     */
    @Overwrite
    public void mulPose(Quaternion q) {
        PoseStack.Pose entry = this.poseStack.getLast();

        Matrix4fExtended mat4 = MatrixUtil.getExtendedMatrix(entry.pose());
        mat4.rotate(q);

        Matrix3fExtended mat3 = MatrixUtil.getExtendedMatrix(entry.normal());
        mat3.rotate(q);
    }
}
