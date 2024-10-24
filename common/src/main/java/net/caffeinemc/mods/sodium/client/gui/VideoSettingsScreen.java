package net.caffeinemc.mods.sodium.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.*;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionGroup;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import net.caffeinemc.mods.sodium.client.console.Console;
import net.caffeinemc.mods.sodium.client.console.message.MessageLevel;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPromptable;
import net.caffeinemc.mods.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.caffeinemc.mods.sodium.client.gui.widgets.CenteredFlatWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.FlatButtonWidget;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// TODO: scrolling in the page list, the options themselves, and the tooltip if necessary
// TODO: make the search bar work
// TODO: constrain the tooltip to its safe area if it's too big, then show a scroll bar if it's still too big
// TODO: wrap options within groups in two columns
// TODO: stop the options from overlapping the bottom or top buttons
// TODO: make the mod config headers interactive: only show one mod's pages at a time, click on a mod header to open that mod's first settings page and close the previous mod's page list
// TODO: the donation button is gone when the search button is clicked?
// TODO: "setting unavailable" overlaps with the label, even though the label should be automatically truncated to fit
public class VideoSettingsScreen extends Screen implements ScreenPromptable {
    private final List<ControlElement<?>> controls = new ArrayList<>();

    private final Screen prevScreen;

    private OptionPage currentPage;

    private FlatButtonWidget applyButton, closeButton, undoButton;
    private FlatButtonWidget donateButton, hideDonateButton;

    private boolean hasPendingChanges;
    private ControlElement<?> hoveredElement;

    private @Nullable ScreenPrompt prompt;
    private FlatButtonWidget searchButton;

    private VideoSettingsScreen(Screen prevScreen) {
        super(Component.literal("Sodium Renderer Settings"));

        this.prevScreen = prevScreen;

        this.checkPromptTimers();
    }

    private void checkPromptTimers() {
        // Never show the prompt in developer workspaces.
        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        var options = SodiumClientMod.options();

        // If the user has already seen the prompt, don't show it again.
        if (options.notifications.hasSeenDonationPrompt) {
            return;
        }

        HashedFingerprint fingerprint = null;

        try {
            fingerprint = HashedFingerprint.loadFromDisk();
        } catch (Throwable t) {
            SodiumClientMod.logger()
                    .error("Failed to read the fingerprint from disk", t);
        }

        // If the fingerprint doesn't exist, or failed to be loaded, abort.
        if (fingerprint == null) {
            return;
        }

        // The fingerprint records the installation time. If it's been a while since installation, show the user
        // a prompt asking for them to consider donating.
        var now = Instant.now();
        var threshold = Instant.ofEpochSecond(fingerprint.timestamp())
                .plus(3, ChronoUnit.DAYS);

        if (now.isAfter(threshold)) {
            this.openDonationPrompt(options);
        }
    }

    private void openDonationPrompt(SodiumOptions options) {
        var prompt = new ScreenPrompt(this, DONATION_PROMPT_MESSAGE, 320, 190,
                new ScreenPrompt.Action(Component.literal("Buy us a coffee"), this::openDonationPage));
        prompt.setFocused(true);

        options.notifications.hasSeenDonationPrompt = true;

        try {
            SodiumOptions.writeToDisk(options);
        } catch (IOException e) {
            SodiumClientMod.logger()
                    .error("Failed to update config file", e);
        }
    }

    public static Screen createScreen(Screen currentScreen) {
        if (SodiumClientMod.options().isReadOnly()) {
            return new ConfigCorruptedScreen(currentScreen, VideoSettingsScreen::new);
        } else {
            return new VideoSettingsScreen(currentScreen);
        }
    }

    public void setPage(OptionPage page) {
        this.currentPage = page;

        this.rebuildGUI();
    }

    @Override
    protected void init() {
        super.init();

        this.rebuildGUI();

        if (this.prompt != null) {
            this.prompt.init();
        }
    }

