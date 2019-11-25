package com.sujitech.tessercubecore.common.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.sujitech.tessercubecore.common.Event
import com.sujitech.tessercubecore.common.ObservableCollection
import com.sujitech.tessercubecore.common.extension.load


fun <T> RecyclerView.getItemsSource(): ObservableCollection<T>? {
    val adapterCopy = this.adapter
    if (adapterCopy is AutoAdapter<*>) {
        return adapterCopy.items as ObservableCollection<T>
    }
    return null
}

fun <T> RecyclerView.updateItemsSource(newItems: List<T>?) {
    val itemsSource = getItemsSource<T>()
    if (itemsSource != null) {
        itemsSource.clear()
        if (newItems != null) {
            itemsSource.addAll(newItems)
        }
    }
}

interface IItemSelector<T> {
    fun selectLayout(item: T): Int
}

open class ItemSelector<T>(private val layoutId: Int) : IItemSelector<T> {
    override fun selectLayout(item: T): Int {
        return layoutId
    }
}

open class AutoAdapter<T>(val itemSelector: IItemSelector<T>)
    : androidx.recyclerview.widget.RecyclerView.Adapter<AutoAdapter.AutoViewHolder>() {
    class AutoViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView)

    constructor(@LayoutRes layoutId: Int = android.R.layout.simple_list_item_1): this(ItemSelector(layoutId))

    private enum class ViewType {
        Item,
        EmptyView,
        Header,
        Footer;

        companion object {
            private val values = values()
            fun getByValue(value: Int) = values.firstOrNull { it.ordinal == value }
        }
    }

    data class ItemClickEventArg<T>(
            val item: T
    )

    data class ActionData<T>(
            @IdRes val id: Int,
            val action: (View, T, position: Int, AutoAdapter<T>) -> Unit
    )

    private val onItemsChanged: (Any, ObservableCollection.CollectionChangedEventArg) -> Unit = { _, _ ->
        notifyDataSetChanged()
    }

    val items = ObservableCollection<T>().apply {
        collectionChanged += onItemsChanged
    }

    override fun getItemViewType(position: Int): Int {
        if (items.count() == 0 && emptyView != 0) {
            return ViewType.EmptyView.ordinal
        }

        if (hasHeader && position == 0) {
            return ViewType.Header.ordinal
        }

        if (hasFooter) {
            var requirePosition = items.count()
            if (hasHeader) {
                requirePosition += 1
            }
            if (position == requirePosition) {
                return ViewType.Footer.ordinal
            }
        }

        var actualPosition = position
        if (hasHeader) {
            actualPosition -= 1
        }

        val item = getItem(actualPosition)
        return itemSelector.selectLayout(item!!)
//        return ViewType.Item.ordinal
    }

    protected open fun getItem(position: Int): T? {
        return items.getOrNull(position)
    }

    private val actions: ArrayList<ActionData<T>> = ArrayList()

    var itemClicked: Event<ItemClickEventArg<T>> = Event()
    var itemLongPressed: Event<ItemClickEventArg<T>> = Event()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutoViewHolder {
        val type = ViewType.getByValue(viewType)
        when (type) {
            ViewType.EmptyView -> {
                if (emptyView != 0) {
                    return AutoViewHolder(LayoutInflater.from(parent.context).inflate(emptyView, parent, false))
                }
            }
            ViewType.Header -> {
                if (headerViewRes != 0) {
                    return AutoViewHolder(LayoutInflater.from(parent.context).inflate(headerViewRes, parent, false))
                }
                val view = headerView
                if (view != null) {
                    return AutoViewHolder(view)
                }
            }
            ViewType.Footer -> {
                if (footerViewRes != 0) {
                    return AutoViewHolder(LayoutInflater.from(parent.context).inflate(footerViewRes, parent, false))
                }
                val view = footerView
                if (view != null) {
                    return AutoViewHolder(view)
                }
            }
            else -> return AutoViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false).apply {
                //                if (itemPadding != 0) {
//                    setPadding(itemPadding, itemPadding, itemPadding, itemPadding)
//                }
            })
        }
        return AutoViewHolder(View(parent.context))
    }

    override fun getItemCount(): Int {
        var count = items.count()
        if (count == 0) {
            count += if (emptyView == 0) {
                0
            } else {
                1
            }
        } else {
            count += if (hasHeader) {
                1
            } else {
                0
            }
            count += if (hasFooter) {
                1
            } else {
                0
            }
        }
        return count
    }

    override fun onBindViewHolder(viewHolder: AutoViewHolder, position: Int) {
        var actualPosition = position
        if (hasHeader) {
            actualPosition -= 1
        }
        if (hasHeader && actualPosition == -1) {
            onBindHeader?.invoke(viewHolder.itemView)
        }

        if (hasFooter && actualPosition == items.count()) {
            onBindFooter?.invoke(viewHolder.itemView)
        }

        val item = getItem(actualPosition)
        if (item != null) {
            viewHolder.itemView.setOnClickListener {
                itemClicked.invoke(viewHolder.itemView, ItemClickEventArg(item))
            }
            viewHolder.itemView.setOnLongClickListener {
                itemLongPressed.invoke(viewHolder.itemView, ItemClickEventArg(item))
                itemLongPressed.any()
            }
            actions.forEach {
                viewHolder.itemView.findViewById<View>(it.id)?.let { view ->
                    it.action.invoke(view, item, actualPosition, this)
                }
            }
        }
    }

    var footerEnabled = true
    var headerEnabled = true

    private val hasHeader
        get() = (headerView != null || headerViewRes != 0) && headerEnabled
    private val hasFooter
        get() = (footerView != null || footerViewRes != 0) && footerEnabled

    private var emptyView: Int = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun whenEmpty(@LayoutRes id: Int) {
        emptyView = id
    }

    private var headerViewRes: Int = 0

    fun withHeader(@LayoutRes id: Int) {
        headerViewRes = id
    }

    private var headerView: View? = null

    fun withHeader(view: View) {
        headerView = view
    }

    private var footerViewRes: Int = 0

    fun withFooter(@LayoutRes id: Int) {
        footerViewRes = id
    }

    private var footerView: View? = null

    fun withFooter(view: View) {
        footerView = view
    }

    private var onBindHeader: ((View) -> Unit)? = null

    fun bindHeader(block: (view: View) -> Unit) {
        onBindHeader = block
    }

    private var onBindFooter: ((View) -> Unit)? = null

    fun bindFooter(block: (view: View) -> Unit) {
        onBindFooter = block
    }

    fun bindImage(@IdRes id: Int, value: (T) -> String) {
        actions.add(ActionData(id) { view, item, _, _ ->
            if (view is ImageView) {
                view.load(value.invoke(item))
            }
        })
    }

    //
//    fun bindCustom(@IdRes id: Int, action: (View, T, position: Int, AutoAdapter<T>) -> Unit) {
//        actions.add(ActionData(id, action))
//    }
//
    fun <K : View> bindCustom(@IdRes id: Int, action: (K, T, position: Int, AutoAdapter<T>) -> Unit) {
        actions.add(ActionData(id) { view, t, position, autoAdapter ->
            if (view as? K != null) {
                action.invoke(view, t, position, autoAdapter)
            }
        })
    }

    fun bindText(@IdRes id: Int, value: (T) -> String) {
        actions.add(ActionData(id) { view, item, _, _ ->
            if (view is TextView) {
                view.text = value.invoke(item)
            }
        })
    }

}
