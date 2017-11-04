package im.kingway.jin.klick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class KlickLauncher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val s = intent.action
        if ("android.intent.action.BOOT_COMPLETED" == s) {
            Log.d(TAG, "Receive message when boot completed")
            val intent1 = Intent(context, KlickService::class.java)
            context.startService(intent1)
        }
    }

    companion object {
        private val TAG = "KlickLauncher"
    }
}
