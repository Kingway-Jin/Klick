package im.kingway.jin.klick

import android.view.MotionEvent

interface OnWorkspaceTouchListener {
    fun onTouch(event: MotionEvent): Boolean
}
