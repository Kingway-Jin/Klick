package im.kingway.jin.klick

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.AsyncTask
import android.os.Vibrator
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.util.*

class KlickApplication : Application() {

    var shakeFlashLight = false
    var canBreath = false

    var sharedPrefs: SharedPreferences? = null
        private set
    private var mWindowManager: WindowManager? = null
    private var mSensorManager: SensorManager? = null
    private var mAudioManager: AudioManager? = null
    private var mTelephonyManager: TelephonyManager? = null
    private var mVibrator: Vibrator? = null
    private var mActivityManager: ActivityManager? = null
    private var mNotificationManager: NotificationManager? = null
    private var mNotificationBuilder: Notification.Builder? = null

    var mAppsMap: MutableMap<String, AppItem> = HashMap()
    var mSelectedPackage: MutableSet<String> = HashSet()
    var mExcludePackage: MutableSet<String> = HashSet()
    private val appIcons = HashMap<String, Drawable>()

    val screenRect = Rect()

    var gestures = LongArray(GESTURE_CNT)

    val handleDrawable: Drawable
        get() {
            var d: Drawable
            val choice = sharedPrefs!!.getInt(CUSTOMIZE_ICON_CHOICE, 1)
            if (choice == 0) {
                val file = sharedPrefs!!.getString(CUSTOMIZE_ICON_FILE, null)
                try {
                    d = BitmapDrawable.createFromPath(file)
                    if (d == null)
                        d = resources.getDrawable(R.drawable.handle_1)
                } catch (e: Exception) {
                    d = resources.getDrawable(R.drawable.handle_1)
                }

            } else if (choice == 1)
                d = resources.getDrawable(R.drawable.handle_1)
            else if (choice == 2)
                d = resources.getDrawable(R.drawable.handle_2)
            else if (choice == 3)
                d = resources.getDrawable(R.drawable.handle_3)
            else
                d = resources.getDrawable(R.drawable.handle_1)

            return d
        }

    val handleBgDrawable: Drawable
        get() {
            var d: Drawable
            val choice = sharedPrefs!!.getInt(CUSTOMIZE_ICON_BG_CHOICE, 1)
            if (choice == 0) {
                val file = sharedPrefs!!.getString(CUSTOMIZE_ICON_BG_FILE, null)
                try {
                    d = BitmapDrawable.createFromPath(file)
                    if (d == null)
                        d = resources.getDrawable(R.color.transparent)
                } catch (e: Exception) {
                    d = resources.getDrawable(R.color.transparent)
                }

            } else if (choice == 2)
                d = resources.getDrawable(R.drawable.handle_bg)
            else
                d = resources.getDrawable(R.color.transparent)

            return d
        }

    private val activityList = LinkedList<Activity>()

    override fun onCreate() {
        super.onCreate()

        CrashHandler.instance.init(this)

        sharedPrefs = getSharedPreferences(packageName, 0)
        if (sharedPrefs!!.all.isEmpty()) {
            val f = File(PrefsActivity.BACKUP_FILE_PATH)
            if (f.exists() && f.isFile && f.length() > 0) {
                Utils.loadSharedPreferencesFromFile(sharedPrefs!!, f)
            }
        }

        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mVibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        mActivityManager = getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        mWindowManager!!.defaultDisplay.getRectSize(screenRect)
        mNotificationBuilder = Notification.Builder(this)
        mNotificationBuilder!!.setSmallIcon(R.drawable.small_notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.klick))
                .setContentTitle(resources.getString(R.string.app_name))
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(System.currentTimeMillis())
        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        LONG_PRESS_THRESHOLD = sharedPrefs!!.getInt(SETTING_LONG_PRESS_THRESHOLD, 300)
        DOUBLE_TAP_THRESHOLD = sharedPrefs!!.getInt(SETTING_DOUBLE_TAP_THRESHOLD, 0)
        FLASH_LIGHT_ON_MAX_SECONDS = sharedPrefs!!.getInt(SETTING_FLASH_LIGHT_ON_MAX_SECONDS, 60)
        VOL_RATIO = sharedPrefs!!.getInt(FEEDBACK_SOUND_VOLUME, 0).toFloat() / 10f
        VIBRATE_MILLS = sharedPrefs!!.getInt(FEEDBACK_VIBRATE_MILLISECONDS, 0)
        VIBRATE_MILLS_LONG_CLICK = sharedPrefs!!.getInt(FEEDBACK_VIBRATE_MILLISECONDS_LONG_CLICK, 0)
        FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR = sharedPrefs!!.getBoolean(SETTING_FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR, false)
        AUTO_LOCK_SCREEN_PHONE_ON_HEAD = sharedPrefs!!.getBoolean(SETTING_AUTO_LOCK_SCREEN_PHONE_ON_HEAD, false)
        AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = sharedPrefs!!.getBoolean(SETTING_AUTO_LOCK_SCREEN_PHONE_FACE_DOWN, false)
        AUTO_HIDE_FLOATING_ICON = sharedPrefs!!.getBoolean(SETTING_AUTO_HIDE_FLOATING_ICON, false)
        //        DEFAULT_SHOW_THE_FIRST_APP_PAGE = mSharedPrefs.getBoolean(KlickApplication.SHOW_FIRST_APP_PAGE_BY_DEFAULT, false);
        MODE_INCLUDE_RECENT_TASK = sharedPrefs!!.getInt(KlickApplication.INCLUDE_RECENT_TASK_IN_APP_LIST, 0)
        REORDER_APPS = sharedPrefs!!.getBoolean(KlickApplication.SETTING_REORDER_APPS, false)
        SHAKE_SWITCH_FLASH_LIGHT = sharedPrefs!!.getBoolean(KlickApplication.SETTING_SHAKE_SWITCH_FLASH_LIGHT, false)

