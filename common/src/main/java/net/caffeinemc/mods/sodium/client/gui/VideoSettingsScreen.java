package net.caffeinemc.mods.sodium.client.gui;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPromptable;
import net.caffeinemc.mods.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.caffeinemc.mods.sodium.client.gui.widgets.*;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
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

// TODO: make the search bar work
// TODO: wrap options within groups in two columns
// TODO: make the mod config headers interactive: only show one mod's pages at a time, click on a mod header to open that mod's first settings page and close the previous mod's page list
// TODO: change the scroll bar colors to make it look better against a lighter gray background
// TODO: show option group's names somewhere
// TODO: add button or some other way for user to reset a specific option, all options on a page, and all options of a mod to their default values (not just "reset" changes, but reset to default value)
// TODO: make RD option respect Vanilla's >16 RD only allowed if memory >1GB constraint
public class VideoSettingsScreen extends Screen implements ScreenPromptable {
    private final List<ControlElement> controls = new ArrayList<>();

    private final Screen prevScreen;

    private ModOptions currentMod;
    private OptionPage currentPage;

    private PageListWidget pageList;
    private OptionListWidget optionList;

    private FlatButtonWidget applyButton, closeButton, undoButton;
    private DonationButtonWidget donateButton;

    private boolean hasPendingChanges;

    private final ScrollableTooltip tooltip = new ScrollableTooltip(this);

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

        this.rebuildGUIOptions();

        this.pageList = new PageListWidget(this, new Dim2i(0, 0, 125, this.height));

        this.applyButton = new FlatButtonWidget(new Dim2i(this.pageList.getLimitX() + Layout.INNER_MARGIN, Layout.INNER_MARGIN, Layout.BUTTON_LONG, Layout.BUTTON_SHORT), Component.translatable("sodium.options.buttons.apply"), ConfigManager.CONFIG::applyAllOptions, true, false);
        this.closeButton = new FlatButtonWidget(new Dim2i(this.applyButton.getLimitX() + Layout.INNER_MARGIN, Layout.INNER_MARGIN, Layout.BUTTON_LONG, Layout.BUTTON_SHORT), Component.translatable("gui.done"), this::onClose, true, false);
        this.undoButton = new FlatButtonWidget(new Dim2i(this.closeButton.getLimitX() + Layout.INNER_MARGIN, Layout.INNER_MARGIN, Layout.BUTTON_LONG, Layout.BUTTON_SHORT), Component.translatable("sodium.options.buttons.undo"), this::undoChanges, true, false);

        this.donateButton = new DonationButtonWidget(List.of(this.applyButton, this.closeButton, this.undoButton), this.width, this::openDonationPage, this::addRenderableWidget);

        this.addRenderableWidget(this.pageList);
        this.addRenderableWidget(this.undoButton);
        this.addRenderableWidget(this.applyButton);
        this.addRenderableWidget(this.closeButton);
    }

    private void rebuildGUIOptions() {
        this.removeWidget(this.optionList);
        this.optionList = this.addRenderableWidget(new OptionListWidget(this, new Dim2i(
                130, Layout.INNER_MARGIN * 2 + Layout.BUTTON_SHORT,
                210, this.height - (Layout.INNER_MARGIN * 2 + Layout.BOTTOM_MARGIN + Layout.BUTTON_SHORT)),
                this.currentPage, this.currentMod.theme()
        ));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.updateControls(mouseX, mouseY);

        super.render(graphics, this.prompt != null ? -1 : mouseX, this.prompt != null ? -1 : mouseY, delta);

        this.tooltip.render(graphics);

        if (this.prompt != null) {
            this.prompt.render(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    protected void renderMenuBackground(@NotNull GuiGraphics guiGraphics, int i, int j, int k, int l) {
    }

    private void updateControls(int mouseX, int mouseY) {
        var hovered = this.getActiveControls()
                // using ControlElement#isHovered causes a one frame delay because it is updated in the elements render method
                // this caused flickering when going from hovering the tooltip back to the option
                .filter(element -> element.isMouseOver(mouseX, mouseY))
                .findFirst()
                .orElse(this.getActiveControls() // If there is no hovered element, use the focused element.
                        .filter(ControlElement::isFocused)
                        .findFirst()
                        .orElse(null));

        boolean hasChanges = ConfigManager.CONFIG.anyOptionChanged();

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.donateButton.updateDisplay();

        this.hasPendingChanges = hasChanges;

        this.tooltip.onControlHover(hovered, mouseX, mouseY);
    }

    private Stream<ControlElement> getActiveControls() {
        return this.optionList.getControls().stream();
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
    public boolean mouseScrolled(double x, double y, double f, double amount) {
        // change the gui scale with scrolling if the control key is held
        if (Screen.hasControlDown()) {
            var location = ResourceLocation.parse("sodium:general.gui_scale");
            var option = ConfigManager.CONFIG.getOption(location);
            if (option instanceof IntegerOption guiScaleOption) {
                var value = guiScaleOption.getValidatedValue();
                if (value instanceof Integer intValue) {
                    var range = guiScaleOption.getRange();
                    var top = range.max() + 1;
                    var auto = range.min();

                    // re-maps the auto value (presumably 0) to be at the top of the scroll range
                    if (intValue == auto) {
                        intValue = top;
                    }
                    var newValue = Math.clamp(intValue + (int) Math.signum(amount), auto + 1, top);
                    if (newValue != intValue) {
                        if (newValue == top) {
                            newValue = auto;
                        }
                        if (range.isValueValid(newValue)) {
                            guiScaleOption.modifyValue(newValue);
                            ConfigManager.CONFIG.applyOption(location);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        if (this.tooltip.mouseScrolled(x, y, amount)) {
            return true;
        }

        return super.mouseScrolled(x, y, f, amount);
    }

    @Override
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T guiEventListener) {
        return super.addRenderableWidget(guiEventListener);
    }

    @Override
    public void removeWidget(GuiEventListener guiEventListener) {
        super.removeWidget(guiEventListener);
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
