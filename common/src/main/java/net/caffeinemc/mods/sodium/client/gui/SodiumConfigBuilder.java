package net.caffeinemc.mods.sodium.client.gui;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlValueFormatterImpls;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ParticleStatus;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

// TODO: get initialValue from the vanilla options (it's private)
public class SodiumConfigBuilder implements ConfigEntryPoint {
    private static final SodiumOptions DEFAULTS = SodiumOptions.defaults();

    private final Options vanillaOpts;
    private final StorageEventHandler vanillaStorage;
    private final SodiumOptions sodiumOpts;
    private final StorageEventHandler sodiumStorage;

    private final @Nullable Window window;
    private final Monitor monitor;

    public SodiumConfigBuilder() {
        var minecraft = Minecraft.getInstance();
        this.window = minecraft.getWindow();
        this.monitor = this.window == null ? null : this.window.findBestMonitor();

        this.vanillaOpts = minecraft.options;
        this.vanillaStorage = this.vanillaOpts == null ? null : () -> {
            this.vanillaOpts.save();

            SodiumClientMod.logger().info("Flushed changes to Minecraft configuration");
        };

        this.sodiumOpts = SodiumClientMod.options();
        this.sodiumStorage = () -> {
            try {
                SodiumOptions.writeToDisk(this.sodiumOpts);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't save configuration changes", e);
            }

            SodiumClientMod.logger().info("Flushed changes to Sodium configuration");
        };
    }

    @Override
    public void registerConfigEarly(ConfigBuilder builder) {
        new SodiumConfigBuilder().buildEarlyConfig(builder);
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        new SodiumConfigBuilder().buildFullConfig(builder);
    }

    private static ModOptionsBuilder createModOptionsBuilder(ConfigBuilder builder) {
        return builder.registerOwnModOptions()
                .setName("Sodium Renderer")
                .formatVersion(version -> {
                    var result = version.splitWithDelimiters("\\+", 2);
                    return result[0];
                });
    }

    private void buildEarlyConfig(ConfigBuilder builder) {
        createModOptionsBuilder(builder).addPage(
                builder.createOptionPage()
                        .setName(Component.translatable("sodium.options.pages.performance"))
                        .addOptionGroup(
                                builder.createOptionGroup()
                                        .addOption(this.buildNoErrorContextOption(builder))));
    }

    private void buildFullConfig(ConfigBuilder builder) {
        createModOptionsBuilder(builder)
                .setColorTheme(builder.createColorTheme().setFullThemeRGB(
                        Colors.THEME, Colors.THEME_LIGHTER, Colors.THEME_DARKER))
                .addPage(this.buildGeneralPage(builder))
                .addPage(this.buildQualityPage(builder))
                .addPage(this.buildPerformancePage(builder))
                .addPage(this.buildAdvancedPage(builder));

        // TODO: this is for debugging and dev
        buildExampleAPIUserConfig(builder);
    }

