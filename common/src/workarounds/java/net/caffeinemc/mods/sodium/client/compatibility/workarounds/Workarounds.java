package net.caffeinemc.mods.sodium.client.compatibility.workarounds;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Workarounds {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-Workarounds");

    private static final AtomicReference<Set<Reference>> ACTIVE_WORKAROUNDS = new AtomicReference<>(EnumSet.noneOf(Reference.class));

    public static void init() {
        var workarounds = findNecessaryWorkarounds();

        if (!workarounds.isEmpty()) {
            LOGGER.warn("Sodium has applied one or more workarounds to prevent crashes or other issues on your system: [{}]",
                    workarounds.stream()
                            .map(Enum::name)
                            .collect(Collectors.joining(", ")));
            LOGGER.warn("This is not necessarily an issue, but it may result in certain features or optimizations being " +
                    "disabled. You can sometimes fix these issues by upgrading your graphics driver.");
        }

        ACTIVE_WORKAROUNDS.set(workarounds);
    }

    private static Set<Reference> findNecessaryWorkarounds() {
        var workarounds = EnumSet.noneOf(Reference.class);
        var operatingSystem = OsUtils.getOs();

        if (NvidiaWorkarounds.isNvidiaGraphicsCardPresent()) {
            workarounds.add(Reference.NVIDIA_THREADED_OPTIMIZATIONS_BROKEN);
        }

        if (IntelWorkarounds.isUsingIntelGen8OrOlder()) {
            workarounds.add(Reference.INTEL_FRAMEBUFFER_BLIT_CRASH_WHEN_UNFOCUSED);
            workarounds.add(Reference.INTEL_DEPTH_BUFFER_COMPARISON_UNRELIABLE);
        }

        if (operatingSystem == OsUtils.OperatingSystem.LINUX) {
            var session = System.getenv("XDG_SESSION_TYPE");

            if (session == null) {
                LOGGER.warn("Unable to determine desktop session type because the environment variable XDG_SESSION_TYPE " +
                        "is not set! Your user session may not be configured correctly.");
            }

            if (Objects.equals(session, "wayland")) {
                // This will also apply under Xwayland, even though the problem does not happen there
                workarounds.add(Reference.NO_ERROR_CONTEXT_UNSUPPORTED);
            }
        }

        return Collections.unmodifiableSet(workarounds);
    }

    public static boolean isWorkaroundEnabled(Reference id) {
        return ACTIVE_WORKAROUNDS.get()
                .contains(id);
    }

    public enum Reference {
        /**
         * The NVIDIA driver applies "Threaded Optimizations" when Minecraft is detected, causing severe
         * performance issues and crashes.
         * <a href="https://github.com/CaffeineMC/sodium/issues/1816">GitHub Issue</a>
         */
        NVIDIA_THREADED_OPTIMIZATIONS_BROKEN,

        /**
         * Requesting a No Error Context causes a crash at startup when using a Wayland session.
         * <a href="https://github.com/CaffeineMC/sodium/issues/1624">GitHub Issue</a>
         */
        NO_ERROR_CONTEXT_UNSUPPORTED,

        /**
         * Intel's graphics driver for Gen8 and older seems to be faulty and causes a crash when calling
         * glFramebufferBlit after the window loses focus.
         * <a href="https://github.com/CaffeineMC/sodium/issues/2727">GitHub Issue</a>
         */
        INTEL_FRAMEBUFFER_BLIT_CRASH_WHEN_UNFOCUSED,

        /**
         * Intel's graphics driver for Gen8 and older does not respect depth comparison rules per the OpenGL
         * specification, causing block model overlays to Z-fight when the overlay is on a different render pass than
         * the base model.
         * <a href="https://github.com/CaffeineMC/sodium/issues/2830">GitHub Issue</a>
         */
        INTEL_DEPTH_BUFFER_COMPARISON_UNRELIABLE
    }
}
