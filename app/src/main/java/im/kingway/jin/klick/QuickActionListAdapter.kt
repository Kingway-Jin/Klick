package im.kingway.jin.klick

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Switch
import android.widget.TextView
import java.util.*

class QuickActionListAdapter(private val mApp: KlickApplication, private var quickActionItemList: MutableList<QuickActionItem>) : BaseAdapter() {
    protected var showList: MutableList<QuickActionItem> = LinkedList()
    private var onlyActive = true

    internal var compQuickActionItem: Comparator<QuickActionItem> = Comparator { lhs, rhs ->
        if (lhs.clickCount > rhs.clickCount) {
            -1
        } else if (lhs.clickCount < rhs.clickCount) {
            1
        } else {
            lhs.text!!.compareTo(rhs.text!!)
        }
    }

    init {
        setQuickActionItemList(quickActionItemList)
    }

    private fun prepareShowList() {
        showList.clear()
        for (quickActionItem in quickActionItemList) {
            if (onlyActive) {
                if (quickActionItem.isSelected) {
                    showList.add(quickActionItem)
                }
            } else {
                showList.add(quickActionItem)
            }
        }
        for (item in showList) {
            Log.d(TAG, item.text!! + item.clickCount)
        }
        Collections.sort(showList, compQuickActionItem)
        for (item in showList) {
            Log.d(TAG, item.text!! + item.clickCount)
        }
    }

    public fun setQuickActionItemList(newQuickActionItemList: MutableList<QuickActionItem>) {
        quickActionItemList = newQuickActionItemList

        for (quickActionItem in quickActionItemList) {
            quickActionItem.isSelected = isClickableTextActive(quickActionItem.packageName, getText(quickActionItem.text))
            if (quickActionItem.text!!.endsWith(POSTFIX_NEW_MSG)) {
                quickActionItem.isSelected = true
            }
        }
        prepareShowList()
    }

    fun toggleOnlyShowActive() {
        onlyActive = !onlyActive
        prepareShowList()
        notifyDataSetInvalidated()
    }

    override fun getCount(): Int {
        return showList.size
    }

    override fun getItem(position: Int): QuickActionItem {
        return showList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var v = view
        val vh: ViewHolder
        if (v == null || v.id != R.id.quick_action_item) {
            v = LayoutInflater.from(mApp).inflate(R.layout.quick_action_list_item, parent, false)
            vh = ViewHolder()
            vh.toggleQuickAction = v!!.findViewById(R.id.toggle_quick_action) as Switch
            vh.quickActionText = v.findViewById(R.id.quick_action_text) as TextView
            v.tag = vh
        } else {
            vh = v.tag as ViewHolder
        }
        vh.toggleQuickAction!!.tag = position
        vh.toggleQuickAction!!.setOnCheckedChangeListener { buttonView, isChecked ->
            val p = buttonView.tag as Int
            val item = getItem(p)
            item.isSelected = isChecked
            if (item.isSelected) {
                setClickableTextActive(item.packageName, item.text, item.clickCount)
            } else {
                setClickableTextInactive(item.packageName, item.text)
            }
        }

        if (onlyActive) {
            vh.toggleQuickAction!!.visibility = View.GONE
        } else {
            vh.toggleQuickAction!!.visibility = View.VISIBLE
        }

        val quickActionItem = getItem(position)
        vh.toggleQuickAction!!.isChecked = quickActionItem.isSelected
        vh.quickActionText!!.text = quickActionItem.text

        return v
    }

    private inner class ViewHolder {
        internal var quickActionText: TextView? = null
        internal var toggleQuickAction: Switch? = null
    }

    fun clear() {
        quickActionItemList.clear()
        showList.clear()
    }

    fun increaseClickCount(position: Int) {
        val quickActionItem = getItem(position)
        quickActionItem.increaseClickCount()
        setClickableTextActive(quickActionItem.packageName, quickActionItem.text, quickActionItem.clickCount)
    }

    private fun isClickableTextActive(packageName: String?, text: String): Boolean {
        val count = mApp.sharedPrefs!!.getInt("quick_action:" + packageName + ":" + getText(text)
        , -1)
        return count >= 0
    }

    private fun setClickableTextActive(packageName: String?, text: String?, count: Int) {
        mApp.sharedPrefs!!.edit().putInt("quick_action:" + packageName + ":" + getText(text),
        count).commit()
    }

    private fun setClickableTextInactive(packageName: String?, text: String?) {
        mApp.sharedPrefs!!.edit().remove("quick_action:" + packageName + ":" + getText(text))
        .commit()
    }

    private fun getText(text: String?): String {
        var txt = text
        if (txt!!.endsWith(POSTFIX_NEW_MSG)) {
            txt = POSTFIX_NEW_MSG
        }
        return txt
    }

    companion object {
        val TAG = QuickActionListAdapter::class.java.simpleName
        val POSTFIX_NEW_MSG = "条新消息"
        val YOUBUTE_SKIP_AD = "跳过广告"
    }
}
