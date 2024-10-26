package net.caffeinemc.mods.sodium.client.compatibility.environment.probe;

import org.jetbrains.annotations.NotNull;

public enum GraphicsAdapterVendor {
    NVIDIA,
    AMD,
    INTEL,
    UNKNOWN;

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

    public static GraphicsAdapterVendor fromIcdName(String name) {
        // Intel Gen 4, 5, 6    - ig4icd
        // Intel Gen 7          - ig7icd
        // Intel Gen 7.5        - ig75icd
        // Intel Gen 8          - ig8icd
        // Intel Gen 9, 9.5     - ig9icd
        // Intel Gen 11         - ig11icd
        // Intel Gen 12         - ig12icd (UHD Graphics, with early drivers)
        //                        igxelpicd (Xe-LP; integrated)
        //                        igxehpicd (Xe-HP; dedicated)
        if (name.matches("ig(4|7|75|8|9|11|12|xelp|xehp)icd(32|64)")) {
            return INTEL;
        }

        if (name.matches("nvoglv(32|64)")) {
            return NVIDIA;
        }

        if (name.matches("atiglpxx|atig6pxx")) {
            return AMD;
        }

        return UNKNOWN;
    }
}
