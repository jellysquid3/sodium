package net.caffeinemc.mods.sodium.client.platform.windows.api;

import net.caffeinemc.mods.sodium.client.platform.NativeWindowHandle;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.SharedLibrary;

import java.util.Objects;

import static org.lwjgl.system.APIUtil.apiCreateLibrary;
import static org.lwjgl.system.APIUtil.apiGetFunctionAddressOptional;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Shell32 {
    private static final SharedLibrary LIBRARY = apiCreateLibrary("shell32");

    private static final long PFN_ShellExecuteW = apiGetFunctionAddressOptional(LIBRARY, "ShellExecuteW");

    public static void browseUrl(@Nullable NativeWindowHandle window, String url) {
        Objects.requireNonNull(url, "URL parameter must be non-null");

        try (var stack = MemoryStack.stackPush()) {
            stack.nUTF16("open", true);
            var lpOperation = stack.getPointerAddress();

            stack.nUTF16(url, true);
            var lpFile = stack.getPointerAddress();

            nShellExecuteW(window != null ? window.getWin32Handle() : NULL,
                    lpOperation,
                    lpFile,
                    NULL,
                    NULL,
                    0x1 /* SW_NORMAL */);
        }
    }

    public static long nShellExecuteW(
            /* HWND */      long hwnd,
            /* LPCWSTR */   long lpOperation,
            /* LPCWSTR */   long lpFile,
            /* LPCWSTR */   long lpParameters,
            /* LPCWSTR */   long lpDirectory,
            /* INT */       int nShowCmd
    ) {
        return JNI.invokePPPPPP(hwnd, lpOperation, lpFile, lpParameters, lpDirectory, nShowCmd, checkPfn(PFN_ShellExecuteW));
    }

    private static long checkPfn(long pfn) {
        if (pfn == NULL) {
            throw new NullPointerException("Function pointer not available");
        }

        return pfn;
    }
}
