package net.caffeinemc.mods.sodium.mixin.platform.neoforge;

import net.caffeinemc.mods.sodium.client.world.SodiumAuxiliaryLightManager;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AuxiliaryLightManager.class)
public interface AuxiliaryLightManagerMixin extends SodiumAuxiliaryLightManager {
}
