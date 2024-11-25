package net.caffeinemc.mods.sodium.neoforge;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.render.frapi.SodiumRenderer;
import net.caffeinemc.mods.sodium.client.util.FlawlessFrames;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforgespi.language.IModInfo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;

@Mod(value = "sodium", dist = Dist.CLIENT)
public class SodiumForgeMod {
    public SodiumForgeMod(IEventBus bus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, screen) -> SodiumOptionsGUI.createScreen(screen));
        Renderer.register(SodiumRenderer.INSTANCE);

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (IModInfo mod : ModList.get().getMods()) {
            String handler = (String) mod.getModProperties().getOrDefault("frex:flawless_frames_handler", null);

            if (handler == null) {
                continue;
            }

            try {
                lookup.findStatic(Class.forName(handler), "acceptController", MethodType.methodType(void.class, Function.class)).invoke(FlawlessFrames.getProvider());
            } catch (Throwable e) {
                throw new RuntimeException("Failed to execute Flawless Frames handler for mod " + mod.getModId() + "!", e);
            }
        }
    }
}