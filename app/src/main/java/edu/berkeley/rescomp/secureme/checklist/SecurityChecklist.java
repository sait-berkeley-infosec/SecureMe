package edu.berkeley.rescomp.secureme.checklist;

import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.rescomp.secureme.R;


public class SecurityChecklist {
    public static final String SECURE_LOCK_SCREEN = "Secure lock screen";
    public static final String ENCRYPTION = "Encryption";
    public static final String REMOTE_CONTROL = "Remote control";
    public static final String LOCATION = "Location";
    public static final String SIM_LOCK = "SIM lock";

    public static final String PACKAGE_DEVICE_MANAGER = "com.google.android.apps.adm";

    public static List<SecurityItem> ITEMS = new ArrayList<SecurityItem>();

    public static Map<String, SecurityItem> ITEM_MAP = new HashMap<String, SecurityItem>();

    public final static SecurityChecklist INSTANCE = new SecurityChecklist();

    private SecurityChecklist() {
        addItem(new SecurityItem(SECURE_LOCK_SCREEN));
        addItem(new SecurityItem(ENCRYPTION));
        addItem(new SecurityItem(REMOTE_CONTROL));
        addItem(new SecurityItem(LOCATION));
        addItem(new SecurityItem(SIM_LOCK));
    }

    private static void addItem(SecurityItem item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.title, item);
    }

    public class SecurityItem {
        public String title;
        private String details;

        public SecurityItem(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }

        public String getDetails(Context context) {
            checkSettings(context);
            return details;
        }
    }

    public static void checkSettings(Context context) {
        ITEM_MAP.get(SECURE_LOCK_SCREEN).details = context.getString(getLockScreenDetails(context));
        ITEM_MAP.get(ENCRYPTION).details = context.getString(getEncryptionDetails(context));
        ITEM_MAP.get(REMOTE_CONTROL).details = context.getString(getRemoteControlDetails(context));
        ITEM_MAP.get(LOCATION).details = context.getString(getLocationDetails(context));
        ITEM_MAP.get(SIM_LOCK).details = context.getString(getSIMLockDetails(context));
    }

    private static int getLockScreenDetails(Context context) {
        ContentResolver cr = context.getContentResolver();
        long pwMode = android.provider.Settings.Secure.getLong(cr, "lockscreen.password_type",
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);

        if (pwMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC ||
                pwMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC ||
                pwMode == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC ||
                pwMode == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
            return R.string.secure_lock_screen_good;
        }

        return R.string.secure_lock_screen_bad;
    }

    private static int getEncryptionDetails(Context context) {
        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            int status = devicePolicyManager.getStorageEncryptionStatus();
            if (DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE == status) {
                return R.string.encryption_good;
            }
        }
        return R.string.encryption_bad;
    }

    public static int getRemoteControlDetails(Context context) {
        if (isAppInstalled(context, PACKAGE_DEVICE_MANAGER)) {
            return R.string.remote_control_good;
        }

        return R.string.remote_control_bad;
    }

    public static int getLocationDetails(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean locationEnabled = false;
        try {
            locationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        return locationEnabled ? R.string.location_on : R.string.location_off;
    }

    public static int getSIMLockDetails(Context context) {
        TelephonyManager telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telMgr.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) {
            return R.string.sim_lock_not_gsm;
        }
        int simState = telMgr.getSimState();
        switch (simState) {
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                return R.string.sim_lock_good;
            default:
                return R.string.sim_lock_bad;
        }
    }

    private static boolean isAppInstalled(Context context, String target) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            if (app.packageName.equals(target)) {
                return true;
            }
        }
        return false;
    }
}
