package edu.berkeley.rescomp.secureme.checklist;

import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

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
    public static final String ANTIVIRUS = "Antivirus";

    public static final String PACKAGE_DEVICE_MANAGER = "com.google.android.apps.adm";
    public static final String PACKAGE_LOOKOUT = "com.lookout";
    public static final String PACKAGE_AVAST = "com.avast.android.mobilesecurity";

    public static List<SecurityItem> ITEMS = new ArrayList<SecurityItem>();

    public static Map<String, SecurityItem> ITEM_MAP = new HashMap<String, SecurityItem>();

    public static final SecurityChecklist INSTANCE = new SecurityChecklist();

    private SecurityChecklist() {
        addItem(new LocationItem());
        addItem(new AntivirusItem());
        addItem(new SimLockItem());
        addItem(new EncryptionItem());
        addItem(new RemoteControlItem());
        addItem(new LockScreenItem());
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

        public int getButtonTextId() { return buttonTextId; }

        /**
         * Gives the resource id of the icon next to the security item.
         *
         * @return resource id of icon; 0 if no icon
         */
        public int getIconResource() { return 0; }

        /**
         * Should return null if there is to be no button.
         *
         * @return intent string to associate with button (null if no intent string specified)
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

        @Override
        public void update(Context context) {
            ContentResolver cr = context.getContentResolver();
            long pwMode = Settings.Secure.getLong(cr, "lockscreen.password_type",
                    DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);

            boolean patternLockOn = android.provider.Settings.System.getInt(
                    cr,Settings.Secure.LOCK_PATTERN_ENABLED, 0)==1;

            if (pwMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC ||
                    pwMode == DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC ||
                    pwMode == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC ||
                    pwMode == DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
                detailsId = R.string.secure_lock_screen_good;
            } else {
                if (patternLockOn) {
                    detailsId = R.string.secure_lock_screen_good;
                } else {
                    detailsId = R.string.secure_lock_screen_bad;
                }
            }
        }

        @Override
        public int getIconResource() {
            if (detailsId == R.string.secure_lock_screen_good) {
                return R.drawable.ic_action_accept;
            } else {
                return R.drawable.ic_action_cancel;
            }
        }
    }

    private class EncryptionItem extends SecurityItem {
        private EncryptionItem() {
            super(ENCRYPTION);
            buttonTextId = R.string.encryption_button;
        }

        @Override
        public void update(Context context) {
            DevicePolicyManager devicePolicyManager =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                int status = devicePolicyManager.getStorageEncryptionStatus();
                if (DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE == status) {
                    detailsId = R.string.encryption_good;
                    intentString = null;
                } else if (DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED == status) {
                    detailsId = R.string.encryption_unavailable;
                    intentString = null;
                } else {
                    detailsId = R.string.encryption_bad;
                    intentString = "android.app.action.START_ENCRYPTION";
                }
            } else {
                detailsId = R.string.encryption_unavailable;
                intentString = null;
            }
        }

        @Override
        public int getIconResource() {
            if (detailsId == R.string.encryption_good) {
                return R.drawable.ic_action_accept;
            } else if (detailsId == R.string.encryption_bad) {
                return R.drawable.ic_action_cancel;
            } else if (detailsId == R.string.encryption_unavailable) {
                return R.drawable.ic_action_warning;
            } else {
                return 0;
            }
        }
    }

    private class AntivirusItem extends SecurityItem {
        private Intent intentGetAntivirus;
        // intentOpenAntivirus should be null if an antivirus is not installed
        private Intent intentOpenAntivirus;
        private Context context;

        private AntivirusItem() {
            super(ANTIVIRUS);
            intentGetAntivirus = new Intent(Intent.ACTION_VIEW);
            intentGetAntivirus.setData(Uri.parse("market://details?id=" + PACKAGE_LOOKOUT));
            // intentGetAntivirus.setData(Uri.parse("market://details?id=" + PACKAGE_AVAST));
        }

        public Intent getIntent() {
            if (intentOpenAntivirus == null) {
                return (isPlayStoreAvailable()) ? intentGetAntivirus : null;
            } else {
                return intentOpenAntivirus;
            }
        }

        private boolean isPlayStoreAvailable() {
            return (context != null
                    && intentGetAntivirus.resolveActivity(context.getPackageManager()) != null);
        }

        /**
         * Sets intentOpenAntivirus to null if an antivirus is not installed.
         */
        @Override
        public void update(Context context) {
            this.context = context;
            if (isAppInstalled(context, PACKAGE_AVAST)) {
                detailsId = R.string.antivirus_good;
                buttonTextId = R.string.antivirus_avast_open;
                intentOpenAntivirus = context.getPackageManager()
                        .getLaunchIntentForPackage(PACKAGE_AVAST);
            } else if (isAppInstalled(context, PACKAGE_LOOKOUT)) {
                detailsId = R.string.antivirus_good;
                buttonTextId = R.string.antivirus_lookout_open;
                intentOpenAntivirus = context.getPackageManager()
                        .getLaunchIntentForPackage(PACKAGE_LOOKOUT);
            } else {
                if (isPlayStoreAvailable()) {
                    detailsId = R.string.antivirus_bad;
                    buttonTextId = R.string.antivirus_get;
                    intentOpenAntivirus = null;
                } else {
                    detailsId = R.string.antivirus_unavailable;
                }
            }
        }

        @Override
        public int getIconResource() {
            if (detailsId == R.string.antivirus_good) {
                return R.drawable.ic_action_accept;
            } else if (detailsId == R.string.antivirus_bad) {
                return R.drawable.ic_action_cancel;
            } else {
                return 0;
            }
        }
    }

    private class RemoteControlItem extends SecurityItem {
        private Intent intentGetAdm;
        // intentOpenAdm should be null if Android Device Manager is not installed
        private Intent intentOpenAdm;
        private Context context;

        private RemoteControlItem() {
            super(REMOTE_CONTROL);
            intentGetAdm = new Intent(Intent.ACTION_VIEW);
            intentGetAdm.setData(Uri.parse("market://details?id=" + PACKAGE_DEVICE_MANAGER));
        }

        public Intent getIntent() {
            if (intentOpenAdm == null) {
                // Open ADM Play Store page if Play Store is available; null if Play is unavailable
                return (isPlayStoreAvailable()) ? intentGetAdm : null;
            } else {
                return intentOpenAdm;
            }
        }

        private boolean isPlayStoreAvailable() {
            return (context != null
                    && intentGetAdm.resolveActivity(context.getPackageManager()) != null);
        }

        /**
         * Sets intentOpenAdm to null if Android Device Manager is not installed.
         */
        @Override
        public void update(Context context) {
            this.context = context;
            if (isAppInstalled(context, PACKAGE_DEVICE_MANAGER)) {
                detailsId = R.string.remote_control_good;
                buttonTextId = R.string.remote_control_open;
                intentOpenAdm = context.getPackageManager()
                        .getLaunchIntentForPackage(PACKAGE_DEVICE_MANAGER);
            } else {
                if (isPlayStoreAvailable()) {
                    detailsId = R.string.remote_control_bad;
                    buttonTextId = R.string.remote_control_get;
                    intentOpenAdm = null;
                } else {
                    detailsId = R.string.remote_control_unavailable;
                }
            }
        }

        @Override
        public int getIconResource() {
            if (detailsId == R.string.remote_control_good) {
                return R.drawable.ic_action_accept;
            } else if (detailsId == R.string.remote_control_bad) {
                return R.drawable.ic_action_cancel;
            } else {
                return 0;
            }
        }
    }

    private class LocationItem extends SecurityItem {
        private LocationItem() {
            super(LOCATION);
            buttonTextId = R.string.location_button;
            intentString = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        }

        @Override
        public void update(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // KitKat and above
                int locationMode = Settings.Secure.LOCATION_MODE_OFF;
                try {
                    locationMode = Settings.Secure.getInt(context.getContentResolver(),
                                                          Settings.Secure.LOCATION_MODE);
                } catch (Settings.SettingNotFoundException e) {
                    e.printStackTrace();
                }
                detailsId = (locationMode != Settings.Secure.LOCATION_MODE_OFF) ?
                             R.string.location_on : R.string.location_off;
            } else {
                // before KitKat
                String locationProviders = Settings.Secure.getString(
                        context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
                detailsId = (!TextUtils.isEmpty(locationProviders)) ?  // TODO: is TextUtils necessary?
                             R.string.location_on : R.string.location_off;
            }
        }

        @Override
        public int getIconResource() {
            if (detailsId == R.string.location_on) {
                return R.drawable.ic_action_location_found;
            } else if (detailsId == R.string.location_off) {
                return R.drawable.ic_action_location_off;
            } else {
                return 0;
            }
        }
    }

    protected class SimLockItem extends SecurityItem {
        private SimLockItem() {
            super(SIM_LOCK);
            buttonTextId = R.string.sim_button;
            intentString = Settings.ACTION_SECURITY_SETTINGS;
            // TODO: investigate SIM lock settings intent
        }

        @Override
        public void update(Context context) {
            TelephonyManager telMgr =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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

        @Override
        public int getIconResource() {
            if (detailsId == R.string.sim_lock_good) {
                return R.drawable.ic_action_accept;
            } else if (detailsId == R.string.sim_lock_bad) {
                return R.drawable.ic_action_cancel;
            } else {
                return 0;
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