        FLOATING_POSITION_X = sharedPrefs!!.getInt(SETTING_FLOATING_POSITION_X, 0)
        FLOATING_POSITION_Y = sharedPrefs!!.getInt(SETTING_FLOATING_POSITION_Y, 0)

        for (i in 0 until GESTURE_CNT) {
            gestures[i] = sharedPrefs!!.getLong(SETTING_GESTURE_PREFIX + i, 0)
        }

        setIconSizeInDip(this, sharedPrefs!!.getInt(CUSTOMIZE_ICON_SIZE, MAX_ICON_SIZE))
        setOpacityPCT(sharedPrefs!!.getInt(CUSTOMIZE_ICON_OPACITY, 30))

        GESTURE_DETECT_SENSITIVITY = sharedPrefs!!.getInt(KlickApplication.SETTING_GESTURE_DETECT_SENSITIVITY, 100)
        GESTURE_DETECT_SENSITIVITY = if (GESTURE_DETECT_SENSITIVITY > 10) GESTURE_DETECT_SENSITIVITY else 10

        HIDE_FROM_SOFT_KEYBOARD_DISTANCE = sharedPrefs!!.getInt(SETTING_HIDE_FROM_SOFT_KEYBOARD_DISTANCE, HANDLE_HEIGHT_PX)

