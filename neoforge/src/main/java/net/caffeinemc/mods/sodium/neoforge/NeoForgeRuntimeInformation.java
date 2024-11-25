package net.caffeinemc.mods.sodium.neoforge;

import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

import java.nio.file.Path;

public class NeoForgeRuntimeInformation implements PlatformRuntimeInformation {
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean platformHasEarlyLoadingScreen() {
        return true;
    }

    @Override
    public boolean platformUsesRefmap() {
        return false;
    }

    @Override
    public boolean isModInLoadingList(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public boolean usesAlphaMultiplication() {
        return true;
    }
}
