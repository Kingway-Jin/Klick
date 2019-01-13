package im.kingway.jin.klick

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.*
import java.io.File

class PrefsActivity : Activity() {

    private var mApp: KlickApplication? = null

    private var valuesIncludeRecentTask: Array<String>? = null
    private val iconChoices = intArrayOf(0, R.drawable.handle_1, R.drawable.handle_2, R.drawable.handle_3)
    private var iconChoiceNames: Array<String>? = null
    private val backgroundChoices = intArrayOf(0, R.color.transparent, R.drawable.handle_bg)
    private var backgroundChoiceNames: Array<String>? = null

    private var gestureView: View? = null
    private var gestureSeq: Int = 0
    private val gestureSettingIds = intArrayOf(R.id.gesture_home, R.id.gesture_back, R.id.gesture_menu, R.id
            .gesture_app_switch, R.id.gesture_lock_screen, R.id.gesture_expand_status_bar, R.id
            .gesture_show_more_actions, R.id.gesture_adjust_music_volume, R.id.gesture_open_camera, R.id
            .gesture_scroll_top, R.id.gesture_app_switch_forward, R.id.gesture_app_switch_backward, R.id
            .gesture_show_more_actions_quick_action, R.id.gesture_show_more_actions_quick_launch,
            R.id.gesture_open_dict)

    private fun convertGestureToCode(gesture: String): Long {
        Log.d(TAG, "convertGestureToCode: " + gesture)
        var code = 0
        var step = 1
        for (i in 0 until gesture.length) {
            code += (GestureEnum.getCode(gesture.substring(i, i + 1)) * step).toInt()
            step *= 10
        }
        Log.d(TAG, "convertGestureToCode: " + code)
        return code.toLong()
    }

    private fun getGestureDesc(gesture: String?): String {
        Log.d(TAG, "getGestureDesc: " + gesture!!)
        val sb = StringBuffer()
        for (i in 0 until gesture.length) {
            val g = gesture.substring(i, i + 1)
            if (i == 0) {
                sb.append(resources.getString(GestureEnum.getDesc(g)))
            } else if (i == 1) {
                sb.append(resources.getString(R.string.then)).append(resources.getString(R.string.slip))
                        .append(resources.getString(GestureEnum.getShortDesc(g)))
            } else {
                sb.append(resources.getString(R.string.then)).append(resources.getString(GestureEnum
                        .getShortDesc(g)))
            }
        }
        var ret = sb.toString()
        if (ret.isEmpty()) ret = resources.getString(R.string.label_gesture_not_set)
        Log.d(TAG, "getGestureDesc: " + ret)
        return ret
    }

    private fun getGestureDesc(code: Long): String {
        Log.d(TAG, "getGestureDesc: " + code)
        return getGestureDesc(convertCodeToGesture(code))
    }

    private fun convertCodeToGesture(code: Long): String {
        var code = code
        Log.d(TAG, "convertCodeToGesture: " + code)
        val sb = StringBuffer()
        while (code != 0L) {
            sb.append(GestureEnum.getType(code % 10))
            code /= 10
        }
        Log.d(TAG, "convertCodeToGesture: " + sb.toString())
        return sb.toString()
    }

