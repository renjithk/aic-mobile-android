package edu.artic.adapter

import android.annotation.SuppressLint
import android.arch.core.executor.ArchTaskExecutor
import android.arch.paging.AsyncPagedListDiffer
import android.arch.paging.PagedList
import android.content.Context
import android.support.annotation.LayoutRes
import android.support.annotation.UiThread
import android.support.v7.recyclerview.extensions.AsyncDifferConfig
import android.support.v7.util.DiffUtil
import android.support.v7.util.ListUpdateCallback
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.lang.IllegalArgumentException
import java.lang.IndexOutOfBoundsException
import java.util.ArrayList

/**
 * Description: Default implementation of [RecyclerView.Adapter].
 *
 * Use this class to greatly simplify integration with MVVM (that's
 * `Model-View-ViewModel`) architectures. Main selling points are as
 * follows:
 */
abstract class BaseRecyclerViewAdapter<TModel, VH : BaseViewHolder>(
        private val diffItemCallback: DiffUtil.ItemCallback<TModel> = object :
                DiffUtil.ItemCallback<TModel>() {
            override fun areItemsTheSame(oldItem: TModel, newItem: TModel): Boolean =
                    areContentsTheSame(oldItem, newItem)

            override fun areContentsTheSame(oldItem: TModel, newItem: TModel): Boolean =
                    oldItem == newItem
        }
) : RecyclerView.Adapter<BaseViewHolder>() {

    interface OnItemClickListener<in T> {

        /**
         * Callback for [on-click listeners][View.setOnClickListener]. Always invoked
         * on main thread.
         *
         * @param position   The position in [itemsList] that was clicked.
         * @param item       The item from the list.
         * @param viewHolder The view holder that was clicked.
         */
        fun onItemClick(position: Int, item: T, viewHolder: BaseViewHolder)
    }

    interface OnItemLongClickListener<in T> {

        /**
         * Callback for [on-long-click listeners][View.setOnLongClickListener]. Always
         * invoked on main thread.
         *
         * @param position   The position in [itemsList] that was clicked.
         * @param item       The item from the list.
         * @param viewHolder The view holder that was clicked.
         *
         * @return true if view consumed the click.
         */
        fun onItemLongPress(position: Int, item: T, viewHolder: BaseViewHolder): Boolean
    }

    /**
     * Interface for when headers or footers are called.
     *
     * One instance should be registered for the headers, and a different
     * instance should be registered for the footers. While permitted, we
     * do not recommend using the same callback for both types of
     * [BaseViewHolder]s.
     *
     * (HF -> Header/Footer)
     */
    interface OnHFItemClickListener {

        /**
         * Called when a header or footer item is clicked.
         *
         * This listener is registered with a [BaseRecyclerViewAdapter]
         * via [onHeaderClickListener] and/or [onFooterClickListener]. Which
         * of those two methods it is registered with determines what kind of
         * parameters you should expect.
         *
         * @param position   The position within the headers/footers list.
         * @param viewHolder The view holder clicked.
         */
        fun onItemClick(position: Int, viewHolder: BaseViewHolder)
    }

    private val headerHolders = ArrayList<BaseViewHolder>()
    private val headerLayoutIds = ArrayList<Int>()
    private val footerHolders = ArrayList<BaseViewHolder>()
    private val footerLayoutIds = ArrayList<Int>()

    var onItemClickListener: OnItemClickListener<TModel>? = null
        set(onItemClickListener) {
            field = onItemClickListener
            notifyDataSetChanged()
        }
    var onItemLongClickListener: OnItemLongClickListener<TModel>? = null
        set(itemLongPressListener) {
            field = itemLongPressListener
            notifyDataSetChanged()
        }

    private var onHeaderClickListener: OnHFItemClickListener? = null
    private var onFooterClickListener: OnHFItemClickListener? = null

    private val pagedListAdapterHelper: AsyncPagedListDiffer<TModel> by lazy {
        AsyncPagedListDiffer(adapterHelperWrapper,
                AsyncDifferConfig.Builder<TModel>(diffItemCallback).build())
    }

    /**
     * Backing list of data. Update this atomically with [setItemsList].
     */
    private var itemsList: List<TModel> = arrayListOf()

    private val provider = dataSourceFactory { ListDataSource(itemsList) }

    /**
     * Set up backing content for this adapter to display. Each item in the given
     * list corresponds directly to one [BaseViewHolder].
     *
     * If the list is not a [PagedList], it will be split into 10-item
     * pages for efficiency's sake.
     *
     * **For dynamic loading:**
     * 1. Instantiate a [DataSource][android.arch.paging.DataSource] to provide items
     * 2. Call [setPagedList] with a [PagedList] instead of invoking this method
     */
    @SuppressLint("RestrictedApi")
    open fun setItemsList(itemsList: List<TModel>?) {
        this.itemsList = itemsList ?: arrayListOf()
        if (itemsList is PagedList) { // submit directly
            pagedListAdapterHelper.submitList(itemsList)
        } else {
            pagedListAdapterHelper.submitList(PagedList.Builder(provider.create(),
                    PagedList.Config.Builder().setPageSize(10)
                            .setEnablePlaceholders(false)
                            .setPrefetchDistance(10)
                            .build())
                    .setNotifyExecutor(ArchTaskExecutor.getMainThreadExecutor())
                    .setFetchExecutor(ArchTaskExecutor.getIOThreadExecutor())
                    .build())
        }
    }

    /**
     * Sets a paged list on this [BaseRecyclerViewAdapter]. Use this for paginated content.
     */
    fun setPagedList(pagedList: PagedList<TModel>) {
        pagedListAdapterHelper.submitList(pagedList)
    }

    fun setOnHeaderClickListener(onHeaderClickListener: OnHFItemClickListener) {
        this.onHeaderClickListener = onHeaderClickListener
    }

    fun setOnFooterClickListener(onFooterClickListener: OnHFItemClickListener) {
        this.onFooterClickListener = onFooterClickListener
    }

    /**
     * Clears existing headers out.
     */
    fun clearHeaders() {
        val count = headerHolders.size
        headerHolders.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun addHeaderView(layoutResId: Int, parent: ViewGroup) {
        addHeaderHolder(BaseViewHolder(parent, layoutResId))
    }

    fun addHeaderHolder(baseViewHolder: BaseViewHolder) {
        preventReuseOfIdsFrom(footerLayoutIds, baseViewHolder.layout, "footer")
        headerHolders.add(baseViewHolder)
        headerLayoutIds.add(baseViewHolder.layout)
        notifyItemInserted(headersCount - 1)
    }

    fun addFooterHolder(baseViewHolder: BaseViewHolder) {
        preventReuseOfIdsFrom(headerLayoutIds, baseViewHolder.layout, "header")
        footerHolders.add(baseViewHolder)
        footerLayoutIds.add(baseViewHolder.layout)
        notifyItemInserted(footerStartIndex + footersCount)
    }

    fun addFooterView(footerResId: Int, parent: ViewGroup) {
        addFooterHolder(BaseViewHolder(parent, footerResId))
    }

    fun getHeaderHolders(): List<BaseViewHolder> {
        return headerHolders
    }

    fun getFooterHolders(): List<BaseViewHolder> {
        return footerHolders
    }

    open protected fun onCurrentListChanged(pagedList: PagedList<TModel>) = Unit

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        var viewHolder: BaseViewHolder? = headerViewHolderForViewType(viewType)
        if (viewHolder != null) {
            if (onHeaderClickListener != null) {
                val finalViewHolder = viewHolder
                viewHolder.itemView.setOnClickListener {
                    val position = getViewHolderPosition(finalViewHolder)
                    onHeaderClickListener?.onItemClick(position, finalViewHolder)
                }
            }
        } else {
            viewHolder = footerHolderForViewType(viewType)
            if (viewHolder != null) {
                if (onFooterClickListener != null) {
                    val finalViewHolder1 = viewHolder
                    viewHolder.itemView.setOnClickListener {
                        val position = getViewHolderPosition(finalViewHolder1) - footerStartIndex - 1
                        onFooterClickListener?.onItemClick(position, finalViewHolder1)
                    }
                }
            } else {
                viewHolder = onCreateItemViewHolder(parent, viewType)

                if (this.onItemClickListener != null) {
                    val finalViewHolder2 = viewHolder
                    setItemClickListener(viewHolder, View.OnClickListener {
                        val position = getAdjustedItemPosition(getViewHolderPosition(finalViewHolder2))
                        onItemPositionClicked(finalViewHolder2, position)
                    })
                }

                if (this.onItemLongClickListener != null) {
                    val finalViewHolder2 = viewHolder
                    setItemLongPressListener(viewHolder, View.OnLongClickListener {
                        val position = getAdjustedItemPosition(getViewHolderPosition(finalViewHolder2))
                        return@OnLongClickListener onItemPositionLongClicked(finalViewHolder2, position)
                    })
                }
            }
        }
        if (viewHolder.itemView.parent === parent) {
            // The viewHolder wasn't fully recycled - RecyclerView.Recycler might choke if we're not careful
            try {
                parent.removeView(viewHolder.itemView)
            } catch (e: Throwable) {
                // ignored
            }
        }
        return viewHolder
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (position < headersCount) {
            onBindItemHeaderViewHolder(holder, position)
        } else if (position > footerStartIndex) {
            onBindItemFooterViewHolder(holder, position - footerStartIndex - 1)
        } else {
            val adjusted = getAdjustedItemPosition(position)
            onBindViewHolder(holder as VH, getItemOrNull(adjusted), adjusted)
        }
        // workaround for adapters not bound by the containing RecyclerView.
        holder.itemView.setTag(R.id.tag_position, position)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onViewRecycled(holder: BaseViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.itemView.getTag(R.id.tag_position) as Int
        when {
            isPositionItem(position) -> onItemViewHolderRecycled(holder as VH, position)
            isHeaderPosition(position) -> onHeaderViewHolderRecycled(holder, position)
            else -> onFooterViewHolderRecycled(holder, position)
        }
    }

    /**
     * Release temporary resources here. Implementations using
     * [android.arch.lifecycle.ViewModel]s in particular may want
     * to perform some custom logic here.
     */
    open fun onItemViewHolderRecycled(holder: VH, position: Int) = Unit

    open fun onHeaderViewHolderRecycled(holder: BaseViewHolder, position: Int) = Unit

    open fun onFooterViewHolderRecycled(holder: BaseViewHolder, position: Int) = Unit

    fun getItemOrNull(position: Int): TModel? {
        if (position < 0 || position >= itemsListCount) {
            return null
        } else {
            return pagedListAdapterHelper.getItem(position)
        }
    }

    fun getItem(position: Int): TModel {
        return pagedListAdapterHelper.getItem(position)
                ?: throw IndexOutOfBoundsException("Invalid position $position")
    }

    override fun getItemCount(): Int {
        return headersCount + fullItemsCount + footersCount
    }

    private val fullItemsCount: Int
        get() = itemsListCount + extraItemsCount

    val itemsListCount: Int
        get() = pagedListAdapterHelper.itemCount

    protected open val extraItemsCount: Int
        get() = 0

    override fun getItemViewType(position: Int): Int {
        val viewType: Int
        if (position < headersCount) {
            viewType = headerLayoutIds[position]
        } else if (position > footerStartIndex && footersCount > 0) {
            viewType = footerLayoutIds[position - footerStartIndex - 1]
        } else {
            viewType = getViewType(position - headersCount)
        }
        return viewType
    }

    /**
     * Returns the index of item in the [itemsList]. Use [rawItemIndexOf] for index in adapter view.
     * for true item position.
     */
    fun itemIndexOf(model: TModel) = getItemsList().indexOf(model)

    fun rawItemIndexOf(model: TModel) = getRawPosition(itemIndexOf(model))

    val headersCount: Int
        get() = headerHolders.size

    val footersCount: Int
        get() = footerHolders.size

    fun isHeaderPosition(rawPosition: Int) = rawPosition < headersCount

    fun isFooterPosition(rawPosition: Int) = rawPosition > footerStartIndex

    fun getItemsList(): List<TModel> = pagedListAdapterHelper.currentList ?: arrayListOf()

    /**
     * Variant of [getItemViewType] with the `position`
     * parameter corrected to account for header items.
     * Subclasses are strongly recommended to return the
     * layout resource Id of the view instead to ensure
     * types never clash.
     *
     * To specify the layout of a header or footer ViewHolder,
     * use [addHeaderView] or [addFooterView], respectively.
     *
     * @param position the position within the items-list dataset.
     *
     * @return The layout associated with the [TModel].
     */
    @LayoutRes
    protected abstract fun getLayoutResId(position: Int): Int

    /**
     * Determine what layout should be inflated for the [BaseViewHolder] at
     * the given [position]. Note that the parameter is already adjusted to
     * account for header items.
     *
     * Please use [getLayoutResId] instead. If two [BaseViewHolder]s possess
     * the same layout but require different binding logic, isolate that
     * distinction to the 3-args `onBindViewHolder(VH, TModel?, Int)`.
     */
    @Deprecated("Implementors should not need to override this method; please migrate to 'getLayoutResId'.", ReplaceWith("getLayoutResId(position)"))
    protected open fun getViewType(position: Int) = getLayoutResId(position)

    protected open fun setItemClickListener(viewHolder: BaseViewHolder, onClickListener: View.OnClickListener) {
        viewHolder.itemView.setOnClickListener(onClickListener)
    }

    protected open fun setItemLongPressListener(viewHolder: BaseViewHolder, onClickListener: View.OnLongClickListener) {
        viewHolder.itemView.setOnLongClickListener(onClickListener)
    }

    protected open fun onBindItemFooterViewHolder(holder: BaseViewHolder, footerPosition: Int) {}

    @Suppress("UNUSED_PARAMETER")
    protected open fun onBindItemHeaderViewHolder(holder: BaseViewHolder, headerPosition: Int) {
    }

    /**
     * Subclasses should implement this to create ViewHolders for the main set of items.
     * This does not cover any headers or footers that may be present in the layout.

     * @param parent   the RecyclerView itself
     * *
     * @param viewType what type of view to create
     * *
     * @return a new 'item' ViewHolder
     */
    protected abstract fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): VH

    protected abstract fun onBindViewHolder(holder: VH, item: TModel?, position: Int)

    /**
     * @return The starting index of footer views (if any exist)
     */
    val footerStartIndex: Int
        get() = headersCount + fullItemsCount - 1

    protected fun isPositionItem(rawPosition: Int) = rawPosition in headersCount..footerStartIndex

    protected fun getAdjustedItemPosition(rawPosition: Int) = rawPosition - headersCount

    protected fun getRawPosition(itemPosition: Int) = itemPosition + headersCount

    /**
     * Dispatch point for [onClick][View.setOnClickListener] events.
     *
     * The 'position' integer is always that of the [BaseViewHolder] at the instant
     * it was clicked; precise fallback logic for detached views, unusual conditions,
     * and so forth are covered by [BaseRecyclerViewAdapter.getViewHolderPosition].
     */
    protected open fun onItemPositionClicked(viewHolder: BaseViewHolder, position: Int) {
        getItemOrNull(position)?.let { item ->
            this.onItemClickListener?.onItemClick(position, item, viewHolder)
            onItemClicked(position, item, viewHolder)
        }
    }

    /**
     * Delegate for clicks on [this adapter's itemViews][BaseViewHolder.itemView].
     *
     * Always called by [onItemPositionClicked] on the UI Thread, right after
     * [onItemClickListener?.onItemClick][OnItemClickListener.onItemClick].
     */
    @UiThread
    protected open fun onItemClicked(position: Int, item: TModel, viewHolder: BaseViewHolder) = Unit

    @UiThread
    protected open fun onItemPositionLongClicked(viewHolder: BaseViewHolder, position: Int): Boolean {
        return getItemOrNull(position)?.let { item ->
            return@let this.onItemLongClickListener?.onItemLongPress(position, item, viewHolder)
        } ?: false
    }

    private fun getViewHolderPosition(viewHolder: BaseViewHolder): Int {
        var position = viewHolder.adapterPosition
        // no contained recyclerview, but this might be part of a LinearLayout or BaseAdapter
        if (position == RecyclerView.NO_POSITION) {
            position = viewHolder.layoutPosition
            if (position == RecyclerView.NO_POSITION) {
                val tagPosition = viewHolder.itemView.getTag(R.id.tag_position) as Int?
                if (tagPosition != null) {
                    position = tagPosition
                }
            }
        }
        return position
    }

    private fun headerViewHolderForViewType(viewType: Int): BaseViewHolder? {
        for (i in headerLayoutIds.indices) {
            val resId = headerLayoutIds[i]
            if (viewType == resId) {
                return headerHolders[i]
            }
        }
        return null
    }

    private fun footerHolderForViewType(viewType: Int): BaseViewHolder? {
        for (i in footerLayoutIds.indices) {
            val resId = footerLayoutIds[i]
            if (viewType == resId) {
                return footerHolders[i]
            }
        }
        return null
    }

    fun clear() {
        pagedListAdapterHelper.currentList?.clear()
        notifyDataSetChanged()
    }

    /**
     * Bridges updates from the [PagedList] to the adapter based on headers/footers.
     */
    private val adapterHelperWrapper = object : ListUpdateCallback {
        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(getRawPosition(position), count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(getRawPosition(fromPosition), toPosition)
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(getRawPosition(position), count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(getRawPosition(position), count)
        }
    }


    companion object {

        @JvmStatic
        fun inflateView(parent: ViewGroup, @LayoutRes layoutResId: Int): View {
            return LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        }

        @JvmStatic
        fun inflateView(context: Context, @LayoutRes layoutResId: Int): View {
            return LayoutInflater.from(context).inflate(layoutResId, null)
        }

        /**
         * If the headers and footers of a [BaseRecyclerViewAdapter] share any
         * ids, RecyclerView.ViewPrefetcher will crash non-deterministically
         * in [RecyclerView.Recycler.recycleViewHolderInternal].
         *
         *
         * To preempt that, this method throws an IllegalArgumentException as
         * soon as a ViewHolder with such a [getViewType] is proffered to
         * [addFooterHolder] or [addHeaderHolder], which should make early
         * detection and diagnosis of this problem much more feasible.
         *
         *
         * @param forbiddenIds  a list of ids already in use. Typically [headerLayoutIds] or [footerLayoutIds]
         * @param id            the suggested id
         * @param forbiddenType a name for the type of ids - this will appear in the exception message
         */
        @JvmStatic
        protected fun preventReuseOfIdsFrom(
                forbiddenIds: List<Int>, id: Int, forbiddenType: String) {
            if (forbiddenIds.contains(id)) {
                throw IllegalArgumentException(
                        "The layout id $id is already registered to a $forbiddenType."
                )
            }
        }
    }
}

/**
 * Convenience call that utilizes just the viewmodel
 */
inline fun <T> onItemClickListener(crossinline function: (T) -> Unit) = object : BaseRecyclerViewAdapter.OnItemClickListener<T> {
    override fun onItemClick(position: Int, item: T, viewHolder: BaseViewHolder) {
        function(item)
    }
}

inline fun <T> onItemClickListenerWithPosition(crossinline function: (pos : Int, T) -> Unit) = object : BaseRecyclerViewAdapter.OnItemClickListener<T> {
    override fun onItemClick(position: Int, item: T, viewHolder: BaseViewHolder) {
        function(position, item)
    }
}

/**
 * Convenience call that utilizes just the viewmodel
 */
inline fun <T> onItemLongClickListener(crossinline function: (T) -> Boolean) = object : BaseRecyclerViewAdapter.OnItemLongClickListener<T> {
    override fun onItemLongPress(position: Int, item: T, viewHolder: BaseViewHolder): Boolean {
        return function(item)
    }
}

/**
 * Convenience call that utilizes just the viewHolder
 */
inline fun onHFClickListener(crossinline function: (BaseViewHolder) -> Unit) = object : BaseRecyclerViewAdapter.OnHFItemClickListener {

    override fun onItemClick(position: Int, viewHolder: BaseViewHolder) {
        function(viewHolder)
    }
}

/**
 * Convenience call
 */
inline fun onHFClickListener(crossinline function: (Int, BaseViewHolder) -> Unit) = object : BaseRecyclerViewAdapter.OnHFItemClickListener {

    override fun onItemClick(position: Int, viewHolder: BaseViewHolder) {
        function(position, viewHolder)
    }
}
