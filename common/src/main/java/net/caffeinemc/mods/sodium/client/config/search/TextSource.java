package net.caffeinemc.mods.sodium.client.config.search;

public abstract class TextSource {
    private String text;

    protected abstract String getTextFromSource();

    public String getText() {
        if (this.text == null) {
            this.text = this.getTextFromSource();
        }

        return this.text;
    }

    public int getLength() {
        return this.getText().length();
    }
}
