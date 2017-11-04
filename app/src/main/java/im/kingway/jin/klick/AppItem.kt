package im.kingway.jin.klick

import android.content.ComponentName
import android.content.pm.ActivityInfo

class AppItem : Comparable<AppItem> {
    var ai: ActivityInfo? = null
    var name: String? = null
        private set
    var component: ComponentName
        private set
    var isSelected = false
    var isExcluded = false
    var clickCount = 0
    var isInRectentTaskList = false

    val key: String
        get() = component.packageName + "|" + component.className

    constructor(ai: ActivityInfo, name: String) {
        this.ai = ai
        this.name = name
        this.component = ComponentName(ai.applicationInfo.packageName, ai.name)
    }

    constructor(name: String, component: ComponentName) {
        this.ai = null
        this.name = name
        this.component = component
    }

    constructor(item: AppItem) {
        this.ai = item.ai
        this.name = item.name
        this.component = item.component
    }

    override fun compareTo(appItem: AppItem): Int {
        return if (this === appItem) {
            0
        } else {
            this.component.packageName.compareTo(appItem.component.packageName)
        }
    }

    override fun hashCode(): Int {
        return this.component.packageName.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppItem) return false

        return other.component.packageName == this.component.packageName
    }
}