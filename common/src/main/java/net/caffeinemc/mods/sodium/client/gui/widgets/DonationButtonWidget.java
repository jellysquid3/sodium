package net.caffeinemc.mods.sodium.client.gui.widgets;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.ButtonTheme;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

public class DonationButtonWidget {
    private static final int DONATE_BUTTON_WIDTH = 100;
    private static final int CLOSE_BUTTON_MARGIN = 3;

    private static final ResourceLocation CUP_TEXTURE = ResourceLocation.fromNamespaceAndPath("sodium", "textures/gui/coffee_cup.png");
    private static final int CUP_SPRITE_SIZE = 10;

    private final FlatButtonWidget hideDonateButton;
    private final FlatButtonWidget donateButtonLong;
    private final FlatButtonWidget donateButtonCompact;
    private boolean donateButtonEnabled = !SodiumClientMod.options().notifications.hasClearedDonationButton;

    private final Collection<FlatButtonWidget> colliders;

    public DonationButtonWidget(Collection<FlatButtonWidget> colliders, int width, Runnable openDonationPage, Consumer<AbstractWidget> widgetConsumer) {
        this.colliders = colliders;

        this.hideDonateButton = new FlatButtonWidget(new Dim2i(width - Layout.BUTTON_SHORT - Layout.INNER_MARGIN, Layout.INNER_MARGIN, Layout.BUTTON_SHORT, Layout.BUTTON_SHORT), Component.literal("x"), this::hideDonationButton, true, false);
        var infoButtonOffset = this.hideDonateButton.getX() - CLOSE_BUTTON_MARGIN;

        this.donateButtonLong = new FlatButtonWidget(new Dim2i(infoButtonOffset - DONATE_BUTTON_WIDTH, Layout.INNER_MARGIN, DONATE_BUTTON_WIDTH, Layout.BUTTON_SHORT), Component.translatable("sodium.options.buttons.donate"), openDonationPage, true, false);
        this.donateButtonCompact = new IconButtonWidget(new Dim2i(infoButtonOffset - Layout.BUTTON_SHORT, Layout.INNER_MARGIN, Layout.BUTTON_SHORT, Layout.BUTTON_SHORT), CUP_TEXTURE, CUP_SPRITE_SIZE, openDonationPage, true, false);

        widgetConsumer.accept(this.hideDonateButton);
        widgetConsumer.accept(this.donateButtonLong);
        widgetConsumer.accept(this.donateButtonCompact);

        this.updateDisplay();
    }

    public void updateDisplay() {
        if (!this.donateButtonEnabled) {
            this.setButtonState(ButtonState.HIDDEN);
            return;
        }

        var maxCollidingX = 0;
        for (var collider : this.colliders) {
            if (collider.isVisible()) {
                maxCollidingX = Math.max(maxCollidingX, collider.getLimitX());
            }
        }
        maxCollidingX += Layout.INNER_MARGIN;

        if (maxCollidingX <= this.donateButtonLong.getX()) {
            this.setButtonState(ButtonState.LONG);
        } else if (maxCollidingX <= this.donateButtonCompact.getX()) {
            this.setButtonState(ButtonState.COMPACT);
        } else {
            this.setButtonState(ButtonState.HIDDEN);
        }
    }

    private enum ButtonState {
        HIDDEN,
        LONG,
        COMPACT
    }

    private void setButtonState(ButtonState state) {
        switch (state) {
            case HIDDEN:
                this.hideDonateButton.setVisible(false);
                this.donateButtonLong.setVisible(false);
                this.donateButtonCompact.setVisible(false);
                break;
            case LONG:
                this.hideDonateButton.setVisible(true);
                this.donateButtonLong.setVisible(true);
                this.donateButtonCompact.setVisible(false);
                break;
            case COMPACT:
                this.hideDonateButton.setVisible(true);
                this.donateButtonLong.setVisible(false);
                this.donateButtonCompact.setVisible(true);
                break;
        }
    }

    private void hideDonationButton() {
        SodiumOptions options = SodiumClientMod.options();
        options.notifications.hasClearedDonationButton = true;

        try {
            SodiumOptions.writeToDisk(options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }

        this.donateButtonEnabled = false;
    }

    private static class IconButtonWidget extends FlatButtonWidget {
        private final ResourceLocation sprite;
        private final int spriteSize;

        public IconButtonWidget(Dim2i dim, ResourceLocation sprite, int spriteSize, Runnable action, boolean drawBackground, boolean leftAlign, ButtonTheme theme) {
            super(dim, null, action, drawBackground, leftAlign, theme);

            this.sprite = sprite;
            this.spriteSize = spriteSize;
        }

        public IconButtonWidget(Dim2i dim, ResourceLocation sprite, int spriteSize, Runnable action, boolean drawBackground, boolean leftAlign) {
            super(dim, null, action, drawBackground, leftAlign);

            this.sprite = sprite;
            this.spriteSize = spriteSize;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);

            if (!this.isVisible()) {
                return;
            }

            var halfSpriteSize = this.spriteSize / 2;
            graphics.blit(RenderType::guiTextured, this.sprite, this.getCenterX() - halfSpriteSize, this.getCenterY() - halfSpriteSize, 0, 0, this.spriteSize, this.spriteSize, this.spriteSize, this.spriteSize, this.getTextColor());
        }
    }
}
