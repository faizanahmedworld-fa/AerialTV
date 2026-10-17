package tv.aerialtv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load

data class GuideCategory(val id: String, val label: String, val count: Int)

fun ImageView.loadLogo(url: String?) {
    if (url.isNullOrBlank()) {
        setImageResource(R.drawable.ic_tv_placeholder)
    } else {
        load(url) {
            crossfade(false)
            placeholder(R.drawable.ic_tv_placeholder)
            error(R.drawable.ic_tv_placeholder)
        }
    }
}

class CategoryAdapter(
    private val onFocus: (GuideCategory) -> Unit,
    private val onClick: (GuideCategory) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {

    private var items: List<GuideCategory> = emptyList()
    private var selected: String? = null

    fun submit(list: List<GuideCategory>) {
        items = list
        notifyDataSetChanged()
    }

    init {
        setHasStableIds(true)
    }

    fun setSelected(id: String) {
        if (selected == id) return
        val old = items.indexOfFirst { it.id == selected }
        selected = id
        if (old >= 0) notifyItemChanged(old)
        items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun getItemId(position: Int) = items[position].id.hashCode().toLong()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        holder.count.text = item.count.toString()
        holder.itemView.isActivated = item.id == selected
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onFocus(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.categoryLabel)
        val count: TextView = view.findViewById(R.id.categoryCount)
    }
}

class ChannelAdapter(
    private val isFavorite: (Channel) -> Boolean,
    private val onClick: (Channel) -> Unit,
    private val onLongClick: (Channel) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

    var items: List<Channel> = emptyList()
        private set
    var playingKey: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int) = items[position].number.toLong()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ch = items[position]
        val ctx = holder.itemView.context
        holder.number.text = ch.number.toString()
        holder.name.text = ch.name
        holder.name.setTextColor(
            ContextCompat.getColor(ctx, if (ch.key == playingKey) R.color.accent else R.color.text_primary)
        )
        holder.category.text = ch.categories.joinToString("  ·  ") { Categories.label(it) }
        holder.favorite.visibility = if (isFavorite(ch)) View.VISIBLE else View.GONE
        holder.logo.loadLogo(ch.logo)
        holder.itemView.setOnClickListener { onClick(ch) }
        holder.itemView.setOnLongClickListener {
            onLongClick(ch)
            notifyItemChanged(holder.bindingAdapterPosition)
            true
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.channelNumber)
        val logo: ImageView = view.findViewById(R.id.channelLogo)
        val name: TextView = view.findViewById(R.id.channelName)
        val category: TextView = view.findViewById(R.id.channelCategory)
        val favorite: TextView = view.findViewById(R.id.channelFavorite)
    }
}
