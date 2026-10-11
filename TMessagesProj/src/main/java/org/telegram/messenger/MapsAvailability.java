package org.telegram.messenger;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

/** Local prerequisites only: a key's server-side authorization cannot be checked here. */
public final class MapsAvailability {
    private MapsAvailability() {
    }

    public static boolean canUseGoogleMaps(Context context) {
        if (context == null) {
            return false;
        }
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            String key = info.metaData == null ? null
                    : info.metaData.getString("com.google.android.geo.API_KEY");
            if (key == null || key.trim().isEmpty()) {
                FileLog.d("Google Maps unavailable: API key missing; using OpenStreetMap");
                return false;
            }
            int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
            if (status != ConnectionResult.SUCCESS) {
                FileLog.d("Google Maps unavailable: Play Services status " + status + "; using OpenStreetMap");
                return false;
            }
            return true;
        } catch (Exception | LinkageError e) {
            // Do not include metadata or credentials in diagnostics.
            FileLog.d("Google Maps prerequisites check failed; using OpenStreetMap");
            return false;
        }
    }
}
