package im.kingway.jin.klick

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.AsyncTask
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

class KlickAccessibilityService : AccessibilityService() {

    val allClickableTextAsListAdapter: QuickActionListAdapter
        get() {
            val quickActionItemList = LinkedList<QuickActionItem>()

            val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
            nodeInfoList.add(currentRootInActiveWindow!!)

            while (nodeInfoList.size > 0) {
                val nodeInfo = nodeInfoList.removeAt(0)

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

            return QuickActionListAdapter(application as KlickApplication, quickActionItemList)
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val currentAppPackageName = event.packageName.toString()
        Log.d(TAG, "currentAppPackageName: $currentAppPackageName")
        if (currentAppPackageName == currentRootInActiveWindow?.packageName ||
                mApp!!.mAppsMap.containsKey(currentAppPackageName) &&
                !isExcludedApp(currentAppPackageName)) {
            if (currentAppPackageName != currentRootInActiveWindow?.packageName) {
                recentAppPackageName.remove(currentAppPackageName)
                recentAppPackageName.add(0, currentAppPackageName)

                saveRecentAppPackageName()

//                val intent = Intent()
//                intent.action = KlickApplication.ACTION_HIDE_MORE_ACTION_VIEW
//                applicationContext.sendBroadcast(intent)
            }
            currentRootInActiveWindow = rootInActiveWindow
        }

        if ("com.tencent.mm" == currentAppPackageName || "com.google.android.youtube" == currentAppPackageName) {
            AutoClickAsyncTask(Integer(++KlickAccessibilityService.autoClickCounter)).execute(currentRootInActiveWindow)
        }
    }

    private fun loadRecentAppPackageName() {
        recentAppPackageName.clear()
        val pkgs = (application as KlickApplication).sharedPrefs!!.getString("RECENT_APP_PACKAGE_NAME", "")
        val pkgArray = pkgs!!.split(";".toRegex()).dropLastWhile({ it.isEmpty() })
                .toTypedArray().filter { !isExcludedApp(it) }
        recentAppPackageName.addAll(pkgArray)
    }

    private fun saveRecentAppPackageName() {
        (application as KlickApplication).sharedPrefs!!.edit().putString("RECENT_APP_PACKAGE_NAME", recentAppPackageName.joinToString(";")).apply()
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
            val nodeInfo = nodeInfoList.removeAt(0)

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

            val nodeInfo = nodeInfoList.removeAt(0)

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

            val nodeInfo = nodeInfoList.removeAt(0)

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

            val nodeInfo = nodeInfoList.removeAt(0)

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
            val nodeInfo = nodeInfoList.removeAt(0)

            if (isClickable(nodeInfo) && nodeInfo.text != null) {
                Log.d(TAG, nodeInfo.text.toString())
            }
            for (j in 0 until nodeInfo.childCount) {
                nodeInfoList.add(nodeInfo.getChild(j))
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

        fun switchAppBackward(): String {
            switchToAppPackageName = currentRootInActiveWindow?.packageName.toString() ?: ""

            if (recentAppPackageName.isEmpty()) return switchToAppPackageName

            var index = recentAppPackageName.indexOf(switchToAppPackageName)
            index = if (index < 0) 0 else index + 1
            index = if (index >= recentAppPackageName.size) recentAppPackageName.size - 1 else index

            switchToAppPackageName = recentAppPackageName[index]
            Log.d(TAG, "$switchToAppPackageName, $index")
            return switchToAppPackageName
        }
    }

    fun isExcludedApp(pkgName: String?) = "com.meizu.flyme.launcher" == pkgName ||
            "im.kingway.jin.klick" == pkgName ||
            "com.android.systemui" == pkgName ||
            "com.iflytek.inputmethod" == pkgName ||
            mApp!!.mExcludePackage.contains(pkgName)
}
