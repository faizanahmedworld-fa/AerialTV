package tv.aerialtv.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Four rising bars, like the signal strength on a phone. 1 to 4 bars are lit; 0 means the
 * channel has not been checked yet and every bar stays dim.
 */
class SignalBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var bars: Int = 0
        set(value) {
            val v = value.coerceIn(0, 4)
            if (field == v) return
            field = v
            contentDescription = context.getString(labelFor(v))
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val dim = ContextCompat.getColor(context, R.color.signal_dim)
    private val colors = intArrayOf(
        dim,
        ContextCompat.getColor(context, R.color.signal_weak),
        ContextCompat.getColor(context, R.color.accent),
        ContextCompat.getColor(context, R.color.success),
        ContextCompat.getColor(context, R.color.success),
    )

    init {
        contentDescription = context.getString(labelFor(0))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w * 0.09f
        val barW = (w - gap * 3) / 4
        val radius = barW / 3
        val lit = colors[bars]
        for (i in 0 until 4) {
            val barH = h * (0.4f + 0.2f * i)
            val left = i * (barW + gap)
            rect.set(left, h - barH, left + barW, h)
            paint.color = if (i < bars) lit else dim
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    companion object {
        fun labelFor(bars: Int) = when (bars) {
            1 -> R.string.signal_weak
            2 -> R.string.signal_fair
            3 -> R.string.signal_good
            4 -> R.string.signal_strong
            else -> R.string.signal_checking
        }
    }
}
