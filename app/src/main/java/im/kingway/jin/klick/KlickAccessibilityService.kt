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
import kotlin.collections.HashMap

class KlickAccessibilityService : AccessibilityService() {

    init {
        klickAccessibilityService = this
    }

    var mHandler: Handler = object : Handler() {
        @Synchronized override fun handleMessage(msg: Message) {
            when (msg.what) {
                KlickApplication.MSG_AUTO_CLICK -> {
                    autoClickAsyncTask = AutoClickAsyncTask(Integer(++KlickAccessibilityService.autoClickCounter))
                    (autoClickAsyncTask as AutoClickAsyncTask).execute(rootInActiveWindow)
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
            if (null != rootInActiveWindow) {
                nodeInfoList.add(rootInActiveWindow)
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
        val currentAppPackageName = event.packageName?.toString()
        val currentAppClassName = event.className?.toString()
        mApp?.sendHideFromSoftKeyboardMsg(currentAppPackageName, currentAppClassName)
        if (currentAppPackageName == null) {
            return
        }
        Log.d(TAG, "currentAppPackageName: $currentAppPackageName, currentAppClassName: $currentAppClassName")
        if (!isExcludedApp(currentAppPackageName)) {
            recentAppPackageName.remove(currentAppPackageName)
            recentAppPackageName.add(0, currentAppPackageName)
//            rootInActiveWindow = rootInActiveWindow

            if (mApp!!.sharedPrefs!!.getInt(KlickApplication.CUSTOMIZE_ICON_CHOICE, 0) == 4) {
                mApp!!.sharedPrefs!!.edit()
                        .putString(KlickApplication.CUSTOMIZE_ICON_APP, currentAppPackageName)
                        .commit()
                val intent = Intent(KlickApplication.ACTION_CUSTOMIZE_ICON)
                sendBroadcast(intent)
            }
        }

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

    fun performClickOn(nodeInfo: AccessibilityNodeInfo?) {
        Log.d(TAG, "performing click on view: ${nodeInfo.toString()}")
        nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performClickOnViewWithText(rootNode: AccessibilityNodeInfo?, text: String, asyncCounter: Integer?) {
        if (rootNode == null) {
            return
        }
        if (text in QuickActionListAdapter.TEXT_PATTERN) {
            performClickOnViewContainsText(rootNode, text, asyncCounter)
        } else {
            val cacheKey = rootNode.packageName.toString() + ":" + text
            var nodeInfo = findClickableNodeByText(rootNode, text, asyncCounter)
            if (nodeInfo == null && nodePathCache.containsKey(cacheKey)) {
                nodePathCache.remove(cacheKey)
                nodeInfo = findClickableNodeByText(rootNode, text, asyncCounter)
            }
            Log.d(TAG, "performing click on view: ${nodeInfo.toString()} with text ${text}")
            nodeInfo?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
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

    fun getNodeListBySubstring(substring: String): List<AccessibilityNodeInfo> {
        val matchedNodeList = mutableListOf<AccessibilityNodeInfo>()
        if (rootInActiveWindow == null) {
            return matchedNodeList
        }

        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(rootInActiveWindow!!)

        while (nodeInfoList.size > 0) {
            val nodeInfo: AccessibilityNodeInfo? = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (nodeInfo.text != null && nodeInfo.text.contains(substring)) {
                    matchedNodeList.add(nodeInfo)
                }
                for (j in 0 until nodeInfo.childCount) {
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return matchedNodeList
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

        val cacheKey = rootNode.packageName.toString() + ":" + text
        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        var nodePath: MutableList<Int> = nodePathCache.getOrDefault(cacheKey, LinkedList())
        nodeInfoList.add(rootNode)
        Log.d(TAG, nodePath.joinToString())

        if (nodePath.isNotEmpty()) {
            for (index in 0..nodePath.lastIndex) {
                if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                    return null
                }

                val childIndex = nodePath[index]
                if (childIndex >= nodeInfoList[index].childCount) {
                    return null
                }
                val nodeInfo: AccessibilityNodeInfo = nodeInfoList[index].getChild(childIndex)
                if (text == nodeInfo.text) {
                    return getClickableParent(nodeInfo, asyncCounter)
                } else if (index == nodePath.lastIndex) {
                    for (j in 0 until nodeInfoList[index].childCount) {
                        if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                            return null
                        }
                        val nodeInfo: AccessibilityNodeInfo = nodeInfoList[index].getChild(j)
                        if (text == nodeInfo.text) {
                            return getClickableParent(nodeInfo, asyncCounter)
                        }
                    }
                }
                nodeInfoList.add(nodeInfo)
            }
        } else {
            nodePath.add(0)
            while (nodePath.size > 0 && nodePath[0] < rootNode.childCount) {
                if (!(asyncCounter?.equals(KlickAccessibilityService.autoClickCounter) ?: true)) {
                    return null
                }

                val index = nodePath.size - 1
                val childIndex = nodePath[index]
                if (childIndex >= nodeInfoList[index].childCount) {
                    nodePath.removeAt(index)
                    nodePath[index - 1] += 1
                    nodeInfoList.removeAt(index)
                    continue
                }

                val nodeInfo: AccessibilityNodeInfo = nodeInfoList[index].getChild(childIndex)

                if (text == nodeInfo.text) {
                    nodePathCache[cacheKey] = nodePath
                    return getClickableParent(nodeInfo, asyncCounter)
                }
                if (nodeInfo.childCount > 0) {
                    nodePath.add(0)
                    nodeInfoList.add(nodeInfo)
                } else {
                    nodePath[index] += 1
                }
            }
        }
        return null
    }

    fun getClickableParent(nodeInfo: AccessibilityNodeInfo, asyncCounter: Integer?): AccessibilityNodeInfo? {
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
//        var rootInActiveWindow: AccessibilityNodeInfo? = null
        var klickAccessibilityService: KlickAccessibilityService? = null
        var autoClickCounter = 0
        var prevAutoClickAsyncTaskStartTime = 0L;
        var nodePathCache: MutableMap<String, MutableList<Int>> = HashMap()
        private var autoClickAsyncTask: AutoClickAsyncTask? = null

        fun switchAppBackward(): String {
            switchToAppPackageName = klickAccessibilityService?.rootInActiveWindow?.packageName.toString() ?: ""

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
