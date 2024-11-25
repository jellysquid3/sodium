package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums;

// NVDRS_GPU_SUPPORT
public enum NVDRSGPUSupport {
    GEFORCE,
    QUADRO,
    NVS;

    public static int all() {
        return -1;
    }
}
