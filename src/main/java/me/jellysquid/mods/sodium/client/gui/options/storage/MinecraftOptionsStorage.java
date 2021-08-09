package me.jellysquid.mods.sodium.client.gui.options.storage;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public class MinecraftOptionsStorage implements OptionStorage<Options> {
    private final Minecraft minecraft;

    public MinecraftOptionsStorage() {
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public Options getData() {
        return this.minecraft.options;
    }

    @Override
    public void save() {
        this.getData().save();

        SodiumClientMod.logger().info("Flushed changes to Minecraft configuration");
    }
}
