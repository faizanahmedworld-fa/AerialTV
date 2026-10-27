package tv.aerialtv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** One line in a pick list: a section header or something to choose. */
sealed interface PickRow {
    data class Header(val text: String) : PickRow

    data class Item(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val emoji: String? = null,
        val icon: Int = 0,
        val selected: Boolean = false,
    ) : PickRow
}

/**
 * Lists of countries, languages and settings rows all look the same: a flag or icon, a title,
 * maybe a second line, and a tick or an arrow on the right. Headers split the sections.
 */
class PickAdapter(
    var trailing: Trailing,
    private val onPick: (PickRow.Item) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Trailing { TICK, ARROW, NONE }

    var rows: List<PickRow> = emptyList()
        private set

    fun submit(list: List<PickRow>) {
        rows = list
        notifyDataSetChanged()
    }

    /** Flips one row's tick without rebuilding the whole list. */
    fun setSelected(id: String, selected: Boolean) {
        val i = rows.indexOfFirst { it is PickRow.Item && it.id == id }
        if (i < 0) return
        rows = rows.toMutableList().also { it[i] = (it[i] as PickRow.Item).copy(selected = selected) }
        notifyItemChanged(i)
    }

    fun firstItemPosition(): Int = rows.indexOfFirst { it is PickRow.Item }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = if (rows[position] is PickRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == 0) R.layout.item_header else R.layout.item_pick
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (viewType == 0) HeaderHolder(view) else ItemHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PickRow.Header -> (holder as HeaderHolder).text.text = row.text
            is PickRow.Item -> (holder as ItemHolder).bind(row)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view as TextView
    }

    inner class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val emoji: TextView = view.findViewById(R.id.pickEmoji)
        private val icon: ImageView = view.findViewById(R.id.pickIcon)
        private val title: TextView = view.findViewById(R.id.pickTitle)
        private val subtitle: TextView = view.findViewById(R.id.pickSubtitle)
        private val tick: ImageView = view.findViewById(R.id.pickTrailing)

        fun bind(row: PickRow.Item) {
            title.text = row.title
            subtitle.text = row.subtitle.orEmpty()
            subtitle.visibility = if (row.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            emoji.text = row.emoji.orEmpty()
            emoji.visibility = if (row.emoji.isNullOrBlank()) View.GONE else View.VISIBLE
            icon.visibility = if (row.icon == 0) View.GONE else View.VISIBLE
            if (row.icon != 0) icon.setImageResource(row.icon)
            when (trailing) {
                Trailing.TICK -> {
                    tick.setImageResource(R.drawable.ic_check)
                    tick.visibility = if (row.selected) View.VISIBLE else View.INVISIBLE
                }
                Trailing.ARROW -> {
                    tick.setImageResource(R.drawable.ic_chevron_right)
                    tick.visibility = View.VISIBLE
                }
                Trailing.NONE -> tick.visibility = View.GONE
            }
            itemView.isActivated = row.selected
            itemView.setOnClickListener { onPick(row) }
            itemView.setOnFocusChangeListener { v, hasFocus -> v.focusScale(hasFocus, 1.02f) }
        }
    }
}

/** A choice to make with one press: the big cards on the "Who's watching" step. */
data class Choice(val id: String, val icon: Int, val title: String, val subtitle: String)

class ChoiceAdapter(private val onPick: (Choice) -> Unit) : RecyclerView.Adapter<ChoiceAdapter.Holder>() {

    private var items: List<Choice> = emptyList()
    var selected: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submit(list: List<Choice>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_choice, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = items[position]
        holder.icon.setImageResource(c.icon)
        holder.title.text = c.title
        holder.subtitle.text = c.subtitle
        holder.itemView.isActivated = c.id == selected
        holder.tick.visibility = if (c.id == selected) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener { onPick(c) }
        holder.itemView.setOnFocusChangeListener { v, hasFocus -> v.focusScale(hasFocus, 1.04f) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.choiceIcon)
        val title: TextView = view.findViewById(R.id.choiceTitle)
        val subtitle: TextView = view.findViewById(R.id.choiceSubtitle)
        val tick: ImageView = view.findViewById(R.id.choiceTick)
    }
}

/** Pills that toggle on and off: the interests grid. */
data class Chip(val id: String, val icon: Int, val label: String)

class ChipAdapter(private val onToggle: (Chip, Boolean) -> Unit) : RecyclerView.Adapter<ChipAdapter.Holder>() {

    private var items: List<Chip> = emptyList()
    private val selected = LinkedHashSet<String>()

    fun submit(list: List<Chip>, chosen: Set<String>) {
        items = list
        selected.clear()
        selected += chosen
        notifyDataSetChanged()
    }

    fun chosen(): Set<String> = selected.toSet()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chip, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val chip = items[position]
        val on = chip.id in selected
        holder.icon.setImageResource(if (on) R.drawable.ic_check else chip.icon)
        holder.label.text = chip.label
        holder.itemView.isActivated = on
        val color = holder.itemView.context.getColor(if (on) R.color.on_accent else R.color.text_primary)
        holder.label.setTextColor(color)
        holder.icon.setColorFilter(color)
        holder.itemView.setOnClickListener {
            val now = chip.id !in selected
            if (now) selected += chip.id else selected -= chip.id
            notifyItemChanged(holder.bindingAdapterPosition)
            onToggle(chip, now)
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus -> v.focusScale(hasFocus, 1.05f) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.chipIcon)
        val label: TextView = view.findViewById(R.id.chipLabel)
    }
}
