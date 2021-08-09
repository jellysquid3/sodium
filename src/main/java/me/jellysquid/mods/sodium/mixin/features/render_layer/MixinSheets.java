package me.jellysquid.mods.sodium.mixin.features.render_layer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.state.properties.WoodType;

@Mixin(Sheets.class)
public class MixinSheets {
    @Shadow
    @Final
    public static Map<WoodType, Material> SIGN_MATERIALS;

    // Instantiating a Material every time a sign tries to grab a texture identifier causes a significant
    // performance impact as no RenderType will ever be cached for the sprite. Minecraft already maintains a
    // WoodType -> Material cache but for some reason doesn't use it.
    @Inject(method = "getSignMaterial", at = @At("HEAD"), cancellable = true)
    private static void preGetSignTextureId(WoodType type, CallbackInfoReturnable<Material> ci) {
        if (SIGN_MATERIALS != null) {
            Material sprite = SIGN_MATERIALS.get(type);

            if (type != null) {
                ci.setReturnValue(sprite);
            }
        }
    }
}
