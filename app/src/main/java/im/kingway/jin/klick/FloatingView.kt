package im.kingway.jin.klick

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.telephony.TelephonyManager
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.*
import java.util.*

class FloatingView(private val mApp: KlickApplication) : FrameLayout(mApp.applicationContext) {
    private val mHandleLayout: LinearLayout
    private val mWindowParams = WindowManager.LayoutParams()
    private val mHandle: ImageView
    private val mAssitHandle: View
    var mMoreActionsView: MoreActionsView? = null
    private val mTransAnimation: ValueAnimator

    private var previousPositionX: Int = 0
    private var previousPositionY: Int = 0
    private var currentPositionX: Int = 0
    private var currentPositionY: Int = 0
    private var downRawX: Float = 0.toFloat()
    private var downRawY: Float = 0.toFloat()
    private var previousRawX: Float = 0.toFloat()
    private var previousRawY: Float = 0.toFloat()
    private var dragSpeed: Float = 0.toFloat()
    private var previousDragSpeed: Float = 0.toFloat()
    private var dragDirection: Int = 0
    private var previousDragDirection: Int = 0
    private var xMovement: Float = 0.toFloat()
    private var yMovement: Float = 0.toFloat()
    private var movement: Float = 0.toFloat()
    private var direction: Long = 0
    private var previousDirection: Long = 0
    private val rawXYList = LinkedList<Float>()

    private var tapStartAt: Long = 0
    private var preTapStartAt: Long = 0
    private var isSliding = false

    private val oneDIP2PX: Int
    private var currHandleOpacity: Int = 0

    private var action: Actions? = null
    private var preAction: Actions? = null
    private var isAnimating = false
    private var gesture: Long = 0
    private var gestureStep: Long = 10
    private val posList = LinkedList<PhonePos>()
    private var hidFromSoftKeyboardDistance = 0
    private val imeClientCountMap = HashMap<String, Int>()

    private var activeQuickActions = mutableListOf<String>()
    private var loopIndexActiveQuickAction = 0
    private var quickActionTipView: TextView

