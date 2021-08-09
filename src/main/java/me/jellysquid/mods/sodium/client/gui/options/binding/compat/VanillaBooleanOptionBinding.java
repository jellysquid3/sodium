package me.jellysquid.mods.sodium.client.gui.options.binding.compat;

import me.jellysquid.mods.sodium.client.gui.options.binding.OptionBinding;
import net.minecraft.client.CycleOption;
import net.minecraft.client.Options;

public class VanillaBooleanOptionBinding implements OptionBinding<Options, Boolean> {
    private final CycleOption<Boolean> option;

    public VanillaBooleanOptionBinding(CycleOption<Boolean> option) {
        this.option = option;
    }

    @Override
    public void setValue(Options storage, Boolean value) {
        this.option.setter.accept(storage, this.option, value);
    }

    @Override
    public Boolean getValue(Options storage) {
        return this.option.getter.apply(storage);
    }
}
