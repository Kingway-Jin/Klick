package im.kingway.jin.klick

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.*

class MoreActionsConfActivity : Activity() {
    private var mApp: KlickApplication? = null
    private var mListView: ListView? = null
    private var mAppAdapter: CategoryAdapter? = null
    private val mSelectedApps = ArrayList<AppItem>()
    private val mCandidateApps = ArrayList<AppItem>()
    private val mExcludedApps = ArrayList<AppItem>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionBar!!.setTitle(R.string.quick_start_conf_title)
        setContentView(R.layout.app_folder_conf)
        mListView = findViewById(R.id.app_list_view) as ListView

        mApp = application as KlickApplication
        mApp!!.addActivity(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && mAppAdapter == null) {
            mExcludedApps.clear()
            mSelectedApps.clear()
            mCandidateApps.clear()

            for (appItem in mApp!!.mAppsMap.values) {
                if (appItem.isExcluded)
                    mExcludedApps.add(appItem)
                else if (appItem.isSelected)
                    mSelectedApps.add(appItem)
                else
                    mCandidateApps.add(appItem)
            }

            mAppAdapter = object : CategoryAdapter() {
                override fun getTitleView(caption: String, index: Int, convertView: View?,
                                          parent: ViewGroup): View {
                    val titleView: LinearLayout

                    if (convertView == null || convertView.id != R.id.title_view) {
                        titleView = layoutInflater.inflate(R.layout.title_view, null) as LinearLayout
                    } else {
                        titleView = convertView as LinearLayout
                    }

                    (titleView.findViewById(R.id.title_text_view) as TextView).text = caption

                    return titleView
                }
            }
            mAppAdapter!!.addCategory(resources.getString(R.string.cat_selected), AppListAdapter(this, mSelectedApps))
            mAppAdapter!!.addCategory(resources.getString(R.string.cat_candidate), AppListAdapter(this, mCandidateApps))
            mAppAdapter!!.addCategory(resources.getString(R.string.cat_excluded), AppListAdapter(this, mExcludedApps))
            mListView!!.adapter = mAppAdapter
            mListView!!.onItemClickListener = OnItemClickListener { parent, v, position, id -> onListItemClick(parent as ListView, v, position, id) }
            mListView!!.onItemLongClickListener = OnItemLongClickListener { parent, view, position, id ->
                onListItemLongClick(parent as ListView, view, position, id)
                true
            }
        }
    }

    protected fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if (position >= mSelectedApps.size + mCandidateApps.size + 3) {
            val idx = position - (mSelectedApps.size + mCandidateApps.size + 3)
            val item = mExcludedApps.removeAt(idx)
            item.isExcluded = false
            item.isSelected = false
            mCandidateApps.add(item)
            mAppAdapter!!.notifyDataSetChanged()
        } else if (position >= mSelectedApps.size + 2 && position <= mSelectedApps.size + mCandidateApps.size + 1) {
            val idx = position - (mSelectedApps.size + 2)
            val item = mCandidateApps.removeAt(idx)
            item.isSelected = true
            mSelectedApps.add(item)
            mAppAdapter!!.notifyDataSetChanged()
        } else if (position >= 1 && position <= mSelectedApps.size) {
            val idx = position - 1
            val item = mSelectedApps.removeAt(idx)
            item.isSelected = false
            mCandidateApps.add(0, item)
            mAppAdapter!!.notifyDataSetChanged()
        }

        save()
    }

    protected fun onListItemLongClick(l: ListView, v: View, position: Int, id: Long) {
        if (position >= mSelectedApps.size + mCandidateApps.size + 3) {
            val idx = position - (mSelectedApps.size + mCandidateApps.size + 3)
            val item = mExcludedApps.removeAt(idx)
            item.isExcluded = false
            item.isSelected = false
            mCandidateApps.add(item)
            mAppAdapter!!.notifyDataSetChanged()
        } else if (position >= mSelectedApps.size + 2 && position <= mSelectedApps.size + mCandidateApps.size + 1) {
            val idx = position - (mSelectedApps.size + 2)
            val item = mCandidateApps.removeAt(idx)
            item.isSelected = false
            item.isExcluded = true
            mExcludedApps.add(0, item)
            mAppAdapter!!.notifyDataSetChanged()
        } else if (position >= 1 && position <= mSelectedApps.size) {
            val idx = position - 1
            val item = mSelectedApps.removeAt(idx)
            item.isSelected = false
            item.isExcluded = true
            mExcludedApps.add(0, item)
            mAppAdapter!!.notifyDataSetChanged()
        }

        save()
    }

    private fun save() {
        val selectedStr = StringBuffer()
        for (i in mSelectedApps.indices) {
            if (selectedStr.length > 0)
                selectedStr.append(";").append(mSelectedApps[i].key)
            else
                selectedStr.append(mSelectedApps[i].key)
        }

        val excPkgStr = StringBuffer()
        for (i in mExcludedApps.indices) {
            if (excPkgStr.length > 0)
                excPkgStr.append(", ").append(mExcludedApps[i].component.packageName)
            else
                excPkgStr.append(mExcludedApps[i].component.packageName)
        }

        (application as KlickApplication).sharedPrefs!!.edit()
                .putString(KlickApplication.MORE_ACTIONS, selectedStr.toString())
                .putString(KlickApplication.EXCLUDE_PACKAGES, excPkgStr.toString())
                .commit()

        mApp!!.reloadSelectedApps()
    }

    override fun onDestroy() {
        super.onDestroy()

        mApp!!.removeActivity(this)
        mApp!!.clearIcons()

        System.gc()
    }

    companion object {
        private val TAG = "MoreActionsConfActivity"
    }
}
