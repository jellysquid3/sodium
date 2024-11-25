package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util.NvShortString;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.NvDRS;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.*;

import static org.lwjgl.system.Checks.check;

public class NvAPI {
    private static final SharedLibrary SHARED_LIBRARY;

    static {
        SHARED_LIBRARY = APIUtil.apiCreateLibrary("nvapi64");
    }

    private static class Functions {
        private static final long NvAPI_QueryInterface = APIUtil.apiGetFunctionAddress(SHARED_LIBRARY, "nvapi_QueryInterface");

        // (NvAPI_Status)()*
        private static final long NvAPI_Initialize = NvAPI.getFunction(0x0150E828);

        // (NvAPI_Status)()*
        private static final long NvAPI_Unload = NvAPI.getFunction(0xD22BDD7E);
    }

    public static long getFunction(int interfaceId) {
        var pfn = NvAPI_QueryInterface(interfaceId);

        if (pfn == MemoryUtil.NULL) {
            throw new UnsatisfiedLinkError("Failed to query interface: 0x%08X"
                    .formatted(interfaceId));
        }

        return pfn;
    }

    @NativeType("void*")
    public static long NvAPI_QueryInterface(int interfaceId) {
        return JNI.callP(interfaceId, check(Functions.NvAPI_QueryInterface));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_Initialize() {
        return JNI.callI(check(Functions.NvAPI_Initialize));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_Unload() {
        return JNI.callI(check(Functions.NvAPI_Unload));
    }

    public static void checkError(int status) {
        if (status != NvAPIStatus.NVAPI_OK) {
            throw new RuntimeException("%s (code=%d)"
                    .formatted(getErrorMessage(status), status));
        }
    }

    public static @Nullable String getErrorMessage(int status) {
        String message = null;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            NvShortString messageBuf = NvShortString.allocateStack(stack);
            var result = NvDRS.NvAPI_GetErrorMessage(status, messageBuf.address());

            if (result == NvAPIStatus.NVAPI_OK) {
                message = messageBuf.value();
            }
        }

        return message;
    }
}
