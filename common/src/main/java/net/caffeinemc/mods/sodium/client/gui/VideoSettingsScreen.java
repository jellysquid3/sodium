package net.caffeinemc.mods.sodium.client.gui;

import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.*;
import net.caffeinemc.mods.sodium.client.config.structure.*;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import net.caffeinemc.mods.sodium.client.console.Console;
import net.caffeinemc.mods.sodium.client.console.message.MessageLevel;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPromptable;
import net.caffeinemc.mods.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.caffeinemc.mods.sodium.client.gui.widgets.FlatButtonWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.PageListWidget;
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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

// TODO: scrolling in the page list, the options themselves, and the tooltip if necessary
// TODO: make the search bar work
// TODO: constrain the tooltip to its safe area if it's too big, then show a scroll bar if it's still too big
// TODO: wrap options within groups in two columns
// TODO: stop the options from overlapping the bottom or top buttons
// TODO: make the mod config headers interactive: only show one mod's pages at a time, click on a mod header to open that mod's first settings page and close the previous mod's page list
// TODO: the donation button is gone when the search button is clicked?
// TODO: display each mod's version somewhere (truncate if too long?)
public class VideoSettingsScreen extends Screen implements ScreenPromptable {
    private final List<ControlElement<?>> controls = new ArrayList<>();

    private final Screen prevScreen;

    private ModOptions currentMod;
    private OptionPage currentPage;

    private PageListWidget pageList;
    private OptionListWidget optionList;

    private FlatButtonWidget applyButton, closeButton, undoButton;
    private FlatButtonWidget donateButton, hideDonateButton;

    private boolean hasPendingChanges;
    private ControlElement<?> hoveredElement;

    private @Nullable ScreenPrompt prompt;

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

    public OptionPage getPage() {
        return this.currentPage;
    }

    public void setPage(ModOptions modOptions, OptionPage page) {
        this.currentMod = modOptions;
        if (this.currentPage != page) {
            this.currentPage = page;
            this.rebuildGUIOptions();
        }
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

        // find the first non-external page
        if (this.currentPage == null) {
            var modOptionsIt = ConfigManager.CONFIG.getModOptions().iterator();
            Iterator<Page> pagesIt = null;
            while (this.currentPage == null) {
                if (pagesIt == null) {
                    if (!modOptionsIt.hasNext()) {
                        throw new IllegalStateException("No non-external pages found to display");
                    }
                    this.currentMod = modOptionsIt.next();
                    pagesIt = this.currentMod.pages().iterator();
                }

                if (!pagesIt.hasNext()) {
                    pagesIt = null;
                    continue;
                }

                var page = pagesIt.next();
                if (page instanceof OptionPage optionPage) {
                    this.currentPage = optionPage;
                }
            }
        }

        this.rebuildGUIOptions(this.currentMod.theme());

        this.pageList = new PageListWidget(this, new Dim2i(0, 0, 125, this.height));
        this.undoButton = new FlatButtonWidget(new Dim2i(270, this.height - 30, 65, 20), Component.translatable("sodium.options.buttons.undo"), this::undoChanges, true, false);
        this.applyButton = new FlatButtonWidget(new Dim2i(130, this.height - 30, 65, 20), Component.translatable("sodium.options.buttons.apply"), this::applyChanges, true, false);
        this.closeButton = new FlatButtonWidget(new Dim2i(200, this.height - 30, 65, 20), Component.translatable("gui.done"), this::onClose, true, false);
        this.donateButton = new FlatButtonWidget(new Dim2i(this.width - 128, 6, 100, 20), Component.translatable("sodium.options.buttons.donate"), this::openDonationPage, true, false);
        this.hideDonateButton = new FlatButtonWidget(new Dim2i(this.width - 26, 6, 20, 20), Component.literal("x"), this::hideDonationButton, true, false);

        if (SodiumClientMod.options().notifications.hasClearedDonationButton) {
            this.setDonationButtonVisibility(false);
        }

        this.addRenderableWidget(this.pageList);
        this.addRenderableWidget(this.undoButton);
        this.addRenderableWidget(this.applyButton);
        this.addRenderableWidget(this.closeButton);
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

    private void rebuildGUIOptions() {
        this.removeWidget(this.optionList);
        this.optionList = this.addRenderableWidget(new OptionListWidget(new Dim2i(130, 0, 210, this.height), this.currentPage));
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
    protected void renderMenuBackground(@NotNull GuiGraphics guiGraphics, int i, int j, int k, int l) {
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
        return this.optionList.getControls().stream();
    }

    private void renderOptionTooltip(GuiGraphics graphics, ControlElement<?> element) {
        int textPadding = 5;
        int boxPadding = 5;

        int boxWidth = this.width - 340;

        int boxY = element.getY();
        int boxX = element.getLimitX() + boxPadding;

        Option<?> option = element.getOption();
        List<FormattedCharSequence> tooltip = new ArrayList<>(this.font.split(option.getTooltip(), boxWidth - (textPadding * 2)));

        OptionImpact impact = option.getImpact();

        if (impact != null) {
            tooltip.add(Language.getInstance().getVisualOrder(Component.translatable("sodium.options.performance_impact_string", impact.getName()).withStyle(ChatFormatting.GRAY)));
        }

        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.height - 40;

        // If the box is going to be cut off on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x40000000);

        for (int i = 0; i < tooltip.size(); i++) {
            graphics.drawString(this.font, tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), Colors.FOREGROUND);
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
