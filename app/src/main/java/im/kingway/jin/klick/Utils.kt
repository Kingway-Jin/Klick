package im.kingway.jin.klick

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.database.Cursor
import android.graphics.*
import android.graphics.Bitmap.Config
import android.graphics.PorterDuff.Mode
import android.graphics.Shader.TileMode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.support.v13.view.ViewCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import java.io.*

object Utils {
    private val TAG = "Utils"

    fun dip2px(context: Context, dipValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dipValue * scale + 0.5f).toInt()
    }

    fun px2dip(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    fun isPkgInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        var info: android.content.pm.ApplicationInfo? = null
        try {
            info = context.packageManager.getApplicationInfo(packageName, 0)
            return info != null
        } catch (e: NameNotFoundException) {
            return false
        }
    }

    fun getAppNameByPackageName(context: Context, packageName: String): String {
        var appName = ""
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            val ai: ActivityInfo
            try {
                ai = pm.getActivityInfo(intent.component, PackageManager.GET_META_DATA)
                appName = ai.loadLabel(pm).toString()
            } catch (e: NameNotFoundException) {
            }
        }
        Log.d(TAG, "get app name: $appName, package name: $packageName")
        return appName
    }

    fun getKlickAccessServiceInstance(context: Context): KlickAccessibilityService? {
        val kas = KlickAccessibilityService.sharedInstance
        if (kas == null) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Toast.makeText(context, R.string.enable_klick_accessibility_service, Toast.LENGTH_LONG).show()
        }
        return kas
    }

    fun getRealPathFromURI(context: Context, contentUri: Uri): String {
        var cursor: Cursor? = null
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = context.contentResolver.query(contentUri, proj, null, null, null)
            val column_index = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            return cursor.getString(column_index)
        } finally {
            cursor?.close()
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth // 取drawable的长宽
        val height = drawable.intrinsicHeight
        val config = if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565 // 取drawable的颜色格式
        val bitmap = Bitmap.createBitmap(width, height, config) // 建立对应bitmap
        val canvas = Canvas(bitmap) // 建立对应bitmap的画布
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas) // 把drawable内容画到画布中
        return bitmap
    }

    fun scaleDrawable(drawable: Drawable, w: Int, h: Int): Drawable {
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        Log.d(TAG, "Scale Drawable from ($width, $height) to ($w, $h)")
        val oldbmp = drawableToBitmap(drawable)// drawable转换成bitmap
        val matrix = Matrix() // 创建操作图片用的Matrix对象
        val scaleWidth = w.toFloat() / width // 计算缩放比例
        val scaleHeight = h.toFloat() / height
        matrix.postScale(scaleWidth, scaleHeight) // 设置缩放比例
        val newbmp = Bitmap.createBitmap(oldbmp, 0, 0, width, height, matrix, true) // 建立新的bitmap，其内容是对原bitmap的缩放后的图
        return BitmapDrawable(newbmp) // 把bitmap转换成drawable并返回
    }

    fun createReflectionImageWithOrigin(bitmap: Bitmap): Bitmap {
        val reflectionGap = 4
        val w = bitmap.width
        val h = bitmap.height

        val matrix = Matrix()
        matrix.preScale(1f, -1f)

        val reflectionImage = Bitmap.createBitmap(bitmap, 0, h / 2, w, h / 2, matrix, false)

        val bitmapWithReflection = Bitmap.createBitmap(w, h + h / 2, Config.ARGB_8888)

        val canvas = Canvas(bitmapWithReflection)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        val deafalutPaint = Paint()
        canvas.drawRect(0f, h.toFloat(), w.toFloat(), (h + reflectionGap).toFloat(), deafalutPaint)

        canvas.drawBitmap(reflectionImage, 0f, (h + reflectionGap).toFloat(), null)

        val paint = Paint()
        val shader = LinearGradient(0f, bitmap.height.toFloat(), 0f, (bitmapWithReflection.height + reflectionGap).toFloat(), 0x70ffffff, 0x00ffffff, TileMode.CLAMP)
        paint.shader = shader
        // Set the Transfer mode to be porter duff and destination in
        paint.xfermode = PorterDuffXfermode(Mode.DST_IN)
        // Draw a rectangle using the paint with our linear gradient
        canvas.drawRect(0f, h.toFloat(), w.toFloat(), (bitmapWithReflection.height + reflectionGap).toFloat(), paint)

        return bitmapWithReflection
    }

    fun logCrash(ex: Throwable?) {
        var ex = ex
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val logFilePath = Environment.getExternalStorageDirectory().toString() + File.separator + "klick_crash_log.txt"
            val f = File(logFilePath)

            var out: PrintWriter? = null
            try {
                out = PrintWriter(BufferedWriter(FileWriter(f, true)))
                while (ex != null) {
                    ex.printStackTrace(out)
                    ex = ex.cause
                }
            } catch (e: Exception) {
            } finally {
                out?.close()
            }
        }
    }

    // View宽，高
    fun getLocation(v: View): IntArray {
        val loc = IntArray(4)
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        loc[0] = location[0]
        loc[1] = location[1]
        val w = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        v.measure(w, h)

        loc[2] = v.measuredWidth
        loc[3] = v.measuredHeight

        //base = computeWH();
        return loc
    }

    fun reflectionInvoke(instance: Any?, className: String, methodName: String, classes: Array<Class<*>>, vararg params: Any): Any? {
        try {
            val clazz = Class.forName(className)
            val methods = clazz.methods
            for (m in methods) {
                Log.d(TAG, "Method: " + m.name)
                for (c in m.parameterTypes) {
                    Log.d(TAG, "Method: " + m.name + " - " + c.name)
                }
            }
            val method = clazz.getMethod(methodName, *classes)
            Log.d(TAG, "try to invoke")
            return if (instance == null) {
                method.invoke(clazz, *params)
            } else {
                method.invoke(instance, *params)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to invoke $className.$methodName($params)", e)
        }

        return null
    }

    fun saveSharedPreferencesToFile(pref: SharedPreferences, dst: File): Boolean {
        var res = false
        var output: ObjectOutputStream? = null
        try {
            output = ObjectOutputStream(FileOutputStream(dst))
            output.writeObject(pref.all)

            res = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                output?.flush()
                output?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

        }
        return res
    }

    fun loadSharedPreferencesFromFile(pref: SharedPreferences, src: File): Boolean {
        var res = false
        var input: ObjectInputStream? = null
        try {
            input = ObjectInputStream(FileInputStream(src))
            val prefEdit = pref.edit()
            prefEdit.clear()
            val entries = input.readObject() as Map<String, *>
            for ((key, v) in entries) {
                if (v is Boolean)
                    prefEdit.putBoolean(key, v)
                else if (v is Float)
                    prefEdit.putFloat(key, v)
                else if (v is Int)
                    prefEdit.putInt(key, v)
                else if (v is Long)
                    prefEdit.putLong(key, v)
                else if (v is String)
                    prefEdit.putString(key, v)
            }
            prefEdit.commit()
            res = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } finally {
            try {
                input?.close()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

        }
        return res
    }

    fun launchApp(context: Context, appItem: AppItem?): Boolean {
        if (null == appItem) {
            return false
        }
        try {
            val intent = Intent("android.intent.action.MAIN")
            intent.addCategory("android.intent.category.LAUNCHER")
            intent.component = appItem.component
            intent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent
                    .FLAG_ACTIVITY_NEW_TASK
            context.applicationContext.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.d(TAG, e.message)
        }

        return false
    }

    fun getSharedprefsKeys(context: Context, subStr: String): List<String> {
        Log.d(TAG, "getSharedprefsKeys: " + subStr)
        val sharedPref = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
        val allEntries = sharedPref.getAll()
        Log.d(TAG, "getSharedprefsKeys: " + allEntries)
        return (allEntries.filter { (key, value) -> key.contains(subStr) && value is Int } as
                Map<String, Int>).toList<String, Int>().sortedByDescending( { it.second } ).map {
            it.first }
    }

    fun setStatusBarUpperAPI21(activity: Activity) {
        val window = activity.window
        //取消设置透明状态栏,使 ContentView 内容不再覆盖状态栏
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        //需要设置这个 flag 才能调用 setStatusBarColor 来设置状态栏颜色
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        //设置状态栏颜色
        //由于setStatusBarColor()这个API最低版本支持21, 本人的是15,所以如果要设置颜色,自行到style中通过配置文件设置
        window.statusBarColor = activity.resources.getColor(android.R.color.white, null)
        val mContentView = activity.findViewById(Window.ID_ANDROID_CONTENT) as ViewGroup
        val mChildView = mContentView.getChildAt(0)
        if (mChildView != null) {
            //注意不是设置 ContentView 的 FitsSystemWindows, 而是设置 ContentView 的第一个子 View . 预留出系统 View 的空间.
            ViewCompat.setFitsSystemWindows(mChildView, true)
        }
    }
}
