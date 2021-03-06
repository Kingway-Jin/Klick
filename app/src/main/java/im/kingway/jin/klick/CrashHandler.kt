package im.kingway.jin.klick

import android.content.Context

class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    private var mContext: Context? = null
    private var mDefaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(ctx: Context) {
        mContext = ctx
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        mContext?.getSharedPreferences(mContext?.packageName, 0)?.edit()?.putString("RECENT_APP_PACKAGE_NAME",
                KlickAccessibilityService
                        .recentAppPackageName.joinToString(";"))?.commit()
        Utils.logCrash(ex)
        mDefaultHandler?.uncaughtException(thread, ex) ?: System.exit(2)
    }

    companion object {
        val TAG = CrashHandler::class.java.simpleName
        val instance = CrashHandler()
    }
}