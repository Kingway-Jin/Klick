package im.kingway.jin.klick

import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.BaseAdapter
import java.util.*

abstract class CategoryAdapter : BaseAdapter() {
    private val categories = ArrayList<Category>()

    fun addCategory(title: String, adapter: Adapter) {
        categories.add(Category(title, adapter))
    }

    override fun getCount(): Int {
        var total = 0

        for ((_, adapter) in categories) {
            total += adapter.count + 1
        }

        return total
    }

    override fun getItem(position: Int): Any? {
        var pos = position
        for (category in categories) {
            if (pos == 0) {
                return category
            }

            val size = category.adapter.count + 1
            if (pos < size) {
                return category.adapter.getItem(pos - 1)
            }
            pos -= size
        }

        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getViewTypeCount(): Int {
        var total = 1

        for ((_, adapter) in categories) {
            total += adapter.viewTypeCount
        }

        return total
    }

    override fun getItemViewType(position: Int): Int {
        var pos = position
        var typeOffset = 1

        for ((_, adapter) in categories) {
            if (pos == 0) {
                return 0
            }

            val size = adapter.count + 1
            if (pos < size) {
                return typeOffset + adapter.getItemViewType(pos - 1)
            }
            pos -= size

            typeOffset += adapter.viewTypeCount
        }

        return -1
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        var pos = position
        var categoryIndex = 0

        for ((title, adapter) in categories) {
            if (pos == 0) {
                return getTitleView(title, categoryIndex, convertView, parent)
            }
            val size = adapter.count + 1
            if (pos < size) {
                return adapter.getView(pos - 1, convertView, parent)
            }
            pos -= size

            categoryIndex++
        }

        return null
    }

    protected abstract fun getTitleView(caption: String, index: Int, convertView: View?, parent:
    ViewGroup): View

}