        reloadAllApps()
        reloadSelectedApps()
    }

    fun cancelAllNotification() {
        mNotificationManager!!.cancelAll()
    }

    fun showNotification(ticker: String, msg: String?, pIntent: PendingIntent?) {
        mNotificationManager!!.cancel(KlickApplication.DEFAULT_NOTIFICATION)
        mNotificationBuilder!!.setTicker(ticker).setContentText(msg).setContentIntent(pIntent)
        val n = mNotificationBuilder!!.build()
        mNotificationManager!!.notify(KlickApplication.DEFAULT_NOTIFICATION, n)
    }

    fun reloadAllApps() {
        mAppsMap.clear()
        appIcons.clear()
        val pm = packageManager
        val it = Intent(Intent.ACTION_MAIN)
        it.addCategory(Intent.CATEGORY_LAUNCHER)
        val ra = pm.queryIntentActivities(it, 0)
        var item: AppItem
        for (i in ra.indices) {
            val ai = ra[i].activityInfo
            item = AppItem(ai, ai.loadLabel(pm).toString())
            item.clickCount = sharedPrefs!!.getInt(KlickApplication.APP_CLICK_COUNT_PREFIX + item.key, 0)
            item.isExcluded = false
            item.isSelected = false
            mAppsMap.put(item.component.packageName, item)
        }
    }

    fun reloadSelectedApps() {
        mExcludePackage.clear()
        val excPkg = sharedPrefs!!.getString(KlickApplication.EXCLUDE_PACKAGES, "")
        for (pkg in excPkg!!.split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (!pkg.isEmpty()) {
                mExcludePackage.add(pkg.trim { it <= ' ' })
                if (mAppsMap.containsKey(pkg)) {
                    mAppsMap[pkg]?.isExcluded = true
                }
            }
        }

        mSelectedPackage.clear()
        val moreActions = sharedPrefs!!.getString(KlickApplication.MORE_ACTIONS, "")
        val compNames = moreActions!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (cn in compNames) {
            var keyPkg = cn
            if (cn.indexOf("|") != -1) {
                keyPkg = cn.substring(0, cn.indexOf("|"))
            }

            if (mAppsMap.containsKey(keyPkg) && !mExcludePackage.contains(keyPkg)) {
                val item = mAppsMap[keyPkg]
                item?.isSelected = true
                mSelectedPackage.add(keyPkg)
                getAppIcon(item)
            }
        }
    }

    fun addActivity(activity: Activity) {
        activityList.add(activity)
    }

    fun removeActivity(activity: Activity) {
        activityList.remove(activity)
        Log.d(TAG, "Activity Count: " + activityList.size)
    }

    fun exit() {
        for (activity in activityList) {
            activity.finish()
        }
        activityList.clear()
        System.exit(0)
    }

    fun getAppIcon(appItem: AppItem?): Drawable? {
        if (appItem == null) {
            return null
        }
        var d: Drawable? = appIcons[appItem.key]
        if (null == d) {
            try {
                if (appItem.component != null) {
                    d = this.packageManager.getActivityIcon(appItem.component)
                    appIcons.put(appItem.key, d)
                } else if (appItem.ai != null) {
                    d = appItem.ai!!.loadIcon(this.packageManager)
                    appIcons.put(appItem.key, d)
                }
            } catch (ex: Exception) {
            }
        }

        return d
    }

    fun clearIcons() {
        appIcons.clear()
    }

    fun clearIcons(appList: List<AppItem>) {
        for (item in appList) {
            appIcons.remove(item.key)
        }
    }

    fun getmWindowManager(): WindowManager? {
        return mWindowManager
    }

    fun getmSensorManager(): SensorManager? {
        return mSensorManager
    }

    fun getmAudioManager(): AudioManager? {
        return mAudioManager
    }

    fun getmVibrator(): Vibrator? {
        return mVibrator
    }

    fun getmTelephonyManager(): TelephonyManager? {
        return mTelephonyManager
    }

    fun getmActivityManager(): ActivityManager? {
        return mActivityManager
    }

    fun getFloattingPositionY(actually: Boolean): Int {
        var y = FLOATING_POSITION_Y
        if (actually) {
            if (y > screenRect.height())
                y = screenRect.height()
        }
        return y
    }

    fun getFloattingPositionX(actually: Boolean): Int {
        var x = FLOATING_POSITION_X
        if (actually) {
            if (x > 0)
                x = screenRect.width()
        } else {
            if (x > 0) {
                x = if (screenRect.height() > screenRect.width()) screenRect.height() else screenRect.width()
            }
        }
        return x
    }

    fun getAssistHandlePositionX(actually: Boolean): Int {
        var x = FLOATING_POSITION_X
        if (actually) {
            if (x > 0) {
                x = 0
            } else {
                x = screenRect.width()
            }
        } else {
            if (x > 0) {
                x = 0
            } else {
                x = if (screenRect.height() > screenRect.width()) screenRect.height() else screenRect.width()
            }
        }
        return x
    }

    fun getScreenRect(refresh: Boolean): Rect {
        if (refresh) {
            mWindowManager!!.defaultDisplay.getRectSize(screenRect)
        }
        return screenRect
    }

    fun appAdded(pkg: String) {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            val ai: ActivityInfo
            try {
                ai = pm.getActivityInfo(intent.component, PackageManager.GET_META_DATA)
                val item = AppItem(ai, ai.loadLabel(pm).toString())
                item.clickCount = sharedPrefs!!.getInt(KlickApplication.APP_CLICK_COUNT_PREFIX + item.key, 0)
                item.isExcluded = mExcludePackage.contains(item.component.packageName)
                item.isSelected = mSelectedPackage.contains(item.component.packageName)
                mAppsMap.put(item.component.packageName, item)
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Failed to load the launcher of package " + pkg)
            }

        } else {
            Log.e(TAG, "Failed to load the launcher of package " + pkg)
        }
    }

    fun appRemoved(pkg: String) {
        //        mSelectedPackage.remove(pkg);
        val item = mAppsMap.remove(pkg)
        if (item != null)
            appIcons.remove(item.key)
    }

    fun appReplaced(pkg: String) {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            val ai: ActivityInfo
            try {
                ai = pm.getActivityInfo(intent.component, PackageManager.GET_META_DATA)
                val item = AppItem(ai, ai.loadLabel(pm).toString())
                item.clickCount = sharedPrefs!!.getInt(KlickApplication.APP_CLICK_COUNT_PREFIX + item.key, 0)
                item.isExcluded = mExcludePackage.contains(item.component.packageName)
                item.isSelected = mSelectedPackage.contains(item.component.packageName)
                mAppsMap.remove(item.component.packageName)
                mAppsMap.put(item.component.packageName, item)
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Failed to load the launcher of package " + pkg)
            }

        } else {
            Log.e(TAG, "Failed to load the launcher of package " + pkg)
        }
    }

    fun asyncLoadIcon() {
        val at = LoadIconAsyncTask()
        at.execute(1)
    }

    private inner class LoadIconAsyncTask : AsyncTask<Int, Int, Int>() {
        protected override fun doInBackground(vararg params: Int?): Int {
            for (pkg in mSelectedPackage) {
                getAppIcon(mAppsMap[pkg])
            }
            return 0
        }
    }

    fun getResourceStringWithAppVersion(resId: Int): String {
        val pm = packageManager
        var pi: PackageInfo? = null
        try {
            pi = pm.getPackageInfo(packageName, 0)
            return String.format(resources.getString(resId), pi!!.versionName)
        } catch (e: NameNotFoundException) {
            e.printStackTrace()
        }

        return resources.getString(resId)
    }

    fun playFeedback(isLongPress: Boolean) {
        if (KlickApplication.VOL_RATIO > 0) {
            getmAudioManager()!!.playSoundEffect(AudioManager.FX_KEY_CLICK, KlickApplication.VOL_RATIO)
        }
        if (isLongPress && KlickApplication.VIBRATE_MILLS_LONG_CLICK > KlickApplication.VIBRATE_MILLS) {
            getmVibrator()!!.vibrate(KlickApplication.VIBRATE_MILLS_LONG_CLICK.toLong())
        } else if (KlickApplication.VIBRATE_MILLS > 0) {
            getmVibrator()!!.vibrate(KlickApplication.VIBRATE_MILLS.toLong())
        }
    }

    fun adjustStreamVolume(stream: Int, adjust: Int, showUI: Int) {
        if (stream == AudioManager.STREAM_MUSIC) {
            mAudioManager!!.adjustStreamVolume(stream, adjust, showUI)
        } else {
            mAudioManager!!.adjustStreamVolume(AudioManager.STREAM_SYSTEM, adjust, showUI)
            mAudioManager!!.adjustStreamVolume(AudioManager.STREAM_RING, adjust, showUI)
            mAudioManager!!.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, adjust, showUI)
        }
    }

    companion object {
        private val TAG = "KlickApplication"
        val SETTING_FLOATING_POSITION_X = "SETTING_FLOATING_POSITION_X"
        val SETTING_FLOATING_POSITION_Y = "SETTING_FLOATING_POSITION_Y"
        val FEEDBACK_VIBRATE_MILLISECONDS = "FEEDBACK_VIBRATE_MILLISECONDS"
        val FEEDBACK_VIBRATE_MILLISECONDS_LONG_CLICK = "FEEDBACK_VIBRATE_MILLISECONDS_LONG_CLICK"
        val FEEDBACK_SOUND_VOLUME = "FEEDBACK_SOUND_VOLUME"
        val SETTING_LONG_PRESS_THRESHOLD = "SETTING_LONG_PRESS_THRESHOLD"
        val SETTING_DOUBLE_TAP_THRESHOLD = "SETTING_DOUBLE_TAP_THRESHOLD"
        val SETTING_FLASH_LIGHT_ON_MAX_SECONDS = "FLASH_LIGHT_ON_MAX_SECONDS"
        val SETTING_GESTURE_DETECT_SENSITIVITY = "SETTING_GESTURE_DETECT_SENSITIVITY"
        val SETTING_GESTURE_PREFIX = "GESTURE_"
        val SETTING_HIDE_FROM_SOFT_KEYBOARD_DISTANCE = "SETTING_HIDE_FROM_SOFT_KEYBOARD_DISTANCE"

        val SEQ_NO_HOME = 0
        val SEQ_NO_BACK = 1
        val SEQ_NO_MENU = 2
        val SEQ_NO_APP_SWITCH = 3
        val SEQ_NO_LOCK_SCREEN = 4
        val SEQ_NO_EXPAND_STATUS_BAR = 5
        val SEQ_NO_SHOW_MORE_ACTIONS = 6
        val SEQ_NO_ADJUST_MUSIC_VOL = 7
        val SEQ_NO_OPEN_CAMERA = 8
        val SEQ_NO_OPEN_DICT = 9
        val SEQ_NO_APP_SWITCH_FORWARD = 10
        val SEQ_NO_APP_SWITCH_BACKWARD = 11
        val SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION = 12
        val SEQ_NO_SHOW_MORE_ACTIONS_QUICK_LAUNCH = 13

        val ACTION_HOME = "im.kingway.klick.jin.action.KEYCODE_HOME"
        val ACTION_BACK = "im.kingway.klick.jin.action.KEYCODE_BACK"
        val ACTION_MENU = "im.kingway.klick.jin.action.KEYCODE_MENU"
        val ACTION_APP_SWITCH = "im.kingway.klick.jin.action.KEYCODE_APP_SWITCH"
        val ACTION_LOCK_SCREEN = "im.kingway.klick.jin.action.LOCK_SCREEN"
        val ACTION_EXPEND_STATUS_BAR = "im.kingway.klick.jin.action.EXEPND_STATUS_BAR"
        val ACTION_TORCH = "im.kingway.klick.jin.action.TORCH"
        val ACTION_HIDE_KLICK = "im.kingway.klick.jin.action.HIDE_KLICK"
        val ACTION_UNHIDE_KLICK = "im.kingway.klick.jin.action.UNHIDE_KLICK"
        val ACTION_MORE_ACTIONS = "im.kingway.klick.jin.action.MORE_ACTIONS"
        val ACTION_CUSTOMIZE_ICON = "im.kingway.klick.jin.action.CUSTOMIZE_ICON"
        val ACTION_LOOKUP_WORD = "im.kingway.klick.jin.action.LOOKUP_WORD"
        val ACTION_HIDE_MORE_ACTION_VIEW = "im.kingway.klick.jin.action.HIDE_MORE_ACTION_VIEW"

        val MORE_ACTIONS = "MORE_ACTIONS"
        val EXCLUDE_PACKAGES = "EXCLUDE_PACKAGES"
        val SETTING_REORDER_APPS = "REORDER_APPS"
        val APP_CLICK_COUNT_PREFIX = "CLICK_COUNT-"

        val DEFAULT_NOTIFICATION = 1

        val DRAG_FALL_BACK = -1
        val DRAG_TO_LEFT = 0
        val DRAG_TO_RIGHT = 1

        val TRANSPARENT_BACKGROUND_THRESHOLD = 1000
        val MORE_ACTION_VIEW_HIDE_DELAY = 1000
        val MSG_DOUBLE_TAP_TIMEOUT = 0
        val MSG_LONG_PRESS_TRIGGER = 1
        val MSG_HIDE_MORE_ACTION_VIEW = 2
        val MSG_TRANSPARENT_BACKGROUND = 3
        val MSG_TURN_OFF_FLASH_LIGHT = 5
        val MSG_BREATHING = 6

        val MAX_ICON_SIZE = 64
        val MIN_ICON_SIZE = 24
        var HANDLE_HEIGHT_DP = 64
        var HANDLE_WIDTH_DP = 64
        var HANDLE_HEIGHT_PX: Int = 0
        var HANDLE_WIDTH_PX: Int = 0

        var ICON_BG_OPACITY_PCT = 0.3 //0.3
        var ICON_BG_OPACITY_PCT_ACTIVE = 0.6 //0.5
        var ICON_OPACITY_PCT = 0.3
        var ICON_OPACITY_PCT_ACTIVE = 0.6
        var ICON_BG_OPACITY: Int = 0
        var ICON_BG_OPACITY_ACTIVE: Int = 0
        var ICON_OPACITY: Int = 0
        var ICON_OPACITY_ACTIVE: Int = 0
        var FULLY_OPACITY = 255
        var LONG_PRESS_OPACITY = 255

        val POS_FACE_UP = 1
        val POS_FACE_DOWN = 2
        val POS_FACE_LEFT = 3
        val POS_FACE_RIGHT = 4
        val POS_STANDING_UP = 5
        val POS_ON_HEAD = 6
        val POS_IN_BETWEEN = 7

        val INCLUDE_RECENT_TASK_IN_APP_LIST = "INCLUDE_RECENT_TASK_IN_APP_LIST"
        val SETTING_AUTO_LOCK_SCREEN_PHONE_ON_HEAD = "AUTO_LOCK_SCREEN_PHONE_ON_HEAD"
        val SETTING_AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = "AUTO_LOCK_SCREEN_PHONE_FACE_DOWN"
        //    public static final String SHOW_FIRST_APP_PAGE_BY_DEFAULT = "SHOW_FIRST_APP_PAGE_BY_DEFAULT";
        val SETTING_FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR = "FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR"
        val SETTING_AUTO_HIDE_FLOATING_ICON = "AUTO_HIDE_FLOATING_ICON"
        val SETTING_SHAKE_SWITCH_FLASH_LIGHT = "SHAKE_SWITCH_FLASH_LIGHT"
        val CUSTOMIZE_ICON_FILE = "CUSTOMIZE_ICON_FILE"
        val CUSTOMIZE_ICON_BG_FILE = "CUSTOMIZE_ICON_BG_FILE"
        val CUSTOMIZE_ICON_CHOICE = "CUSTOMIZE_ICON_CHOICE"
        val CUSTOMIZE_ICON_DRAWABLE = "CUSTOMIZE_ICON_DRAWABLE"
        val CUSTOMIZE_ICON_BG_CHOICE = "CUSTOMIZE_ICON_BG_CHOICE"
        val CUSTOMIZE_ICON_BG_DRAWABLE = "CUSTOMIZE_ICON_BG_DRAWABLE"
        val CUSTOMIZE_ICON_OPACITY = "CUSTOMIZE_ICON_OPACITY"
        val CUSTOMIZE_ICON_SIZE = "CUSTOMIZE_ICON_SIZE"

        //    public static boolean DEFAULT_SHOW_THE_FIRST_APP_PAGE = false;
        var MODE_INCLUDE_RECENT_TASK = 0
        var REORDER_APPS = false

        var AUTO_LOCK_SCREEN_PHONE_ON_HEAD = false
        var AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = false
        var FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR = false
        var AUTO_HIDE_FLOATING_ICON = false
        var SHAKE_SWITCH_FLASH_LIGHT = false
        var VIBRATE_MILLS: Int = 0
        var VIBRATE_MILLS_LONG_CLICK = 40
        var VOL_RATIO: Float = 0.toFloat()
        var LONG_PRESS_THRESHOLD: Int = 0
        var DOUBLE_TAP_THRESHOLD: Int = 0
        var FLASH_LIGHT_ON_MAX_SECONDS = 60
        var SLIP_START_THRESHOLD = 10
        var DRAG_START_THRESHOLD = 10
        var GESTURE_DETECT_SENSITIVITY = 10
        var HIDE_FROM_SOFT_KEYBOARD_DISTANCE: Int = 0

        var FLOATING_POSITION_X: Int = 0
        var FLOATING_POSITION_Y: Int = 0

        var GESTURE_CNT = 14

        fun setIconSizeInDip(context: Context, sizeInDip: Int) {
            HANDLE_HEIGHT_DP = sizeInDip
            HANDLE_WIDTH_DP = sizeInDip
            HANDLE_WIDTH_PX = Utils.dip2px(context, HANDLE_WIDTH_DP.toFloat())
            HANDLE_HEIGHT_PX = Utils.dip2px(context, HANDLE_HEIGHT_DP.toFloat())
            SLIP_START_THRESHOLD = HANDLE_HEIGHT_PX / 4
            DRAG_START_THRESHOLD = HANDLE_HEIGHT_PX / 10
        }

        fun setOpacityPCT(pct: Int) {
            ICON_BG_OPACITY_PCT = pct / 100.0
            ICON_BG_OPACITY_PCT_ACTIVE = ICON_BG_OPACITY_PCT + (1 - ICON_BG_OPACITY_PCT) / 2
            ICON_OPACITY_PCT = ICON_BG_OPACITY_PCT
            ICON_OPACITY_PCT_ACTIVE = ICON_BG_OPACITY_PCT_ACTIVE
            ICON_BG_OPACITY = (FULLY_OPACITY * ICON_BG_OPACITY_PCT).toInt()
            ICON_BG_OPACITY_ACTIVE = (FULLY_OPACITY * ICON_BG_OPACITY_PCT_ACTIVE).toInt()
            ICON_OPACITY = (FULLY_OPACITY * ICON_OPACITY_PCT).toInt()
            ICON_OPACITY_ACTIVE = (FULLY_OPACITY * ICON_OPACITY_PCT_ACTIVE).toInt()
            LONG_PRESS_OPACITY = ICON_OPACITY / 2
        }
    }
}
