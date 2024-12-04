package net.caffeinemc.mods.sodium.client.compatibility.environment.probe;

import net.caffeinemc.mods.sodium.client.compatibility.environment.GlContextInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

public enum GraphicsAdapterVendor {
    NVIDIA,
    AMD,
    INTEL,
    UNKNOWN;

    // Intel Gen 4, 5, 6    - ig4icd
    // Intel Gen 7          - ig7icd
    // Intel Gen 7.5        - ig75icd
    // Intel Gen 8          - ig8icd
    // Intel Gen 9, 9.5     - ig9icd
    // Intel Gen 11         - ig11icd
    // Intel Gen 12         - ig12icd (UHD Graphics, with early drivers)
    //                        igxelpicd (Xe-LP; integrated)
    //                        igxehpicd (Xe-HP; dedicated)
    private static final Pattern INTEL_ICD_PATTERN =
            Pattern.compile("ig(4|7|75|8|9|11|12|xelp|xehp)icd(32|64)\\.dll", Pattern.CASE_INSENSITIVE);

    private static final Pattern NVIDIA_ICD_PATTERN =
            Pattern.compile("nvoglv(32|64)\\.dll", Pattern.CASE_INSENSITIVE);

    private static final Pattern AMD_ICD_PATTERN =
            Pattern.compile("(atiglpxx|atig6pxx)\\.dll", Pattern.CASE_INSENSITIVE);

    @NotNull
    static GraphicsAdapterVendor fromPciVendorId(String vendor) {
        if (vendor.contains("0x1002")) {
            return AMD;
        } else if (vendor.contains("0x10de")) {
            return NVIDIA;
        } else if (vendor.contains("0x8086")) {
            return INTEL;
        }

        return UNKNOWN;
    }

    @NotNull
    public static GraphicsAdapterVendor fromIcdName(String name) {
        if (matchesPattern(INTEL_ICD_PATTERN, name)) {
            return INTEL;
        } else if (matchesPattern(NVIDIA_ICD_PATTERN, name)) {
            return NVIDIA;
        } else if (matchesPattern(AMD_ICD_PATTERN, name)) {
            return AMD;
        } else {
            return UNKNOWN;
        }
    }

    @NotNull
    public static GraphicsAdapterVendor fromContext(GlContextInfo context) {
        var vendor = context.vendor();

        return switch (vendor) {
            case "NVIDIA Corporation" -> NVIDIA;
            case "Intel", "Intel Open Source Technology Center" -> INTEL;
            case "AMD", "ATI Technologies Inc." -> AMD;
            default -> UNKNOWN;
        };

    }

    private static boolean matchesPattern(Pattern pattern, String name) {
        return pattern.matcher(name)
                .matches();
    }
}
