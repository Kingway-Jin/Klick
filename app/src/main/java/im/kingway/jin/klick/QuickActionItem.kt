package im.kingway.jin.klick

import android.view.accessibility.AccessibilityNodeInfo

class QuickActionItem : Comparable<QuickActionItem> {
    var nodeInfo: AccessibilityNodeInfo? = null
    var packageName: String? = null
    var text: String? = null
    var clickCount = 0
    var isSelected: Boolean = false
        set(selected) {
            field = selected
            if (selected && clickCount <= 0) {
                clickCount = 1
            }
        }

    fun increaseClickCount() {
        clickCount += 1
    }

    override fun compareTo(quickActionItem: QuickActionItem): Int {
        return if (this === quickActionItem) {
            0
        } else {
            if (clickCount == quickActionItem.clickCount) {
                text!!.compareTo(quickActionItem.text!!)
            } else {
                clickCount - quickActionItem.clickCount
            }
        }
    }

    override fun hashCode(): Int {
        return (packageName!! + text!!).hashCode()
    }

}