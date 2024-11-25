package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.structures;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util.NvUnicodeString;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums.NVDRSSettingLocation;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums.NVDRSSettingType;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums.NVDRSSettingValues;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;

// NVDRS_SETTING_V1
public class NVRDSSettingV1 extends Struct<NVRDSSettingV1> {
    public static final int SIZEOF;
    public static final int ALIGNOF;

    private static final int OFFSET_VERSION;
    private static final int OFFSET_SETTING_NAME;
    private static final int OFFSET_SETTING_ID;
    private static final int OFFSET_SETTING_TYPE;
    private static final int OFFSET_SETTING_LOCATION;
    private static final int OFFSET_IS_CURRENT_PREDEFINED;
    private static final int OFFSET_IS_PREDEFINED_VALID;

    private static final int OFFSET_PREDEFINED_VALUE;
    private static final int OFFSET_CURRENT_VALUE;

    public static final int VERSION;

    static {
        var layout = __struct(
                __member(Integer.BYTES), // version

                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF), // settingName
                __member(Integer.BYTES), // settingId,
                __member(Integer.BYTES), // settingType
                __member(Integer.BYTES), // settingLocation
                __member(Integer.BYTES), // isCurrentPredefined
                __member(Integer.BYTES), // isPredefinedValid,

                __member(NVDRSSettingValues.SIZEOF, NVDRSSettingValues.ALIGNOF), // predefinedValue union
                __member(NVDRSSettingValues.SIZEOF, NVDRSSettingValues.ALIGNOF) // currentValue union
        );

        OFFSET_VERSION = layout.offsetof(0);
        OFFSET_SETTING_NAME = layout.offsetof(1);
        OFFSET_SETTING_ID = layout.offsetof(2);
        OFFSET_SETTING_TYPE = layout.offsetof(3);
        OFFSET_SETTING_LOCATION = layout.offsetof(4);
        OFFSET_IS_CURRENT_PREDEFINED = layout.offsetof(5);
        OFFSET_IS_PREDEFINED_VALID = layout.offsetof(6);

        OFFSET_PREDEFINED_VALUE = layout.offsetof(7);
        OFFSET_CURRENT_VALUE = layout.offsetof(8);

        SIZEOF = layout.getSize();
        ALIGNOF = layout.getAlignment();

        VERSION = SIZEOF | (1 << 16);
    }

    protected NVRDSSettingV1(long address, ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected @NotNull NVRDSSettingV1 create(long address, ByteBuffer container) {
        return new NVRDSSettingV1(address, container);
    }

    public static NVRDSSettingV1 allocateStack(MemoryStack stack) {
        return new NVRDSSettingV1(stack.ncalloc(ALIGNOF, 1, SIZEOF), null);
    }

    public void setVersion(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_VERSION, value);
    }

    public void setSettingName(String value) {
        NvUnicodeString.fromPointer(this.address + OFFSET_SETTING_NAME)
                .setValue(value);
    }

    public void setSettingId(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_SETTING_ID, value);
    }

    public void setSettingType(NVDRSSettingType value) {
        MemoryUtil.memPutInt(this.address + OFFSET_SETTING_TYPE, value.ordinal());
    }

    public void setSettingLocation(NVDRSSettingLocation value) {
        MemoryUtil.memPutInt(this.address + OFFSET_SETTING_LOCATION, value.ordinal());
    }

    public NVDRSSettingValues getPredefinedValue() {
        return NVDRSSettingValues.fromPointer(this.address + OFFSET_PREDEFINED_VALUE);
    }

    public NVDRSSettingValues getCurrentValue() {
        return NVDRSSettingValues.fromPointer(this.address + OFFSET_CURRENT_VALUE);
    }

    public void setIsCurrentPredefined(boolean value) {
        MemoryUtil.memPutInt(this.address + OFFSET_IS_CURRENT_PREDEFINED, value ? 1 : 0);
    }

    public void setPredefinedValid(boolean value) {
        MemoryUtil.memPutInt(this.address + OFFSET_IS_PREDEFINED_VALID, value ? 1 : 0);
    }

    @Override
    public int sizeof() {
        return SIZEOF;
    }
}
