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
            nodeInfoList.add(rootInActiveWindow)

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
        currentAppPackageName = event.packageName.toString()
        recentAppPackageName.remove(currentAppPackageName)
        recentAppPackageName.add(0, currentAppPackageName)

        saveRecentAppPackageName()

        if (currentAppPackageName != switchToAppPackageName) {
            recentAppPackageNameSnapshot.clear()
        }
        switchToAppPackageName = ""

        val intent = Intent()
        intent.action = KlickApplication.ACTION_HIDE_MORE_ACTION_VIEW
        applicationContext.sendBroadcast(intent)
    }

    private fun loadRecentAppPackageName() {
        recentAppPackageName.clear()
        val pkgs = (application as KlickApplication).sharedPrefs!!.getString("RECENT_APP_PACKAGE_NAME", "")
        val pkgArray = pkgs!!.split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
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

    fun performClickOnViewWithText(text: String) {
        val nodeInfo = findClickableNodeByText(text)
        if (nodeInfo != null) {
            Log.d(TAG, "performing click on view: " + nodeInfo.toString())
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findClickableNodeByText(text: String): AccessibilityNodeInfo? {
        val nodeInfoList = LinkedList<AccessibilityNodeInfo>()
        nodeInfoList.add(rootInActiveWindow)

        while (nodeInfoList.size > 0) {
            val nodeInfo = nodeInfoList.removeAt(0)

            if (nodeInfo.text != null && text == nodeInfo.text) {
                return getClickableParent(nodeInfo)
            }
            for (j in 0 until nodeInfo.childCount) {
                nodeInfoList.add(nodeInfo.getChild(j))
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
        loadRecentAppPackageName()
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind")
        sharedInstance = null
        return super.onUnbind(intent)
    }

    companion object {
        val TAG = KlickAccessibilityService::class.java.getSimpleName()
        var sharedInstance: KlickAccessibilityService? = null
        var recentAppPackageName: MutableList<String> = LinkedList()
        var recentAppPackageNameSnapshot: MutableList<String> = LinkedList()
        var switchToAppPackageName = ""
        var currentAppPackageName = ""

        fun removeApp(packageName: String) {
            recentAppPackageNameSnapshot.remove(packageName)
            recentAppPackageName.remove(packageName)
        }

        fun switchApp(step: Int): String {
            var index = recentAppPackageNameSnapshot.indexOf(switchToAppPackageName)
            index += step
            if (index < 0) {
                index = 0
            }
            if (index >= recentAppPackageNameSnapshot.size) {
                index = recentAppPackageNameSnapshot.size - 1
            }
            if (index >= 0) {
                switchToAppPackageName = recentAppPackageNameSnapshot[index]
            } else {
                switchToAppPackageName = ""
            }
            return if (switchToAppPackageName == currentAppPackageName) "" else switchToAppPackageName
        }

        fun switchAppForward(): String {
            switchToAppPackageName = ""

            if (recentAppPackageNameSnapshot.isEmpty()) {
                recentAppPackageNameSnapshot.addAll(recentAppPackageName)
            }

            val index = recentAppPackageNameSnapshot.indexOf(currentAppPackageName)
            if (index > 0) {
                switchToAppPackageName = recentAppPackageNameSnapshot[index - 1]
            }
            Log.d(TAG, "$currentAppPackageName, $switchToAppPackageName, $index")
            return switchToAppPackageName
        }

        fun switchAppBackward(): String {
            switchToAppPackageName = ""

            if (recentAppPackageNameSnapshot.isEmpty()) {
                recentAppPackageNameSnapshot.addAll(recentAppPackageName)
                recentAppPackageNameSnapshot.remove("com.meizu.flyme.launcher")
            }

            val index = recentAppPackageNameSnapshot.indexOf(currentAppPackageName)
            if (index < recentAppPackageNameSnapshot.size - 1) {
                switchToAppPackageName = recentAppPackageNameSnapshot[index + 1]
            }
            for (pkg in recentAppPackageNameSnapshot) {
                Log.d(TAG, "recentAppPackageNameSnapshot: " + pkg)
            }
            for (pkg in recentAppPackageName) {
                Log.d(TAG, "recentAppPackageName: " + pkg)
            }
            Log.d(TAG, "$currentAppPackageName, $switchToAppPackageName, $index")
            return switchToAppPackageName
        }
    }
}