    private fun showGestureDialog(title: Int) {
        gestureView = layoutInflater.inflate(R.layout.gesture_view, null)
        (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = ""
        val rb = gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton
        rb.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                var g: String? = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
                if (g == null || g.isEmpty() || g.length == 1) {
                    g = GestureEnum.SINGLE_TAP.type
                } else {
                    g = GestureEnum.SINGLE_TAP.type + g.substring(1)
                }
                (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
                (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

                (gestureView!!.findViewById(R.id.txt_gesture_tips) as TextView).text = ""
            }
        }

        (gestureView!!.findViewById(R.id.rbDoubleTap) as RadioButton).setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                var g: String? = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
                if (g == null || g!!.isEmpty() || g!!.length == 1) {
                    g = GestureEnum.DOUBLE_TAP.type
                } else {
                    g = GestureEnum.DOUBLE_TAP.type + g!!.substring(1)
                }
                (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
                (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

                if (KlickApplication.DOUBLE_TAP_THRESHOLD <= 0) {
                    (gestureView!!.findViewById(R.id.txt_gesture_tips) as TextView).setText(R.string
                            .double_tap_threshold_not_set)
                } else {
                    (gestureView!!.findViewById(R.id.txt_gesture_tips) as TextView).text = ""
                }
            }
        }
        (gestureView!!.findViewById(R.id.rbLongPress) as RadioButton).setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                var g: String? = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
                if (g == null || g!!.isEmpty() || g!!.length == 1) {
                    g = GestureEnum.LONG_PRESS.type
                } else {
                    g = GestureEnum.LONG_PRESS.type + g!!.substring(1)
                }
                (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
                (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

                if (KlickApplication.LONG_PRESS_THRESHOLD <= 0) {
                    (gestureView!!.findViewById(R.id.txt_gesture_tips) as TextView).setText(R.string
                            .long_press_threshold_not_set)
                } else {
                    (gestureView!!.findViewById(R.id.txt_gesture_tips) as TextView).text = ""
                }
            }
        }
        (gestureView!!.findViewById(R.id.btnIn) as Button).setOnClickListener(OnClickListener {
            var g = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
            if (g.length >= 10) return@OnClickListener
            if (g.isEmpty()) g = GestureEnum.SINGLE_TAP.type
            g += GestureEnum.SLIP_IN.type
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

            if (!(gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbDoubleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbLongPress) as RadioButton).isChecked) {
                (gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked = true
            }
        })
        (gestureView!!.findViewById(R.id.btnOut) as Button).setOnClickListener(OnClickListener {
            var g = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
            if (g.length >= 10) return@OnClickListener
            if (g.isEmpty()) g = GestureEnum.SINGLE_TAP.type
            g += GestureEnum.SLIP_OUT.type
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

            if (!(gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbDoubleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbLongPress) as RadioButton).isChecked) {
                (gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked = true
            }
        })
        (gestureView!!.findViewById(R.id.btnUp) as Button).setOnClickListener(OnClickListener {
            var g = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
            if (g.length >= 10) return@OnClickListener
            if (g.isEmpty()) g = GestureEnum.SINGLE_TAP.type
            g += GestureEnum.SLIP_UP.type
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

            if (!(gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbDoubleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbLongPress) as RadioButton).isChecked) {
                (gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked = true
            }
        })
        (gestureView!!.findViewById(R.id.btnDown) as Button).setOnClickListener(OnClickListener {
            var g = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
            if (g.length >= 10) return@OnClickListener
            if (g.isEmpty()) g = GestureEnum.SINGLE_TAP.type
            g += GestureEnum.SLIP_DOWN.type
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = g
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = getGestureDesc(g)

            if (!(gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbDoubleTap) as RadioButton).isChecked
                    && !(gestureView!!.findViewById(R.id.rbLongPress) as RadioButton).isChecked) {
                (gestureView!!.findViewById(R.id.rbSingleTap) as RadioButton).isChecked = true
            }
        })
        val adb = AlertDialog.Builder(this@PrefsActivity)
        adb.setTitle(title)
        adb.setView(gestureView)
        adb.setNegativeButton(R.string.cancel) { d, which -> d.dismiss() }
        adb.setNeutralButton(R.string.clear) { d, which ->
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag = ""
            (gestureView!!.findViewById(R.id.txt_gesture) as TextView).text = ""
            (this@PrefsActivity.findViewById(gestureSettingIds[gestureSeq]) as TextView).text = getGestureDesc(0)
            mApp!!.gestures[gestureSeq] = 0
            mApp!!.sharedPrefs!!.edit().putLong(KlickApplication
                    .SETTING_GESTURE_PREFIX + gestureSeq, 0).commit()
            d.dismiss()
        }
        adb.setPositiveButton(R.string.ok) { d, which ->
            val g = (gestureView!!.findViewById(R.id.txt_gesture) as TextView).tag.toString()
            (this@PrefsActivity.findViewById(gestureSettingIds[gestureSeq]) as TextView).text = getGestureDesc(g)
            val code = convertGestureToCode(g)
            mApp!!.gestures[gestureSeq] = code
            mApp!!.sharedPrefs!!.edit().putLong(KlickApplication.SETTING_GESTURE_PREFIX + gestureSeq, code)
                    .commit()
            d.dismiss()
        }
        adb.show()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mApp = application as KlickApplication
        mApp!!.addActivity(this)
        setContentView(R.layout.prefs)
        Utils.setStatusBarUpperAPI21(this);

        valuesIncludeRecentTask = resources.getStringArray(R.array.values_app_list_include_recent_task)
        iconChoiceNames = resources.getStringArray(R.array.icon_choices)
        backgroundChoiceNames = resources.getStringArray(R.array.bg_choices)


        var ll: LinearLayout? = null
        (findViewById(R.id.gesture_home) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_HOME])
        (findViewById(R.id.setting_gesture_home) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_HOME
            showGestureDialog(R.string.label_gesture_home)
        }

