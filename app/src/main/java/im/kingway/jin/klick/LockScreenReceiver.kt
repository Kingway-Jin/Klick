package im.kingway.jin.klick

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class LockScreenReceiver : DeviceAdminReceiver() {
    override fun onDisabled(paramContext: Context, paramIntent: Intent) {
        super.onDisabled(paramContext, paramIntent)
    }

    override fun onEnabled(paramContext: Context, paramIntent: Intent) {
        super.onEnabled(paramContext, paramIntent)
    }

    override fun onReceive(paramContext: Context, paramIntent: Intent) {
        super.onReceive(paramContext, paramIntent)
    }
}