    var mHandler: Handler = object : Handler() {
        @Synchronized override fun handleMessage(msg: Message) {
            when (msg.what) {
                KlickApplication.MSG_DOUBLE_TAP_TIMEOUT -> if (action == Actions.tap) {
                    mApp.playFeedback(false)
                    onAction(gesture)
                    preAction = null
                    action = null
                    setHandleOpacity(KlickApplication.ICON_OPACITY)
                }
                KlickApplication.MSG_LONG_PRESS_TRIGGER -> {
                    val tapStartAtMsg = msg.data.getLong("tapStartAt")
                    if ((touchState == MotionEvent.ACTION_DOWN || touchState == MotionEvent.ACTION_MOVE) && action == Actions.tap && !isSliding && tapStartAt == tapStartAtMsg) {
                        action = Actions.long_press
                        gesture = GestureEnum.LONG_PRESS.code
                        setHandleOpacity(KlickApplication.LONG_PRESS_OPACITY)
                        mApp.playFeedback(true)
                        if (gesture == mApp.gestures[KlickApplication
                                .SEQ_NO_ADJUST_MUSIC_VOL]) {
                            mApp.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager
                                    .FLAG_SHOW_UI)
                        }
                    }
                }
                KlickApplication.MSG_HIDE_MORE_ACTION_VIEW -> hideMoreActionsView()
                KlickApplication.MSG_TRANSPARENT_BACKGROUND -> {
                    val toOpacity = msg.data.getInt("OPACITY")
                    if (currHandleOpacity != toOpacity) {
                        startTransAnimation(currHandleOpacity, toOpacity)
                    }
                }
                KlickApplication.MSG_TURN_OFF_FLASH_LIGHT -> mMoreActionsView!!.turnFlashLight(false)
                KlickApplication.MSG_BREATHING -> {
                    this.removeMessages(KlickApplication.MSG_BREATHING)
                    val breathing = msg.data.getInt("BREATHING", 0)

                    if (mApp.canBreath) {
                        Log.d(TAG, "breathing: " + breathing + ", isAnimating: " + isAnimating + ", isSliding: " +
                                isSliding + ", touchState: " + touchState)
                        if (isAnimating || isSliding || touchState != MotionEvent.ACTION_UP) {
                            startToBreath(1, 5000)
                        } else {
                            breath(breathing)
                        }
                    }
                }
                else -> {
                }
            }
            super.handleMessage(msg)
        }
    }

    private val mSensorEventListener = object : SensorEventListener {
        private var x: Float = 0.toFloat()
        private var y: Float = 0.toFloat()
        private var z: Float = 0.toFloat()
        private var last_x: Float = 0.toFloat()
        private var last_y: Float = 0.toFloat()
        private var last_z: Float = 0.toFloat()
        private var currPos: Int = 0
        private var switchHandMode = false
        private val SHAKE_THRESHOLD = 800
        private val shakeTime: Long = 1000000000
        private val shakeTimeOff: Long = 200
        private var lastUpdate: Long = 0
        private var lastShakeTime: Long = 0
        private var shakeCount = 0

        override fun onSensorChanged(event: SensorEvent) {
            if (isAnimating || this@FloatingView.visibility != View.VISIBLE) {
                return
            }

            if (mMoreActionsView!!.visibility != View.VISIBLE && detectSoftKeyboardShowOrNot()) {
                return
            }

            synchronized(mApp.getmSensorManager()!!) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        x = event.values[0]
                        y = event.values[1]
                        z = event.values[2]

                        if (mApp.shakeFlashLight && mMoreActionsView!!.isFlashlightOn) {
                            val diffTime = System.currentTimeMillis() - lastShakeTime
                            lastShakeTime = System.currentTimeMillis()
                            val speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000
                            if (speed > 800 && speed < 5000) {
                                shakeCount++
                            } else if (speed < 300) {
                                shakeCount = if (--shakeCount < 0) 0 else shakeCount
                            }
                            Log.d(TAG, "Shake Count: $shakeCount, Speed: $speed")
                            if (shakeCount == 3) {
                                mMoreActionsView!!.turnFlashLight(false)
                                Log.d(TAG, "Flash Light Off")
                            }
                        }
                        last_x = x
                        last_y = y
                        last_z = z

                        if (z > 6 && y < 8 && x <= 1 && x >= -1) {
                            currPos = KlickApplication.POS_FACE_UP
                        } else if (z < -8) {
                            currPos = KlickApplication.POS_FACE_DOWN
                        } else if (x > 5 && Math.abs(y) < 3 && Math.abs(z) < 3) {
                            currPos = KlickApplication.POS_FACE_LEFT
                        } else if (x < -5 && Math.abs(y) < 3 && Math.abs(z) < 3) {
                            currPos = KlickApplication.POS_FACE_RIGHT
                        } else if (y > 8) {
                            currPos = KlickApplication.POS_STANDING_UP
                        } else if (y < -8 && x <= 1 && z <= 1) {
                            currPos = KlickApplication.POS_ON_HEAD
                        } else {
                            currPos = KlickApplication.POS_IN_BETWEEN
                        }
//                        if (z > 6 && y < 8 && x <= 1 && x >= -1) {
//                            currPos = KlickApplication.POS_FACE_UP
//                        } else if (z < -8) {
//                            currPos = KlickApplication.POS_FACE_DOWN
//                        } else if (x > 5 && y < 8 && z < 6) {
//                            currPos = KlickApplication.POS_FACE_LEFT
//                        } else if (x < -5 && y < 8 && z < 6) {
//                            currPos = KlickApplication.POS_FACE_RIGHT
//                        } else if (y > 8) {
//                            currPos = KlickApplication.POS_STANDING_UP
//                        } else if (y < -8 && x <= 1 && z <= 1) {
//                            currPos = KlickApplication.POS_ON_HEAD
//                        } else {
//                            currPos = KlickApplication.POS_IN_BETWEEN
//                        }
                        Log.d(TAG, "currPos-" + currPos + ": " + x + ", " + y + ", " + z);

                        if (mMoreActionsView != null && mMoreActionsView!!.visibility == View.VISIBLE) {
                            if (mMoreActionsView!!.getmBackgroundView().height != mApp.getScreenRect(true).height()) {
                                mMoreActionsView!!.setSize(mApp.getScreenRect(false).width(), mApp.getScreenRect(false)
                                        .height())
                            }
                            return@synchronized
                        }

//                        if (KlickApplication.POS_IN_BETWEEN == currPos || posList.isNotEmpty() &&
//                                posList[0].pos == currPos) {
//                            if (switchHandMode && posList.isNotEmpty() && (event.timestamp -
//                                    posList[0].startTime!!) / 1000000000L > 3) {
//                                switchHandMode = false
//                                if (currHandleOpacity != KlickApplication.ICON_OPACITY) {
//                                    posList.clear()
//                                    startTransAnimation(currHandleOpacity, KlickApplication.ICON_OPACITY)
//                                }
//                            }
//                            return@synchronized
//                        }

                        if (KlickApplication.POS_FACE_LEFT != currPos && KlickApplication
                                .POS_FACE_RIGHT != currPos) {
                            if (switchHandMode) {
                                switchHandMode = false
                                if (currHandleOpacity != KlickApplication.ICON_OPACITY) {
                                    posList.clear()
                                    startTransAnimation(currHandleOpacity, KlickApplication.ICON_OPACITY)
                                }
                            }
                            return@synchronized
                        }

                        if (posList.isEmpty() || currPos != posList[0].pos) {
                            posList.add(0, PhonePos(currPos, event.timestamp))
                            if (posList.size > 3) {
                                posList.removeAt(3)
                            }
                        }

                        if (mApp.shakeFlashLight && !mMoreActionsView!!.isFlashlightOn) {
                            if (posList.size >= 2 && posList[0].pos == KlickApplication.POS_FACE_DOWN &&
                                    (posList[1].pos == KlickApplication.POS_FACE_UP || posList[posList
                                            .size - 1].pos == KlickApplication.POS_FACE_UP)) {
                                //                            Log.d(TAG, posList.get(0).getCurTime() + ", " + (posList.get(0).getCurTime() -
                                // lastUpdate) + ", " + (posList.get(0).getCurTime() - posList.get(1).getCurTime()) + " -- " + posList.size());
                                if (posList[0].curTime!! - posList[1].curTime!! <= shakeTime * 1) {
                                    lastUpdate = posList[0].curTime!!
                                    mApp.getmVibrator()!!.vibrate(KlickApplication.VIBRATE_MILLS_LONG_CLICK.toLong())
                                }
                            } else if (posList.size >= 2 && posList[0].pos == KlickApplication.POS_FACE_UP
                                    && (posList[1].pos == KlickApplication.POS_FACE_DOWN || posList[posList.size - 1].pos == KlickApplication.POS_FACE_DOWN)) {
                                //                            Log.d(TAG, posList.get(0).getCurTime() + ", " + (posList.get(0).getCurTime() -
                                // lastUpdate) + ", " + (posList.get(0).getCurTime() - posList.get(1).getCurTime()) + " -- " + posList.size());
                                if (posList[0].curTime!! - lastUpdate <= shakeTime * 1 && posList[0]
                                        .curTime!! - posList[1].curTime!! <= shakeTime * 1) {
                                    lastUpdate = 0
                                    shakeCount = 0
                                    mApp.getmVibrator()!!.vibrate(KlickApplication.VIBRATE_MILLS_LONG_CLICK.toLong())
                                    mMoreActionsView!!.turnFlashLight(true)
                                    Log.d(TAG, "Flash Light On")
                                }
                            }
                        }

                        if (mApp.getmTelephonyManager()!!.callState == TelephonyManager.CALL_STATE_IDLE && (KlickApplication.POS_ON_HEAD == currPos && KlickApplication.AUTO_LOCK_SCREEN_PHONE_ON_HEAD || KlickApplication.POS_FACE_DOWN == currPos && KlickApplication.AUTO_LOCK_SCREEN_PHONE_FACE_DOWN)) {
                            mApp.applicationContext.sendBroadcast(Intent(KlickApplication.ACTION_LOCK_SCREEN))
                            return@synchronized
                        }

                        if (KlickApplication.FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR && (currPos ==
                                KlickApplication.POS_FACE_LEFT || currPos == KlickApplication
                                .POS_FACE_RIGHT)) {
                            if (!switchHandMode && (mApp.getFloattingPositionX(false) == 0 &&
                                    currPos == KlickApplication.POS_FACE_RIGHT || mApp
                                    .getFloattingPositionX(false) > 0 && currPos == KlickApplication
                                    .POS_FACE_LEFT)) {
                                switchHandMode = true
                                mHandler.removeMessages(KlickApplication.MSG_TRANSPARENT_BACKGROUND)
                                setHandleOpacity(KlickApplication.FULLY_OPACITY)
                                mApp.playFeedback(false)
                            }

                            // && (event.timestamp - posList[0].startTime!!) / 100000000L > 5
                            if (switchHandMode) {
                                switchHandMode = false
                                posList.clear()

                                switchHandle()
                            }
                        }

//                        if (KlickApplication.FACE_RIGHT_LEFT_SWITCH_KLICK_ANCHOR && posList.size >= 2) {
//                            if (switchHandMode && (event.timestamp - posList[1].startTime!!) / 1000000000L > 3) {
//                                switchHandMode = false
//                                if (currHandleOpacity != KlickApplication
//                                        .ICON_OPACITY) {
//                                    posList.clear()
//                                    isAnimating = true
//                                    startTransAnimation(currHandleOpacity, KlickApplication.ICON_OPACITY)
//                                }
//                            }
//
//                            if (!switchHandMode && posList.size >= 2 && posList[1].pos == KlickApplication.POS_FACE_UP && (mApp.getFloattingPositionX(false) == 0 && posList[0].pos == KlickApplication.POS_FACE_LEFT || mApp
//                                    .getFloattingPositionX(false) > 0 && posList[0].pos == KlickApplication.POS_FACE_RIGHT)) {
//                                switchHandMode = true
//                                mHandler.removeMessages(KlickApplication.MSG_TRANSPARENT_BACKGROUND)
//                                setHandleOpacity(KlickApplication.FULLY_OPACITY)
//                                mApp.playFeedback(false)
//                            }
//
//                            if (switchHandMode && posList.size >= 2 && posList[0].pos == KlickApplication.POS_FACE_UP && (mApp.getFloattingPositionX(false) == 0 && posList[1].pos == KlickApplication.POS_FACE_LEFT || mApp.getFloattingPositionX(false) > 0 && posList[1].pos == KlickApplication.POS_FACE_RIGHT)) {
//                                switchHandMode = false
//                                posList.clear()
//                                unregisterSensorEventListener()
//
//                                mApp.getScreenRect(true)
//                                aniStartX = mApp.getFloattingPositionX(true)
//                                KlickApplication.FLOATING_POSITION_X = if (aniStartX == 0) 1 else 0
//                                aniEndX = mApp.getFloattingPositionX(true)
//                                mWindowParams.y = currentPositionY //mApp.getFloattingPositionY
//                                // (true);
//
//                                val animation = ValueAnimator.ofInt(aniStartX, aniEndX)
//                                animation.duration = 300
//                                animation.interpolator = AccelerateDecelerateInterpolator()
//                                animation.addUpdateListener { animation ->
//                                    mWindowParams.x = animation.animatedValue as Int
//                                    mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
//                                }
//                                animation.addListener(object : AnimatorListener {
//                                    override fun onAnimationStart(animation: Animator) {
//                                        isAnimating = true
//                                    }
//
//                                    override fun onAnimationRepeat(animation: Animator) {}
//
//                                    override fun onAnimationEnd(animation: Animator) {
//                                        mWindowParams.x = mApp.getFloattingPositionX(false)
//                                        mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
//                                        mApp.sharedPrefs!!.edit().putInt(KlickApplication
//                                                .SETTING_FLOATING_POSITION_X, aniEndX).commit()
//                                        KlickApplication.FLOATING_POSITION_X = aniEndX
//                                        //                                    mHandler.sendMessageDelayed(getOpacityMsg(KlickApplication.ICON_OPACITY),
//                                        // KlickApplication.TRANSPARENT_BACKGROUND_THRESHOLD);
//                                        registerSensorEventListener()
//                                        startTransAnimation(currHandleOpacity, KlickApplication.ICON_OPACITY)
//                                    }
//
//                                    override fun onAnimationCancel(animation: Animator) {
//                                        isAnimating = false
//                                        startToBreath(1, 3000)
//                                    }
//                                })
//                                animation.start()
//                            }
//                        }
                    }
                    else -> {
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private fun switchHandle() {
        unregisterSensorEventListener()

        mApp.getScreenRect(true)
        aniStartX = mApp.getFloattingPositionX(true)
        KlickApplication.FLOATING_POSITION_X = if (aniStartX == 0) 1 else 0
        aniEndX = mApp.getFloattingPositionX(true)
        mWindowParams.y = currentPositionY //mApp.getFloattingPositionY
        // (true);

        val animation = ValueAnimator.ofInt(aniStartX, aniEndX)
        animation.duration = 300
        animation.interpolator = AccelerateInterpolator()
        animation.addUpdateListener { animation ->
            mWindowParams.x = animation.animatedValue as Int
            mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
        }
        animation.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                mWindowParams.x = mApp.getAssistHandlePositionX(false)
                mApp.getmWindowManager()!!.updateViewLayout(mAssitHandle, mWindowParams)
                mWindowParams.x = mApp.getFloattingPositionX(false)
                mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
                mApp.sharedPrefs!!.edit().putInt(
                        KlickApplication
                                .SETTING_FLOATING_POSITION_X, aniEndX).commit()
                KlickApplication.FLOATING_POSITION_X = aniEndX
                //                                    mHandler.sendMessageDelayed(getOpacityMsg(KlickApplication.ICON_OPACITY),
                // KlickApplication.TRANSPARENT_BACKGROUND_THRESHOLD);
                registerSensorEventListener()
                startTransAnimation(currHandleOpacity, KlickApplication.ICON_OPACITY)
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }
        })
        animation.start()
    }

    private enum class Actions {
        tap, long_press, drag, slip, long_press_slip
    }

    fun startToBreath(breathing: Int, delayMillis: Long) {
        Log.d(TAG, "startToBreath")
        val msgBreathing = Message()
        val bundle = Bundle()
        bundle.putInt("BREATHING", breathing)
        msgBreathing.data = bundle
        msgBreathing.what = KlickApplication.MSG_BREATHING
        mHandler.sendMessageDelayed(msgBreathing, delayMillis)
    }

    private fun breath(breathing: Int) {
        Log.d(TAG, "breath: " + breathing + ", currHandleOpacity: " + currHandleOpacity + ", " +
                "ICON_OPACITY: " + KlickApplication.ICON_OPACITY + ", " +
                "ICON_OPACITY_ACTIVE: " + KlickApplication.ICON_OPACITY_ACTIVE)
        setHandleOpacity(currHandleOpacity + breathing)

        val delayMillis = 300 / (KlickApplication.ICON_OPACITY_ACTIVE - KlickApplication.ICON_OPACITY)
        if (currHandleOpacity >= KlickApplication.ICON_OPACITY_ACTIVE) {
            startToBreath(-1, delayMillis.toLong())
        } else if (currHandleOpacity <= KlickApplication.ICON_OPACITY) {
            startToBreath(1, 3000)
        } else {
            startToBreath(breathing, delayMillis.toLong())
        }
    }

    init {
        mWindowParams.height = -2
        mWindowParams.width = -2
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        mWindowParams.format = PixelFormat.TRANSPARENT
        mWindowParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        mWindowParams.gravity = Gravity.LEFT or Gravity.TOP

        val layoutparams = FrameLayout.LayoutParams(-1, -1)
        layoutParams = layoutparams
        val view = View.inflate(mApp.applicationContext, R.layout.handle, null)
        mHandleLayout = view.findViewById(R.id.handle) as LinearLayout
        mHandle = view.findViewById(R.id.handle_image) as ImageView
        mHandle.setImageDrawable(mApp.handleDrawable)
        mHandle.setBackgroundDrawable(mApp.handleBgDrawable)

        mAssitHandle = View.inflate(mApp.applicationContext, R.layout.assist_handle, null)
        mAssitHandle.setOnTouchListener { view, motionEvent -> when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> switchHandle() }; true }

        quickActionTipView = View.inflate(mApp.applicationContext, R.layout
                .quick_action_text_view, null) as TextView

        setIconSizeInDip(KlickApplication.HANDLE_WIDTH_DP, KlickApplication.HANDLE_HEIGHT_DP)

        mHandle.background.alpha = KlickApplication.ICON_BG_OPACITY_ACTIVE
        mHandle.drawable.alpha = KlickApplication.ICON_OPACITY_ACTIVE

        mApp.getScreenRect(true)
        currentPositionX = mApp.getFloattingPositionX(true)
        currentPositionY = mApp.getFloattingPositionY(true)

        oneDIP2PX = Utils.dip2px(mApp.applicationContext, 1f)

        addView(mHandleLayout)

        mTransAnimation = ValueAnimator.ofInt(KlickApplication.ICON_OPACITY_ACTIVE, KlickApplication.ICON_OPACITY)
        mTransAnimation.duration = 100
        mTransAnimation.interpolator = LinearInterpolator()
        mTransAnimation.addUpdateListener { animation -> setHandleOpacity(animation.animatedValue as Int) }
        mTransAnimation.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }
        })

        val wp = WindowManager.LayoutParams()
        wp.height = -2
        wp.width = -2
        wp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams
                .FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        wp.format = PixelFormat.TRANSPARENT
        wp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        wp.gravity = Gravity.CENTER
        wp.y = -200
        quickActionTipView.text = "^_^"
        mApp.getmWindowManager()!!.addView(quickActionTipView, wp)
        quickActionTipView.visibility = View.INVISIBLE
    }

    private fun startTransAnimation(fromOpacity: Int, toOpacity: Int, duration: Int = 100) {
        mTransAnimation.setIntValues(fromOpacity, toOpacity)
        mTransAnimation.duration = duration.toLong()
        mTransAnimation.start()
    }

    fun setIconSizeInDip(width: Int, height: Int) {
        val widthInPx = Utils.dip2px(mApp.applicationContext, width.toFloat())
        val heightInPx = Utils.dip2px(mApp.applicationContext, height.toFloat())
        var lp: android.view.ViewGroup.LayoutParams = this.layoutParams
        lp.width = widthInPx
        lp.height = heightInPx
        this.layoutParams = lp
        lp = mHandle.layoutParams
        lp.width = widthInPx
        lp.height = heightInPx
        mHandle.layoutParams = lp
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mApp.shakeFlashLight = false

        screenX = event.rawX
        screenY = event.rawY

        when (event.action)  {
            MotionEvent.ACTION_DOWN -> {
                unregisterSensorEventListener()
                mHandler.removeMessages(KlickApplication.MSG_DOUBLE_TAP_TIMEOUT)
                mHandler.removeMessages(KlickApplication.MSG_TRANSPARENT_BACKGROUND)

                preTapStartAt = tapStartAt
                tapStartAt = Date().time
                touchState = MotionEvent.ACTION_DOWN

                touchStartX = event.x
                touchStartY = event.y
                downRawX = event.rawX
                downRawY = event.rawY

                aniStartX = (screenX - touchStartX).toInt()
                aniStartY = (screenY - touchStartY).toInt()

                preAction = action
                action = Actions.tap

                isSliding = false
                gesture = GestureEnum.SINGLE_TAP.code
                gestureStep = 10
                direction = 0
                previousDirection = 0
                movement = 0f

                dragDirection = KlickApplication.DRAG_FALL_BACK
                dragSpeed = 0f
                previousDragDirection = KlickApplication.DRAG_FALL_BACK
                previousDragSpeed = 0f
                previousRawX = event.rawX
                previousRawY = event.rawY
                rawXYList.clear()
                rawXYList.add(0, downRawY)
                rawXYList.add(0, downRawX)

                if (KlickApplication.DOUBLE_TAP_THRESHOLD > 0 && preAction == Actions.tap && tapStartAt - preTapStartAt < KlickApplication.DOUBLE_TAP_THRESHOLD) {
                    gesture = GestureEnum.DOUBLE_TAP.code
                } else if (KlickApplication.LONG_PRESS_THRESHOLD > 0) {
                    val msg = Message()
                    val bundle = Bundle()
                    bundle.putLong("tapStartAt", tapStartAt)
                    msg.data = bundle
                    msg.what = KlickApplication.MSG_LONG_PRESS_TRIGGER
                    mHandler.sendMessageDelayed(msg, KlickApplication.LONG_PRESS_THRESHOLD.toLong())
                }
                setHandleOpacity(KlickApplication.FULLY_OPACITY)
            }
            MotionEvent.ACTION_MOVE -> run {
//                Log.d(TAG, "Gesture: " + gesture)
                touchState = MotionEvent.ACTION_MOVE

                previousDirection = direction
                if (action == Actions.tap || action == Actions.long_press) {
                    xMovement = event.rawX - rawXYList[rawXYList.size - 2]
                    yMovement = event.rawY - rawXYList[rawXYList.size - 1]
                } else {
                    var i = 0
                    while (i < 100 && i < rawXYList.size) {
                        xMovement = event.rawX - rawXYList[i]
                        yMovement = event.rawY - rawXYList[i + 1]
                        if (Math.abs(xMovement) >= KlickApplication.GESTURE_DETECT_SENSITIVITY || Math.abs(yMovement) >= KlickApplication.GESTURE_DETECT_SENSITIVITY) {
                            break
                        }
                        i += 2
                    }
                }

                if (Math.abs(xMovement) >= Math.abs(yMovement)) {
                    if (mApp.getFloattingPositionX(false) == 0) {
                        direction = if (xMovement > 0) GestureEnum.SLIP_IN.code else GestureEnum.SLIP_OUT.code
                    } else {
                        direction = if (xMovement < 0) GestureEnum.SLIP_IN.code else GestureEnum.SLIP_OUT.code
                    }
                } else {
                    direction = if (yMovement > 0) GestureEnum.SLIP_DOWN.code else GestureEnum.SLIP_UP.code
                }
//                Log.d(TAG, "Movement: " + xMovement + ", " + yMovement + " - " + GestureEnum.getType(direction) + " " +
//                        ":" + " " + KlickApplication.GESTURE_DETECT_SENSITIVITY)

                if (action == Actions.drag) {
                    updateFloatingViewPosition()
                    previousDragDirection = dragDirection
                    previousDragSpeed = dragSpeed
                    if (Math.abs(xMovement) >= Math.abs(yMovement)) {
                        if (event.rawX > previousRawX)
                            dragDirection = KlickApplication.DRAG_TO_RIGHT
                        else
                            dragDirection = KlickApplication.DRAG_TO_LEFT
                    } else {
                        dragDirection = KlickApplication.DRAG_FALL_BACK
                    }
                    dragSpeed = Math.abs(xMovement)
                    return@run
                }

                if (gesture == mApp.gestures[KlickApplication.SEQ_NO_ADJUST_MUSIC_VOL]) {
                    adjustMusicVol(event)
                    return@run
                }

                if (gesture == mApp.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION]) {
                    switchAppQuickAction(event)
                    return@run
                }

                if (gesture < 10 && (Math.abs(xMovement) >= KlickApplication.DRAG_START_THRESHOLD || Math.abs(yMovement) >= KlickApplication.DRAG_START_THRESHOLD)) {
                    var startDrag = true
                    for (g in mApp.gestures) {
                        if (g % 10 == gesture && g % 100 / 10 == direction) {
                            startDrag = false
                            break
                        }
                    }
                    if (startDrag) {
                        startDragging()
                    }
                }

                if (Math.abs(xMovement) >= KlickApplication.SLIP_START_THRESHOLD || Math.abs(yMovement) >= KlickApplication.SLIP_START_THRESHOLD) {
                    if (action == Actions.tap) {
                        action = Actions.slip
                    }

                    if (action == Actions.long_press) {
                        action = Actions.long_press_slip
                    }
                }

                if (action == Actions.slip || action == Actions.long_press_slip) {
                    if (previousDirection == direction && gesture * 10 / gestureStep != previousDirection) {
                        gesture = direction * gestureStep + gesture
                        gestureStep *= 10

                        if (gesture == mApp.gestures[KlickApplication.SEQ_NO_ADJUST_MUSIC_VOL]) {
                            mApp.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager
                                    .FLAG_SHOW_UI)
                        }

                        if (gesture == mApp.gestures[KlickApplication
                                .SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION]) {
                            val activePkg = KlickAccessibilityService.currentRootInActiveWindow?.packageName
                            activeQuickActions.clear()
                            activeQuickActions.addAll(Utils.getSharedprefsKeys(context,
                                    "quick_action:" + activePkg + ":").map { it.substring(("quick_action:" + activePkg + ":").length) })
                            activeQuickActions.removeAll { null == KlickAccessibilityService
                                    .sharedInstance?.findClickableNodeByText(it) }
                            if ("com.tencent.mm" == activePkg) {
                                val newMsgTest = KlickAccessibilityService.sharedInstance?.getTextOfClickableNodeByPostfix(QuickActionListAdapter.POSTFIX_NEW_MSG)
                                if (!newMsgTest.isNullOrBlank()) {
                                    activeQuickActions.add(0, newMsgTest!!)
                                }
                            }
                            loopIndexActiveQuickAction =  -1
                            Log.d(TAG, "activePkg: $activePkg QUICK ACTIONS: " + activeQuickActions
                                    .joinToString(" "))
                            quickActionTipView.text = Html.fromHtml(getQuickActionMsg())
                            quickActionTipView.visibility = View.VISIBLE
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "Gesture: " + gesture)
                touchState = MotionEvent.ACTION_UP
                mHandler.removeMessages(KlickApplication.MSG_LONG_PRESS_TRIGGER)

                if (gesture < 10 && direction == GestureEnum.SLIP_OUT.code && Math.abs(xMovement) > 10) {
                    gesture = direction * gestureStep + gesture

                    if (action == Actions.tap) {
                        action = Actions.slip
                    }

                    if (action == Actions.long_press) {
                        action = Actions.long_press_slip
                    }
                }

                if (action == Actions.tap) {
                    mApp.playFeedback(false)
                    if (KlickApplication.DOUBLE_TAP_THRESHOLD > 0) {
                        mHandler.sendEmptyMessageDelayed(KlickApplication
                                .MSG_DOUBLE_TAP_TIMEOUT, KlickApplication.DOUBLE_TAP_THRESHOLD.toLong())
                    } else {
                        onAction(gesture)
                        action = null
                    }
                } else if (action == Actions.drag) {
                    hideMoreActionsView()
                    stopDragging()
                } else {
                    onAction(gesture)
                }

                if (gesture != mApp.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS] &&
                        gesture != mApp.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_LAUNCH] &&
                        (gesture != mApp.gestures[KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION] ||
                                loopIndexActiveQuickAction != -1) &&
                        !mHandler.hasMessages(KlickApplication.MSG_DOUBLE_TAP_TIMEOUT)) {
                    setHandleOpacity(KlickApplication.ICON_OPACITY_ACTIVE)
                    mHandler.sendMessageDelayed(getOpacityMsg(KlickApplication.ICON_OPACITY), KlickApplication.TRANSPARENT_BACKGROUND_THRESHOLD.toLong())
                }

                touchStartX = 0f
                touchStartY = 0f

                startToBreath(1, 3000)
                registerSensorEventListener()
            }
        }
        previousRawX = event.rawX
        previousRawY = event.rawY
        //        if (action == Actions.tap || action == Actions.long_press) {
        //            if (rawXYList.size() > 2) {
        //                rawXYList.remove(0);
        //                rawXYList.remove(0);
        //            }
        //        }
        rawXYList.add(0, event.rawY)
        rawXYList.add(0, event.rawX)
        while (rawXYList.size > 100) {
            rawXYList.removeAt(100)
        }
        return true
    }

    private fun adjustMusicVol(event: MotionEvent) {
        movement += event.rawY - rawXYList[1]
        if (Math.abs(movement) > 30) {
            if (movement < 0) {
                movement += 30f
                mApp.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager
                        .FLAG_SHOW_UI)
            } else if (movement > 0) {
                movement -= 30f
                mApp.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager
                        .FLAG_SHOW_UI)
            }
        }
    }

    private fun switchAppQuickAction(event: MotionEvent) {
        movement += event.rawY - rawXYList[1]
        Log.d(TAG, "switchAppQuickAction: " + movement)
        if (Math.abs(movement) > 80) {
            if (movement < 0) {
                movement += 80f
                loopIndexActiveQuickAction = if (loopIndexActiveQuickAction == -1) {
                    activeQuickActions.size - 1
                } else {
                    (loopIndexActiveQuickAction - 1 +
                            activeQuickActions.size + 1) %
                            (activeQuickActions.size + 1)
                }
            } else if (movement > 0) {
                movement -= 80f
                loopIndexActiveQuickAction = if (loopIndexActiveQuickAction == -1) {
                    0
                } else {
                    (loopIndexActiveQuickAction + 1) %
                            (activeQuickActions.size + 1)
                }
            }

            quickActionTipView.text = Html.fromHtml(getQuickActionMsg())
        }
    }

    private fun getQuickActionMsg(): String {
        var msg = "..."
        if (loopIndexActiveQuickAction in 0 until activeQuickActions.size) {
            Log.d(TAG, "QUICK ACTION: " + activeQuickActions[loopIndexActiveQuickAction])
            msg = "<font color=\"red\">" + activeQuickActions[loopIndexActiveQuickAction]+ "</font>"
            if (loopIndexActiveQuickAction > 0) {
                msg = activeQuickActions[loopIndexActiveQuickAction - 1] + "<br/>" + msg
            } else {
                msg = "...<br/>" + msg
            }
            if (loopIndexActiveQuickAction < activeQuickActions.size - 1) {
                msg = msg + "<br/>" + activeQuickActions[loopIndexActiveQuickAction + 1]
            } else {
                msg = msg + "<br/>..."
            }
        } else {
            msg = "...<br/><font color=\"red\">...</font><br/>..."
            if (activeQuickActions.isNotEmpty()) {
                msg =  activeQuickActions.last() + "<br/><font color=\"red\">.." +
                        ".</font><br/>" + activeQuickActions.first()
            }
        }
        return msg
    }

    private fun hideQuickActionTip() {
//        mApp.getmWindowManager()!!.removeView(quickActionTipView)
        quickActionTipView.visibility = View.INVISIBLE
    }

    private fun setHandleIconAsAppIcon(packageName: String) {
        val drawable = mApp.getAppIcon(mApp.mAppsMap[packageName])
        if (drawable != null) {
            mHandle.setImageDrawable(drawable)
        } else {
            mHandle.setImageDrawable(mApp.handleDrawable)
        }
    }

    private fun updateFloatingViewPosition() {
        if (!isAnimating) {
            mWindowParams.x = (screenX - touchStartX).toInt()
            mWindowParams.y = (screenY - touchStartY).toInt()
            mApp.getmWindowManager()!!.updateViewLayout(this, mWindowParams)
        } else {
            aniEndX = (screenX - touchStartX).toInt()
            aniEndY = (screenY - touchStartY).toInt()
        }
    }

    private fun startDragging() {
        action = Actions.drag
        //        hidFromSoftKeyboardDistance = 0;

        aniEndX = (screenX - touchStartX).toInt()
        aniEndY = (screenY - touchStartY).toInt()
        //        aniStartX = mApp.FLOATING_POSITION_X;
        //        aniStartY = mApp.FLOATING_POSITION_Y;
        val animation = ValueAnimator.ofFloat(0f, 1f)
        animation.duration = 100
        animation.interpolator = LinearInterpolator()
        animation.addUpdateListener { animation ->
            mWindowParams.x = aniStartX + ((aniEndX - aniStartX) * animation.animatedValue as Float).toInt()
            mWindowParams.y = aniStartY + ((aniEndY - aniStartY) * animation.animatedValue as Float).toInt()
            mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
        }
        animation.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
                mWindowParams.x = mApp.getAssistHandlePositionX(false)
                mApp.getmWindowManager()!!.updateViewLayout(mAssitHandle, mWindowParams)
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }
        })
        animation.start()
    }

    private fun stopDragging() {
        aniStartX = (screenX - touchStartX).toInt()
        aniStartY = (screenY - touchStartY).toInt()
        aniEndX = aniStartX
        aniEndY = aniStartY

        val size = Rect()
        mApp.getmWindowManager()!!.defaultDisplay.getRectSize(size)

        if (dragDirection != previousDragDirection || dragDirection == KlickApplication.DRAG_FALL_BACK || dragSpeed + previousDragSpeed <= 20.0) {
            if (aniStartX > size.width() / 2) {
                aniEndX = size.width()
            } else {
                aniEndX = 0
            }
        } else {
            if (dragDirection == KlickApplication.DRAG_TO_LEFT)
                aniEndX = 0
            else
                aniEndX = size.width()
        }
        var duration = if (Math.abs(aniEndX - aniStartX) > 100) 100 else Math.abs(aniEndX - aniStartX)
        if (duration < 10) duration = 10
        mWindowParams.y = aniStartY
        Log.d(TAG, "aniStart - aniEnd: $aniStartX, $aniEndX")

        val animation = ValueAnimator.ofInt(aniStartX, aniEndX)
        animation.duration = (duration * 2).toLong()
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.addUpdateListener { animation ->
            mWindowParams.x = animation.animatedValue as Int
            mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
        }
        animation.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
                unregisterSensorEventListener()
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                previousPositionX = currentPositionX
                previousPositionY = currentPositionY
                currentPositionX = aniEndX
                currentPositionY = aniEndY

                if (hidFromSoftKeyboardDistance > 0 && mApp.getScreenRect(true).width() < mApp.getScreenRect(false)
                        .height() && mApp.getScreenRect(false).centerY() - currentPositionY >= 0 && mApp
                        .getScreenRect(false).centerY() - currentPositionY <= KlickApplication
                        .HANDLE_HEIGHT_PX * 3) {
                    hidFromSoftKeyboardDistance += previousPositionY - currentPositionY
                    KlickApplication.HIDE_FROM_SOFT_KEYBOARD_DISTANCE = mApp
                            .getScreenRect(false).centerY() - currentPositionY
                    mApp.sharedPrefs!!.edit().putInt(KlickApplication
                            .SETTING_HIDE_FROM_SOFT_KEYBOARD_DISTANCE, KlickApplication.HIDE_FROM_SOFT_KEYBOARD_DISTANCE).commit()
                }

                if (KlickApplication.AUTO_HIDE_FLOATING_ICON && currentPositionY < KlickApplication.HANDLE_WIDTH_PX) {
                    KlickApplication.FLOATING_POSITION_X = aniEndX
                    this@FloatingView.visibility = View.GONE
                    val intent = Intent()
                    intent.action = KlickApplication.ACTION_HIDE_KLICK
                    mApp.applicationContext.sendBroadcast(intent)
                } else {
                    KlickApplication.FLOATING_POSITION_X = aniEndX
                    KlickApplication.FLOATING_POSITION_Y = aniEndY
                    mWindowParams.x = mApp.getAssistHandlePositionX(false)
                    mApp.getmWindowManager()!!.updateViewLayout(mAssitHandle, mWindowParams)
                    mWindowParams.x = mApp.getFloattingPositionX(false)
                    mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
                    mApp.sharedPrefs!!.edit().putInt(KlickApplication
                            .SETTING_FLOATING_POSITION_X, aniEndX).putInt(KlickApplication.SETTING_FLOATING_POSITION_Y, aniEndY).commit()
                }
                isAnimating = false
                startToBreath(1, 3000)
                registerSensorEventListener()
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }
        })
        animation.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mHandler.removeMessages(KlickApplication.MSG_TRANSPARENT_BACKGROUND)
        mHandler.removeMessages(KlickApplication.MSG_LONG_PRESS_TRIGGER)
        mHandler.removeMessages(KlickApplication.MSG_HIDE_MORE_ACTION_VIEW)
        unregisterSensorEventListener()
        mMoreActionsView = null
        System.gc()
    }

    fun refresh() {
        mHandle.setImageDrawable(mApp.handleDrawable)
        mHandle.background = mApp.handleBgDrawable
        this.setHandleOpacity(KlickApplication.ICON_OPACITY)
        this.setIconSizeInDip(KlickApplication.HANDLE_WIDTH_DP, KlickApplication.HANDLE_WIDTH_DP)
        mWindowParams.x = currentPositionX
        mWindowParams.y = currentPositionY
        mApp.getmWindowManager()!!.updateViewLayout(this, mWindowParams)
    }

    fun onAction(gesture: Long) {
        var noGestureSet = true
        var actSeq = -1
        for (i in 0 until mApp.gestures.size) {
            if (mApp.gestures[i] != 0L) {
                noGestureSet = false
            }
            if (gesture == mApp.gestures[i]) {
                actSeq = i
                break
            }
        }

        when (actSeq) {
            KlickApplication.SEQ_NO_HOME // Home
            -> if (Utils.getKlickAccessServiceInstance(context) != null) {
                Utils.getKlickAccessServiceInstance(context)!!.performGlobalAction(AccessibilityService
                        .GLOBAL_ACTION_HOME)
            }
            KlickApplication.SEQ_NO_BACK // Back
            -> if (Utils.getKlickAccessServiceInstance(context) != null) {
                Utils.getKlickAccessServiceInstance(context)!!.performGlobalAction(AccessibilityService
                        .GLOBAL_ACTION_BACK)
            }
        //            case KlickApplication.SEQ_NO_MENU: // Menu
        //                KeyEventHandler.getInstance(mApp.getApplicationContext()).inputKeyEvent(KeyEvent.KEYCODE_MENU);
        //                break;
            KlickApplication.SEQ_NO_APP_SWITCH // APP Switch
            ->
                //                if (!KeyEventHandler.getInstance(mApp.getApplicationContext()).inputKeyEvent(KeyEvent
                // .KEYCODE_APP_SWITCH) &&
                //                        getKlickAccessServiceInstance() != null) {
                //                    getKlickAccessServiceInstance().performGlobalAction(AccessibilityService
                // .GLOBAL_ACTION_RECENTS);
                //                }
                showRecentActivity()
            KlickApplication.SEQ_NO_APP_SWITCH_FORWARD -> {
                val pkgForward = KlickAccessibilityService.switchAppBackward()
                Utils.launchApp(mApp, mApp.mAppsMap[pkgForward])
                Log.d(TAG, "SEQ_NO_APP_SWITCH_FORWARD: " + pkgForward)
                mHandle.setImageDrawable(mApp.handleDrawable)
            }
            KlickApplication.SEQ_NO_APP_SWITCH_BACKWARD -> {
                val pkgBackward = KlickAccessibilityService.switchAppBackward()
                Utils.launchApp(mApp, mApp.mAppsMap[pkgBackward])
                Log.d(TAG, "SEQ_NO_APP_SWITCH_BACKWARD: " + pkgBackward)
                mHandle.setImageDrawable(mApp.handleDrawable)
            }
            KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS // Show More Actions
            -> {
                showMoreActionsView(1)
                Log.d(TAG, "SEQ_NO_SHOW_MORE_ACTIONS")
            }
            KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_ACTION // Show More Actions
            -> {
                if (loopIndexActiveQuickAction == -1) {
                    showMoreActionsView(0)
                } else if (loopIndexActiveQuickAction in 0 until activeQuickActions.size) {
                    KlickAccessibilityService.sharedInstance?.increaseClickCounter(KlickAccessibilityService.currentRootInActiveWindow?.packageName.toString(),
                            activeQuickActions[loopIndexActiveQuickAction])
                    KlickAccessibilityService.sharedInstance?.performClickOnViewWithText(activeQuickActions[loopIndexActiveQuickAction])
                }
                hideQuickActionTip()
                Log.d(TAG, "SEQ_NO_SHOW_MORE_ACTIONS")
            }
            KlickApplication.SEQ_NO_SHOW_MORE_ACTIONS_QUICK_LAUNCH // Show More Actions
            -> {
                showMoreActionsView(2)
                Log.d(TAG, "SEQ_NO_SHOW_MORE_ACTIONS")
            }
            KlickApplication.SEQ_NO_EXPAND_STATUS_BAR // Expend Status Bar
            -> if (Utils.getKlickAccessServiceInstance(context) != null) {
                Utils.getKlickAccessServiceInstance(context)!!.performGlobalAction(AccessibilityService
                        .GLOBAL_ACTION_NOTIFICATIONS)
            }
            KlickApplication.SEQ_NO_LOCK_SCREEN // Lock Screen
            -> mApp.applicationContext.sendBroadcast(Intent(KlickApplication.ACTION_LOCK_SCREEN))
            KlickApplication.SEQ_NO_OPEN_CAMERA // Open Camera
            -> mMoreActionsView!!.openCamera()
            KlickApplication.SEQ_NO_OPEN_DICT -> {
                val intent = Intent()
                intent.action = KlickApplication.ACTION_LOOKUP_WORD
                mApp.applicationContext.sendBroadcast(intent)
            }
            KlickApplication.SEQ_NO_ADJUST_MUSIC_VOL -> {
            }
            else -> if (noGestureSet) {
                Toast.makeText(mApp, R.string.no_gesture_set, Toast.LENGTH_LONG).show()
                val prefsIntent = Intent(mApp.applicationContext, PrefsActivity::class.java)
                prefsIntent.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        or Intent.FLAG_ACTIVITY_NEW_TASK)
                mApp.applicationContext.startActivity(prefsIntent)
            }
        }//                Utils.reflectionInvoke(mApp.getApplicationContext().getSystemService("power"),
        //                        "android.os.PowerManager", "goToSleep", new Class[] {long.class}, SystemClock.uptimeMillis());
    }

    fun showRecentActivity() {
        Log.d(TAG, "Show recent activity")
        val localIntent = Intent("com.android.systemui.recents.TOGGLE_RECENTS")
        mApp.applicationContext.sendBroadcast(localIntent)
    }

    fun unhide() {
        var duration = if (Math.abs(previousPositionY - currentPositionY) > 100)
            100
        else
            Math.abs(previousPositionY - currentPositionY)
        if (duration < 10) duration = 10

        previousPositionX = mApp.getFloattingPositionX(false)
        mWindowParams.x = previousPositionX
        mWindowParams.y = currentPositionY
        this@FloatingView.visibility = View.VISIBLE

        val animation = ValueAnimator.ofInt(currentPositionY, previousPositionY)
        animation.duration = (duration * 2).toLong()
        animation.startDelay = 500
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.addUpdateListener { animation ->
            mWindowParams.y = animation.animatedValue as Int
            mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
        }
        animation.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
                unregisterSensorEventListener()
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                currentPositionX = previousPositionX
                currentPositionY = previousPositionY
                isAnimating = false
                startToBreath(1, 3000)
                registerSensorEventListener()

                mWindowParams.x = mApp.getAssistHandlePositionX(false)
                mApp.getmWindowManager()!!.updateViewLayout(mAssitHandle, mWindowParams)
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }
        })
        animation.start()
    }

    fun hideFromSoftKeyboard(hide: Boolean) {
        Log.d(TAG, "hideFromSoftKeyboard: " + hide)

        var fromY = 0
        var toY = 0
        var duration = 0

        if (hide) {
            if (mApp.getScreenRect(true).width() > mApp.getScreenRect(false).height()) {
                fromY = currentPositionY
                toY = 0
            } else if (currentPositionY > mApp.getScreenRect(false).centerY() - KlickApplication.HIDE_FROM_SOFT_KEYBOARD_DISTANCE) {
                fromY = currentPositionY
                toY = mApp.getScreenRect(true).centerY() - KlickApplication.HIDE_FROM_SOFT_KEYBOARD_DISTANCE
            } else {
                hidFromSoftKeyboardDistance = 0
                return
            }
            hidFromSoftKeyboardDistance = fromY - toY
            duration = if (hidFromSoftKeyboardDistance > 100) 100 else hidFromSoftKeyboardDistance
        } else {
            if (hidFromSoftKeyboardDistance > 0) {
                fromY = currentPositionY
                toY = fromY + hidFromSoftKeyboardDistance
                duration = if (hidFromSoftKeyboardDistance > 100) 100 else hidFromSoftKeyboardDistance
                hidFromSoftKeyboardDistance = 0
            } else {
                return
            }
        }

        if (duration < 10) duration = 10

        mWindowParams.x = mApp.getFloattingPositionX(false)
        mWindowParams.y = fromY

        val animation = ValueAnimator.ofInt(fromY, toY)
        animation.duration = (duration * 2).toLong()
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.addUpdateListener { animation ->
            mWindowParams.y = animation.animatedValue as Int
            mApp.getmWindowManager()!!.updateViewLayout(this@FloatingView, mWindowParams)
        }
        animation.addListener(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
                unregisterSensorEventListener()
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                currentPositionX = mApp.getFloattingPositionX(false)
                currentPositionY = (animation as ValueAnimator).animatedValue as Int
                isAnimating = false
                startToBreath(1, 3000)
                registerSensorEventListener()

                mWindowParams.x = mApp.getAssistHandlePositionX(false)
                mApp.getmWindowManager()!!.updateViewLayout(mAssitHandle, mWindowParams)
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                startToBreath(1, 3000)
            }
        })
