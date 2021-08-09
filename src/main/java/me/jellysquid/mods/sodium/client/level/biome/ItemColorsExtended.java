package me.jellysquid.mods.sodium.client.level.biome;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;

public interface ItemColorsExtended {
    ItemColor getColorProvider(ItemStack stack);
}
