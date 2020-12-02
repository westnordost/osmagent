package de.westnordost.streetcomplete.user

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.getYamlObject
import java.util.*
import kotlin.math.min

/** Show a flag of a country in a circle */
class CircularFlagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private var drawable: Drawable? = null
    private var boundsOffset: Rect = Rect()

    var countryCode: String? = null
    set(value) {
        field = value
        updateCountryCode(value)
    }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // make it square
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val size = if ((widthMode == MeasureSpec.EXACTLY) xor (heightMode == MeasureSpec.EXACTLY)) {
            if (widthMode == MeasureSpec.EXACTLY) width else height
        } else min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        clipPath.addOval(RectF(0f, 0f, w.toFloat(), w.toFloat()), Path.Direction.CW)
    }

    override fun setRotation(rotation: Float) {
        /* this view can't handle rotation properly */
    }

    override fun onDraw(canvas: Canvas) {
        // clip it round
        canvas.clipPath(clipPath)
        val d = drawable
        if (d != null) {
            d.setBounds(
                boundsOffset.left,
                boundsOffset.top,
                width - boundsOffset.right,
                height - boundsOffset.bottom
            )
            d.draw(canvas)
        }
        super.onDraw(canvas)
    }

    private fun updateCountryCode(countryCode: String?) {
        if (countryCode == null) {
            drawable = null
        } else {
            val resId = getFlagResIdWithFallback(countryCode)
            if (resId == 0) {
                drawable = null
            } else {
                val d = context.getDrawable(resId)!!
                val alignment = get(resources, countryCode)
                boundsOffset = if (alignment != null) {
                    getBoundsOffset(d, alignment)
                } else {
                    Rect(0, 0, 0, 0)
                }
                drawable = d
            }
        }
        invalidate()
    }

    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
        if (drawable == this.drawable) {
            invalidate()
        }
    }

    private fun getBoundsOffset(d: Drawable, align: FlagAlignment): Rect {
        val w = d.intrinsicWidth
        val h = d.intrinsicHeight
        val scale = width.toFloat() / min(w,h)
        val hOffset = -w * scale + width
        return when(align) {
            FlagAlignment.CENTER -> Rect((hOffset / 2f).toInt(), 0, (hOffset / 2f).toInt(), 0)
            FlagAlignment.LEFT -> Rect(0, 0, hOffset.toInt(), 0)
            FlagAlignment.RIGHT -> Rect(hOffset.toInt(), 0, 0, 0)
            FlagAlignment.STRETCH -> Rect(0, 0, 0, 0)
        }
    }

    private fun getFlagResIdWithFallback(countryCode: String): Int {
        val resId = getFlagResId(countryCode)
        return if (resId == 0 && countryCode.contains('-')) {
            getFlagResId(countryCode.substringBefore('-'))
        } else {
            resId
        }
    }

    private fun getFlagResId(countryCode: String): Int {
        val lowerCaseCountryCode = countryCode.toLowerCase(Locale.US).replace('-', '_')
        return resources.getIdentifier("ic_flag_$lowerCaseCountryCode", "drawable", context.packageName)
    }

    companion object {
        /* make sure the YAML is only read once and kept once for all instances of SquareFlagView*/
        private var map: Map<String, FlagAlignment>? = null

        private fun get(resources: Resources, countryCode: String): FlagAlignment? {
            if (map == null) {
                synchronized(this) {
                    if (map == null) {
                        map = readFlagAlignments(resources)
                    }
                }
            }
            return map!![countryCode]
        }

        private fun readFlagAlignments(resources: Resources): Map<String, FlagAlignment> =
            resources.getYamlObject<HashMap<String, String>>(R.raw.flag_alignments).map {
                it.key to FlagAlignment.valueOf(it.value.toUpperCase(Locale.US))
            }.toMap()
    }

    private enum class FlagAlignment { CENTER, LEFT, RIGHT, STRETCH }
}
