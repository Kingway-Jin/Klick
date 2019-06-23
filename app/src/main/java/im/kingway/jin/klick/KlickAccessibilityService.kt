package im.kingway.jin.klick

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

class KlickAccessibilityService : AccessibilityService() {

    var mHandler: Handler = object : Handler() {
        @Synchronized override fun handleMessage(msg: Message) {
            when (msg.what) {
                KlickApplication.MSG_AUTO_CLICK -> {
                    autoClickAsyncTask = AutoClickAsyncTask(Integer(++KlickAccessibilityService.autoClickCounter))
                    (autoClickAsyncTask as AutoClickAsyncTask).execute(currentRootInActiveWindow)
                }
                else -> {
                }
            }
            super.handleMessage(msg)
        }
    }

    val allClickableTextAsListAdapter: QuickActionListAdapter
        get() {
            val quickActionItemList = LinkedList<QuickActionItem>()
            quickActionItemList.clear()

            val nodeInfoList = LinkedList<AccessibilityNodeInfo?>()
            if (null != currentRootInActiveWindow) {
                nodeInfoList.add(currentRootInActiveWindow)
            }

            while (nodeInfoList.size > 0) {
                val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

                if (nodeInfo != null) {
                    if (!nodeInfo.text.isNullOrBlank()) {
                        val clickableNode = getClickableParent(nodeInfo, null)
                        if (null != clickableNode) {
                            val quickActionItem = QuickActionItem()
                            quickActionItem.nodeInfo = clickableNode
                            quickActionItem.packageName = nodeInfo.packageName.toString()
                            quickActionItem.text = nodeInfo.text.toString()
                            quickActionItemList.add(quickActionItem)
                        }
                    }
                    for (j in 0 until nodeInfo.childCount) {
                        nodeInfoList.add(nodeInfo.getChild(j))
                    }
                }
            }

            return QuickActionListAdapter(application as KlickApplication, quickActionItemList)
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == null) {
            return
        }
        val currentAppPackageName = event.packageName.toString()
        Log.d(TAG, "currentAppPackageName: $currentAppPackageName")
        if (currentAppPackageName != currentRootInActiveWindow?.packageName
        && !isExcludedApp(currentAppPackageName)) {
            recentAppPackageName.remove(currentAppPackageName)
            recentAppPackageName.add(0, currentAppPackageName)
        }
        currentRootInActiveWindow = rootInActiveWindow

//        if ("com.tencent.mm" == currentAppPackageName || "com.google.android.youtube" == currentAppPackageName) {
//            val msgBreathing = Message()
//            msgBreathing.what = KlickApplication.MSG_AUTO_CLICK
//            mHandler.sendMessageDelayed(msgBreathing, 100)
//        } else {
//            mHandler.removeMessages(KlickApplication.MSG_AUTO_CLICK)
//        }
    }

    private fun loadRecentAppPackageName() {
        recentAppPackageName.clear()
        val pkgs = (application as KlickApplication).sharedPrefs!!.getString("RECENT_APP_PACKAGE_NAME", "")
        val pkgArray = pkgs!!.split(";".toRegex()).dropLastWhile({ it.isEmpty() })
                .toTypedArray().filter { !isExcludedApp(it) }
        recentAppPackageName.addAll(pkgArray)
    }

    private fun isClickableTextActive(packageName: String, text: String): Boolean {
        val count = (application as KlickApplication).sharedPrefs!!.getInt("quick_action:"
                + packageName + ":" + text, -1)
        return count >= 0
    }

    public fun increaseClickCounter(packageName: String, text: String) {
        val count = (application as KlickApplication).sharedPrefs!!.getInt("quick_action:"
                + packageName + ":" + text, -1)
        if (count >= 0) {
            (application as KlickApplication).sharedPrefs!!.edit().putInt("quick_action:" +
                    packageName + ":" + text, count + 1).commit()
        }
    }

    fun scrollToTop(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(nodeInfo!!)

        while (nodeInfoList.size > 0) {
            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (nodeInfo.isScrollable()) {
                    Log.d(TAG, "performing scroll on view: ${nodeInfo.toString()}")
                    val arguments = Bundle()
                    arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0)
                    nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id, arguments)
                    return
                }
                for (j in 0 until nodeInfo.childCount) {
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
    }

    fun performClickOnViewWithText(rootNode: AccessibilityNodeInfo?, text: String, asyncCounter: Integer?) {
        val nodeInfo = findClickableNodeByText(rootNode, text, asyncCounter)
        Log.d(TAG, "performing click on view: ${nodeInfo.toString()} with text ${text}")
        nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performClickOnViewByPostfix(rootNode: AccessibilityNodeInfo?, postfix: String, asyncCounter: Integer?) {
        val nodeInfo = findClickableNodeByPostfix(rootNode, postfix, asyncCounter)
        Log.d(TAG, "performing click on view: ${nodeInfo.toString()} with postfix ${postfix}")
        nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performClickOnViewContainsText(rootNode: AccessibilityNodeInfo?, text: String, asyncCounter: Integer?) {
        val nodeInfo = findClickableNodeContainsText(rootNode, text, asyncCounter)
        Log.d(TAG, "performing click on view: ${nodeInfo.toString()} contains ${text}")
        nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun getTextOfClickableNodeByPostfix(postfix: String): String? {
        if (currentRootInActiveWindow == null) {
            return null
        }

        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(currentRootInActiveWindow!!)

        while (nodeInfoList.size > 0) {
            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (nodeInfo.text != null && nodeInfo.text.endsWith(postfix)) {
                    return nodeInfo.text.toString()
                }
                for (j in 0 until nodeInfo.childCount) {
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return null
    }

    fun findClickableNodeByPostfix(rootNode: AccessibilityNodeInfo?, postfix: String, asyncCounter: Integer?): AccessibilityNodeInfo? {
        if (rootNode == null) {
            return null
        }

        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(rootNode!!)

        while (nodeInfoList.size > 0) {
            if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                return null
            }

            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (nodeInfo.text != null && nodeInfo.text.endsWith(postfix)) {
                    return getClickableParent(nodeInfo, asyncCounter)
                }
                for (j in 0 until nodeInfo.childCount) {
                    if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                        return null
                    }
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return null
    }

    fun findClickableNodeContainsText(rootNode: AccessibilityNodeInfo?, text: String, asyncCounter: Integer?): AccessibilityNodeInfo? {
        if (rootNode == null) {
            return null
        }

        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(rootNode!!)

        while (nodeInfoList.size > 0) {
            if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                return null
            }

            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (nodeInfo.text != null && nodeInfo.text.contains(text)) {
                    return getClickableParent(nodeInfo, asyncCounter)
                }
                for (j in 0 until nodeInfo.childCount) {
                    if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                        return null
                    }
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return null
    }

    fun findClickableNodeByText(rootNode: AccessibilityNodeInfo?, text: String, asyncCounter: Integer?): AccessibilityNodeInfo? {
        if (rootNode == null) {
            return null
        }

        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(rootNode!!)

        while (nodeInfoList.size > 0) {
            if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                return null
            }

            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (text == nodeInfo.text) {
                    return getClickableParent(nodeInfo, asyncCounter)
                }
                for (j in 0 until nodeInfo.childCount) {
                    if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                        return null
                    }
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return null
    }

    private fun getClickableParent(nodeInfo: AccessibilityNodeInfo, asyncCounter: Integer?): AccessibilityNodeInfo? {
        var childNodeInfo = nodeInfo
        if (childNodeInfo.isClickable) {
            return childNodeInfo
        } else {
            while (childNodeInfo.parent != null) {
                if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                    return null
                }
                childNodeInfo = childNodeInfo.parent
                if (childNodeInfo.isClickable) {
                    return childNodeInfo
                }
            }
        }
        return null
    }

    private fun loopChild(rootNode: AccessibilityNodeInfo) {
        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(rootNode)

        while (nodeInfoList.size > 0) {
            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (isClickable(nodeInfo) && nodeInfo.text != null) {
                    Log.d(TAG, nodeInfo.text.toString())
                }
                for (j in 0 until nodeInfo.childCount) {
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
    }

    private fun isClickable(nodeInfo: AccessibilityNodeInfo): Boolean {
        var childNodeInfo = nodeInfo
        if (childNodeInfo.isClickable) {
            return true
        } else {
            while (childNodeInfo.parent != null) {
                childNodeInfo = childNodeInfo.parent
                if (childNodeInfo.isClickable) {
                    return true
                }
            }
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onCreate() {}

    override fun onServiceConnected() {
        Log.d(TAG, "onServiceConnected")
        super.onServiceConnected()
        sharedInstance = this
        mApp = application as KlickApplication
        loadRecentAppPackageName()
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind")
        sharedInstance = null
        mApp = null
        return super.onUnbind(intent)
    }

    private inner class AutoClickAsyncTask : AsyncTask<AccessibilityNodeInfo, Int, Int> {
        val autoClickCounter: Integer

        constructor(autoClickCounter: Integer) {
            this.autoClickCounter = autoClickCounter
        }

        protected override fun doInBackground(vararg params: AccessibilityNodeInfo?): Int {
            if (params == null || params.size == 0 || params[0] == null)
                return 0

            var rootNodeInfo = params[0]!!

            if ("com.tencent.mm" == rootNodeInfo.packageName) {
                performClickOnViewByPostfix(rootNodeInfo, QuickActionListAdapter.POSTFIX_NEW_MSG, autoClickCounter)
            } else if ("com.google.android.youtube" == rootNodeInfo.packageName) {
                performClickOnViewContainsText(rootNodeInfo, QuickActionListAdapter.YOUBUTE_SKIP_AD, autoClickCounter)
            }

            return 0
        }
    }

    companion object {
        val TAG = KlickAccessibilityService::class.java.getSimpleName()
        var sharedInstance: KlickAccessibilityService? = null
        var recentAppPackageName: MutableList<String> = LinkedList()
        var switchToAppPackageName = ""
        var mApp: KlickApplication? = null
        var currentRootInActiveWindow: AccessibilityNodeInfo? = null
        var autoClickCounter = 0
        var prevAutoClickAsyncTaskStartTime = 0L;
        private var autoClickAsyncTask: AutoClickAsyncTask? = null

        fun switchAppBackward(): String {
            switchToAppPackageName = currentRootInActiveWindow?.packageName.toString() ?: ""

            for (pkg in recentAppPackageName) {
                if (pkg != switchToAppPackageName && mApp!!.mAppsMap?.containsKey(pkg)) {
                    switchToAppPackageName = pkg
                    break
                }
            }

            Log.d(TAG, "Swith to APP: $switchToAppPackageName")
            return switchToAppPackageName
        }
    }

    fun isExcludedApp(pkgName: String?) = "com.meizu.flyme.launcher" == pkgName ||
            "im.kingway.jin.klick" == pkgName ||
            "com.android.systemui" == pkgName ||
            "com.iflytek.inputmethod" == pkgName ||
            mApp!!.mExcludePackage.contains(pkgName)
}