//        animation.startDelay = 500
        animation.start()
    }

    fun addToWindowManager() {
        mWindowParams.x = mApp.getFloattingPositionX(false)
        mWindowParams.y = currentPositionY
        mApp.getmWindowManager()!!.addView(this, mWindowParams)

        setHandleOpacity(KlickApplication.ICON_OPACITY_ACTIVE)
        mHandler.sendMessageDelayed(getOpacityMsg(KlickApplication.ICON_OPACITY), KlickApplication.TRANSPARENT_BACKGROUND_THRESHOLD.toLong())

        addAssitHandleToWindowManager()
        addMoreActionsViewToWindowManager()

        startToBreath(1, 3000)
        registerSensorEventListener()
    }

    fun addAssitHandleToWindowManager() {
        mWindowParams.x = mApp.getAssistHandlePositionX(false)
        mWindowParams.y = currentPositionY
        mApp.getmWindowManager()!!.addView(mAssitHandle, mWindowParams)
    }

    fun addMoreActionsViewToWindowManager() {
        mMoreActionsView = MoreActionsView(mApp, this)
        mMoreActionsView!!.setSize(mApp.getScreenRect(true).width(), mApp.getScreenRect(false).height())
        mWindowParams.x = 0
        mWindowParams.y = 0
        mApp.getmWindowManager()!!.addView(mMoreActionsView, mWindowParams)
        mMoreActionsView!!.init(0)
        mMoreActionsView!!.scrollToDefaultScreenImmediate()
        hideMoreActionsView()
    }

    fun showMoreActionsView(showPageNumber: Int) {
        mHandler.removeMessages(KlickApplication.MSG_TRANSPARENT_BACKGROUND)

        val startDelay: Long = 0
        //        if (mMoreActionsView.getWidth() != mApp.getScreenRect(true).width()) {
        //            removeMoreActionsViewFromWindowManager();
        //            addMoreActionsViewToWindowManager();
        //            startDelay = 50;
        //        }

        mApp.asyncLoadIcon()
        mMoreActionsView!!.init(showPageNumber)
        mTransAnimation.setIntValues(currHandleOpacity, 0)
        mTransAnimation.duration = 50
        mMoreActionsView!!.setAniVisibility(View.VISIBLE, startDelay, mTransAnimation)
    }

    fun hideMoreActionsView() {
        if (mMoreActionsView == null || mMoreActionsView!!.visibility != View.VISIBLE) return
        mTransAnimation.setIntValues(0, KlickApplication.ICON_OPACITY)
        mTransAnimation.duration = 50
        mMoreActionsView!!.setAniVisibility(View.INVISIBLE, 0, mTransAnimation)
    }

    fun removeFromWindowManager() {
        unregisterSensorEventListener()
        removeMoreActionsViewFromWindowManager()
        mApp.getmWindowManager()!!.removeView(this)
        mApp.getmWindowManager()!!.removeView(mAssitHandle)
    }

    fun removeMoreActionsViewFromWindowManager() {
        if (mMoreActionsView != null) mApp.getmWindowManager()!!.removeView(mMoreActionsView)
    }

    fun unregisterSensorEventListener() {
        mApp.getmSensorManager()!!.unregisterListener(mSensorEventListener)
    }

    fun registerSensorEventListener() {
        posList.clear()
        mApp.getmSensorManager()!!.registerListener(mSensorEventListener, mApp.getmSensorManager()!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun setHandleOpacity(opacity: Int) {
        currHandleOpacity = opacity
        mHandle.background.alpha = opacity
        mHandle.drawable.alpha = opacity
    }

    private fun detectSoftKeyboardShowOrNot(): Boolean {
        isAnimating = true
        val runningServiceInfoList = mApp.getmActivityManager()!!
                .getRunningServices(Integer.MAX_VALUE)
        for (runningServiceInfo in runningServiceInfoList) {
            if ("android".equals(runningServiceInfo.clientPackage, ignoreCase = true)) {
                //                Log.d("RRRRRRRRRRRR", runningServiceInfo.clientPackage + " - " + runningServiceInfo.clientCount + "
                // - " + runningServiceInfo.service.getClassName());
                val className = runningServiceInfo.service.className
                val classNameLowerCase = className.toLowerCase()
                if (classNameLowerCase.contains("input") || classNameLowerCase.contains("ime") || classNameLowerCase
                        .contains("keyboard")) {
                    if (imeClientCountMap.containsKey(className)) {
                        if (imeClientCountMap[className]!! > runningServiceInfo.clientCount) {
                            imeClientCountMap.put(className, runningServiceInfo.clientCount)
                            // restore
                            if (hidFromSoftKeyboardDistance > 0) {
                                hideFromSoftKeyboard(false)
                                return true
                            }
                        } else if (imeClientCountMap[className]!! < runningServiceInfo
                                .clientCount) {
                            // hide from soft keyboard
                            imeClientCountMap.put(className, runningServiceInfo.clientCount)
                            hideFromSoftKeyboard(true)
                            return true
                        }
                        break
                    } else {
                        imeClientCountMap.put(className, runningServiceInfo.clientCount)
                        if (runningServiceInfo.clientCount > 1) {
                            hideFromSoftKeyboard(true)
                            return true
                        }
                    }
                }
            }
        }
        isAnimating = false
        return false
    }

    companion object {
        private val TAG = "FloatingView"

        private var screenX = 0f
        private var screenY = 200f
        private var touchState = MotionEvent.ACTION_UP
        private var touchStartX = 0f
        private var touchStartY = 0f

        var aniStartX: Int = 0
        var aniStartY: Int = 0
        var aniEndX: Int = 0
        var aniEndY: Int = 0

        fun getOpacityMsg(toOpacity: Int): Message {
            val msg = Message()
            val bundle = Bundle()
            bundle.putInt("OPACITY", toOpacity)
            msg.data = bundle
            msg.what = KlickApplication.MSG_TRANSPARENT_BACKGROUND
            return msg
        }
    }
}
