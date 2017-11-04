package im.kingway.jin.klick

import android.content.Context
import android.util.AttributeSet

class MyWorkspaceView : WorkspaceView {
    var workspaceChange: WorkspaceChangeInterface? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun scrollToScreen(whichScreen: Int) {
        super.scrollToScreen(whichScreen)
        if (null != workspaceChange)
            workspaceChange!!.onWrokSpaceChange(whichScreen)
    }
}