    private void rebuildGUI() {
        this.controls.clear();

        this.clearWidgets();

        if (this.currentPage == null) {
//            if (this.pages.isEmpty()) {
//                throw new IllegalStateException("No pages are available?!");
//            }

            // Just use the first page for now
            // TODO: fix
            this.currentPage = ConfigManager.CONFIG.getModConfigs().getFirst().pages().getFirst();
        }

        this.rebuildGUIPages();
        int pageY = this.rebuildGUIOptions();

        this.undoButton = new FlatButtonWidget(new Dim2i(270, this.height - 30, 65, 20), Component.translatable("sodium.options.buttons.undo"), this::undoChanges, true, false);
        this.applyButton = new FlatButtonWidget(new Dim2i(130, this.height - 30, 65, 20), Component.translatable("sodium.options.buttons.apply"), this::applyChanges, true, false);
        this.closeButton = new FlatButtonWidget(new Dim2i(200, this.height - 30, 65, 20), Component.translatable("gui.done"), this::onClose, true, false);
        this.donateButton = new FlatButtonWidget(new Dim2i(this.width - 128, 6, 100, 20), Component.translatable("sodium.options.buttons.donate"), this::openDonationPage, true, false);
        this.hideDonateButton = new FlatButtonWidget(new Dim2i(this.width - 26, 6, 20, 20), Component.literal("x"), this::hideDonationButton, true, false);
        this.searchButton = new FlatButtonWidget(new Dim2i(0, this.height - 30, 125, 20), Component.literal("Search...").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), this::hideDonationButton, true, true);

        if (SodiumClientMod.options().notifications.hasClearedDonationButton) {
            this.setDonationButtonVisibility(false);
        }

