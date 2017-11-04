package im.kingway.jin.klick

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppListAdapter : BaseAdapter {
    private var mLayoutInflater: LayoutInflater
    private var mApp: KlickApplication
    var appList: MutableList<AppItem>

    constructor(activity: Activity, appList: MutableList<AppItem>) : super() {
        mLayoutInflater = activity.layoutInflater
        mApp = activity.application as KlickApplication
        this.appList = appList
    }

    override fun getCount(): Int {
        return appList.size
    }

    override fun getItem(position: Int): AppItem {
        return appList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var v = view
        val vh: ViewHolder
        if (v == null || v.id != R.id.app_item) {
            v = mLayoutInflater.inflate(R.layout.app_list_item, parent, false)
            vh = ViewHolder()
            vh.appIcon = v.findViewById(R.id.app_icon) as ImageView
            vh.appName = v.findViewById(R.id.app_name) as TextView
            v.tag = vh
        } else {
            vh = v.tag as ViewHolder
        }

        val ai = getItem(position)
        vh.appIcon!!.setImageDrawable(mApp.getAppIcon(ai))
        vh.appName!!.text = ai.name
        if (ai.isSelected) {
            vh.appName!!.setTextColor(Color.RED)
        } else if (ai.isExcluded) {
            vh.appName!!.setTextColor(Color.LTGRAY)
        } else {
            vh.appName!!.setTextColor(Color.BLACK)
        }

        return v!!
    }

    private inner class ViewHolder {
        internal var appName: TextView? = null
        internal var appIcon: ImageView? = null
    }

    fun clear() {
        appList.clear()
    }

    companion object {
        val TAG = "AppListAdapter"
    }
}