        (findViewById(R.id.gesture_back) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_BACK])
        (findViewById(R.id.setting_gesture_back) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_BACK
            showGestureDialog(R.string.label_gesture_back)
        }

        (findViewById(R.id.gesture_menu) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_MENU])
        (findViewById(R.id.setting_gesture_menu) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_MENU
            showGestureDialog(R.string.label_gesture_menu)
        }

        (findViewById(R.id.gesture_app_switch) as TextView).text = getGestureDesc(mApp!!
                .gestures[KlickApplication.SEQ_NO_APP_SWITCH])
        (findViewById(R.id.setting_gesture_app_switch) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_APP_SWITCH
            showGestureDialog(R.string.label_gesture_app_switch)
        }

        (findViewById(R.id.gesture_app_switch_forward) as TextView).text = getGestureDesc(mApp!!
                .gestures[KlickApplication.SEQ_NO_APP_SWITCH_FORWARD])
        (findViewById(R.id.setting_gesture_app_switch_forward) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_APP_SWITCH_FORWARD
            showGestureDialog(R.string.label_gesture_app_switch_forward)
        }

        (findViewById(R.id.gesture_app_switch_backward) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_APP_SWITCH_BACKWARD])
        (findViewById(R.id.setting_gesture_app_switch_backward) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_APP_SWITCH_BACKWARD
            showGestureDialog(R.string.label_gesture_app_switch_backward)
        }

        (findViewById(R.id.gesture_lock_screen) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_LOCK_SCREEN])
        (findViewById(R.id.setting_gesture_lock_screen) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_LOCK_SCREEN
            showGestureDialog(R.string.label_gesture_lock_screen)
        }

        (findViewById(R.id.gesture_expand_status_bar) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_EXPAND_STATUS_BAR])
        (findViewById(R.id.setting_gesture_expand_status_bar) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_EXPAND_STATUS_BAR
            showGestureDialog(R.string.label_gesture_expand_status_bar)
        }

        (findViewById(R.id.gesture_show_more_actions) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS])
        (findViewById(R.id.setting_gesture_show_more_actions) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS
            showGestureDialog(R.string.label_gesture_show_more_actions_global_action)
        }

        (findViewById(R.id.gesture_show_more_actions_quick_action) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION])
        (findViewById(R.id.setting_gesture_show_more_actions_quick_action) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION
            showGestureDialog(R.string.label_gesture_show_more_actions_quick_action)
        }

        (findViewById(R.id.gesture_show_more_actions_quick_launch) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_LAUNCH])
        (findViewById(R.id.setting_gesture_show_more_actions_quick_launch) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_LAUNCH
            showGestureDialog(R.string.label_gesture_show_more_actions_quick_launch)
        }

        (findViewById(R.id.gesture_adjust_music_volume) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_ADJUST_MUSIC_VOL])
        (findViewById(R.id.setting_gesture_adjust_music_volume) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_ADJUST_MUSIC_VOL
            showGestureDialog(R.string.label_gesture_adjust_music_volume)
        }

        (findViewById(R.id.gesture_open_camera) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_OPEN_CAMERA])
        (findViewById(R.id.setting_gesture_open_camera) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_OPEN_CAMERA
            showGestureDialog(R.string.label_gesture_open_camera)
        }

        (findViewById(R.id.gesture_scroll_top) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_SCROLL_TOP])
        (findViewById(R.id.setting_gesture_scroll_top) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_SCROLL_TOP
            showGestureDialog(R.string.label_gesture_scroll_top)
        }

        (findViewById(R.id.gesture_open_dict) as TextView).text = getGestureDesc(mApp!!.gestures[KlickApplication.SEQ_NO_OPEN_DICT])
        (findViewById(R.id.setting_gesture_open_dict) as LinearLayout).setOnClickListener {
            gestureSeq = KlickApplication.SEQ_NO_OPEN_DICT
            showGestureDialog(R.string.label_gesture_open_dict)
        }
        if (!Utils.isPkgInstalled(mApp!!, "im.kingway.movieenglish")) {
            findViewById(R.id.setting_gesture_open_dict).visibility = View.GONE
        }

        ll = findViewById(R.id.setting_reorder_apps) as LinearLayout
        ll.setOnClickListener {
            val cb = findViewById(R.id.reorder_apps) as CheckBox
            cb.toggle()
        }
        var cb = findViewById(R.id.reorder_apps) as CheckBox
        cb.isChecked = mApp!!.sharedPrefs!!.getBoolean(KlickApplication.SETTING_REORDER_APPS, false)
        cb.setOnCheckedChangeListener { checkBox, value ->
            mApp!!.sharedPrefs!!.edit().putBoolean(KlickApplication
                    .SETTING_REORDER_APPS, value).commit()
            val intent = Intent(KlickApplication.ACTION_MORE_ACTIONS)
            sendBroadcast(intent)
        }

        (findViewById(R.id.value_app_list_include_recent_task) as TextView).text = valuesIncludeRecentTask!![mApp!!
                .sharedPrefs!!.getInt(KlickApplication.INCLUDE_RECENT_TASK_IN_APP_LIST, 0)]
        ll = findViewById(R.id.app_list_include_recent_task) as LinearLayout
        ll.setOnClickListener {
            val choice = mApp!!.sharedPrefs!!.getInt(KlickApplication.INCLUDE_RECENT_TASK_IN_APP_LIST, 0)
            val adb = AlertDialog.Builder(this@PrefsActivity)
            adb.setTitle(R.string.label_app_list_include_recent_task)
            adb.setSingleChoiceItems(valuesIncludeRecentTask, choice) { d, which ->
                KlickApplication.MODE_INCLUDE_RECENT_TASK = which
                val tv = findViewById(R.id.value_app_list_include_recent_task) as TextView
                tv.text = valuesIncludeRecentTask!![which]
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.INCLUDE_RECENT_TASK_IN_APP_LIST, which)
                        .commit()
                d.dismiss()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && which > 0) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    startActivity(intent)
                }
            }
            adb.setNegativeButton(R.string.cancel) { d, which -> d.dismiss() }
            adb.show()
        }

        if (mApp!!.sharedPrefs!!.getBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_ON_HEAD, false))
            (findViewById(R.id.info_lock_phone) as TextView).setText(R.string.info_lock_phone_on_head)
        if (mApp!!.sharedPrefs!!.getBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_FACE_DOWN, false))
            (findViewById(R.id.info_lock_phone) as TextView).setText(R.string.info_lock_phone_face_down)
        ll = findViewById(R.id.lock_phone) as LinearLayout
        ll.setOnClickListener {
            val opts = arrayOfNulls<String>(2)
            opts[0] = resources.getString(R.string.info_lock_phone_on_head)
            opts[1] = resources.getString(R.string.info_lock_phone_face_down)

            var choice = -1
            if (mApp!!.sharedPrefs!!.getBoolean(KlickApplication
                    .SETTING_AUTO_LOCK_SCREEN_PHONE_ON_HEAD, false))
                choice = 0
            if (mApp!!.sharedPrefs!!.getBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_FACE_DOWN, false))
                choice = 1

            val adb = AlertDialog.Builder(this@PrefsActivity)
            adb.setTitle(R.string.label_lock_phone)
            adb.setSingleChoiceItems(opts, choice) { d, which ->
                if (which == 0) {
                    KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD = true
                    KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = false
                    (findViewById(R.id.info_lock_phone) as TextView).setText(R.string.info_lock_phone_on_head)
                } else if (which == 1) {
                    KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD = false
                    KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = true
                    (findViewById(R.id.info_lock_phone) as TextView).setText(R.string.info_lock_phone_face_down)
                } else {
                    KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD = false
                    KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = false
                    (findViewById(R.id.info_lock_phone) as TextView).setText(R.string
                            .info_lock_phone)
                }
                mApp!!.sharedPrefs!!.edit()
                        .putBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_ON_HEAD, KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD)
                        .putBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_FACE_DOWN, KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN)
                        .commit()
                d.dismiss()
            }
            adb.setPositiveButton(R.string.clear) { d, which ->
                KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD = false
                KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN = false
                (findViewById(R.id.info_lock_phone) as TextView).setText(R.string
                        .info_lock_phone)
                mApp!!.sharedPrefs!!.edit()
                        .putBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_ON_HEAD, KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD)
                        .putBoolean(KlickApplication.SETTING_AUTO_LOCK_SCREEN_PHONE_FACE_DOWN, KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN)
                        .commit()
                d.dismiss()
            }
            adb.setNegativeButton(R.string.cancel) { d, which -> d.dismiss() }
            adb.show()
        }

        ll = findViewById(R.id.exit) as LinearLayout
        ll.setOnClickListener {
            mApp!!.sharedPrefs!!.edit().putString("RECENT_APP_PACKAGE_NAME", KlickAccessibilityService.recentAppPackageName.joinToString(";"))?.commit()
            stopService(Intent(mApp!!.applicationContext, KlickService::class.java))
            mApp!!.exit()
        }

        val sbLongClickThreshold = findViewById(R.id.long_click_threshold) as SeekBar
        sbLongClickThreshold.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.SETTING_LONG_PRESS_THRESHOLD,
                300) / 10
        (findViewById(R.id.label_long_click_threshold) as TextView).text = resources.getString(R.string
                .label_long_click_threshold) + " - " + sbLongClickThreshold.progress * 10 + resources
                .getString(R.string.milli_second)
        sbLongClickThreshold.max = 100
        sbLongClickThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progress = progress
                progress *= 10
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.SETTING_LONG_PRESS_THRESHOLD, progress).commit()
                (findViewById(R.id.label_long_click_threshold) as TextView).text = resources.getString(R.string
                        .label_long_click_threshold) + " - " + progress + resources.getString(R.string
                        .milli_second)
                KlickApplication.LONG_PRESS_THRESHOLD = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val sbDoubleClickThreshold = findViewById(R.id.double_click_threshold) as SeekBar
        sbDoubleClickThreshold.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.SETTING_DOUBLE_TAP_THRESHOLD, 0) / 10
        (findViewById(R.id.label_double_click_threshold) as TextView).text = resources.getString(R.string
                .label_double_click_threshold) + " - " + sbDoubleClickThreshold.progress * 10 + resources
                .getString(R.string.milli_second)
        sbDoubleClickThreshold.max = 100
        sbDoubleClickThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.SETTING_DOUBLE_TAP_THRESHOLD, progress * 10)
                        .commit()
                (findViewById(R.id.label_double_click_threshold) as TextView).text = resources.getString(R
                        .string.label_double_click_threshold) + " - " + progress * 10 + resources.getString(R
                        .string.milli_second)
                KlickApplication.DOUBLE_TAP_THRESHOLD = progress * 10
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val sbFlashLightOnMaxSeconds = findViewById(R.id.flash_light_on_max_seconds) as SeekBar
        sbFlashLightOnMaxSeconds.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.SETTING_FLASH_LIGHT_ON_MAX_SECONDS, 60) / 10
        (findViewById(R.id.label_flash_light_on_max_seconds) as TextView).text = resources.getString(R.string
                .label_flash_light_on_max_seconds) + " - " + sbFlashLightOnMaxSeconds.progress * 10 +
                resources.getString(R.string.second)
        sbFlashLightOnMaxSeconds.max = 30
        sbFlashLightOnMaxSeconds.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progress = progress
                progress *= 10
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.SETTING_FLASH_LIGHT_ON_MAX_SECONDS, progress)
                        .commit()
                (findViewById(R.id.label_flash_light_on_max_seconds) as TextView).text = resources.getString(R
                        .string.label_flash_light_on_max_seconds) + " - " + progress + resources.getString(R
                        .string.second)
                KlickApplication.FLASH_LIGHT_ON_MAX_SECONDS = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val sbGestureDetectSensitivity = findViewById(R.id.gesture_detect_sensitivity) as SeekBar
        sbGestureDetectSensitivity.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.SETTING_GESTURE_DETECT_SENSITIVITY, 100) / 10
        (findViewById(R.id.label_gesture_detect_sensitivity) as TextView).text = resources.getString(R.string
                .label_gesture_detect_sensitivity) + " - " + sbGestureDetectSensitivity.progress * 10 +
                resources.getString(R.string.pixels)
        sbGestureDetectSensitivity.max = 20
        sbGestureDetectSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progress = progress
                progress *= 10
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.SETTING_GESTURE_DETECT_SENSITIVITY, progress)
                        .commit()
                (findViewById(R.id.label_gesture_detect_sensitivity) as TextView).text = resources.getString(R
                        .string.label_gesture_detect_sensitivity) + " - " + progress + resources.getString(R
                        .string.pixels)
                KlickApplication.GESTURE_DETECT_SENSITIVITY = if (progress > 10) progress else 10
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        var sbVibrate = findViewById(R.id.seekBarMilliseconds) as SeekBar
        sbVibrate.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.FEEDBACK_VIBRATE_MILLISECONDS, 0) / 5
        (findViewById(R.id.label_feedback_vibrate) as TextView).text = resources.getString(R.string
                .label_feedback_vibrate) + " - " + sbVibrate.progress * 5 + resources.getString(R.string
                .milli_second)
        sbVibrate.max = 20
        sbVibrate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progress = progress
                progress *= 5
                KlickApplication.VIBRATE_MILLS = progress
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.FEEDBACK_VIBRATE_MILLISECONDS, progress).commit()
                (findViewById(R.id.label_feedback_vibrate) as TextView).text = resources.getString(R.string
                        .label_feedback_vibrate) + " - " + progress + resources.getString(R.string.milli_second)
                mApp!!.getmVibrator()!!.vibrate(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //                mApp.getmVibrator().vibrate(seekBar.getProgress() * 5);
            }
        })

        sbVibrate = findViewById(R.id.seekBarMillisecondsLongClick) as SeekBar
        sbVibrate.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.FEEDBACK_VIBRATE_MILLISECONDS_LONG_CLICK,
                0) / 5
        (findViewById(R.id.label_long_click_feedback_vibrate) as TextView).text = resources.getString(R.string
                .label_long_click_feedback_vibrate) + " - " + sbVibrate.progress * 5 + resources.getString(R.string.milli_second)
        sbVibrate.max = 20
        sbVibrate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progress = progress
                progress *= 5
                KlickApplication.VIBRATE_MILLS_LONG_CLICK = progress
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.FEEDBACK_VIBRATE_MILLISECONDS_LONG_CLICK,
                        progress).commit()
                (findViewById(R.id.label_long_click_feedback_vibrate) as TextView).text = resources.getString(R
                        .string.label_long_click_feedback_vibrate) + " - " + progress + resources.getString(R
                        .string.milli_second)
                mApp!!.getmVibrator()!!.vibrate(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //                mApp.getmVibrator().vibrate(seekBar.getProgress() * 5);
            }
        })

        val sbVol = findViewById(R.id.seekBarVolume) as SeekBar
        sbVol.max = 10
        sbVol.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.FEEDBACK_SOUND_VOLUME, 0)
        (findViewById(R.id.label_feedback_sound) as TextView).text = resources.getString(R.string.label_feedback_sound) + " - " +
                mApp!!.sharedPrefs!!.getInt(KlickApplication.FEEDBACK_SOUND_VOLUME,0) * 10 + resources.getString(R.string.percentage)
        sbVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                KlickApplication.VOL_RATIO = progress.toFloat() / 10f
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.FEEDBACK_SOUND_VOLUME, progress).commit()
                (findViewById(R.id.label_feedback_sound) as TextView).text = resources.getString(R.string
                        .label_feedback_sound) + " - " + progress * 10 + resources.getString(R.string.percentage)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mApp!!.getmAudioManager()!!.playSoundEffect(AudioManager.FX_KEY_CLICK, seekBar.progress.toFloat() / 10f)
            }
        })
        val sbOpacity = findViewById(R.id.value_customize_icon_opacity) as SeekBar
        sbOpacity.max = 20
        sbOpacity.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_OPACITY, 30) / 5
        (findViewById(R.id.label_customize_icon_opacity) as TextView).text =
                resources.getString(R.string.label_customize_icon_opacity) + " - " +
                mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_OPACITY, 30) + resources.getString(R.string.percentage)


        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var progress = progress
                progress *= 5
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.CUSTOMIZE_ICON_OPACITY, progress).commit()
                (findViewById(R.id.label_customize_icon_opacity) as TextView).text = resources.getString(R
                        .string.label_customize_icon_opacity) + " - " + progress + resources.getString(R.string
                        .percentage)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val opacity = seekBar.progress * 5
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.CUSTOMIZE_ICON_OPACITY, opacity).commit()
                KlickApplication.setOpacityPCT(opacity)
                val intent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                intent.putExtra(KlickApplication.CUSTOMIZE_ICON_OPACITY, opacity)
                sendBroadcast(intent)
            }
        })

        val sbSize = findViewById(R.id.value_customize_icon_size) as SeekBar
        sbSize.max = KlickApplication.MAX_ICON_SIZE - KlickApplication.MIN_ICON_SIZE
        sbSize.progress = mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_SIZE, KlickApplication.MAX_ICON_SIZE) - KlickApplication.MIN_ICON_SIZE
        (findViewById(R.id.label_customize_icon_size) as TextView).text = resources.getString(R.string
                .label_customize_icon_size) + " - " + (sbSize.progress + KlickApplication.MIN_ICON_SIZE) + "DIP"
        sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                (findViewById(R.id.label_customize_icon_size) as TextView).text = resources.getString(R.string
                        .label_customize_icon_size) + " - " + (progress + KlickApplication.MIN_ICON_SIZE) + "DIP"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.CUSTOMIZE_ICON_SIZE, seekBar.progress + KlickApplication.MIN_ICON_SIZE).commit()
                KlickApplication.setIconSizeInDip(this@PrefsActivity, seekBar
                        .progress + KlickApplication.MIN_ICON_SIZE)
                val intent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                intent.putExtra(KlickApplication.CUSTOMIZE_ICON_SIZE, seekBar.progress + KlickApplication.MIN_ICON_SIZE)
                sendBroadcast(intent)
            }
        })

        var choice = mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_CHOICE, 1)
        try {
            if (choice == 0) {
                val filePath = mApp!!.sharedPrefs!!.getString(KlickApplication.CUSTOMIZE_ICON_FILE, null)
                val d = BitmapDrawable.createFromPath(filePath) ?: throw Exception()
                (findViewById(R.id.preview_customize_icon) as ImageView).setImageDrawable(d)
                (findViewById(R.id.info_customize_icon) as TextView).text = filePath
            } else {
                val d = resources.getDrawable(iconChoices[choice]).mutate()
                d.alpha = KlickApplication.FULLY_OPACITY
                (findViewById(R.id.preview_customize_icon) as ImageView).setImageDrawable(d)
                (findViewById(R.id.info_customize_icon) as TextView).text = iconChoiceNames!![choice]
            }
        } catch (e: Exception) {
            val d = resources.getDrawable(iconChoices[1]).mutate()
            d.alpha = KlickApplication.FULLY_OPACITY
            (findViewById(R.id.preview_customize_icon) as ImageView).setImageDrawable(d)
            (findViewById(R.id.info_customize_icon) as TextView).text = iconChoiceNames!![1]
        }

        ll = findViewById(R.id.customize_icon) as LinearLayout
        ll.setOnClickListener {
            val choice = mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_CHOICE, 1)
            val adb = AlertDialog.Builder(this@PrefsActivity)
            adb.setTitle(R.string.label_customize_icon)
            adb.setSingleChoiceItems(iconChoiceNames, choice) { d, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "image/*"
                    val wrapperIntent = Intent.createChooser(intent, null)
                    startActivityForResult(wrapperIntent, PICK_ICON_REQUEST)
                } else {
                    val dd = resources.getDrawable(iconChoices[which]).mutate()
                    dd.alpha = KlickApplication.FULLY_OPACITY

                    (findViewById(R.id.preview_customize_icon) as ImageView).setImageDrawable(dd)
                    (findViewById(R.id.info_customize_icon) as TextView).text = iconChoiceNames!![which]

                    mApp!!.sharedPrefs!!.edit().putInt(KlickApplication.CUSTOMIZE_ICON_CHOICE, which).commit()

                    val ciIntent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                    ciIntent.putExtra(KlickApplication.CUSTOMIZE_ICON_DRAWABLE, iconChoices[which])
                    sendBroadcast(ciIntent)
                }
                d.dismiss()
            }
            adb.setNegativeButton(R.string.cancel) { d, which -> d.dismiss() }
            adb.show()
        }

        choice = mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_BG_CHOICE, 1)
        try {
            if (choice == 0) {
                val filePath = mApp!!.sharedPrefs!!.getString(KlickApplication.CUSTOMIZE_ICON_BG_FILE, null)
                val d = BitmapDrawable.createFromPath(filePath) ?: throw Exception()
                (findViewById(R.id.preview_customize_icon_bg) as ImageView).setImageDrawable(d)
                (findViewById(R.id.info_customize_icon_bg) as TextView).text = filePath
            } else {
                val d = resources.getDrawable(backgroundChoices[choice]).mutate()
                d.alpha = KlickApplication.FULLY_OPACITY
                (findViewById(R.id.preview_customize_icon_bg) as ImageView).setImageDrawable(d)
                (findViewById(R.id.info_customize_icon_bg) as TextView).text = backgroundChoiceNames!![choice]
            }
        } catch (e: Exception) {
            val d = resources.getDrawable(backgroundChoices[1]).mutate()
            d.alpha = KlickApplication.FULLY_OPACITY
            (findViewById(R.id.preview_customize_icon_bg) as ImageView).setImageDrawable(d)
            (findViewById(R.id.info_customize_icon_bg) as TextView).text = backgroundChoiceNames!![1]
        }

        ll = findViewById(R.id.customize_icon_bg) as LinearLayout
        ll.setOnClickListener {
            val choice = mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_BG_CHOICE, 1)
            val adb = AlertDialog.Builder(this@PrefsActivity)
            adb.setTitle(R.string.label_customize_icon_bg)
            adb.setSingleChoiceItems(backgroundChoiceNames, choice) { d, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "image/*"
                    val wrapperIntent = Intent.createChooser(intent, null)
                    startActivityForResult(wrapperIntent, PICK_ICON_BG_REQUEST)
                } else {
                    val dd = resources.getDrawable(backgroundChoices[which]).mutate()
                    dd.alpha = KlickApplication.FULLY_OPACITY

                    (findViewById(R.id.preview_customize_icon_bg) as ImageView).setImageDrawable(dd)
                    (findViewById(R.id.info_customize_icon_bg) as TextView).text = backgroundChoiceNames!![which]

                    mApp!!.sharedPrefs!!.edit()
                            .putInt(KlickApplication.CUSTOMIZE_ICON_BG_CHOICE, which)
                            .commit()

                    val ciIntent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                    ciIntent.putExtra(KlickApplication.CUSTOMIZE_ICON_BG_DRAWABLE, backgroundChoices[which])
                    sendBroadcast(ciIntent)
                }
                d.dismiss()
            }
            adb.setNegativeButton(R.string.cancel) { d, which -> d.dismiss() }
            adb.show()
        }

        val labelAbout = findViewById(R.id.label_about) as TextView
        labelAbout.text = mApp!!.getResourceStringWithAppVersion(R.string.label_about)

        if (KlickService.sharedInstance == null) startService(Intent(this, KlickService::class
                .java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        if (requestCode == PICK_ICON_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val uri = intent.data
                val filePath = Utils.getRealPathFromURI(this, uri!!)
                try {
                    val d = BitmapDrawable.createFromPath(filePath)
                    if (d != null) {
                        (findViewById(R.id.preview_customize_icon) as ImageView).setImageDrawable(d)
                        (findViewById(R.id.info_customize_icon) as TextView).text = filePath

                        mApp!!.sharedPrefs!!.edit()
                                .putInt(KlickApplication.CUSTOMIZE_ICON_CHOICE, 0)
                                .putString(KlickApplication.CUSTOMIZE_ICON_FILE, filePath)
                                .commit()

                        val ciIntent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                        ciIntent.putExtra(KlickApplication.CUSTOMIZE_ICON_FILE, filePath)
                        sendBroadcast(ciIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.icon_file_not_valid, Toast.LENGTH_SHORT).show()
                }

            }
        }
        if (requestCode == PICK_ICON_BG_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val uri = intent.data
                val filePath = Utils.getRealPathFromURI(this, uri!!)
                try {
                    val d = BitmapDrawable.createFromPath(filePath)
                    if (d != null) {
                        (findViewById(R.id.preview_customize_icon_bg) as ImageView).setImageDrawable(d)
                        (findViewById(R.id.info_customize_icon_bg) as TextView).text = filePath

                        mApp!!.sharedPrefs!!.edit()
                                .putInt(KlickApplication.CUSTOMIZE_ICON_BG_CHOICE, 0)
                                .putString(KlickApplication.CUSTOMIZE_ICON_BG_FILE, filePath)
                                .commit()

                        val ciIntent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                        ciIntent.putExtra(KlickApplication.CUSTOMIZE_ICON_BG_FILE, filePath)
                        sendBroadcast(ciIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.icon_file_not_valid, Toast.LENGTH_SHORT).show()
                }

            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        val f = File(BACKUP_FILE_PATH)
        Utils.saveSharedPreferencesToFile(mApp!!.sharedPrefs!!, f)
        super.onDestroy()
        (findViewById(R.id.preview_customize_icon_bg) as ImageView).setImageResource(0)
        (findViewById(R.id.preview_customize_icon) as ImageView).setImageResource(0)

        mApp!!.removeActivity(this)
    }

    companion object {
        private val TAG = "PrefsActivity"
        val BACKUP_FILE_PATH = Environment.getExternalStorageDirectory().toString() + File.separator +
                "klick_setting.bak"
        private val PICK_ICON_REQUEST = 1
        protected val PICK_ICON_BG_REQUEST = 0
    }

}
