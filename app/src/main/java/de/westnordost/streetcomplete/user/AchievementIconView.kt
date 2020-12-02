package de.westnordost.streetcomplete.user

import android.content.Context
import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import de.westnordost.streetcomplete.R
import kotlinx.android.synthetic.main.view_achievement_icon.view.*

/** Shows an achievement icon with its frame and level indicator */
class AchievementIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr)  {

    var icon: Drawable?
        set(value) { iconView.setImageDrawable(value) }
        get() = iconView.drawable

    var level: Int
        set(value) {
            levelText.text = value.toString()
            levelText.isInvisible = value < 2
        }
        get() = levelText.text.toString().toIntOrNull() ?: 0

    init {
        inflate(context, R.layout.view_achievement_icon, this)
        outlineProvider = AchievementFrameOutlineProvider
    }
}

object AchievementFrameOutlineProvider : ViewOutlineProvider() {
    private val points = arrayOf(
        0.45,0.98,
        0.47,0.99,
        0.50,1.00,
        0.53,0.99,
        0.55,0.98,

        0.98,0.55,
        0.99,0.53,
        1.00,0.50,
        0.99,0.47,
        0.98,0.45,

        0.55,0.02,
        0.53,0.01,
        0.50,0.00,
        0.47,0.01,
        0.45,0.02,

        0.02,0.45,
        0.01,0.47,
        0.00,0.50,
        0.01,0.53,
        0.02,0.55,

        0.45,0.98
    )

    override fun getOutline(view: View, outline: Outline) {
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return

        val p = Path()
        p.moveTo((points[0] * w).toFloat(), (points[1] * h).toFloat())
        for (i in 2 until points.size step 2) {
            p.lineTo((points[i] * w).toFloat(), (points[i+1] * h).toFloat())
        }
        outline.setConvexPath(p)
    }
}
