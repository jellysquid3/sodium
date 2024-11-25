package net.caffeinemc.mods.sodium.mixin.features.shader.uniform;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * On the NVIDIA drivers (and maybe some others), the OpenGL submission thread requires expensive state synchronization
 * to happen when glGetUniformLocation and glGetInteger are called. In our case, this is rather unnecessary, since
 * these uniform locations can be trivially cached.
 */
@Mixin(CompiledShaderProgram.class)
public class ShaderInstanceMixin {

}
