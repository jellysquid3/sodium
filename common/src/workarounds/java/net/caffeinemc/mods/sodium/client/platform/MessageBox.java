package net.caffeinemc.mods.sodium.client.platform;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.platform.windows.api.Shell32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.User32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox.MsgBoxCallback;
import net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox.MsgBoxParamSw;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;

public class MessageBox {
    private static final @Nullable MessageBoxImpl IMPL = MessageBoxImpl.chooseImpl();

    public static void showMessageBox(NativeWindowHandle window,
                                      IconType icon, String title,
                                      String description,
                                      @Nullable String helpUrl)
    {
        if (IMPL != null) {
            IMPL.showMessageBox(window, icon, title, description, helpUrl);
        }
    }

    private interface MessageBoxImpl {
        static @Nullable MessageBoxImpl chooseImpl() {
            if (OsUtils.getOs() == OsUtils.OperatingSystem.WIN) {
                return new WindowsMessageBoxImpl();
            } else {
                // TODO: Tiny File Dialogs is really bad. We need something better.
                //return new TFDMessageBoxImpl();
                return null;
            }
        }

        void showMessageBox(NativeWindowHandle window,
                            IconType icon, String title,
                            String description,
                            @Nullable String helpUrl);
    }

    private static class WindowsMessageBoxImpl implements MessageBoxImpl {
        @Override
        public void showMessageBox(NativeWindowHandle window,
                                   IconType icon, String title,
                                   String description,
                                   @Nullable String helpUrl) {
            Objects.requireNonNull(title);
            Objects.requireNonNull(description);
            Objects.requireNonNull(icon);

            final MsgBoxCallback msgBoxCallback;

            if (helpUrl != null) {
                msgBoxCallback = MsgBoxCallback.create(lpHelpInfo -> {
                    Shell32.browseUrl(window, helpUrl);
                });
            } else {
                msgBoxCallback = null;
            }

            final long hWndOwner;

            if (window != null) {
                hWndOwner = window.getWin32Handle();
            } else {
                hWndOwner = MemoryUtil.NULL;
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer lpText = stack.malloc(MemoryUtil.memLengthUTF16(description, true));
                MemoryUtil.memUTF16(description, true, lpText);

                ByteBuffer lpCaption = stack.malloc(MemoryUtil.memLengthUTF16(title, true));
                MemoryUtil.memUTF16(title, true, lpCaption);

                var params = MsgBoxParamSw.allocate(stack);
                params.setCbSize(MsgBoxParamSw.SIZEOF);
                params.setHWndOwner(hWndOwner);
                params.setText(lpText);
                params.setCaption(lpCaption);
                params.setStyle(getStyle(icon, msgBoxCallback != null));
                params.setCallback(msgBoxCallback);

                User32.callMessageBoxIndirectW(params);
            } finally {
                if (msgBoxCallback != null) {
                    msgBoxCallback.free();
                }
            }
        }

        private static int getStyle(IconType icon, boolean showHelp) {
            int style = switch (icon) {
                case INFO -> 0x00000040; /* MB_ICONINFORMATION */
                case WARNING -> 0x00000030; /* MB_ICONWARNING */
                case ERROR -> 0x00000010; /* MB_ICONERROR */
            };

            if (showHelp) {
                style |= 0x00004000 /* MB_HELP */;
            }

            return style;
        }

    }

    public enum IconType {
        INFO,
        WARNING,
        ERROR
    }
}
