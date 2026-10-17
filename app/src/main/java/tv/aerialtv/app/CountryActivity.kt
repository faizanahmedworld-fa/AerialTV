package tv.aerialtv.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** First screen on a new install, and reachable later from the channel list. */
class CountryActivity : AppCompatActivity() {

    private sealed interface Row
    private data class Header(val text: String) : Row
    private data class Item(val country: Country, val selected: Boolean) : Row

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_country)

        val prefs = Prefs(this)
        val all = Countries.all(this)
        val chosen = all.firstOrNull { it.code == prefs.country }
        val suggested = (listOfNotNull(chosen) + Countries.detect(this, all).take(2) +
            listOfNotNull(all.firstOrNull { it.code == "int" })).distinct()

        val rows = buildList {
            if (suggested.isNotEmpty()) {
                add(Header(getString(R.string.suggested)))
                suggested.forEach { add(Item(it, it == chosen)) }
            }
            add(Header(getString(R.string.all_countries)))
            all.filter { it !in suggested }.forEach { add(Item(it, false)) }
        }

        val list = findViewById<RecyclerView>(R.id.countryList)
        list.layoutManager = LinearLayoutManager(this)
        list.itemAnimator = null
        list.adapter = Adapter(rows) { country ->
            prefs.country = country.code
            setResult(RESULT_OK)
            finish()
        }
        list.post { list.findViewHolderForAdapterPosition(1)?.itemView?.requestFocus() }
    }

    private class Adapter(
        private val rows: List<Row>,
        private val onPick: (Country) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (rows[position] is Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == 0) R.layout.item_country_header else R.layout.item_country
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val text = holder.itemView as TextView
            when (val row = rows[position]) {
                is Header -> text.text = row.text
                is Item -> {
                    text.text = row.country.label
                    text.isActivated = row.selected
                    text.setOnClickListener { onPick(row.country) }
                }
            }
        }
    }
}
