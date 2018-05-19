package im.kingway.jin.klick

import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.*
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class KlickService : Service() {
    private var mReceiver: BroadcastReceiver? = null
    private var mFloatingView: FloatingView? = null
    private var mDevicePolicyManager: DevicePolicyManager? = null
    private var mApp: KlickApplication? = null
    private var lockScreenComponentName: ComponentName? = null

    override fun onCreate() {
        super.onCreate()
        mApp = application as KlickApplication
        sharedInstance = this

        mDevicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        lockScreenComponentName = ComponentName(mApp!!.applicationContext, LockScreenReceiver::class.java)

        if (mReceiver == null) {
            mReceiver = KlickIntentReceiver()
            registerRecievers()
        }

        mFloatingView = FloatingView(mApp as KlickApplication)
        mFloatingView!!.addToWindowManager()
    }

    override fun onDestroy() {
        super.onDestroy()

        stopForeground(true)

        unregisterReceiver(mReceiver)
        mReceiver = null

        mFloatingView!!.removeFromWindowManager()
        mFloatingView = null
        lockScreenComponentName = null
        mDevicePolicyManager = null

        sharedInstance = null
        mApp = null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    internal inner class KlickIntentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (KlickApplication.ACTION_HIDE_KLICK == intent.action) {
                val newIntent = Intent()
                newIntent.action = KlickApplication.ACTION_UNHIDE_KLICK
                val pendIntent = PendingIntent.getBroadcast(mApp!!.applicationContext, 0, newIntent, 0)
                runForeground(R.string.unhide_klick_notification_ticker, R.string.unhide_klick_notification_msg, pendIntent)
            }
            if (KlickApplication.ACTION_UNHIDE_KLICK == intent.action) {
                mFloatingView!!.unhide()
            }
            if (KlickApplication.ACTION_MORE_ACTIONS == intent.action) {
                //        		mFloatingView.mMoreActionsView.showApps(true);
            }
            if (KlickApplication.ACTION_HIDE_MORE_ACTION_VIEW == intent.action) {
                mFloatingView!!.mMoreActionsView!!.clearQuickAction()
            }
            if (KlickApplication.ACTION_LOCK_SCREEN == intent.action) {
                if (mDevicePolicyManager!!.isAdminActive(lockScreenComponentName!!)) {
                    mFloatingView!!.unregisterSensorEventListener()
                    mDevicePolicyManager!!.lockNow()
                } else {
                    Log.d(TAG, "android.app.action.ADD_DEVICE_ADMIN")
                    Toast.makeText(mApp, R.string.enable_klick_device_admin, Toast.LENGTH_LONG).show()
                    val localIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    localIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, lockScreenComponentName)
                    localIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.klick_lock_screen))
                    localIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    startActivity(localIntent)
                }
            }
            if (Intent.ACTION_SCREEN_ON == intent.action) {
                mFloatingView!!.registerSensorEventListener()
                mApp!!.canBreath = true
                mFloatingView!!.startToBreath(1, 3000)
            }
            if (Intent.ACTION_SCREEN_OFF == intent.action) {
                mApp!!.canBreath = false
                mFloatingView!!.unregisterSensorEventListener()
            }
            if (KlickApplication.ACTION_CUSTOMIZE_ICON == intent.action) {
                mFloatingView!!.refresh()
            }
            if (KlickApplication.ACTION_LOOKUP_WORD == intent.action) {
                try {
                    if (Utils.isPkgInstalled(mApp!!, "im.kingway.movieenglish")) {
                        var lookupWord = ""
                        val cm = mApp!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        if (cm.primaryClip.itemCount > 0) {
                            lookupWord = cm.primaryClip.getItemAt(0).text.toString()
                        }
                        val cn = ComponentName("im.kingway.movieenglish", "im.kingway.movieenglish.DictionaryActivity")
                        val lookupIntent = Intent()
                        lookupIntent.component = cn
                        lookupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        lookupIntent.putExtra("WORD", lookupWord)
                        lookupIntent.putExtra("DICT_ID", 0L)
                        startActivity(lookupIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
            if (Intent.ACTION_PACKAGE_ADDED == intent.action) {
                var pkg = intent.dataString
                pkg = pkg!!.substring(pkg.indexOf(":") + 1)
                Log.d(TAG, "ACTION_PACKAGE_ADDED: " + pkg)
                mApp!!.appAdded(pkg)
            }
            if (Intent.ACTION_PACKAGE_REMOVED == intent.action) {
                var pkg = intent.dataString
                pkg = pkg!!.substring(pkg.indexOf(":") + 1)
                Log.d(TAG, "ACTION_PACKAGE_REMOVED: " + pkg)
                mApp!!.appRemoved(pkg)
            }
            if (Intent.ACTION_PACKAGE_REPLACED == intent.action) {
                var pkg = intent.dataString
                pkg = pkg!!.substring(pkg.indexOf(":") + 1)
                Log.d(TAG, "ACTION_PACKAGE_REPLACED: " + pkg)
                mApp!!.appReplaced(pkg)
            }

            if (Intent.ACTION_HEADSET_PLUG == intent.action) {
                if (intent.getIntExtra("state", 0) == 0) {
                    mApp!!.getmAudioManager()!!.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                }
            }
        }
    }

    private fun registerRecievers() {
        var intentfilter = IntentFilter(KlickApplication
                .ACTION_HIDE_KLICK)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(KlickApplication.ACTION_UNHIDE_KLICK)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(KlickApplication.ACTION_MORE_ACTIONS)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(KlickApplication.ACTION_LOCK_SCREEN)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(KlickApplication.ACTION_CUSTOMIZE_ICON)
        registerReceiver(mReceiver, intentfilter)
        intentfilter = IntentFilter(KlickApplication.ACTION_LOOKUP_WORD)
        registerReceiver(mReceiver, intentfilter)

        intentfilter = IntentFilter()
        intentfilter.addDataScheme("package")
        intentfilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        intentfilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentfilter.addAction(Intent.ACTION_PACKAGE_REPLACED)
        registerReceiver(mReceiver, intentfilter)

        intentfilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        registerReceiver(mReceiver, intentfilter)

        intentfilter = IntentFilter(KlickApplication.ACTION_HIDE_MORE_ACTION_VIEW)
        registerReceiver(mReceiver, intentfilter)
    }

    private fun runForeground(ticker: Int, msg: Int, pIntent: PendingIntent) {
        runForeground(resources.getString(ticker), resources.getString(msg), pIntent)
    }

    private fun runForeground(ticker: String, msg: String?, pIntent: PendingIntent) {
        if (msg == null)
            mApp!!.cancelAllNotification()
        else {
            mApp!!.showNotification(ticker, msg, pIntent)
        }
    }

    companion object {
        private val TAG = "KlickService"
        var sharedInstance: KlickService? = null
    }
}