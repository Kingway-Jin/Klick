package im.kingway.jin.klick

import android.content.Context
import android.util.AttributeSet

class MyWorkspaceView : WorkspaceView {
    var workspaceChange: WorkspaceChangeInterface? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun scrollToScreen(whichScreen: Int) {
        super.scrollToScreen(whichScreen)
        onWrokSpaceChange(whichScreen)
    }

    override fun scrollToScreenImmediate(whichScreen: Int) {
        super.scrollToScreenImmediate(whichScreen)
        onWrokSpaceChange(whichScreen)
    }

    private fun onWrokSpaceChange(whichScreen: Int) {
        if (null != workspaceChange)
            workspaceChange!!.onWrokSpaceChange(whichScreen)
    }
}
