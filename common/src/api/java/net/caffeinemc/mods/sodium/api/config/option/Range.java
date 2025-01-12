package net.caffeinemc.mods.sodium.api.config.option;

public record Range(int min, int max, int step) {
    public Range {
        if (min > max) {
            throw new IllegalArgumentException("Min must be less than or equal to max");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("Step must be greater than 0");
        }
    }

    public boolean isValueValid(int value) {
        return value >= this.min && value <= this.max && (value - this.min) % this.step == 0;
    }

    public int getSpread() {
        return this.max - this.min;
    }
}
