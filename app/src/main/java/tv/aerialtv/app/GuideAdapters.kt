package tv.aerialtv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class GuideCategory(val id: String, val label: String, val count: Int, val icon: Int)

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

    fun setSelected(id: String?) {
        if (selected == id) return
        val old = items.indexOfFirst { it.id == selected }
        selected = id
        if (old >= 0) notifyItemChanged(old)
        items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    fun labelOf(id: String): String = items.firstOrNull { it.id == id }?.label.orEmpty()

    override fun getItemId(position: Int) = items[position].id.hashCode().toLong()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        holder.count.text = item.count.toString()
        holder.icon.setImageResource(item.icon)
        holder.itemView.isActivated = item.id == selected
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            v.focusScale(hasFocus, 1.02f)
            if (hasFocus) onFocus(item)
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.categoryIcon)
        val label: TextView = view.findViewById(R.id.categoryLabel)
        val count: TextView = view.findViewById(R.id.categoryCount)
    }
}

class ChannelAdapter(
    private val isFavorite: (Channel) -> Boolean,
    private val signalOf: (Channel) -> Int,
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

    /** Only the bars changed: repaint them without touching focus or the logo. */
    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) return super.onBindViewHolder(holder, position, payloads)
        holder.signal.bars = signalOf(items[position])
    }

    fun refreshSignals() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, SIGNAL)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ch = items[position]
        val ctx = holder.itemView.context
        val playing = ch.key == playingKey
        holder.number.text = ch.number.toString()
        holder.name.text = ch.name
        holder.name.setTextColor(ContextCompat.getColor(ctx, if (playing) R.color.accent else R.color.text_primary))
        holder.playing.visibility = if (playing) View.VISIBLE else View.GONE
        holder.category.text = ch.categories.joinToString("  ·  ") { Categories.label(ctx, it) }
        holder.signal.bars = signalOf(ch)
        holder.favorite.visibility = if (isFavorite(ch)) View.VISIBLE else View.GONE
        holder.logo.loadLogo(ch.logo)
        holder.itemView.setOnClickListener { onClick(ch) }
        holder.itemView.setOnFocusChangeListener { v, hasFocus -> v.focusScale(hasFocus, 1.02f) }
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
        val favorite: ImageView = view.findViewById(R.id.channelFavorite)
        val playing: ImageView = view.findViewById(R.id.channelPlaying)
        val signal: SignalBarsView = view.findViewById(R.id.channelSignal)
    }

    private companion object {
        const val SIGNAL = "signal"
    }
}
