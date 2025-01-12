package net.caffeinemc.mods.sodium.client.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gui.options.*;
import net.caffeinemc.mods.sodium.client.gui.options.binding.compat.VanillaBooleanOptionBinding;
import net.caffeinemc.mods.sodium.client.gui.options.control.*;
import net.caffeinemc.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import net.caffeinemc.mods.sodium.client.gui.options.storage.SodiumOptionsStorage;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// TODO: Rename in Sodium 0.6
public class SodiumGameOptionPages {
    private static final SodiumOptionsStorage sodiumOpts = new SodiumOptionsStorage();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();
    private static final Window window = Minecraft.getInstance().getWindow();

    public static OptionPage general() {
        Monitor monitor = window.findBestMonitor();
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.renderDistance"))
                        .setTooltip(Component.translatable("sodium.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, 32, 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((options, value) -> options.renderDistance().set(value), options -> options.renderDistance().get())
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.simulationDistance"))
                        .setTooltip(Component.translatable("sodium.options.simulation_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 5, 32, 1, ControlValueFormatter.translateVariable("options.chunks")))
                        .setBinding((options, value) -> options.simulationDistance().set(value), options -> options.simulationDistance().get())
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.gamma"))
                        .setTooltip(Component.translatable("sodium.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((opts, value) -> opts.gamma().set(value * 0.01D), (opts) -> (int) (opts.gamma().get() / 0.01D))
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.guiScale"))
                        .setTooltip(Component.translatable("sodium.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, Minecraft.getInstance().getWindow().calculateScale(0, Minecraft.getInstance().isEnforceUnicode()), 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> {
                            opts.guiScale().set(value);

                            Minecraft client = Minecraft.getInstance();
                            client.resizeDisplay();
                        }, opts -> opts.guiScale().get())
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(Component.translatable("options.fullscreen"))
                        .setTooltip(Component.translatable("sodium.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.fullscreen().set(value);

                            Minecraft client = Minecraft.getInstance();
                            Window window = client.getWindow();

                            if (window != null && window.isFullscreen() != opts.fullscreen().get()) {
                                window.toggleFullScreen();

                                // The client might not be able to enter full-screen mode
                                opts.fullscreen().set(window.isFullscreen());
                            }
                        }, (opts) -> opts.fullscreen().get())
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.fullscreen.resolution"))
                        .setTooltip(Component.translatable("sodium.options.fullscreen_resolution.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, null != monitor? monitor.getModeCount(): 0, 1, ControlValueFormatter.resolution()))
                        .setBinding((options, value) -> {
                            if (null != monitor) {
                                window.setPreferredFullscreenVideoMode(0 == value? Optional.empty(): Optional.of(monitor.getMode(value - 1)));
                            }
                        }, options -> {
                            if (null == monitor) {
                                return 0;
                            }
                            else {
                                Optional<VideoMode> optional = window.getPreferredFullscreenVideoMode();
                                return optional.map((videoMode) -> monitor.getVideoModeIndex(videoMode) + 1).orElse(0);
                            }
                        })
                        .setEnabled(() -> OsUtils.getOs() == OsUtils.OperatingSystem.WIN && Minecraft.getInstance().getWindow().findBestMonitor() != null)
                        .setFlags(OptionFlag.REQUIRES_VIDEOMODE_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(Component.translatable("options.vsync"))
                        .setTooltip(Component.translatable("sodium.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(Minecraft.getInstance().options.enableVsync()))
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.framerateLimit"))
                        .setTooltip(Component.translatable("sodium.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, ControlValueFormatter.fpsLimit()))
                        .setBinding((opts, value) -> {
                            opts.framerateLimit().set(value);
                            Minecraft.getInstance().getFramerateLimitTracker().setFramerateLimit(value);
                        }, opts -> opts.framerateLimit().get())
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(Component.translatable("options.viewBobbing"))
                        .setTooltip(Component.translatable("sodium.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding(new VanillaBooleanOptionBinding(Minecraft.getInstance().options.bobView()))
                        .build())
                .add(OptionImpl.createBuilder(AttackIndicatorStatus.class, vanillaOpts)
                        .setName(Component.translatable("options.attackIndicator"))
                        .setTooltip(Component.translatable("sodium.options.attack_indicator.tooltip"))
                        .setControl(opts -> new CyclingControl<>(opts, AttackIndicatorStatus.class, new Component[] { Component.translatable("options.off"), Component.translatable("options.attack.crosshair"), Component.translatable("options.attack.hotbar") }))
                        .setBinding((opts, value) -> opts.attackIndicator().set(value), (opts) -> opts.attackIndicator().get())
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(Component.translatable("options.autosaveIndicator"))
                        .setTooltip(Component.translatable("sodium.options.autosave_indicator.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.showAutosaveIndicator().set(value), opts -> opts.showAutosaveIndicator().get())
                        .build())
                .build());

        return new OptionPage(Component.translatable("stat.generalButton"), ImmutableList.copyOf(groups));
    }

    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(GraphicsStatus.class, vanillaOpts)
                        .setName(Component.translatable("options.graphics"))
                        .setTooltip(Component.translatable("sodium.options.graphics_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, GraphicsStatus.class, new Component[] { Component.translatable("options.graphics.fast"), Component.translatable("options.graphics.fancy"), Component.translatable("options.graphics.fabulous") }))
                        .setBinding(
                                (opts, value) -> opts.graphicsMode().set(value),
                                opts -> opts.graphicsMode().get())
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(CloudStatus.class, vanillaOpts)
                        .setName(Component.translatable("options.renderClouds"))
                        .setTooltip(Component.translatable("sodium.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, CloudStatus.class, new Component[] { Component.translatable("options.off"), Component.translatable("options.graphics.fast"), Component.translatable("options.graphics.fancy") }))
                        .setBinding((opts, value) -> {
                            opts.cloudStatus().set(value);

                            if (Minecraft.useShaderTransparency()) {
                                RenderTarget framebuffer = Minecraft.getInstance().levelRenderer.getCloudsTarget();
                                if (framebuffer != null) {
                                    framebuffer.clear();
                                }
                            }
                        }, opts -> opts.cloudStatus().get())
                        .setImpact(OptionImpact.LOW)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setName(Component.translatable("soundCategory.weather"))
                        .setTooltip(Component.translatable("sodium.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.leaves_quality.name"))
                        .setTooltip(Component.translatable("sodium.options.leaves_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.leavesQuality = value, opts -> opts.quality.leavesQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(ParticleStatus.class, vanillaOpts)
                        .setName(Component.translatable("options.particles"))
                        .setTooltip(Component.translatable("sodium.options.particle_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, ParticleStatus.class, new Component[] { Component.translatable("options.particles.all"), Component.translatable("options.particles.decreased"), Component.translatable("options.particles.minimal") }))
                        .setBinding((opts, value) -> opts.particles().set(value), (opts) -> opts.particles().get())
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(Component.translatable("options.ao"))
                        .setTooltip(Component.translatable("sodium.options.smooth_lighting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.ambientOcclusion().set(value), opts -> opts.ambientOcclusion().get())
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.biomeBlendRadius"))
                        .setTooltip(Component.translatable("sodium.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 1, 7, 1, ControlValueFormatter.biomeBlend()))
                        .setBinding((opts, value) -> opts.biomeBlendRadius().set(value), opts -> opts.biomeBlendRadius().get())
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.entityDistanceScaling"))
                        .setTooltip(Component.translatable("sodium.options.entity_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 50, 500, 25, ControlValueFormatter.percentage()))
                        .setBinding((opts, value) -> opts.entityDistanceScaling().set(value / 100.0), opts -> Math.round(opts.entityDistanceScaling().get().floatValue() * 100.0F))
                        .setImpact(OptionImpact.HIGH)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setName(Component.translatable("options.entityShadows"))
                        .setTooltip(Component.translatable("sodium.options.entity_shadows.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.entityShadows().set(value), opts -> opts.entityShadows().get())
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.vignette.name"))
                        .setTooltip(Component.translatable("sodium.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .build())
                .build());


        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setName(Component.translatable("options.mipmapLevels"))
                        .setTooltip(Component.translatable("sodium.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels().set(value), opts -> opts.mipmapLevels().get())
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());


        return new OptionPage(Component.translatable("sodium.options.pages.quality"), ImmutableList.copyOf(groups));
    }

    public static OptionPage performance() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.chunk_update_threads.name"))
                        .setTooltip(Component.translatable("sodium.options.chunk_update_threads.tooltip"))
                        .setControl(o -> new SliderControl(o, 0, Runtime.getRuntime().availableProcessors(), 1, ControlValueFormatter.quantityOrDisabled("threads", "Default")))
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.chunkBuilderThreads = value, opts -> opts.performance.chunkBuilderThreads)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(DeferMode.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.defer_chunk_updates.name"))
                        .setTooltip(Component.translatable("sodium.options.defer_chunk_updates.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, DeferMode.class))
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.chunkBuildDeferMode = value, opts -> opts.performance.chunkBuildDeferMode)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build())
                .build()
        );

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.use_block_face_culling.name"))
                        .setTooltip(Component.translatable("sodium.options.use_block_face_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.performance.useBlockFaceCulling = value, opts -> opts.performance.useBlockFaceCulling)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.use_fog_occlusion.name"))
                        .setTooltip(Component.translatable("sodium.options.use_fog_occlusion.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.performance.useFogOcclusion = value, opts -> opts.performance.useFogOcclusion)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.use_entity_culling.name"))
                        .setTooltip(Component.translatable("sodium.options.use_entity_culling.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setBinding((opts, value) -> opts.performance.useEntityCulling = value, opts -> opts.performance.useEntityCulling)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.animate_only_visible_textures.name"))
                        .setTooltip(Component.translatable("sodium.options.animate_only_visible_textures.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.HIGH)
                        .setBinding((opts, value) -> opts.performance.animateOnlyVisibleTextures = value, opts -> opts.performance.animateOnlyVisibleTextures)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                        .build()
                )
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.use_no_error_context.name"))
                        .setTooltip(Component.translatable("sodium.options.use_no_error_context.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.performance.useNoErrorGLContext = value, opts -> opts.performance.useNoErrorGLContext)
                        .setEnabled(SodiumGameOptionPages::supportsNoErrorContext)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .build())
                .add(OptionImpl.createBuilder(InactivityFpsLimit.class, vanillaOpts)
                        .setName(Component.translatable("options.inactivityFpsLimit"))
                        .setTooltip(v -> Component.translatable(v.getId() == 0 ? "options.inactivityFpsLimit.minimized.tooltip" : "options.inactivityFpsLimit.afk.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, InactivityFpsLimit.class, new Component[] { Component.translatable("options.inactivityFpsLimit.minimized"), Component.translatable("options.inactivityFpsLimit.afk") }))
                        .setBinding((opts, value) -> opts.inactivityFpsLimit().set(value), opts -> opts.inactivityFpsLimit().get())
                        .build())
                .build());

        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            groups.add(OptionGroup.createBuilder()
                    .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                            .setName(Component.translatable("sodium.options.sort_behavior.name"))
                            .setTooltip(Component.translatable("sodium.options.sort_behavior.tooltip"))
                            .setControl(TickBoxControl::new)
                            .setBinding((opts, value) -> opts.performance.sortingEnabled = value, opts -> opts.performance.sortingEnabled)
                            .setImpact(OptionImpact.LOW)
                            .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                            .build())
                    .build());
        }

        return new OptionPage(Component.translatable("sodium.options.pages.performance"), ImmutableList.copyOf(groups));
    }

    private static boolean supportsNoErrorContext() {
        GLCapabilities capabilities = GL.getCapabilities();
        return (capabilities.OpenGL46 || capabilities.GL_KHR_no_error)
                && !Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED);
    }

    public static OptionPage advanced() {
        List<OptionGroup> groups = new ArrayList<>();

        boolean isPersistentMappingSupported = MappedStagingBuffer.isSupported(RenderDevice.INSTANCE);

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.use_persistent_mapping.name"))
                        .setTooltip(Component.translatable("sodium.options.use_persistent_mapping.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.MEDIUM)
                        .setEnabled(() -> isPersistentMappingSupported)
                        .setBinding((opts, value) -> opts.advanced.useAdvancedStagingBuffers = value, opts -> opts.advanced.useAdvancedStagingBuffers)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build()
                )
                .build());

        groups.add(OptionGroup.createBuilder()
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setName(Component.translatable("sodium.options.cpu_render_ahead_limit.name"))
                        .setTooltip(Component.translatable("sodium.options.cpu_render_ahead_limit.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 9, 1, ControlValueFormatter.translateVariable("sodium.options.cpu_render_ahead_limit.value")))
                        .setBinding((opts, value) -> opts.advanced.cpuRenderAheadLimit = value, opts -> opts.advanced.cpuRenderAheadLimit)
                        .build()
                )
                .build());

        return new OptionPage(Component.translatable("sodium.options.pages.advanced"), ImmutableList.copyOf(groups));
    }
}
