package im.kingway.jin.klick

import android.content.Context
import android.util.Log
import java.io.OutputStream

class KeyEventHandler private constructor(context: Context) {
    private var android_id: String? = null
    private var cmd: CommandShell? = null

    val commandShell: CommandShell
        @Throws(Exception::class)
        get() {
            if (cmd == null)
                if (android_id == null) {
                    val commandshell = CommandShell("sh")
                    cmd = commandshell
                } else {
                    val commandshell1 = CommandShell("su")
                    cmd = commandshell1
                }
            return cmd as CommandShell
        }

    val newCommandShell: CommandShell
        @Throws(Exception::class)
        get() {
            if (android_id == null) {
                cmd = CommandShell("sh")
            } else {
                cmd = CommandShell("su")
            }
            return cmd as CommandShell
        }

    init {
        cmd = null
        android_id = null
        val s = android.provider.Settings.Secure.getString(context.contentResolver, "android_id")
        android_id = s
    }

    inner class CommandShell @Throws(Exception::class)
    constructor(s: String) {

        var o: OutputStream
        var p: Process

        @Throws(Exception::class)
        fun close() {
            Log.d("cmdshell", "Destroying shell")
            o.flush()
            o.close()
            p.destroy()
        }

        @Throws(Exception::class)
        fun system(s: String) {
            val s1 = StringBuilder("Running command: '").append(s).append("'").toString()
            Log.d("cmdshell", s1)
            val outputstream = o
            val s2 = s.toString()
            val abyte0 = StringBuilder(s2).append("\n").toString().toByteArray(charset("ASCII"))
            outputstream.write(abyte0)
            outputstream.flush()
        }

        init {
            val s1 = StringBuilder("Starting shell: '").append(s).append("'").toString()
            Log.d("cmdshell", s1)
            val process = Runtime.getRuntime().exec(s)
            p = process
            val outputstream = p.outputStream
            o = outputstream
        }
    }

    fun inputKeyEvent(keyCode: Int): Boolean {
        var input = "input keyevent " + keyCode
        if (EXPORT_LD_LIBRARY_PATH)
            input = "LD_LIBRARY_PATH=/vendor/lib:/system/lib:\$LD_LIBRARY_PATH input keyevent $keyCode"
        try {
            commandShell.system(input)
            return true
        } catch (e: Exception) {
            Log.e("cmdshell", "", e)
            cmd = null
        }

        return false
    }

    companion object {
        private var instance: KeyEventHandler? = null
        var EXPORT_LD_LIBRARY_PATH = false

        fun getInstance(context: Context): KeyEventHandler {
            if (instance == null)
                instance = KeyEventHandler(context)
            return instance as KeyEventHandler
        }
    }
}
