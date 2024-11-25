package net.caffeinemc.mods.sodium.mixin.features.model;

import com.google.common.collect.ImmutableList;
import net.caffeinemc.mods.sodium.client.util.WeightedRandomListExtension;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandomList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WeightedRandomList.class)
public class WeightedRandomListMixin<E extends WeightedEntry> implements WeightedRandomListExtension<E> {
    @Shadow
    @Final
    private ImmutableList<E> items;

    @Shadow
    @Final
    private int totalWeight;

    @Override
    public E sodium$getQuick(RandomSource random) {
        if (this.totalWeight == 0) return null;

        int randomValue = Math.abs((int) random.nextLong()) % this.totalWeight;

        int i = 0;
        int len = items.size();

        E weighted;

        do {
            if (i >= len) {
                return null;
            }

            weighted = items.get(i++);
            randomValue -= weighted.getWeight().asInt();
        } while (randomValue >= 0);

        return weighted;
    }
}
