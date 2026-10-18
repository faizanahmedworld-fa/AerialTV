package tv.aerialtv.app

import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** Small animation and image helpers shared by every screen. */

fun ImageView.loadLogo(url: String?) {
    if (url.isNullOrBlank()) {
        setImageResource(R.drawable.ic_tv_placeholder)
    } else {
        load(url) {
            crossfade(180)
            placeholder(R.drawable.ic_tv_placeholder)
            error(R.drawable.ic_tv_placeholder)
        }
    }
}

/** Rows and cards grow a touch when the remote lands on them, so the eye finds the cursor. */
fun View.focusScale(hasFocus: Boolean, amount: Float = 1.03f) {
    animate().cancel()
    animate()
        .scaleX(if (hasFocus) amount else 1f)
        .scaleY(if (hasFocus) amount else 1f)
        .setDuration(160)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

fun View.fadeIn(duration: Long = 200, translateY: Float = 0f) {
    animate().cancel()
    if (!isShown) {
        alpha = 0f
        translationY = translateY
    }
    visibility = View.VISIBLE
    animate().alpha(1f).translationY(0f).setDuration(duration).setInterpolator(DecelerateInterpolator()).start()
}

fun View.fadeOut(duration: Long = 160, translateY: Float = 0f, then: (() -> Unit)? = null) {
    if (visibility != View.VISIBLE) {
        then?.invoke()
        return
    }
    animate().cancel()
    animate().alpha(0f).translationY(translateY).setDuration(duration).withEndAction {
        visibility = View.GONE
        alpha = 1f
        translationY = 0f
        then?.invoke()
    }.start()
}

/** Plays the list's rows in one after another the next time they lay out. */
fun RecyclerView.riseIn() {
    layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_list)
    scheduleLayoutAnimation()
}
