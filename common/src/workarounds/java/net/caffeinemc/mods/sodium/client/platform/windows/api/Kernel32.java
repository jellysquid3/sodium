package net.caffeinemc.mods.sodium.client.platform.windows.api;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import java.nio.ByteBuffer;

public class Kernel32 {
    private static final SharedLibrary LIBRARY = APIUtil.apiCreateLibrary("kernel32");

    private static final int MAX_PATH = 32767;

    private static final int GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT = 1 << 0;
    private static final int GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS = 1 << 2;

    private static final long PFN_GetCommandLineW;
    private static final long PFN_SetEnvironmentVariableW;

    private static final long PFN_GetModuleHandleExW;
    private static final long PFN_GetLastError;

    private static final long PFN_GetModuleFileNameW;


    static {
        PFN_GetCommandLineW = APIUtil.apiGetFunctionAddress(LIBRARY, "GetCommandLineW");
        PFN_SetEnvironmentVariableW = APIUtil.apiGetFunctionAddress(LIBRARY, "SetEnvironmentVariableW");
        PFN_GetModuleHandleExW = APIUtil.apiGetFunctionAddress(LIBRARY, "GetModuleHandleExW");
        PFN_GetLastError = APIUtil.apiGetFunctionAddress(LIBRARY, "GetLastError");
        PFN_GetModuleFileNameW = APIUtil.apiGetFunctionAddress(LIBRARY, "GetModuleFileNameW");
    }

    public static void setEnvironmentVariable(String name, @Nullable String value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer lpNameBuf = stack.malloc(16, MemoryUtil.memLengthUTF16(name, true));
            MemoryUtil.memUTF16(name, true, lpNameBuf);

            ByteBuffer lpValueBuf = null;

            if (value != null) {
                lpValueBuf = stack.malloc(16, MemoryUtil.memLengthUTF16(value, true));
                MemoryUtil.memUTF16(value, true, lpValueBuf);
            }

            JNI.callPPI(MemoryUtil.memAddress0(lpNameBuf), MemoryUtil.memAddressSafe(lpValueBuf), PFN_SetEnvironmentVariableW);
        }
    }


    public static void setEnvironmentVariable(String name, short @Nullable [] value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer lpNameBuf = stack.malloc(16, MemoryUtil.memLengthUTF16(name, true));
            MemoryUtil.memUTF16(name, true, lpNameBuf);

            ByteBuffer lpValueBuf = null;

            if (value != null) {
                lpValueBuf = stack.malloc(16, (value.length + 1) << 1);
                lpValueBuf.asShortBuffer()
                        .put(value);
            }

            JNI.callPPI(MemoryUtil.memAddress0(lpNameBuf), MemoryUtil.memAddressSafe(lpValueBuf), PFN_SetEnvironmentVariableW);
        }
    }

    public static long getCommandLine() {
        return JNI.callP(PFN_GetCommandLineW);
    }

    public static long getModuleHandleByNames(String[] names) {
        for (String name : names) {
            var handle = getModuleHandleByName(name);

            if (handle != MemoryUtil.NULL) {
                return handle;
            }
        }

        throw new RuntimeException("Could not obtain handle of module");
    }

    public static long getModuleHandleByName(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer lpFunctionNameBuf = stack.malloc(16, MemoryUtil.memLengthUTF16(name, true));
            MemoryUtil.memUTF16(name, true, lpFunctionNameBuf);

            PointerBuffer phModule = stack.callocPointer(1);

            int result;
            result = JNI.callPPI(GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                    MemoryUtil.memAddress(lpFunctionNameBuf), MemoryUtil.memAddress(phModule), PFN_GetModuleHandleExW);

            if (result == 0) {
                var error = getLastError();

                switch (error) {
                    case 126 /* ERROR_MOD_NOT_FOUND */:
                        return MemoryUtil.NULL;
                    default:
                        throw new RuntimeException("GetModuleHandleEx failed, error=" + error);
                }
            }

            return phModule.get(0);
        }
    }

    public static String getModuleFileName(long phModule) {
        ByteBuffer lpFileName = MemoryUtil.memAlignedAlloc(16, MAX_PATH);

        try {
            int length = JNI.callPPI(phModule, MemoryUtil.memAddress(lpFileName), lpFileName.capacity(), PFN_GetModuleFileNameW);

            if (length == 0) {
                throw new RuntimeException("GetModuleFileNameW failed, error=" + getLastError());
            }

            return MemoryUtil.memUTF16(lpFileName, length);
        } finally {
            MemoryUtil.memAlignedFree(lpFileName);
        }
    }

    public static int getLastError() {
        return JNI.callI(PFN_GetLastError);
    }
}
