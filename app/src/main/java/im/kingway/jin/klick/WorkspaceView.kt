package im.kingway.jin.klick

import android.content.Context
import android.graphics.*
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.Scroller

/**
 * The workspace is a wide area with a infinite number of screens. Each screen
 * contains a view. A workspace is meant to be used with a fixed width only.<br></br>
 * <br></br>
 * This code has been done by using com.android.launcher.Workspace.java
 */
open class WorkspaceView
/**
 * Used to inflate the Workspace from XML.
 *
 * @param context
 * The application's context.
 * @param attrs
 * The attribtues set containing the Workspace's customization
 * values.
 * @param defStyle
 * Unused.
 */
@JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyle: Int = 0) : ViewGroup(context, attrs, defStyle) {

    private var mActivePointerId = INVALID_POINTER

    // the default screen index
    private val defaultScreen: Int
    // The current screen index
    var currentScreen: Int = 0
        private set(theCurrentScreen) {
            if (!scroller!!.isFinished)
                scroller!!.abortAnimation()
            field = Math
                    .max(0, Math.min(theCurrentScreen, childCount))
            scrollTo(currentScreen * width, 0)
            Log.d("workspace", "setCurrentScreen: width is " + width)
            invalidate()
        }
    // The next screen index
    private var nextScreen = INVALID_SCREEN
    // Wallpaper properties
    private var wallpaper: Bitmap? = null
    private var paint: Paint? = null
    private var wallpaperWidth: Int = 0
    private var wallpaperHeight: Int = 0
    private var wallpaperOffset: Float = 0.toFloat()
    private var wallpaperLoaded: Boolean = false
    private var firstWallpaperLayout = true
    private var selectedTab: RectF? = null

    // The scroller which scroll each view
    private var scroller: Scroller? = null
    // A tracker which to calculate the velocity of a movement
    private var mVelocityTracker: VelocityTracker? = null

    // Tha last known values of X and Y
    private var lastMotionX: Float = 0.toFloat()
    private var lastMotionY: Float = 0.toFloat()

    // The current touch state
    private var touchState = TOUCH_STATE_REST
    // The minimal distance of a touch slop
    private var touchSlop: Int = 0

    // An internal flag to reset long press when user is scrolling
    private var allowLongPress: Boolean = false
    // A flag to know if touch event have to be ignored. Used also in internal
    private var locked: Boolean = false

    private var mScrollInterpolator: Interpolator? = null

    private var mMaximumVelocity: Int = 0

    private var selectedTabPaint: Paint? = null
    private var canvas: Canvas? = null

    private var bar: RectF? = null

    private var tabIndicatorBackgroundPaint: Paint? = null

    internal val isDefaultScreenShowing: Boolean
        get() = currentScreen == defaultScreen

    internal var bitmap: Bitmap? = null

    private var onLoadListener: OnWorkspaceLoadListener? = null
    private var onTouchListener: OnWorkspaceTouchListener? = null

    private val lastEvHashCode: Int = 0

    // Added for "flipper" compatibility
    // setCurrentScreen(i);
    var displayedChild: Int
        get() = currentScreen
        set(i) {
            scrollToScreen(i)
            getChildAt(i).requestFocus()
        }

    private class WorkspaceOvershootInterpolator : Interpolator {
        private var mTension: Float = 0.toFloat()

        init {
            mTension = DEFAULT_TENSION
        }

        fun setDistance(distance: Int) {
            mTension = if (distance > 0)
                DEFAULT_TENSION / distance
            else
                DEFAULT_TENSION
        }

        fun disableSettle() {
            mTension = 0f
        }

        override fun getInterpolation(t: Float): Float {
            var t = t
            // _o(t) = t * t * ((tension + 1) * t + tension)
            // o(t) = _o(t - 1) + 1
            t -= 1.0f
            return t * t * ((mTension + 1) * t + mTension) + 1.0f
        }

        companion object {
            private val DEFAULT_TENSION = 1.3f
        }
    }

    init {
        defaultScreen = 0
        initWorkspace()
    }

    /**
     * Initializes various states for this workspace.
     */
    private fun initWorkspace() {
        //		mScrollInterpolator = new WorkspaceOvershootInterpolator();
        mScrollInterpolator = DecelerateInterpolator()
        scroller = Scroller(context, mScrollInterpolator)
        currentScreen = defaultScreen

        paint = Paint()
        paint!!.isDither = false

        // Does this do anything for me?
        val configuration = ViewConfiguration
                .get(context)
        touchSlop = configuration.scaledTouchSlop
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity

        selectedTabPaint = Paint()
        // selectedTabPaint.setColor(Color.RED);
        //		selectedTabPaint.setColor(Color.parseColor("#0099cc"));
        selectedTabPaint!!.color = Color.WHITE
        selectedTabPaint!!.style = Paint.Style.FILL_AND_STROKE

        tabIndicatorBackgroundPaint = Paint()
        //		 tabIndicatorBackgroundPaint.setColor(Color.GRAY);
        tabIndicatorBackgroundPaint!!.color = Color.TRANSPARENT
        tabIndicatorBackgroundPaint!!.style = Paint.Style.FILL
    }

    /**
     * Set a new distance that a touch can wander before we think the user is
     * scrolling in pixels slop<br></br>
     *
     * @param touchSlopP
     */
    fun setTouchSlop(touchSlopP: Int) {
        touchSlop = touchSlopP
    }

    /**
     * Set the background's wallpaper.
     */
    fun loadWallpaper(bitmap: Bitmap) {
        wallpaper = bitmap
        wallpaperLoaded = true
        requestLayout()
        invalidate()
    }

//    /**
//     * Returns the index of the currently displayed screen.
//     *
//     * @return The index of the currently displayed screen.
//     */
//    internal fun getCurrentScreen(): Int {
//        return currentScreen
//    }

//    /**
//     * Sets the current screen.
//     *
//     * @param theCurrentScreen
//     */
//    fun setCurrentScreen(theCurrentScreen: Int) {
//
//        if (!scroller!!.isFinished)
//            scroller!!.abortAnimation()
//        currentScreen = Math
//                .max(0, Math.min(theCurrentScreen, childCount))
//        scrollTo(currentScreen * width, 0)
//        Log.d("workspace", "setCurrentScreen: width is " + width)
//        invalidate()
//    }

    /**
     * Shows the default screen (defined by the firstScreen attribute in XML.)
     */
    internal fun showDefaultScreen() {
        currentScreen = defaultScreen
    }

    /**
     * Registers the specified listener on each screen contained in this
     * workspace.
     *
     * @param l
     * The listener used to respond to long clicks.
     */
    override fun setOnLongClickListener(l: View.OnLongClickListener?) {
        val count = childCount
        for (i in 0 until count) {
            getChildAt(i).setOnLongClickListener(l)
        }
    }

    override fun computeScroll() {
        if (scroller!!.computeScrollOffset()) {
            scrollTo(scroller!!.currX, scroller!!.currY)
            postInvalidate()
        } else if (nextScreen != INVALID_SCREEN) {
            currentScreen = Math.max(0,
                    Math.min(nextScreen, childCount - 1))
            nextScreen = INVALID_SCREEN
        }
    }

    /**
     * ViewGroup.dispatchDraw() supports many features we don't need: clip to
     * padding, layout animation, animation listener, disappearing children,
     * etc. The following implementation attempts to fast-track the drawing
     * dispatch by drawing only what we know needs to be drawn.
     */
    override fun dispatchDraw(canvas: Canvas) {
        // First draw the wallpaper if needed

        if (wallpaper != null) {
            var x = scrollX * wallpaperOffset
            if (x + wallpaperWidth < right - left) {
                x = (right - left - wallpaperWidth).toFloat()
            }
            canvas.drawBitmap(wallpaper!!, x,
                    ((bottom - top - wallpaperHeight) / 2).toFloat(), paint)
        }

        // Determine if we need to draw every child or only the current screen
        val fastDraw = touchState != TOUCH_STATE_SCROLLING && nextScreen == INVALID_SCREEN
        // If we are not scrolling or flinging, draw only the current screen
        if (fastDraw) {
            val v = getChildAt(currentScreen)
            if (null == v) {
                moveToDefaultScreen()
                return
            }
            drawChild(canvas, v, drawingTime)
        } else {
            val drawingTime = drawingTime
            // If we are flinging, draw only the current screen and the target
            // screen
            if (nextScreen >= 0 && nextScreen < childCount
                    && Math.abs(currentScreen - nextScreen) == 1) {
                drawChild(canvas, getChildAt(currentScreen), drawingTime)
                drawChild(canvas, getChildAt(nextScreen), drawingTime)
            } else {
                // If we are scrolling, draw all of our children
                val count = childCount
                for (i in 0 until count) {
                    drawChild(canvas, getChildAt(i), drawingTime)
                }
            }
        }
        updateTabIndicator()
        canvas.drawBitmap(bitmap!!, scrollX.toFloat(), (measuredHeight * (100 - TAB_INDICATOR_HEIGHT_PCT) / 100).toFloat(), paint)

    }

    /**
     * Measure the workspace AND also children
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        // Log.d("workspace","Height is " + height);
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        //		if (widthMode != MeasureSpec.EXACTLY) {
        //			throw new IllegalStateException(
        //					"Workspace can only be used in EXACTLY mode.");
        //		}

        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        //		if (heightMode != MeasureSpec.EXACTLY) {
        //			throw new IllegalStateException(
        //					"Workspace can only be used in EXACTLY mode.");
        //		}

        // The children are given the same width and height as the workspace
        val count = childCount
        for (i in 0 until count) {
            val adjustedHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height * (100 - TAB_INDICATOR_HEIGHT_PCT) / 100, heightMode)
            getChildAt(i).measure(widthMeasureSpec, adjustedHeightMeasureSpec)

        }

        // Compute wallpaper
        if (wallpaperLoaded) {
            wallpaperLoaded = false
            wallpaper = centerToFit(wallpaper, width, height, context)
            wallpaperWidth = wallpaper!!.width
            wallpaperHeight = wallpaper!!.height
        }
        wallpaperOffset = if (wallpaperWidth > width)
            (count * width - wallpaperWidth) / ((count - 1) * width.toFloat())
        else
            1.0f
        if (firstWallpaperLayout) {
            scrollTo(currentScreen * width, 0)
            firstWallpaperLayout = false
        }

        // Log.d("workspace","Top is "+getTop()+", bottom is "+getBottom()+", left is "+getLeft()+", right is "+getRight());

        updateTabIndicator()
        invalidate()
    }

    private fun updateTabIndicator() {
        val width = measuredWidth
        val height = measuredHeight

        // For drawing in its own bitmap:
        bar = RectF(0f, 0f, width.toFloat(), (TAB_INDICATOR_HEIGHT_PCT * height / 100).toFloat())

        var startPos = scrollX / childCount
        var endPos = startPos + width / childCount
        val padding = Utils.dip2px(context, 36f)
        //		selectedTab = new RectF(startPos, 0,
        //				startPos + width / getChildCount(), (TAB_INDICATOR_HEIGHT_PCT
        //						* height / 100));
        if (startPos < padding)
            startPos = padding
        if (endPos > width - padding) {
            endPos = width - padding
        }
        selectedTab = RectF(startPos.toFloat(),
                (TAB_INDICATOR_HEIGHT_PCT * height / 100 - Utils.dip2px(context, 1f)).toFloat(),
                endPos.toFloat(),
                (TAB_INDICATOR_HEIGHT_PCT * height / 100).toFloat())
        //		selectedTab = new RectF(startPos + Utils.dip2px(getContext(), 15),
        //				(TAB_INDICATOR_HEIGHT_PCT * height / 100) - Utils.dip2px(getContext(), 1),
        //				startPos + width / getChildCount() - Utils.dip2px(getContext(), 15),
        //				(TAB_INDICATOR_HEIGHT_PCT * height / 100));
        //		selectedTab = new RectF(startPos,
        //				(TAB_INDICATOR_HEIGHT_PCT * height / 100) - Utils.dip2px(getContext(), 1),
        //				startPos + width / getChildCount(),
        //				(TAB_INDICATOR_HEIGHT_PCT * height / 100));

        if (bitmap == null || bitmap!!.isRecycled) {
            bitmap = Bitmap.createBitmap(width,
                    TAB_INDICATOR_HEIGHT_PCT * height / 100,
                    Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap!!)
        }
        bitmap!!.eraseColor(Color.TRANSPARENT)
        canvas!!.drawRoundRect(bar!!, 0f, 0f, tabIndicatorBackgroundPaint!!)
        //		canvas.drawRoundRect(selectedTab, 5, 5, selectedTabPaint);
        canvas!!.drawRect(selectedTab!!, selectedTabPaint!!)
    }

    /**
     * Overrided method to layout child
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int,
                          bottom: Int) {
        var childLeft = 0
        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                val childWidth = child.measuredWidth
                child.layout(childLeft, 0, childLeft + childWidth,
                        child.measuredHeight)
                childLeft += childWidth
            }
        }
        if (onLoadListener != null) {
            onLoadListener!!.onLoad()
        }
    }

    override fun dispatchUnhandledMove(focused: View, direction: Int): Boolean {
        if (direction == View.FOCUS_LEFT) {
            if (currentScreen > 0) {
                scrollToScreen(currentScreen - 1)
                return true
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (currentScreen < childCount - 1) {
                scrollToScreen(currentScreen + 1)
                return true
            }
        }
        return super.dispatchUnhandledMove(focused, direction)
    }

    /**
     * This method JUST determines whether we want to intercept the motion. If
     * we return true, onTouchEvent will be called and we do the actual
     * scrolling there.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        Log.d("workspace", "Intercepted a touch event")
        if (locked) {
            return true
        }

        /*
         * Shortcut the most recurring case: the user is in the dragging state
         * and he is moving his finger. We want to intercept this motion.
         */
        val action = ev.action
        if (action == MotionEvent.ACTION_MOVE && touchState != TOUCH_STATE_REST) {
            return true
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        // switch (action & MotionEvent.ACTION_MASK) {
        when (action) {
            MotionEvent.ACTION_MOVE ->

                // Log.d("workspace","Intercepted a move event");
                /*
             * Locally do absolute value. mLastMotionX is set to the y value of
             * the down event.
             */
                handleInterceptMove(ev)

            MotionEvent.ACTION_DOWN -> {
                // Remember location of down touch
                val x1 = ev.x
                val y1 = ev.y
                lastMotionX = x1
                lastMotionY = y1
                allowLongPress = true
                mActivePointerId = ev.getPointerId(0)

                /*
             * If being flinged and user touches the screen, initiate drag;
             * otherwise don't. mScroller.isFinished should be false when being
             * flinged.
             */
                touchState = if (scroller!!.isFinished)
                    TOUCH_STATE_REST
                else
                    TOUCH_STATE_SCROLLING
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                mActivePointerId = INVALID_POINTER
                allowLongPress = false

                if (mVelocityTracker != null) {
                    mVelocityTracker!!.recycle()
                    mVelocityTracker = null
                }
                touchState = TOUCH_STATE_REST
            }

            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return touchState != TOUCH_STATE_REST
    }

    private fun handleInterceptMove(ev: MotionEvent) {
        val pointerIndex = ev.findPointerIndex(mActivePointerId)
        val x = ev.getX(pointerIndex)
        val y = ev.getY(pointerIndex)
        val xDiff = Math.abs(x - lastMotionX).toInt()
        val yDiff = Math.abs(y - lastMotionY).toInt()
        val xMoved = xDiff > touchSlop
        val yMoved = yDiff > touchSlop

        if (xMoved || yMoved) {
            // Log.d("workspace","Detected move. Checking to scroll.");
            if (xMoved && !yMoved) {
                // Log.d("workspace","Detected X move. Scrolling.");
                // Scroll if the user moved far enough along the X axis
                touchState = TOUCH_STATE_SCROLLING
                lastMotionX = x
            }
            // Either way, cancel any pending longpress
            if (allowLongPress) {
                allowLongPress = false
                // Try canceling the long press. It could also have been
                // scheduled
                // by a distant descendant, so use the mAllowLongPress flag to
                // block
                // everything
                val currentView = getChildAt(currentScreen)
                currentView.cancelLongPress()
            }
        }
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.action and MotionEvent.ACTION_POINTER_ID_MASK shr MotionEvent.ACTION_POINTER_ID_SHIFT
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            lastMotionX = ev.getX(newPointerIndex)
            lastMotionY = ev.getY(newPointerIndex)
            mActivePointerId = ev.getPointerId(newPointerIndex)
            if (mVelocityTracker != null) {
                mVelocityTracker!!.clear()
            }
        }
    }

    /**
     * Track the touch event
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Log.d("workspace","caught a touch event");
        if (locked) {
            return true
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        val action = ev.action
        val x = ev.x

        when (action) {
            MotionEvent.ACTION_DOWN ->

                // We can still get here even if we returned false from the
                // intercept function.
                // That's the only way we can get a TOUCH_STATE_REST (0) here.
                // That means that our child hasn't handled the event, so we need to
                // Log.d("workspace","caught a down touch event and touchstate =" +
                // touchState);

                if (touchState != TOUCH_STATE_REST) {
                    /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                    if (!scroller!!.isFinished) {
                        scroller!!.abortAnimation()
                    }

                    // Remember where the motion event started
                    lastMotionX = x
                    mActivePointerId = ev.getPointerId(0)
                }
            MotionEvent.ACTION_MOVE ->

                if (touchState == TOUCH_STATE_SCROLLING) {
                    handleScrollMove(ev)
                } else {
                    // Log.d("workspace","caught a move touch event but not scrolling");
                    // NOTE: We will never hit this case in Android 2.2. This is to
                    // fix a 2.1 bug.
                    // We need to do the work of interceptTouchEvent here because we
                    // don't intercept the move
                    // on children who don't scroll.

                    Log.d("workspace", "handling move from onTouch")

                    if (onInterceptTouchEvent(ev) && touchState == TOUCH_STATE_SCROLLING) {
                        handleScrollMove(ev)
                    }

                }
            MotionEvent.ACTION_UP -> {
                // Log.d("workspace","caught an up touch event");
                if (touchState == TOUCH_STATE_SCROLLING) {
                    val velocityTracker = mVelocityTracker
                    velocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                    val velocityX = velocityTracker.xVelocity.toInt()

                    if (velocityX > SNAP_VELOCITY && currentScreen > 0) {
                        // Fling hard enough to move left
                        scrollToScreen(currentScreen - 1)
                    } else if (velocityX < -SNAP_VELOCITY && currentScreen < childCount - 1) {
                        // Fling hard enough to move right
                        scrollToScreen(currentScreen + 1)
                    } else {
                        snapToDestination()
                    }

                    if (mVelocityTracker != null) {
                        mVelocityTracker!!.recycle()
                        mVelocityTracker = null
                    }
                }
                touchState = TOUCH_STATE_REST
                mActivePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_CANCEL -> {
                Log.d("workspace", "caught a cancel touch event")
                touchState = TOUCH_STATE_REST
                mActivePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> {
                Log.d("workspace", "caught a pointer up touch event")
                onSecondaryPointerUp(ev)
            }
        }

        if (onTouchListener != null) {
            onTouchListener!!.onTouch(ev)
        }

        return true
    }

    private fun handleScrollMove(ev: MotionEvent) {
        // Scroll to follow the motion event
        val pointerIndex = ev.findPointerIndex(mActivePointerId)
        val x1 = ev.getX(pointerIndex)
        val deltaX = (lastMotionX - x1).toInt()
        lastMotionX = x1

        if (deltaX < 0) {
            if (scrollX > -1 * SNAP_DISTANCE) {
                val avaliableToScrollLeft = -scrollX - SNAP_DISTANCE
                // Scrollby invalidates automatically
                if (avaliableToScrollLeft > -1 * SNAP_DISTANCE) {
                    if ((deltaX * Math.abs(avaliableToScrollLeft) / SNAP_DISTANCE).toInt() != 0) {
                        scrollBy(Math.max(avaliableToScrollLeft, (deltaX * Math.abs(avaliableToScrollLeft) / SNAP_DISTANCE).toInt()), 0)
                    }
                } else {
                    scrollBy(Math.max(avaliableToScrollLeft, deltaX), 0)
                }
            }
        } else if (deltaX > 0) {
            val availableToScrollRight = getChildAt(childCount - 1)
                    .right - scrollX - width + SNAP_DISTANCE
            if (availableToScrollRight > 0) {
                // Scrollby invalidates automatically
                if (availableToScrollRight < SNAP_DISTANCE) {
                    if ((deltaX * availableToScrollRight / SNAP_DISTANCE).toInt() != 0) {
                        scrollBy(Math.min(availableToScrollRight, (deltaX * availableToScrollRight / SNAP_DISTANCE).toInt()), 0)
                    }
                } else {
                    scrollBy(Math.min(availableToScrollRight, deltaX), 0)
                }
            }
        } else {
            awakenScrollBars()
        }
    }

    /**
     * Scroll to the appropriated screen depending of the current position
     */
    private fun snapToDestination() {
        val screenWidth = width
        val whichScreen = (scrollX + screenWidth / 2) / screenWidth
        Log.d("workspace", "snapToDestination")
        scrollToScreen(whichScreen)
    }

    /**
     * Scroll to a specific screen
     *
     * @param whichScreen
     */
    open fun scrollToScreen(whichScreen: Int) {
        scrollToScreen(whichScreen, false)
    }

    private fun scrollToScreen(whichScreen: Int, immediate: Boolean) {
        Log.d("workspace", "snapToScreen=" + whichScreen)

        val changingScreens = whichScreen != currentScreen

        nextScreen = whichScreen

        val focusedChild = focusedChild
        if (focusedChild != null && changingScreens
                && focusedChild === getChildAt(currentScreen)) {
            focusedChild.clearFocus()
        }

        val newX = whichScreen * width
        val delta = newX - scrollX
        Log.d("workspace", "newX=" + newX + " scrollX=" + scrollX
                + " delta=" + delta)
        scroller!!.startScroll(scrollX, 0, delta, 0,
                if (immediate) 0 else Math.abs(delta) / 3)
        invalidate()
    }

    fun scrollToScreenImmediate(whichScreen: Int) {
        scrollToScreen(whichScreen, true)
    }

    /**
     * Return the parceable instance to be saved
     */
    override fun onSaveInstanceState(): Parcelable? {
        val state = SavedState(super.onSaveInstanceState())
        state.currentScreen = currentScreen
        return state
    }

    /**
     * Restore the previous saved current screen
     */
    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        if (savedState.currentScreen != -1) {
            currentScreen = savedState.currentScreen
        }
    }

    /**
     * Scroll to the left right screen
     */
    fun scrollLeft() {
        if (nextScreen == INVALID_SCREEN && currentScreen > 0
                && scroller!!.isFinished) {
            scrollToScreen(currentScreen - 1)
        }
    }

    /**
     * Scroll to the next right screen
     */
    fun scrollRight() {
        if (nextScreen == INVALID_SCREEN && currentScreen < childCount - 1
                && scroller!!.isFinished) {
            scrollToScreen(currentScreen + 1)
        }
    }

    /**
     * Return the screen's index where a view has been added to.
     *
     * @param v
     * @return
     */
    fun getScreenForView(v: View?): Int {
        val result = -1
        if (v != null) {
            val vp = v.parent
            val count = childCount
            for (i in 0 until count) {
                if (vp === getChildAt(i)) {
                    return i
                }
            }
        }
        return result
    }

    /**
     * Return a view instance according to the tag parameter or null if the view
     * could not be found
     *
     * @param tag
     * @return
     */
    fun getViewForTag(tag: Any): View? {
        val screenCount = childCount
        for (screen in 0 until screenCount) {
            val child = getChildAt(screen)
            if (child.tag === tag) {
                return child
            }
        }
        return null
    }

    /**
     * Unlocks the SlidingDrawer so that touch events are processed.
     *
     * @see .lock
     */
    fun unlock() {
        locked = false
    }

    /**
     * Locks the SlidingDrawer so that touch events are ignores.
     *
     * @see .unlock
     */
    fun lock() {
        locked = true
    }

    /**
     * @return True is long presses are still allowed for the current touch
     */
    fun allowLongPress(): Boolean {
        return allowLongPress
    }

    /**
     * Move to the default screen
     */
    fun moveToDefaultScreen() {
        scrollToScreen(defaultScreen)
        getChildAt(defaultScreen).requestFocus()
    }

    // ========================= INNER CLASSES ==============================

    /**
     * A SavedState which save and onLoadListener the current screen
     */
    class SavedState : View.BaseSavedState {
        internal var currentScreen = -1

        /**
         * Internal constructor
         *
         * @param superState
         */
        internal constructor(superState: Parcelable) : super(superState) {}

        /**
         * Private constructor
         *
         * @param in
         */
        private constructor(`in`: Parcel) : super(`in`) {
            currentScreen = `in`.readInt()
        }

        /**
         * Save the current screen
         */
        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(currentScreen)
        }

        companion object {

            /**
             * Return a Parcelable creator
             */
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    fun setOnLoadListener(load: OnWorkspaceLoadListener) {
        this.onLoadListener = load
    }

    fun setOnTouchListener(touch: OnWorkspaceTouchListener) {
        this.onTouchListener = touch
    }

    fun flipLeft() {
        scrollLeft()
    }

    fun flipRight() {
        scrollRight()
    }

    companion object {

        private val INVALID_POINTER = -1
        private val INVALID_SCREEN = -1

        // The velocity at which a fling gesture will cause us to snap to the next
        // screen
        private val SNAP_VELOCITY = 500
        private val TAB_INDICATOR_HEIGHT_PCT = 1 // 2
        private val TAB_INDICATOR_HEIGHT_DIP = 1
        private val SNAP_DISTANCE = 200

        private val TOUCH_STATE_REST = 0
        private val TOUCH_STATE_SCROLLING = 1

        // ======================== UTILITIES METHODS ==========================

        /**
         * Return a centered Bitmap
         *
         * @param bitmap
         * @param width
         * @param height
         * @param context
         * @return
         */
        internal fun centerToFit(bitmap: Bitmap?, width: Int, height: Int,
                                 context: Context): Bitmap {
            var bm = bitmap
            val bitmapWidth = bm!!.width
            val bitmapHeight = bm.height

            if (bitmapWidth < width || bitmapHeight < height) {
                // Normally should get the window_background color of the context
                val color = Integer.valueOf("FF191919", 16)!!
                val centered = Bitmap.createBitmap(if (bitmapWidth < width)
                    width
                else
                    bitmapWidth, if (bitmapHeight < height)
                    height
                else
                    bitmapHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(centered)
                canvas.drawColor(color)
                canvas.drawBitmap(bm, (width - bitmapWidth) / 2.0f,
                        (height - bitmapHeight) / 2.0f, null)
                bm = centered
            }
            return bm as Bitmap
        }
    }

}
/**
 * Used to inflate the Workspace from XML.
 *
 * @param context
 * The application's context.
 * @param attrs
 * The attribtues set containing the Workspace's customization
 * values.
 */