    private static void buildExampleAPIUserConfig(ConfigBuilder builder) {
        class LocalBinding<V> implements OptionBinding<V> {
            private V value;

            public LocalBinding(V value) {
                this.value = value;
            }

            @Override
            public void save(V value) {
                this.value = value;
            }

            @Override
            public V load() {
                return this.value;
            }
        }

        // for testing cycle detection
        // .setEnabledProvider((state) -> state.readIntOption(ResourceLocation.parse("foo:baz")) == 0, ResourceLocation.parse("foo:baz"))

        var options = builder.registerModOptions("foo", "Foo fadsa fdsa fdsa fdas fdsafdsa", "1.0 fdas fdas fdasfdsaf dsa")
                .addPage(
                        builder.createExternalPage()
                                .setName(Component.literal("External Page"))
                                .setScreenProvider((prevScreen) -> {
                                    Minecraft.getInstance().setScreen(new Screen(Component.literal("External Page")) {
                                        @Override
                                        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                                            super.render(graphics, mouseX, mouseY, delta);
                                            graphics.drawString(Minecraft.getInstance().font, Component.literal("Hello, world!"), 10, 10, 0xFFFFFF);
                                        }
                                    });
                                })
                )
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("Foo Pagej fdjfjfl jfdskl fdjkllfffsdldfskjl j"))
                        .addOptionGroup(builder.createOptionGroup()
                                .addOption(
                                        builder.createBooleanOption(ResourceLocation.parse("foo:bar"))
                                                .setStorageHandler(() -> {
                                                })
                                                .setName(Component.literal("Bar"))
                                                .setTooltip(Component.literal("Baz"))
                                                .setDefaultValue(true)
                                                .setBinding(new LocalBinding<>(true))
                                                .setImpact(OptionImpact.LOW)
                                )
                                .addOption(
                                        builder.createIntegerOption(ResourceLocation.parse("foo:baz"))
                                                .setStorageHandler(() -> {
                                                })
                                                .setName(Component.literal("Baz"))
                                                .setTooltip(Component.literal("Baz"))
                                                .setValueFormatter(ControlValueFormatterImpls.number())
                                                .setDefaultValue(5)
                                                .setRange(0, 10, 1)
                                                .setBinding(new LocalBinding<>(5))
                                                .setEnabledProvider(state -> state.readBooleanOption(ResourceLocation.parse("foo:bar")), ResourceLocation.parse("foo:bar"))
                                )
                                .addOption(
                                        builder.createIntegerOption(ResourceLocation.parse("foo:bla"))
                                                .setStorageHandler(() -> {
                                                })
                                                .setName(Component.literal("Bla"))
                                                .setTooltip(Component.literal("hello"))
                                                .setValueFormatter(ControlValueFormatterImpls.number())
                                                .setDefaultValue(5)
                                                .setRangeProvider(
                                                        state -> new Range(state.readBooleanOption(ResourceLocation.parse("foo:bar")) ? 0 : 1, 5, 1),
                                                        ResourceLocation.parse("foo:bar"))
                                                .setBinding(new LocalBinding<>(5))
                                )
                                .addOption(
                                        builder.createEnumOption(ResourceLocation.parse("foo:zot"), OptionImpact.class)
                                                .setStorageHandler(() -> {
                                                })
                                                .setName(Component.literal("Zot"))
                                                .setTooltip(Component.literal("hello"))
                                                .setDefaultValue(OptionImpact.LOW)
                                                .setAllowedValuesProvider(
                                                        state -> {
                                                            var set = EnumSet.noneOf(OptionImpact.class);
                                                            var value = state.readIntOption(ResourceLocation.parse("foo:bla"));
                                                            var list = Arrays.asList(OptionImpact.values());
                                                            set.addAll(list.subList(0, Math.min(value + 1, list.size())));
                                                            return set;
                                                        },
                                                        ResourceLocation.parse("foo:bla"))
                                                .setElementNameProvider(value -> Component.literal(value.name()))
                                                .setBinding(new LocalBinding<>(OptionImpact.LOW))
                                )
                                .addOption(
                                        builder.createExternalButtonOption(ResourceLocation.parse("foo:button"))
                                                .setName(Component.literal("Other things"))
                                                .setTooltip(Component.literal("Hello"))
                                                .setScreenProvider((prevScreen) -> {
                                                    Minecraft.getInstance().setScreen(new Screen(Component.literal("External Button Page")) {
                                                        @Override
                                                        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                                                            super.render(graphics, mouseX, mouseY, delta);
                                                            graphics.drawString(Minecraft.getInstance().font, Component.literal("Hello, world 2!"), 10, 10, 0xFFFFFF);
                                                        }
                                                    });
                                                })
                                                .setEnabledProvider((state) -> state.readBooleanOption(ResourceLocation.parse("foo:bar")), ResourceLocation.parse("foo:bar"))
                                )
                        )
                );
        for (int i = 0; i < 10; i++) {
            options.addPage(builder.createOptionPage()
                    .setName(Component.literal("Foo " + i))
                    .addOptionGroup(builder.createOptionGroup().addOption(
                            builder.createBooleanOption(ResourceLocation.parse("foo:" + i))
                                    .setStorageHandler(() -> {
                                    })
                                    .setName(Component.literal("Bar " + i))
                                    .setTooltip(Component.literal("Baz " + i))
                                    .setDefaultValue(true)
                                    .setBinding(new LocalBinding<>(true))
                                    .setImpact(OptionImpact.LOW)
                    ))
            );
        }
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.literal("Big Foo"));
        for (int i = 0; i < 4; i++) {
            OptionGroupBuilder group = builder.createOptionGroup();
            for (int j = 0; j < i * 4 + 2; j++) {
                group.addOption(builder.createBooleanOption(ResourceLocation.parse("foo:big_" + i + "_" + j))
                        .setStorageHandler(() -> {
                        })
                        .setName(Component.literal("Bar " + i + "," + j))
                        .setTooltip(Component.literal("Baz " + i + "," + j))
                        .setDefaultValue(true)
                        .setBinding(new LocalBinding<>(true))
                        .setImpact(OptionImpact.LOW)
                );
            }
            page.addOptionGroup(group);
        }
        options.addPage(page);

        var other = builder.registerModOptions("bar", "Bar", "1.0 fdas fdas fdasfdsaf dsa");
        other.registerOptionOverride(builder.createOptionOverride()
                .setTarget(ResourceLocation.parse("foo:bar"))
                .setReplacement(
                        builder.createBooleanOption(ResourceLocation.parse("foo:bar"))
                                .setStorageHandler(() -> {
                                })
                                .setName(Component.literal("Replaced Bar"))
                                .setTooltip(Component.literal("Baz"))
                                .setDefaultValue(true)
                                .setBinding(new LocalBinding<>(true))
                                .setImpact(OptionImpact.MEDIUM)

                )
        );
    }

    private OptionPageBuilder buildGeneralPage(ConfigBuilder builder) {
        var generalPage = builder.createOptionPage().setName(Component.literal("General"));
        generalPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.literal("Render Distance"))
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:general.render_distance"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.renderDistance"))
                                .setTooltip(Component.translatable("sodium.options.view_distance.tooltip"))
                                .setValueFormatter(ControlValueFormatterImpls.translateVariable("options.chunks"))
                                .setRange(2, 32, 1)
                                .setDefaultValue(12)
                                .setBinding(this.vanillaOpts.renderDistance()::set, this.vanillaOpts.renderDistance()::get)
                                .setImpact(OptionImpact.HIGH)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:general.simulation_distance"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.simulationDistance"))
                                .setTooltip(Component.translatable("sodium.options.simulation_distance.tooltip"))
                                .setValueFormatter(ControlValueFormatterImpls.translateVariable("options.chunks"))
                                .setRange(5, 32, 1)
                                .setDefaultValue(12)
                                .setBinding(this.vanillaOpts.simulationDistance()::set, this.vanillaOpts.simulationDistance()::get)
                                .setImpact(OptionImpact.HIGH)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:general.gamma"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.gamma"))
                                .setTooltip(Component.translatable("sodium.options.brightness.tooltip"))
                                .setValueFormatter(ControlValueFormatterImpls.brightness())
                                .setRange(0, 100, 1)
                                .setDefaultValue(50)
                                .setBinding(value -> this.vanillaOpts.gamma().set(value * 0.01D), () -> (int) (this.vanillaOpts.gamma().get() / 0.01D))
                )
        );
        generalPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("options.guiScale"))
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:general.gui_scale"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.guiScale"))
                                .setTooltip(Component.translatable("sodium.options.gui_scale.tooltip"))
                                .setValueFormatter(ControlValueFormatterImpls.guiScale())
                                .setRange(0, this.window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()), 1)
                                .setDefaultValue(0)
                                .setBinding(value -> {
                                    this.vanillaOpts.guiScale().set(value);
                                    Minecraft.getInstance().resizeDisplay();
                                }, this.vanillaOpts.guiScale()::get)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:general.fullscreen"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.fullscreen"))
                                .setTooltip(Component.translatable("sodium.options.fullscreen.tooltip"))
                                .setDefaultValue(false)
                                .setBinding(value -> {
                                    this.vanillaOpts.fullscreen().set(value);

                                    if (this.window.isFullscreen() != this.vanillaOpts.fullscreen().get()) {
                                        this.window.toggleFullScreen();

                                        // The client might not be able to enter full-screen mode
                                        this.vanillaOpts.fullscreen().set(this.window.isFullscreen());
                                    }
                                }, this.vanillaOpts.fullscreen()::get)
                )
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:general.fullscreen_resolution"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.fullscreen.resolution"))
                                .setTooltip(Component.translatable("sodium.options.fullscreen_resolution.tooltip"))
                                .setValueFormatter(ControlValueFormatterImpls.resolution())
                                .setRange(0, this.monitor != null ? this.monitor.getModeCount() : 0, 1)
                                .setDefaultValue(0)
                                .setBinding(value -> {
                                    if (null != this.monitor) {
                                        this.window.setPreferredFullscreenVideoMode(0 == value ? Optional.empty() : Optional.of(this.monitor.getMode(value - 1)));
                                    }
                                }, () -> {
                                    if (null == this.monitor) {
                                        return 0;
                                    } else {
                                        Optional<VideoMode> optional = this.window.getPreferredFullscreenVideoMode();
                                        return optional.map((videoMode) -> this.monitor.getVideoModeIndex(videoMode) + 1).orElse(0);
                                    }
                                })
                                .setEnabledProvider(
                                        (state) -> {
                                            if (this.monitor == null) {
                                                return false;
                                            }
                                            var os = OsUtils.getOs();
                                            return (os == OsUtils.OperatingSystem.WIN || os == OsUtils.OperatingSystem.MAC) &&
                                                    state.readBooleanOption(ResourceLocation.parse("sodium:general.fullscreen"));
                                        },
                                        ResourceLocation.parse("sodium:general.fullscreen"))
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:general.vsync"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.vsync"))
                                .setTooltip(Component.translatable("sodium.options.v_sync.tooltip"))
                                .setDefaultValue(true)
                                .setBinding(this.vanillaOpts.enableVsync()::set, this.vanillaOpts.enableVsync()::get)
                )
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:general.framerate_limit"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.framerateLimit"))
                                .setTooltip(Component.translatable("sodium.options.fps_limit.tooltip"))
                                .setValueFormatter(ControlValueFormatterImpls.fpsLimit())
                                .setRange(10, 260, 10)
                                .setDefaultValue(60)
                                .setBinding(this.vanillaOpts.framerateLimit()::set, this.vanillaOpts.framerateLimit()::get)
                )
        );
        generalPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.literal("View Bobbing"))
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:general.view_bobbing"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.viewBobbing"))
                                .setTooltip(Component.translatable("sodium.options.view_bobbing.tooltip"))
                                .setDefaultValue(true)
                                .setBinding(this.vanillaOpts.bobView()::set, this.vanillaOpts.bobView()::get)
                )
                .addOption(
                        builder.createEnumOption(ResourceLocation.parse("sodium:general.attack_indicator"), AttackIndicatorStatus.class)
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.attackIndicator"))
                                .setTooltip(Component.translatable("sodium.options.attack_indicator.tooltip"))
                                .setDefaultValue(AttackIndicatorStatus.CROSSHAIR)
                                .setElementNameProvider(AttackIndicatorStatus::getCaption)
                                .setBinding(this.vanillaOpts.attackIndicator()::set, this.vanillaOpts.attackIndicator()::get)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:general.autosave_indicator"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.autosaveIndicator"))
                                .setTooltip(Component.translatable("sodium.options.autosave_indicator.tooltip"))
                                .setDefaultValue(true)
                                .setBinding(this.vanillaOpts.showAutosaveIndicator()::set, this.vanillaOpts.showAutosaveIndicator()::get)
                )
        );
        return generalPage;
    }

    private OptionPageBuilder buildQualityPage(ConfigBuilder builder) {
        var qualityPage = builder.createOptionPage().setName(Component.translatable("sodium.options.pages.quality"));

        qualityPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("options.graphics"))
                .addOption(
                        builder.createEnumOption(ResourceLocation.parse("sodium:quality.graphics"), GraphicsStatus.class)
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.graphics"))
                                .setTooltip(Component.translatable("sodium.options.graphics_quality.tooltip"))
                                .setElementNameProvider(EnumOptionBuilder.nameProviderFrom(
                                        Component.translatable("options.graphics.fast"),
                                        Component.translatable("options.graphics.fancy"),
                                        Component.translatable("options.graphics.fabulous")))
                                .setDefaultValue(GraphicsStatus.FANCY)
                                .setBinding(this.vanillaOpts.graphicsMode()::set, this.vanillaOpts.graphicsMode()::get)
                                .setImpact(OptionImpact.HIGH)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
        );

        qualityPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("options.renderClouds"))
                .addOption(
                        builder.createEnumOption(ResourceLocation.parse("sodium:quality.clouds"), CloudStatus.class)
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.renderClouds"))
                                .setTooltip(Component.translatable("sodium.options.clouds_quality.tooltip"))
                                .setElementNameProvider(EnumOptionBuilder.nameProviderFrom(
                                        Component.translatable("options.off"),
                                        Component.translatable("options.graphics.fast"),
                                        Component.translatable("options.graphics.fancy")))
                                .setDefaultValue(CloudStatus.FANCY)
                                .setBinding(this.vanillaOpts.cloudStatus()::set, this.vanillaOpts.cloudStatus()::get)
                                .setImpact(OptionImpact.LOW)
                )
                .addOption(
                        builder.createEnumOption(ResourceLocation.parse("sodium:quality.weather"), SodiumOptions.GraphicsQuality.class)
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("soundCategory.weather"))
                                .setTooltip(Component.translatable("sodium.options.weather_quality.tooltip"))
                                .setDefaultValue(DEFAULTS.quality.weatherQuality)
                                .setBinding(value -> this.sodiumOpts.quality.weatherQuality = value, () -> this.sodiumOpts.quality.weatherQuality)
                                .setImpact(OptionImpact.MEDIUM)
                )
                .addOption(
                        builder.createEnumOption(ResourceLocation.parse("sodium:quality.leaves"), SodiumOptions.GraphicsQuality.class)
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.leaves_quality.name"))
                                .setTooltip(Component.translatable("sodium.options.leaves_quality.tooltip"))
                                .setDefaultValue(DEFAULTS.quality.leavesQuality)
                                .setBinding(value -> this.sodiumOpts.quality.leavesQuality = value, () -> this.sodiumOpts.quality.leavesQuality)
                                .setImpact(OptionImpact.MEDIUM)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createEnumOption(ResourceLocation.parse("sodium:quality.particles"), ParticleStatus.class)
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.particles"))
                                .setTooltip(Component.translatable("sodium.options.particle_quality.tooltip"))
                                .setElementNameProvider(EnumOptionBuilder.nameProviderFrom(
                                        Component.translatable("options.particles.all"),
                                        Component.translatable("options.particles.decreased"),
                                        Component.translatable("options.particles.minimal")
                                ))
                                .setDefaultValue(ParticleStatus.ALL)
                                .setBinding(this.vanillaOpts.particles()::set, this.vanillaOpts.particles()::get)
                                .setImpact(OptionImpact.MEDIUM)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:quality.ao"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.ao"))
                                .setTooltip(Component.translatable("sodium.options.smooth_lighting.tooltip"))
                                .setDefaultValue(true)
                                .setBinding(this.vanillaOpts.ambientOcclusion()::set, this.vanillaOpts.ambientOcclusion()::get)
                                .setImpact(OptionImpact.LOW)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:quality.biome_blend"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.biomeBlendRadius"))
                                .setValueFormatter(ControlValueFormatterImpls.biomeBlend())
                                .setTooltip(Component.translatable("sodium.options.biome_blend.tooltip"))
                                .setRange(1, 7, 1)
                                .setDefaultValue(2)
                                .setBinding(this.vanillaOpts.biomeBlendRadius()::set, this.vanillaOpts.biomeBlendRadius()::get)
                                .setImpact(OptionImpact.LOW)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:quality.entity_distance"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.entityDistanceScaling"))
                                .setValueFormatter(ControlValueFormatterImpls.percentage())
                                .setTooltip(Component.translatable("sodium.options.entity_distance.tooltip"))
                                .setRange(50, 500, 25)
                                .setDefaultValue(100)
                                .setBinding((value) -> this.vanillaOpts.entityDistanceScaling().set(value / 100.0), () -> Math.round(this.vanillaOpts.entityDistanceScaling().get().floatValue() * 100.0F))
                                .setImpact(OptionImpact.HIGH)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:quality.entity_shadows"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.entityShadows"))
                                .setTooltip(Component.translatable("sodium.options.entity_shadows.tooltip"))
                                .setDefaultValue(true)
                                .setBinding(this.vanillaOpts.entityShadows()::set, this.vanillaOpts.entityShadows()::get)
                                .setImpact(OptionImpact.MEDIUM)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:quality.vignette"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.vignette.name"))
                                .setTooltip(Component.translatable("sodium.options.vignette.tooltip"))
                                .setDefaultValue(DEFAULTS.quality.enableVignette)
                                .setBinding(value -> this.sodiumOpts.quality.enableVignette = value, () -> this.sodiumOpts.quality.enableVignette)
                )
        );

        qualityPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("options.mipmapLevels"))
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:quality.mipmap_levels"))
                                .setStorageHandler(this.vanillaStorage)
                                .setName(Component.translatable("options.mipmapLevels"))
                                .setValueFormatter(ControlValueFormatterImpls.multiplier())
                                .setTooltip(Component.translatable("sodium.options.mipmap_levels.tooltip"))
                                .setRange(0, 4, 1)
                                .setDefaultValue(4)
                                .setBinding(this.vanillaOpts.mipmapLevels()::set, this.vanillaOpts.mipmapLevels()::get)
                                .setImpact(OptionImpact.MEDIUM)
                                .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                )
        );
        return qualityPage;
    }

    private OptionPageBuilder buildPerformancePage(ConfigBuilder builder) {
        var performancePage = builder.createOptionPage().setName(Component.translatable("sodium.options.pages.performance"));

        performancePage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.chunk_update_threads.name"))
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:performance.chunk_update_threads"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.chunk_update_threads.name"))
                                .setValueFormatter(ControlValueFormatterImpls.quantityOrDisabled("threads", "Default"))
                                .setTooltip(Component.translatable("sodium.options.chunk_update_threads.tooltip"))
                                .setRange(0, Runtime.getRuntime().availableProcessors(), 1)
                                .setDefaultValue(DEFAULTS.performance.chunkBuilderThreads)
                                .setBinding(value -> this.sodiumOpts.performance.chunkBuilderThreads = value, () -> this.sodiumOpts.performance.chunkBuilderThreads)
                                .setImpact(OptionImpact.HIGH)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:performance.always_defer_chunk_updates"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.always_defer_chunk_updates.name"))
                                .setTooltip(Component.translatable("sodium.options.always_defer_chunk_updates.tooltip"))
                                .setDefaultValue(DEFAULTS.performance.alwaysDeferChunkUpdates)
                                .setBinding(value -> this.sodiumOpts.performance.alwaysDeferChunkUpdates = value, () -> this.sodiumOpts.performance.alwaysDeferChunkUpdates)
                                .setImpact(OptionImpact.HIGH)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                )
        );

        performancePage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.use_block_face_culling.name"))
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:performance.use_block_face_culling"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.use_block_face_culling.name"))
                                .setTooltip(Component.translatable("sodium.options.use_block_face_culling.tooltip"))
                                .setDefaultValue(DEFAULTS.performance.useBlockFaceCulling)
                                .setBinding(value -> this.sodiumOpts.performance.useBlockFaceCulling = value, () -> this.sodiumOpts.performance.useBlockFaceCulling)
                                .setImpact(OptionImpact.MEDIUM)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:performance.use_fog_occlusion"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.use_fog_occlusion.name"))
                                .setTooltip(Component.translatable("sodium.options.use_fog_occlusion.tooltip"))
                                .setDefaultValue(DEFAULTS.performance.useFogOcclusion)
                                .setBinding(value -> this.sodiumOpts.performance.useFogOcclusion = value, () -> this.sodiumOpts.performance.useFogOcclusion)
                                .setImpact(OptionImpact.MEDIUM)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:performance.use_entity_culling"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.use_entity_culling.name"))
                                .setTooltip(Component.translatable("sodium.options.use_entity_culling.tooltip"))
                                .setDefaultValue(DEFAULTS.performance.useEntityCulling)
                                .setBinding(value -> this.sodiumOpts.performance.useEntityCulling = value, () -> this.sodiumOpts.performance.useEntityCulling)
                                .setImpact(OptionImpact.MEDIUM)
                )
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:performance.animate_only_visible_textures"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.animate_only_visible_textures.name"))
                                .setTooltip(Component.translatable("sodium.options.animate_only_visible_textures.tooltip"))
                                .setDefaultValue(DEFAULTS.performance.animateOnlyVisibleTextures)
                                .setBinding(value -> this.sodiumOpts.performance.animateOnlyVisibleTextures = value, () -> this.sodiumOpts.performance.animateOnlyVisibleTextures)
                                .setImpact(OptionImpact.HIGH)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                )
                .addOption(
                        this.buildNoErrorContextOption(builder)
                )
        );

        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            performancePage.addOptionGroup(builder.createOptionGroup()
                    .setName(Component.translatable("sodium.options.sort_behavior.name"))
                    .addOption(
                            builder.createBooleanOption(ResourceLocation.parse("sodium:performance.sort_behavior"))
                                    .setStorageHandler(this.sodiumStorage)
                                    .setName(Component.translatable("sodium.options.sort_behavior.name"))
                                    .setTooltip(Component.translatable("sodium.options.sort_behavior.tooltip"))
                                    .setDefaultValue(DEFAULTS.performance.sortingEnabled)
                                    .setBinding(value -> this.sodiumOpts.performance.sortingEnabled = value, () -> this.sodiumOpts.performance.sortingEnabled)
                                    .setImpact(OptionImpact.LOW)
                                    .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                    )
            );
        }
        return performancePage;
    }

    private OptionBuilder buildNoErrorContextOption(ConfigBuilder builder) {
        return builder.createBooleanOption(ResourceLocation.parse("sodium:performance.use_no_error_context"))
                .setStorageHandler(this.sodiumStorage)
                .setName(Component.translatable("sodium.options.use_no_error_context.name"))
                .setTooltip(Component.translatable("sodium.options.use_no_error_context.tooltip"))
                .setDefaultValue(DEFAULTS.performance.useNoErrorGLContext)
                .setBinding(value -> this.sodiumOpts.performance.useNoErrorGLContext = value, () -> this.sodiumOpts.performance.useNoErrorGLContext)
                .setEnabledProvider((state) -> {
                    GLCapabilities capabilities = GL.getCapabilities();
                    return (capabilities.OpenGL46 || capabilities.GL_KHR_no_error)
                            && !Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED);
                })
                .setImpact(OptionImpact.LOW)
                .setFlags(OptionFlag.REQUIRES_GAME_RESTART);
    }

    private OptionPageBuilder buildAdvancedPage(ConfigBuilder builder) {
        var advancedPage = builder.createOptionPage().setName(Component.translatable("sodium.options.pages.advanced"));

        boolean isPersistentMappingSupported = MappedStagingBuffer.isSupported(RenderDevice.INSTANCE);

        advancedPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.use_persistent_mapping.name"))
                .addOption(
                        builder.createBooleanOption(ResourceLocation.parse("sodium:advanced.use_persistent_mapping"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.use_persistent_mapping.name"))
                                .setTooltip(Component.translatable("sodium.options.use_persistent_mapping.tooltip"))
                                .setDefaultValue(DEFAULTS.advanced.useAdvancedStagingBuffers)
                                .setBinding(value -> this.sodiumOpts.advanced.useAdvancedStagingBuffers = value, () -> this.sodiumOpts.advanced.useAdvancedStagingBuffers)
                                .setEnabled(isPersistentMappingSupported)
                                .setImpact(OptionImpact.MEDIUM)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                )
        );

        advancedPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("sodium.options.cpu_render_ahead_limit.name"))
                .addOption(
                        builder.createIntegerOption(ResourceLocation.parse("sodium:advanced.cpu_render_ahead_limit"))
                                .setStorageHandler(this.sodiumStorage)
                                .setName(Component.translatable("sodium.options.cpu_render_ahead_limit.name"))
                                .setValueFormatter(ControlValueFormatterImpls.translateVariable("sodium.options.cpu_render_ahead_limit.value"))
                                .setTooltip(Component.translatable("sodium.options.cpu_render_ahead_limit.tooltip"))
                                .setRange(0, 9, 1)
                                .setDefaultValue(DEFAULTS.advanced.cpuRenderAheadLimit)
                                .setBinding(value -> this.sodiumOpts.advanced.cpuRenderAheadLimit = value, () -> this.sodiumOpts.advanced.cpuRenderAheadLimit)
                )
        );
        return advancedPage;
    }

}