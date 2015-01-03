package edu.berkeley.rescomp.secureme.checklist;

import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
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

    public static final SecurityChecklist INSTANCE = new SecurityChecklist();

    private SecurityChecklist() {
        addItem(new LockScreenItem());
        addItem(new EncryptionItem());
        addItem(new RemoteControlItem());
        addItem(new LocationItem());
        addItem(new SimLockItem());
    }

    private static void addItem(SecurityItem item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.title, item);
    }

    public static void checkSettings(Context context) {
        for (SecurityItem item : ITEM_MAP.values()) {
            item.update(context);
        }
    }

    public abstract class SecurityItem {
        protected String title;
        protected int detailsId;
        protected int buttonTextId;
        protected String intentString;

        protected SecurityItem(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return getTitle();
        }

        public String getTitle() {
            return title;
        }

        public int getDetailsId() {
            return detailsId;
        }

        /**
         * Returns null if no intent string specified.
         */
        public Intent getIntent() {
            return (intentString == null) ? null : new Intent(intentString);
        }

        public abstract void update(Context context);
    }

    protected class LockScreenItem extends SecurityItem {
        private LockScreenItem() {
            super(SECURE_LOCK_SCREEN);
            intentString = "android.app.action.SET_NEW_PASSWORD";
            buttonTextId = R.string.secure_lock_screen_button;
        }

        public void update(Context context) {
            ContentResolver cr = context.getContentResolver();
            long pwMode = Settings.Secure.getLong(cr, "lockscreen.password_type",
                    DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);

            if (pwMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC ||
                    pwMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC ||
                    pwMode == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC ||
                    pwMode == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
                detailsId = R.string.secure_lock_screen_good;
            } else {
                detailsId = R.string.secure_lock_screen_bad;
            }
        }
    }

    private class EncryptionItem extends SecurityItem {
        private EncryptionItem() {
            super(ENCRYPTION);
            buttonTextId = R.string.encryption_button;
        }

        public void update(Context context) {
            DevicePolicyManager devicePolicyManager =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                int status = devicePolicyManager.getStorageEncryptionStatus();
                if (DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE == status) {
                    detailsId = R.string.encryption_good;
                    intentString = null;  // TODO: may still want to go to Encryption settings
                } else {
                    detailsId = R.string.encryption_bad;
                    intentString = "android.app.action.START_ENCRYPTION";
                }
            } else {
                detailsId = R.string.encryption_unavailable;
                intentString = null;
            }
        }
    }

    private class RemoteControlItem extends SecurityItem {
        private Intent admIntent;

        private RemoteControlItem() {
            super(REMOTE_CONTROL);
        }

        public Intent getIntent() {
            Intent intent;
            if (admIntent != null) {
                intent = admIntent;
                // admIntent = null;  // TODO: Is this necessary/correct/desired?
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=" + PACKAGE_DEVICE_MANAGER));
            }
            return intent;
        }

        public void update(Context context) {
            if (isAppInstalled(context, PACKAGE_DEVICE_MANAGER)) {
                detailsId = R.string.remote_control_good;
                buttonTextId = R.string.remote_control_open;
                admIntent = context.getPackageManager()
                        .getLaunchIntentForPackage(PACKAGE_DEVICE_MANAGER);
            } else {
                detailsId = R.string.remote_control_bad;
                buttonTextId = R.string.remote_control_get;
                admIntent = null;
            }
        }
    }

    private class LocationItem extends SecurityItem {
        private LocationItem() {
            super(LOCATION);
            buttonTextId = R.string.location_button;
            intentString = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        }

        public void update(Context context) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            boolean locationEnabled = false;
            try {
                locationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {
            }
            detailsId = locationEnabled ? R.string.location_on : R.string.location_off;
        }
    }

    protected class SimLockItem extends SecurityItem {
        private SimLockItem() {
            super(SIM_LOCK);
            // TODO: investigate SIM lock settings intent
        }

        public void update(Context context) {
            TelephonyManager telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telMgr.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) {
                detailsId = R.string.sim_lock_not_gsm;
            } else {
                int simState = telMgr.getSimState();
                switch (simState) {
                    case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                    case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                    case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                        detailsId = R.string.sim_lock_good;
                        break;
                    default:
                        detailsId =  R.string.sim_lock_bad;
                }
            }
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
