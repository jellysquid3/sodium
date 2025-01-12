package net.caffeinemc.mods.sodium.mixin.features.gui.hooks.settings;

import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Dynamic
    @Inject(method = {
            "method_19828",
            "lambda$init$2"
    }, require = 1, at = @At("HEAD"), cancellable = true)
    private void open(CallbackInfoReturnable<Screen> ci) {
        ci.setReturnValue(VideoSettingsScreen.createScreen(this));
    }
}
