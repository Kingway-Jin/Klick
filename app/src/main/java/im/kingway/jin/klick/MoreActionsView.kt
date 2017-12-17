package im.kingway.jin.klick

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.Parameters
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.*
import android.view.GestureDetector.OnGestureListener
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.widget.*
import java.util.*

class MoreActionsView(private val mApp: KlickApplication, private var mFloatingView: FloatingView?) : FrameLayout(mApp.applicationContext), OnGestureListener {
    private val mBackgroundView: LinearLayout
    var mViewFlipper: MyWorkspaceView
    private var mCamera: Camera? = null
    private var mCameraDevice: CameraDevice? = null
    private var mSession: CameraCaptureSession? = null
    private var mBuilder: CaptureRequest.Builder? = null
    private var mCameraManager: CameraManager? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mSurface: Surface? = null
    private val quickActionAppNameView: TextView
    private val quickActionListView: ListView

    var isFlashlightOn = false
        private set
    private var cameraComponent: ComponentName? = null
    private val mAppList = ArrayList<AppItem>()

    private val appViewIDs = intArrayOf(R.id.app1, R.id.app2, R.id.app3, R.id.app4, R.id.app5, R.id.app6, R.id.app7, R.id.app8, R.id.app9)

    private val detector: GestureDetector
    private var preMotionEventX: Float = 0.toFloat()
    private var preMotionEventY: Float = 0.toFloat()
    private var direction = 0f
    private var movement = 0f

    private val mView: View
    //    private Animation mAnimationFadeIn;
    //    private Animation mAnimationFadeOut;
    private val mAnimationFadeOutListener: MyAnimationListener

    private var isShowFirstTime = true

    private val pageInitSet = HashSet<Int>()

