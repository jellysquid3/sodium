package net.caffeinemc.mods.sodium.api.config.option;

public record Range(int min, int max, int step) {
    public boolean isValueValid(int value) {
        return value >= this.min && value <= this.max && (value - this.min) % this.step == 0;
    }
}
