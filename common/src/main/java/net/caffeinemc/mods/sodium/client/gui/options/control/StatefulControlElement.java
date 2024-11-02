package net.caffeinemc.mods.sodium.client.gui.options.control;

import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;

public class StatefulControlElement<V> extends ControlElement {
    final StatefulOption<V> option;

    public StatefulControlElement(OptionListWidget list, Dim2i dim, StatefulOption<V> option) {
        super(list, dim);
        this.option = option;
    }

    @Override
    public StatefulOption<V> getOption() {
        return this.option;
    }
}
