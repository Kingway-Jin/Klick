package im.kingway.jin.klick

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
                    val clickableNode = getClickableParent(nodeInfo)
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
        if (mApp!!.mAppsMap.containsKey(currentAppPackageName) && !isExcludedApp(currentAppPackageName)) {
            currentRootInActiveWindow = rootInActiveWindow
            recentAppPackageName.remove(currentAppPackageName)
            recentAppPackageName.add(0, currentAppPackageName)

            saveRecentAppPackageName()

            val intent = Intent()
            intent.action = KlickApplication.ACTION_HIDE_MORE_ACTION_VIEW
            applicationContext.sendBroadcast(intent)
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
        (application as KlickApplication).sharedPrefs!!.edit().putString("RECENT_APP_PACKAGE_NAME", recentAppPackageName.joinToString(";")).commit()
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

    fun performClickOnViewWithText(text: String) {
        val nodeInfo = findClickableNodeByText(text)
        if (nodeInfo != null) {
            Log.d(TAG, "performing click on view: " + nodeInfo.toString())
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    fun getTextOfClickableNodeByPostfix(text: String): String? {
        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(currentRootInActiveWindow!!)

        while (nodeInfoList.size > 0) {
            val nodeInfo = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (nodeInfo.text != null && nodeInfo.text.endsWith(text)) {
                    return nodeInfo.text.toString()
                }
                for (j in 0 until nodeInfo.childCount) {
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return null
    }

    fun findClickableNodeByText(text: String): AccessibilityNodeInfo? {
        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(currentRootInActiveWindow!!)

        while (nodeInfoList.size > 0) {
            val nodeInfo = nodeInfoList.removeAt(0)

            if (nodeInfo != null) {
                if (text == nodeInfo.text) {
                    return getClickableParent(nodeInfo)
                }
                for (j in 0 until nodeInfo.childCount) {
                    nodeInfoList.add(nodeInfo.getChild(j))
                }
            }
        }
        return null
    }

    private fun getClickableParent(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var childNodeInfo = nodeInfo
        if (childNodeInfo.isClickable) {
            return childNodeInfo
        } else {
            while (childNodeInfo.parent != null) {
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

    companion object {
        val TAG = KlickAccessibilityService::class.java.getSimpleName()
        var sharedInstance: KlickAccessibilityService? = null
        var recentAppPackageName: MutableList<String> = LinkedList()
        var switchToAppPackageName = ""
        var mApp: KlickApplication? = null
        var currentRootInActiveWindow: AccessibilityNodeInfo? = null

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
