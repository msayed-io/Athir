package com.athir.datatransfer;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AthirNativePlugin.class);
        super.onCreate(savedInstanceState);
    }

    /**
     * Capacitor 8 only forwards the permission result to legacy plugins, so a
     * @CapacitorPlugin never receives it. Athir needs the authoritative, post-dialog
     * permission state to avoid presenting a grant the user did not actually give,
     * therefore the result is forwarded explicitly to the live plugin instance.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AthirNativePlugin.permissionRequestCode()) {
            AthirNativePlugin.onActivityPermissionResult(requestCode);
        }
    }
}