    private val klickAccessServiceInstance: KlickAccessibilityService?
        get() {
            val kas = KlickAccessibilityService.sharedInstance
            if (kas == null) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                mApp.applicationContext.startActivity(intent)
                Toast.makeText(mApp.applicationContext, R.string.enable_klick_accessibility_service, Toast
                        .LENGTH_LONG).show()
            }
            return kas
        }

    init {

        detector = GestureDetector(this)

        val layoutparams = FrameLayout.LayoutParams(-1, -1)
        layoutParams = layoutparams

        mView = View.inflate(mApp.applicationContext, R.layout.more_action_view, null)
        mBackgroundView = mView.findViewById(R.id.other_handle) as LinearLayout
        quickActionListView = mView.findViewById(R.id.quick_action_list) as ListView
        quickActionListView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            mFloatingView!!.mHandler.removeMessages(KlickApplication
                    .MSG_HIDE_MORE_ACTION_VIEW)
            val adapter = quickActionListView.adapter as QuickActionListAdapter
            val quickActionItem = adapter.getItem(position)
            adapter.increaseClickCount(position)
            quickActionItem.nodeInfo!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            mFloatingView!!.mHandler.sendEmptyMessageDelayed(KlickApplication.MSG_HIDE_MORE_ACTION_VIEW, KlickApplication.MORE_ACTION_VIEW_HIDE_DELAY.toLong())
        }
        quickActionListView.onItemLongClickListener = AdapterView.OnItemLongClickListener { parent, view, position, id ->
            mFloatingView!!.mHandler.removeMessages(KlickApplication
                    .MSG_HIDE_MORE_ACTION_VIEW)
            val adapter = quickActionListView.adapter as QuickActionListAdapter
            val quickActionItem = adapter.getItem(position)
            adapter.increaseClickCount(position)
            quickActionItem.nodeInfo!!.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            mFloatingView!!.mHandler.sendEmptyMessageDelayed(KlickApplication.MSG_HIDE_MORE_ACTION_VIEW, KlickApplication.MORE_ACTION_VIEW_HIDE_DELAY.toLong())
            true
        }
        quickActionAppNameView = mView.findViewById(R.id.quick_action_app_name) as TextView
        quickActionAppNameView.setOnClickListener {
            if (pageInitSet.contains(0)) {
                (quickActionListView.adapter as QuickActionListAdapter).toggleOnlyShowActive()
            }
        }

        val doActionListener = DoActionListener()

        var iv = mView.findViewById(R.id.lockimage) as ImageView
        iv.setOnClickListener(doActionListener)

        iv = mView.findViewById(R.id.volimage) as ImageView
        iv.setOnClickListener {
            mApp.playFeedback(false)
            val mode = mApp.getmAudioManager()!!.ringerMode
            if (mode == AudioManager.RINGER_MODE_NORMAL) {
                mApp.getmAudioManager()!!.ringerMode = AudioManager.RINGER_MODE_SILENT
            } else if (mode == AudioManager.RINGER_MODE_SILENT) {
                mApp.getmAudioManager()!!.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else if (mode == AudioManager.RINGER_MODE_VIBRATE) {
                mApp.getmAudioManager()!!.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            updateVolImage()
        }

        iv = mView.findViewById(R.id.cameraimage) as ImageView
        iv.setOnClickListener(doActionListener)

        iv = mView.findViewById(R.id.homeimage) as ImageView
        iv.setOnClickListener(doActionListener)

        iv = mView.findViewById(R.id.backimage) as ImageView
        iv.setOnClickListener {
            mApp.playFeedback(false)
            mFloatingView!!.mHandler.removeMessages(KlickApplication
                    .MSG_HIDE_MORE_ACTION_VIEW)
            if (klickAccessServiceInstance != null)
                klickAccessServiceInstance!!.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            mFloatingView!!.mHandler.sendEmptyMessageDelayed(KlickApplication.MSG_HIDE_MORE_ACTION_VIEW, KlickApplication.MORE_ACTION_VIEW_HIDE_DELAY.toLong())
        }

        //        iv = (ImageView)mView.findViewById(R.id.menuimage);
        //        iv.setOnClickListener(new OnClickListener() {
        //            @Override
        //            public void onClick(View v) {
        //                mAnimationFadeOutListener.setAction(v.getId(), null);
        //                mFloatingView.hideMoreActionsView();
        //            }
        //        });

        iv = mView.findViewById(R.id.settingimage) as ImageView
        iv.setOnClickListener(doActionListener)
        iv.setOnLongClickListener(doActionListener)

        iv = mView.findViewById(R.id.appswitchimage) as ImageView
        iv.setOnClickListener(doActionListener)

        iv = mView.findViewById(R.id.expendstatusbarimage) as ImageView
        iv.setOnClickListener(doActionListener)

        iv = mView.findViewById(R.id.flashlightimage) as ImageView
        iv.setOnClickListener { switchFlashLight() }

        val pm = mApp.applicationContext.packageManager
        val it = Intent(Intent.ACTION_MAIN)
        it.addCategory(Intent.CATEGORY_LAUNCHER)
        val ra = pm.queryIntentActivities(it, 0)
        val camera = mApp.applicationContext.getString(R.string.camera)
        for (i in ra.indices) {
            val ai = ra[i].activityInfo
            if (ai.loadLabel(pm).toString().equals(camera, ignoreCase = true)) {
                cameraComponent = ComponentName(ai.applicationInfo.packageName, ai.name)
                break
            }
        }

        addView(mBackgroundView)
        updateVolImage()
        mAnimationFadeOutListener = MyAnimationListener()
        mViewFlipper = findViewById(R.id.view_flipper) as MyWorkspaceView
        mViewFlipper.workspaceChange = object : WorkspaceChangeInterface { override fun onWrokSpaceChange
                (which: Int) { refreshSibling(which) }}
        init(1)
    }

    fun updateVolImage() {
        val iv = this.findViewById(R.id.volimage) as ImageView
        val mode = mApp.getmAudioManager()!!.ringerMode
        if (mode == AudioManager.RINGER_MODE_NORMAL) {
            iv.setImageResource(R.drawable.vol)
        } else if (mode == AudioManager.RINGER_MODE_SILENT) {
            iv.setImageResource(R.drawable.vol_muted)
        } else if (mode == AudioManager.RINGER_MODE_VIBRATE) {
            iv.setImageResource(R.drawable.vibrate)
        }
    }

    fun setSize(width: Int, height: Int) {
        val view = this.findViewById(R.id.other_handle) as View
        val lp = view.layoutParams
        lp.width = width
        lp.height = height
        view.layoutParams = lp
    }

    internal inner class AppOnClickListener(private val position: Int) : View.OnClickListener, View.OnLongClickListener {

        override fun onLongClick(view: View): Boolean {
            mApp.playFeedback(true)
            val rmItem = mAppList.removeAt(position)
            rmItem.isSelected = false
            rmItem.isExcluded = true
            mApp.mExcludePackage.add(rmItem.component.packageName)
            mApp.mSelectedPackage.remove(rmItem.component.packageName)

            var excPkg = mApp.mExcludePackage.toString()
            excPkg = excPkg.substring(1, excPkg.length - 1)
            mApp.sharedPrefs!!.edit().putString(KlickApplication.EXCLUDE_PACKAGES, excPkg).commit()

            val delta = Utils.dip2px(mApp, 80f)
            val animatorSet = AnimatorSet()
            val asb = animatorSet.play(ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).setDuration(100))
            val currScreen = mViewFlipper.getChildAt(mViewFlipper.currentScreen)
            for (i in position % 9 + 1..8) {
                var toXDelta = -1f * delta
                var toYDelta = 0f
                if (i == 3 || i == 6) {
                    toXDelta = 2f * delta
                    toYDelta = -1f * delta
                }
                val pvhTranslationX = PropertyValuesHolder.ofFloat("translationX", 0f, toXDelta)
                val pvhTranslationY = PropertyValuesHolder.ofFloat("translationY", 0f, toYDelta)
                asb.with(ObjectAnimator.ofPropertyValuesHolder(currScreen.findViewById(this@MoreActionsView
                        .appViewIDs[i]), pvhTranslationX, pvhTranslationY).setDuration(300))
            }
            animatorSet.addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    this@MoreActionsView.init(2)
                    for (i in FIST_APP_PAGE_INDEX until mViewFlipper.childCount) {
                        refreshQuickLaunchPage(i)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}
            })
            animatorSet.start()

            return true
        }

        override fun onClick(view: View) {
            mApp.playFeedback(false)
            mAnimationFadeOutListener.setAction(0, position)
            mFloatingView!!.hideMoreActionsView()
        }
    }

    private fun showAppQuickAction() {
        if (pageInitSet.contains(0)) {
            return
        }
        try {
            val kas = KlickAccessibilityService.sharedInstance
            if (kas != null) {
                quickActionAppNameView.text = Utils.getAppNameByPackageName(mApp, kas.rootInActiveWindow?.packageName.toString())
                quickActionListView.adapter = kas.allClickableTextAsListAdapter
                pageInitSet.add(0)
            }
            Log.d(TAG, "showAppQuickAction")
        } catch (e: Exception) {
            Log.e(TAG, "", e)
        }

    }

    fun clearQuickAction() {
        pageInitSet.remove(0)
    }

    fun init(showPageNumber: Int) {
        pageInitSet.clear()
        pageInitSet.add(1)
        DEFAULT_PAGE_NUMBER = showPageNumber

        if (showPageNumber == 0) {
            showAppQuickAction()
        }

        showAppQuickLaunch()
        if (showPageNumber == 2) {
            refreshQuickLaunchPage(FIST_APP_PAGE_INDEX)
        }
    }

    private fun refreshSibling(pageNumber: Int) {
        val pre = pageNumber - 1
        val next = pageNumber + 1
        Log.d(TAG, pre.toString() + " - " + pageNumber + " - " + next)

        if (pre == 0) {
            showAppQuickAction()
        }

        if (pre >= FIST_APP_PAGE_INDEX) {
            refreshQuickLaunchPage(pre)
        }

        if (next >= FIST_APP_PAGE_INDEX && next < mViewFlipper.childCount) {
            refreshQuickLaunchPage(next)
        }
    }

    private fun showAppQuickLaunch() {
        mAppList.clear()
        for (item in mApp.mAppsMap.values) {
            item.isInRectentTaskList = false
        }

        val recentTaskPkgs = HashSet<String>()
        val recentApps = ArrayList<AppItem>()
        if (KlickApplication.MODE_INCLUDE_RECENT_TASK == 1 || KlickApplication
                .MODE_INCLUDE_RECENT_TASK == 2) {
            val recentAppPackageName = KlickAccessibilityService
                    .recentAppPackageName
            var i = 0
            var j = 0
            while (j < 18 && i < recentAppPackageName.size) {
                val pkg = recentAppPackageName[i]
                if (!mApp.mExcludePackage.contains(pkg) && !recentTaskPkgs.contains(pkg) && mApp.mAppsMap.containsKey(pkg)) {
                    val item = mApp.mAppsMap[pkg] as AppItem
                    item.isInRectentTaskList = true
                    recentApps.add(item)
                    recentTaskPkgs.add(pkg)
                    j++
                }
                i++
            }
        }

        for (pkg in mApp.mSelectedPackage) {
            if (mApp.mAppsMap[pkg] != null) {
                if (!recentTaskPkgs.contains(pkg)) {
                    mAppList.add(mApp.mAppsMap[pkg] as AppItem)
                }
            } else {
                mApp.mAppsMap.remove(pkg)
            }
        }

        val compApp = Comparator<AppItem> { lhs, rhs ->
            if (lhs.clickCount > rhs.clickCount) {
                -1
            } else if (lhs.clickCount < rhs.clickCount) {
                1
            } else {
                lhs.name!!.compareTo(rhs.name!!)
            }
        }

        if (KlickApplication.MODE_INCLUDE_RECENT_TASK == 1) {
            if (KlickApplication.REORDER_APPS) Collections.sort(mAppList, compApp)
            mAppList.addAll(0, recentApps)
        } else if (KlickApplication.MODE_INCLUDE_RECENT_TASK == 2) {
            mAppList.addAll(0, recentApps)
            if (KlickApplication.REORDER_APPS) Collections.sort(mAppList, compApp)
        } else {
            if (KlickApplication.REORDER_APPS) Collections.sort(mAppList, compApp)
        }

        while (mViewFlipper.childCount > FIST_APP_PAGE_INDEX) {
            val ll = mViewFlipper.getChildAt(FIST_APP_PAGE_INDEX) as LinearLayout
            for (i in appViewIDs.indices) {
                (ll.findViewById(appViewIDs[i]) as ImageView).setImageResource(0)
                ll.findViewById(appViewIDs[i]).setBackgroundResource(0)
                ll.findViewById(appViewIDs[i]).setOnClickListener(null)
                ll.findViewById(appViewIDs[i]).setOnLongClickListener(null)
            }
            mViewFlipper.removeViewAt(FIST_APP_PAGE_INDEX)
        }

        val pn = (mAppList.size + 1) / appViewIDs.size + if ((mAppList.size + 1) % appViewIDs.size != 0) 1 else 0
        for (i in mViewFlipper.childCount - 1 downTo 2) {
            if (i > pn + 1) {
                mViewFlipper.removeViewAt(i)
            }
        }
        for (i in mViewFlipper.childCount - 2 until pn) {
            mViewFlipper.addView(View.inflate(mApp.applicationContext, R.layout.app_view, null) as LinearLayout)
        }

        refreshQuickLaunchPage(2)
    }

    fun refreshQuickLaunchPage(pageNumber: Int) {
        if (pageInitSet.contains(pageNumber)) {
            return
        }

        pageInitSet.add(pageNumber)

        var i = 0
        val j = (pageNumber - FIST_APP_PAGE_INDEX) * appViewIDs.size
        val appView = mViewFlipper.getChildAt(pageNumber) as LinearLayout
        while (i < appViewIDs.size && i + j < mAppList.size) {
            val appItem = mAppList[i + j]
            Log.d(TAG, (i + j).toString() + " - " + appItem.key)
            val appItemView = appView.findViewById(appViewIDs[i]) as ImageView
            appItemView.visibility = View.VISIBLE
            try {
                appItemView.setImageDrawable(mApp.getAppIcon(appItem))
                if (appItem.isInRectentTaskList) {
                    appItemView.setBackgroundResource(R.drawable.selector_recent_task_bg)
                } else {
                    appItemView.setBackgroundResource(R.drawable.selector_more_action_bg1)
                }
                val clickListener = AppOnClickListener(i + j)
                appItemView.setOnClickListener(clickListener)
                appItemView.setOnLongClickListener(clickListener)
            } catch (e: Exception) {
                Log.d(TAG, "APK not found: " + e.message)
            }

            i++
        }

        if (i < appViewIDs.size) {
            val appItemView = appView.findViewById(appViewIDs[i]) as ImageView
            appItemView.visibility = View.VISIBLE
            appItemView.setImageResource(R.drawable.add)
            appItemView.setBackgroundResource(R.drawable.selector_more_action_bg1)

            appItemView.setOnClickListener {
                mApp.playFeedback(false)
                mAnimationFadeOutListener.setAction(R.drawable.add, "onClick")
                mFloatingView!!.hideMoreActionsView()
            }
            appItemView.setOnLongClickListener {
                mApp.playFeedback(true)
                mAnimationFadeOutListener.setAction(R.drawable.add, "onLongClick")
                mFloatingView!!.hideMoreActionsView()
                true
            }

            i++
            while (i < appViewIDs.size) {
                appView.findViewById(appViewIDs[i]).visibility = View.INVISIBLE
                i++
            }
        }
    }

    private fun clearApps() {
        for (j in FIST_APP_PAGE_INDEX until mViewFlipper.childCount) {
            val ll = mViewFlipper.getChildAt(j) as LinearLayout
            for (i in appViewIDs.indices) {
                (ll.findViewById(appViewIDs[i]) as ImageView).setImageResource(0)
                ll.findViewById(appViewIDs[i]).setBackgroundResource(0)
                ll.findViewById(appViewIDs[i]).setOnClickListener(null)
                ll.findViewById(appViewIDs[i]).setOnLongClickListener(null)
            }
        }
        mApp.clearIcons(mAppList)
        mAppList.clear()
    }

    fun scrollToDefaultScreenImmediate() {
        mViewFlipper.scrollToScreenImmediate(DEFAULT_PAGE_NUMBER)
    }

    override fun onDown(arg0: MotionEvent): Boolean {
        return false
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        return false
    }

    override fun onLongPress(arg0: MotionEvent) {
        mApp.playFeedback(true)
        mFloatingView!!.hideMoreActionsView()
    }

    override fun onScroll(arg0: MotionEvent, arg1: MotionEvent, arg2: Float, arg3: Float): Boolean {
        return false
    }

    override fun onShowPress(arg0: MotionEvent) {}

    override fun onSingleTapUp(arg0: MotionEvent): Boolean {
        mApp.playFeedback(false)
        mFloatingView!!.hideMoreActionsView()
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                preMotionEventX = event.x
                preMotionEventY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (preMotionEventX < this.width / 2 && event.x < this.width / 2) {
                    var stream = AudioManager.STREAM_MUSIC
                    (this.findViewById(R.id.label_vol_bar) as TextView).setText(R.string.label_music_vol_ctrl)
                    if (mApp.getFloattingPositionX(false) != 0) {
                        stream = AudioManager.STREAM_SYSTEM
                        (this.findViewById(R.id.label_vol_bar) as TextView).setText(R.string.label_sys_vol_ctrl)
                    }

                    if (direction > 0 && event.y - preMotionEventY > 0) {
                        movement += Math.abs(event.y - preMotionEventY)
                    } else if (direction < 0 && event.y - preMotionEventY < 0) {
                        movement += Math.abs(event.y - preMotionEventY)
                    } else {
                        direction = event.y - preMotionEventY
                        movement = 0f
                    }
                    if (movement > 20) {
                        movement -= 20f
                        if (this.findViewById(R.id.vol_bar).isShown) {
                            if (direction < 0) {
                                mApp.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                            } else if (direction > 0) {
                                mApp.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                            }
                        } else {
                            this.findViewById(R.id.vol_bar).visibility = View.VISIBLE
                        }
                        this.findViewById(R.id.vol_bar).visibility = View.VISIBLE
                        val maxVolume = mApp.getmAudioManager()!!.getStreamMaxVolume(stream)
                        val curVolume = mApp.getmAudioManager()!!.getStreamVolume(stream)
                        (this.findViewById(R.id.vol_progress_bar) as ProgressBar).max = maxVolume
                        (this.findViewById(R.id.vol_progress_bar) as ProgressBar).progress = curVolume
                    }
                } else if (preMotionEventX > this.width / 2 && event.x > this.width / 2) {
                    var stream = AudioManager.STREAM_SYSTEM
                    (this.findViewById(R.id.label_vol_bar) as TextView).setText(R.string.label_sys_vol_ctrl)
                    if (mApp.getFloattingPositionX(false) != 0) {
                        stream = AudioManager.STREAM_MUSIC
                        (this.findViewById(R.id.label_vol_bar) as TextView).setText(R.string.label_music_vol_ctrl)
                    }

                    if (direction > 0 && event.y - preMotionEventY > 0) {
                        movement += Math.abs(event.y - preMotionEventY)
                    } else if (direction < 0 && event.y - preMotionEventY < 0) {
                        movement += Math.abs(event.y - preMotionEventY)
                    } else {
                        direction = event.y - preMotionEventY
                        movement = 0f
                    }
                    if (movement > 20) {
                        movement -= 20f
                        if (this.findViewById(R.id.vol_bar).isShown) {
                            if (direction < 0) {
                                mApp.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                            } else if (direction > 0) {
                                mApp.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                            }
                        } else {
                            this.findViewById(R.id.vol_bar).visibility = View.VISIBLE
                        }
                        val maxVolume = mApp.getmAudioManager()!!.getStreamMaxVolume(stream)
                        val curVolume = mApp.getmAudioManager()!!.getStreamVolume(stream)
                        (this.findViewById(R.id.vol_progress_bar) as ProgressBar).max = maxVolume
                        (this.findViewById(R.id.vol_progress_bar) as ProgressBar).progress = curVolume
                    }
                } else {
                    direction = 0f
                    movement = 0f
                }
                Log.i(TAG, "" + direction + " - " + movement + ": " + event.y + " - " + preMotionEventY)
                preMotionEventX = event.x
                preMotionEventY = event.y
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> this.findViewById(R.id.vol_bar).visibility = View.INVISIBLE
            else -> {
            }
        }
        return this.detector.onTouchEvent(event)
    }

    fun setAniVisibility(visibility: Int, startDelay: Long, animator: Animator) {
        val scale = KlickApplication.HANDLE_HEIGHT_DP / 260f
        val duration = 150
        val rect = mApp.getScreenRect(true)
        val x = rect.centerX()
        var y = (Math.max(rect.height(), rect.width()) - Utils.dip2px(mApp, 356f)) / 2 + Utils.dip2px(mApp, 226f)
        if (rect.width() > rect.height()) {
            y -= Math.abs(rect.height() - rect.width()) / 2
        }

        val loc = Utils.getLocation(mFloatingView!!)
        var fx = loc[0]
        var fy = loc[1]
        if (fx == 0 && fy == 0) {
            fx = mApp.getFloattingPositionX(true)
            fy = mApp.getFloattingPositionY(true)

            fx = if (fx > 0) fx - KlickApplication.HANDLE_WIDTH_PX else 0
        }

        val translationFromX = (if (fx > 0)
            x - rect.left - Utils.dip2px(mApp, 130f)
        else
            rect.left + Utils.dip2px(mApp,
                    130f) - x).toFloat()

        fy = fy + (KlickApplication.HANDLE_HEIGHT_PX - (Utils.dip2px(mApp, 16f) * scale).toInt()) / 2
        fx = if (fx > 0)
            fx + KlickApplication.HANDLE_WIDTH_PX / 2
        else
            KlickApplication.HANDLE_WIDTH_PX / 2

        val translationToX = (fx - x).toFloat()
        val translationToY = (fy - y).toFloat()
        var translationFromY = translationToY + Utils.dip2px(mApp, 130f) - KlickApplication.HANDLE_HEIGHT_PX / 2
        if (y.toFloat() + translationFromY + Utils.dip2px(mApp, 130f).toFloat() > rect.height()) {
            translationFromY = (rect.height() - Utils.dip2px(mApp, 130f) - y).toFloat()
        }

        val animatorSet = AnimatorSet()

        if (visibility == View.INVISIBLE) {
            val tx = PropertyValuesHolder.ofFloat("translationX", translationFromX, translationToX)
            val ty = PropertyValuesHolder.ofFloat("translationY", translationFromY, translationToY)
            val sx = PropertyValuesHolder.ofFloat("scaleX", 1f, scale)
            val sy = PropertyValuesHolder.ofFloat("scaleY", 1f, scale)
            val alpha = PropertyValuesHolder.ofFloat("alpha", 1f, 0f)
            val translationAndScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mViewFlipper, tx, ty,
                    sx, sy).setDuration(duration.toLong())
            val alphaAnimator = ObjectAnimator.ofPropertyValuesHolder(mViewFlipper, alpha)
            alphaAnimator.duration = animator.duration
            alphaAnimator.startDelay = 20

            animatorSet.play(translationAndScaleAnimator).before(animator)
            animatorSet.play(animator).with(alphaAnimator)

            animatorSet.addListener(mAnimationFadeOutListener)
        } else {
            mViewFlipper.translationX = translationToX
            mViewFlipper.translationY = translationToY
            mViewFlipper.scaleX = scale
            mViewFlipper.scaleY = scale
            val tx = PropertyValuesHolder.ofFloat("translationX", translationToX, translationFromX)
            val ty = PropertyValuesHolder.ofFloat("translationY", translationToY, translationFromY)
            val sx = PropertyValuesHolder.ofFloat("scaleX", scale, 1f)
            val sy = PropertyValuesHolder.ofFloat("scaleY", scale, 1f)
            val alpha = PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
            val translationAndScaleAnimator = ObjectAnimator.ofPropertyValuesHolder(mViewFlipper, tx, ty,
                    sx, sy).setDuration(duration.toLong())
            val alphaAnimator = ObjectAnimator.ofPropertyValuesHolder(mViewFlipper, alpha)
            alphaAnimator.duration = animator.duration
            translationAndScaleAnimator.startDelay = 20

            animatorSet.play(translationAndScaleAnimator).after(alphaAnimator)
            animatorSet.play(animator).with(alphaAnimator)

            animatorSet.addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    this@MoreActionsView.scrollToDefaultScreenImmediate()
                    this@MoreActionsView.visibility = View.VISIBLE
                }

                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    this@MoreActionsView.clipChildren = true
                    if (isShowFirstTime) {
                        isShowFirstTime = false
                        this@MoreActionsView.scrollToDefaultScreenImmediate()
                    }

                    refreshSibling(DEFAULT_PAGE_NUMBER)
                }

                override fun onAnimationCancel(animation: Animator) {}
            })
        }

        animatorSet.startDelay = startDelay
        animatorSet.start()
    }

    internal inner class MyAnimationListener : AnimationListener, AnimatorListener {
        private var action: Int = 0
        private var data: Any? = null

        fun setAction(action: Int, data: Any) {
            this.action = action
            this.data = data
        }

        override fun onAnimationStart(animation: Animation) {
            this@MoreActionsView.clipChildren = false
        }

        override fun onAnimationRepeat(animation: Animation) {}

        override fun onAnimationEnd(animation: Animation) {
            onAnimationEnd()
        }

        fun onAnimationEnd() {
            this@MoreActionsView.visibility = View.INVISIBLE
            //			mApp.getmWindowManager().removeView(MoreActionsView.this);

            when (action) {
                -1 -> {
                }
                R.id.lockimage -> mApp.applicationContext.sendBroadcast(Intent(KlickApplication.ACTION_LOCK_SCREEN))
                R.id.expendstatusbarimage -> if (klickAccessServiceInstance != null)
                    klickAccessServiceInstance!!.performGlobalAction(AccessibilityService
                            .GLOBAL_ACTION_NOTIFICATIONS)
                R.id.cameraimage -> openCamera()
                R.id.homeimage -> if (klickAccessServiceInstance != null)
                    klickAccessServiceInstance!!.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                R.id.appswitchimage -> if (!KeyEventHandler.getInstance(mApp.applicationContext).inputKeyEvent(KeyEvent
                        .KEYCODE_APP_SWITCH) && klickAccessServiceInstance != null)
                    klickAccessServiceInstance!!.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            //            case R.id.menuimage:
            //                KeyEventHandler.getInstance(mApp.getApplicationContext()).inputKeyEvent(KeyEvent.KEYCODE_MENU);
            //                break;
                R.id.settingimage -> {
                    if ("onLongClick" == data) {
                        val prefsIntent = Intent(mApp.applicationContext, PrefsActivity::class.java)
                        prefsIntent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent
                                .FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                        mApp.applicationContext.startActivity(prefsIntent)
                    }
                    if ("onClick" == data) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NEW_TASK
                            mApp.applicationContext.startActivity(intent)
                        } catch (e: Exception) {
                            Log.d(TAG, e.message)
                        }

                    }
                }
                R.drawable.add -> {
                    if ("onClick" == data) {
                        val intent = Intent(mApp.applicationContext, MoreActionsConfActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        mApp.applicationContext.startActivity(intent)
                    }

                    if ("onLongClick" == data) {
                        //					if (getKlickAccessServiceInstance() != null)
                        //						getKlickAccessServiceInstance().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                        val localIntent = Intent("android.intent.action.MAIN")
                        localIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        localIntent.addCategory("android.intent.category.HOME")
                        mApp.applicationContext.startActivity(localIntent)
                    }
                }
                0 -> if (data != null && data is Int) {
                    val position = (data as Int?)!!

                    Utils.launchApp(mApp, mAppList[position])

                    if (mAppList[position].clickCount >= 0) {
                        mAppList[position].clickCount = mAppList[position].clickCount + 1
                        val e = mApp.sharedPrefs!!.edit()
                        e.putInt(KlickApplication.APP_CLICK_COUNT_PREFIX + mAppList[position].key,
                                mAppList[position].clickCount)
                        e.commit()
                    }
                }
            }

            action = -1
            data = null
            clearApps()
        }

        override fun onAnimationStart(animation: Animator) {}

        override fun onAnimationEnd(animation: Animator) {
            onAnimationEnd()
        }

        override fun onAnimationCancel(animation: Animator) {}

        override fun onAnimationRepeat(animation: Animator) {}
    }

    fun getmBackgroundView(): LinearLayout {
        return mBackgroundView
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mAppList.clear()
        mViewFlipper.removeAllViews()
        mFloatingView = null
        System.gc()
    }

    fun openCamera() {
        try {
            val intent = Intent("android.intent.action.MAIN")
            intent.addCategory("android.intent.category.LAUNCHER")
            intent.component = cameraComponent
            intent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NEW_TASK
            mApp.applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(mApp.applicationContext, "Failed to launch Camera", Toast.LENGTH_SHORT).show()
        }

    }

    fun switchFlashLight() {
        turnFlashLight(!isFlashlightOn)
    }

    fun turnFlashLight(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            turnFlashLightCamera2(!isFlashlightOn)
        else
            turnFlashLightCamera(!isFlashlightOn)
    }

    fun turnFlashLightCamera(on: Boolean) {
        if (!on) {
            mFloatingView!!.mHandler.removeMessages(KlickApplication.MSG_TURN_OFF_FLASH_LIGHT)
            try {
                mCamera!!.stopPreview()
                mCamera!!.release()
                mCamera = null
            } catch (e: Exception) {
                e.printStackTrace()
            }

            isFlashlightOn = false
            (mView.findViewById(R.id.flashlightimage) as ImageView).setImageResource(R.drawable.flashlight_off)
        } else {
            try {
                mCamera = Camera.open()
                val params = mCamera!!.parameters
                params.flashMode = Parameters.FLASH_MODE_TORCH
                mCamera!!.parameters = params
                mCamera!!.startPreview()
                (mView.findViewById(R.id.flashlightimage) as ImageView).setImageResource(R.drawable.flashlight_on)
                isFlashlightOn = true
                mFloatingView!!.mHandler.sendEmptyMessageDelayed(KlickApplication
                        .MSG_TURN_OFF_FLASH_LIGHT, (KlickApplication.FLASH_LIGHT_ON_MAX_SECONDS * 1000).toLong())
            } catch (e: Exception) {
                Log.d(TAG, e.message)
                Toast.makeText(context, R.string.turn_flashlight_on_failed, Toast.LENGTH_SHORT).show()
            }

        }
    }

    @Synchronized
    fun turnFlashLightCamera2(on: Boolean) {
        if (!on) {
            mFloatingView!!.mHandler.removeMessages(KlickApplication.MSG_TURN_OFF_FLASH_LIGHT)
            try {
                mBuilder!!.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                mSession!!.setRepeatingRequest(mBuilder!!.build(), null, null)
                mSession!!.close()
                mCameraDevice!!.close()
                mCameraDevice = null
                mSession = null
            } catch (e: Exception) {
                e.printStackTrace()
            }

            isFlashlightOn = false
            (mView.findViewById(R.id.flashlightimage) as ImageView).setImageResource(R.drawable.flashlight_off)
        } else {
            try {
                mCameraManager = mApp.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                //here to judge if flash is available
                val cameraCharacteristics = mCameraManager!!.getCameraCharacteristics("0")
                val flashAvailable = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!
                if (flashAvailable) {
                    mCameraManager!!.openCamera("0", MyCameraDeviceStateCallback(), null)
                } else {
                    Toast.makeText(mApp, "Flash not available", Toast.LENGTH_SHORT).show()
                    //todo: throw Exception
                }
                mCameraManager!!.openCamera("0", MyCameraDeviceStateCallback(), null)

                //				mBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                //				mSession.setRepeatingRequest(mBuilder.build(), null, null);

                (mView.findViewById(R.id.flashlightimage) as ImageView).setImageResource(R.drawable.flashlight_on)
                isFlashlightOn = true
                mFloatingView!!.mHandler.sendEmptyMessageDelayed(KlickApplication
                        .MSG_TURN_OFF_FLASH_LIGHT, (KlickApplication.FLASH_LIGHT_ON_MAX_SECONDS * 1000).toLong())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, R.string.turn_flashlight_on_failed, Toast.LENGTH_SHORT).show()
            }

        }
    }

    /**
     * camera device callback
     */
    internal inner class MyCameraDeviceStateCallback : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            //get builder
            try {
                mBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                //flash on, default is on
                mBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                mBuilder!!.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
                val list = ArrayList<Surface>()
                mSurfaceTexture = SurfaceTexture(1)
                val size = getSmallestSize(mCameraDevice!!.id)
                mSurfaceTexture!!.setDefaultBufferSize(size.width, size.height)
                mSurface = Surface(mSurfaceTexture)
                list.add(mSurface as Surface)
                mBuilder!!.addTarget(mSurface!!)
                camera.createCaptureSession(list, MyCameraCaptureSessionStateCallback(), null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }

        }

        override fun onDisconnected(camera: CameraDevice) {

        }

        override fun onError(camera: CameraDevice, error: Int) {

        }
    }

    /**
     * session callback
     */
    internal inner class MyCameraCaptureSessionStateCallback : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            mSession = session
            try {
                mSession!!.setRepeatingRequest(mBuilder!!.build(), null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        override fun onConfigureFailed(session: CameraCaptureSession) {

        }
    }

    @Throws(CameraAccessException::class)
    private fun getSmallestSize(cameraId: String): Size {
        val outputSizes = mCameraManager!!.getCameraCharacteristics(cameraId).get<StreamConfigurationMap>(CameraCharacteristics
                .SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(SurfaceTexture::class.java)
        if (outputSizes == null || outputSizes!!.size == 0) {
            throw IllegalStateException("Camera " + cameraId + "doesn't support any outputSize.")
        }
        var chosen = outputSizes!![0]
        for (s in outputSizes!!) {
            if (chosen.getWidth() >= s.getWidth() && chosen.getHeight() >= s.getHeight()) {
                chosen = s
            }
        }
        return chosen
    }

    private inner class DoActionListener : View.OnClickListener, View.OnLongClickListener {
        override fun onClick(v: View) {
            mApp.playFeedback(false)
            mAnimationFadeOutListener.setAction(v.id, "onClick")
            mFloatingView!!.hideMoreActionsView()
        }

        override fun onLongClick(v: View): Boolean {
            mApp.playFeedback(true)
            mAnimationFadeOutListener.setAction(v.id, "onLongClick")
            mFloatingView!!.hideMoreActionsView()
            return true
        }
    }

    companion object {
        private val TAG = "MoreActionsView"
        private val FIST_APP_PAGE_INDEX = 2
        private var DEFAULT_PAGE_NUMBER = 0
    }
}