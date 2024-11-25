package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.NvAPI;
import org.lwjgl.system.JNI;
import org.lwjgl.system.NativeType;

import static org.lwjgl.system.Checks.check;

public class NvDRS {
    private static class Functions {
        // (NvAPI_Status)(NvAPI_Status, NvAPI_ShortString*)
        private static final long NvAPI_GetErrorMessage = NvAPI.getFunction(0x6C2D048C);

        // (NvAPI_Status)(NvDRSSessionHandle*)
        private static final long NvAPI_DRS_CreateSession = NvAPI.getFunction(0x0694D52E);

        // (NvAPI_Status)(NvDRSSessionHandle)
        private static final long NvAPI_DRS_LoadSettings = NvAPI.getFunction(0x375DBD6B);
        // (NvAPI_Status)(NvDRSSessionHandle)
        private static final long NvAPI_DRS_SaveSettings = NvAPI.getFunction(0xFCBC7E14);

        // (NvAPI_Status)(NvDRSSessionHandle, NVDRS_PROFILE_V1*)
        private static final long NvAPI_DRS_CreateProfile = NvAPI.getFunction(0xCC176068);

        // (NvAPI_Status)(NvDRSSessionHandle, NvDRSProfileHandle, NVDRS_APPLICATION_V4*)
        private static final long NvAPI_DRS_CreateApplication = NvAPI.getFunction(0x4347A9DE);

        // (NvAPI_Status)(NvDRSSessionHandle, NvDRSProfileHandle, NVDRS_SETTING_V1*)
        private static final long NvAPI_DRS_SetSetting = NvAPI.getFunction(0x577DD202);

        // (NvAPI_Status)(NvDRSSessionHandle)
        private static final long NvAPI_DRS_DestroySession = NvAPI.getFunction(0xDAD9CFF8);
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_CreateSession(@NativeType("NvDRSSessionHandle*") long phSession) {
        return JNI.callPI(phSession, check(Functions.NvAPI_DRS_CreateSession));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_LoadSettings(@NativeType("NvDRSSessionHandle") long hSession) {
        return JNI.callPI(hSession, check(Functions.NvAPI_DRS_LoadSettings));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_DestroySession(@NativeType("NvDRSSessionHandle") long hSession) {
        return JNI.callPI(hSession, check(Functions.NvAPI_DRS_DestroySession));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_GetErrorMessage(@NativeType("NvAPI_Status") int status,
                                             @NativeType("NvAPI_ShortString*") long pStringBuffer) {
        return JNI.callPI(status, pStringBuffer, check(Functions.NvAPI_GetErrorMessage));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_CreateProfile(@NativeType("NvDRSSessionHandle") long hSession,
                                               @NativeType("NVDRS_PROFILE_V1") long pProfile,
                                               @NativeType("NvDRSProfileHandle*") long pOutHandle) {
        return JNI.callPPPI(hSession, pProfile, pOutHandle, check(Functions.NvAPI_DRS_CreateProfile));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_CreateApplication(@NativeType("NvDRSSessionHandle") long hSession,
                                                   @NativeType("NvDRSProfileHandle") long hProfile,
                                                   @NativeType("NVDRS_APPLICATION_V4") long pApplication) {
        return JNI.callPPPI(hSession, hProfile, pApplication, check(Functions.NvAPI_DRS_CreateApplication));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_SetSetting(@NativeType("NvDRSSessionHandle") long hSession,
                                            @NativeType("NvDRSProfileHandle") long hProfile,
                                            @NativeType("NVDRS_SETTING_V1") long pSetting) {
        return JNI.callPPPI(hSession, hProfile, pSetting, check(Functions.NvAPI_DRS_SetSetting));
    }

    @NativeType("NvAPI_Status")
    public static int NvAPI_DRS_SaveSettings(@NativeType("NvDRSSessionHandle") long hSession) {
        return JNI.callPI(hSession, check(Functions.NvAPI_DRS_SaveSettings));
    }
}