        this.addRenderableWidget(this.undoButton);
        this.addRenderableWidget(this.applyButton);
        this.addRenderableWidget(this.closeButton);
        this.addRenderableWidget(this.searchButton);
        this.addRenderableWidget(this.donateButton);
        this.addRenderableWidget(this.hideDonateButton);
    }

    private void setDonationButtonVisibility(boolean value) {
        this.donateButton.setVisible(value);
        this.hideDonateButton.setVisible(value);
    }

    private void hideDonationButton() {
        SodiumOptions options = SodiumClientMod.options();
        options.notifications.hasClearedDonationButton = true;

        try {
            SodiumOptions.writeToDisk(options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }

        this.setDonationButtonVisibility(false);
    }

    private void rebuildGUIPages() {
        int x = 0;
        int y = 5;
        int width = 125;

        for (var modConfig : ConfigManager.CONFIG.getModConfigs()) {
            CenteredFlatWidget header = new CenteredFlatWidget(new Dim2i(x, y, width, this.font.lineHeight * 2), Component.literal(modConfig.name()), () -> {
            }, false);

            y += this.font.lineHeight * 2;

            this.addRenderableWidget(header);

            for (OptionPage page : modConfig.pages()) {
                CenteredFlatWidget button = new CenteredFlatWidget(new Dim2i(x, y, width, this.font.lineHeight * 2), page.name(), () -> this.setPage(page), true);
                button.setSelected(this.currentPage == page);

                y += this.font.lineHeight * 2;

                this.addRenderableWidget(button);
            }
        }
/*

        CenteredFlatWidget button = new CenteredFlatWidget(new Dim2i(x, y, width, font.lineHeight * 2), Component.literal("" +
                "Iris Shaders"), () -> {}, false);

        y += font.lineHeight * 2;

        this.addRenderableWidget(button);

        CenteredFlatWidget button2 = new CenteredFlatWidget(new Dim2i(x, y, width, font.lineHeight * 2), Component.literal("Shader Packs"), () -> {}, true);

        y += font.lineHeight * 2;

        this.addRenderableWidget(button2);
        */
    }

    private int rebuildGUIOptions() {
        int x = 130;
        int y = 23;

        for (OptionGroup group : this.currentPage.groups()) {
            // Add each option's control element
            for (Option<?> option : group.options()) {
                Control<?> control = option.getControl();
                ControlElement<?> element = control.createElement(new Dim2i(x, y, 200, 18));

                this.addRenderableWidget(element);

                this.controls.add(element);

                // Move down to the next option
                y += 18;
            }

            // Add padding beneath each option group
            y += 4;
        }

        return y;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.updateControls();

        super.render(graphics, this.prompt != null ? -1 : mouseX, this.prompt != null ? -1 : mouseY, delta);

        if (this.hoveredElement != null) {
            this.renderOptionTooltip(graphics, this.hoveredElement);
        }

        if (this.prompt != null) {
            this.prompt.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics, int i, int j, int k, int l) {
        guiGraphics.fillGradient(0, 0, 125, this.minecraft.getMainRenderTarget().height, 0x40000000, 0x90000000);
        //graphics.fill(0, 0, 335, 20, 0x40000000);
        RenderSystem.enableBlend();
    }

    private void updateControls() {
        ControlElement<?> hovered = this.getActiveControls()
                .filter(ControlElement::isHovered)
                .findFirst()
                .orElse(this.getActiveControls() // If there is no hovered element, use the focused element.
                        .filter(ControlElement::isFocused)
                        .findFirst()
                        .orElse(null));

        boolean hasChanges = ConfigManager.CONFIG.anyOptionChanged();

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
        this.hoveredElement = hovered;
    }

    private Stream<ControlElement<?>> getActiveControls() {
        return this.controls.stream();
    }

    private void renderOptionTooltip(GuiGraphics graphics, ControlElement<?> element) {
        Dim2i dim = element.getDimensions();

        int textPadding = 5;
        int boxPadding = 5;

        int boxWidth = this.width - 340;

        int boxY = dim.y();
        int boxX = dim.getLimitX() + boxPadding;

        Option<?> option = element.getOption();
        List<FormattedCharSequence> tooltip = new ArrayList<>(this.font.split(option.getTooltip(), boxWidth - (textPadding * 2)));

        OptionImpact impact = option.getImpact();

        if (impact != null) {
            tooltip.add(Language.getInstance().getVisualOrder(Component.translatable("sodium.options.performance_impact_string", impact.getName()).withStyle(ChatFormatting.GRAY)));
        }

        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.height - 40;

        // If the box is going to be cutoff on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x40000000);

        for (int i = 0; i < tooltip.size(); i++) {
            graphics.drawString(this.font, tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF);
        }
    }

    private void applyChanges() {
        var flags = ConfigManager.CONFIG.applyAllOptions();

        Minecraft client = Minecraft.getInstance();

        if (client.level != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                client.levelRenderer.allChanged();
            } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                client.levelRenderer.needsUpdate();
            }
        }

        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.updateMaxMipLevel(client.options.mipmapLevels().get());
            client.delayTextureReload();
        }

        if (flags.contains(OptionFlag.REQUIRES_VIDEOMODE_RELOAD)) {
            client.getWindow().changeFullscreenVideoMode();
        }

        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            Console.instance().logMessage(MessageLevel.WARN,
                    "sodium.console.game_restart", true, 10.0);
        }
    }

    private void undoChanges() {
        ConfigManager.CONFIG.resetAllOptions();
    }

    private void openDonationPage() {
        Util.getPlatform()
                .openUri("https://caffeinemc.net/donate");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.prompt != null && this.prompt.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (this.prompt == null && keyCode == GLFW.GLFW_KEY_P && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(this.prevScreen, Minecraft.getInstance(), Minecraft.getInstance().options));

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.prompt != null) {
            return this.prompt.mouseClicked(mouseX, mouseY, button);
        }

        boolean clicked = super.mouseClicked(mouseX, mouseY, button);

        if (!clicked) {
            this.setFocused(null);
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        return super.mouseScrolled(d, e, f, g);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.prevScreen);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.prompt == null ? super.children() : this.prompt.getWidgets();
    }

    @Override
    public void setPrompt(@Nullable ScreenPrompt prompt) {
        this.prompt = prompt;
    }

    @Nullable
    @Override
    public ScreenPrompt getPrompt() {
        return this.prompt;
    }

    @Override
    public Dim2i getDimensions() {
        return new Dim2i(0, 0, this.width, this.height);
    }

    private static final List<FormattedText> DONATION_PROMPT_MESSAGE;

    static {
        DONATION_PROMPT_MESSAGE = List.of(
                FormattedText.composite(Component.literal("Hello!")),
                FormattedText.composite(Component.literal("It seems that you've been enjoying "), Component.literal("Sodium").withColor(0x27eb92), Component.literal(", the powerful and open rendering optimization mod for Minecraft.")),
                FormattedText.composite(Component.literal("Mods like these are complex. They require "), Component.literal("thousands of hours").withColor(0xff6e00), Component.literal(" of development, debugging, and tuning to create the experience that players have come to expect.")),
                FormattedText.composite(Component.literal("If you'd like to show your token of appreciation, and support the development of our mod in the process, then consider "), Component.literal("buying us a coffee").withColor(0xed49ce), Component.literal(".")),
                FormattedText.composite(Component.literal("And thanks again for using our mod! We hope it helps you (and your computer.)"))
        );
    }
}
