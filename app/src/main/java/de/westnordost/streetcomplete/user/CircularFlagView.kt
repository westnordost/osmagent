package de.westnordost.streetcomplete.user

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.getYamlObject
import java.util.Locale
import kotlin.math.min

/** Show a flag of a country in a circle */
class CircularFlagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private var drawable: Drawable? = null
    private var boundsOffset: Rect? = null

    var countryCode: String? = null
    set(value) {
        field = value
        updateCountryCode(value)
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0,0,view.width,view.height)
                }
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
        boundsOffset = null
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
        val d = drawable ?: return
        val cc = countryCode ?: return
        var offset = boundsOffset
        if (offset == null) {
            offset = getBoundsOffset(d, cc)
            boundsOffset = offset
        }

        // clip it round
        canvas.clipPath(clipPath)
        d.setBounds(offset.left, offset.top, width - offset.right, height - offset.bottom)
        d.draw(canvas)

    }

    private fun updateCountryCode(countryCode: String?) {
        if (countryCode == null) {
            drawable = null
        } else {
            val resId = getFlagResIdWithFallback(countryCode)
            drawable = if (resId != 0) resources.getDrawable(resId) else null
        }
        invalidate()
    }

    private fun getBoundsOffset(drawable: Drawable, countryCode: String): Rect {
        val alignment = get(resources, countryCode)
        return if (alignment != null) {
            getBoundsOffset(drawable, alignment)
        } else {
            Rect(0, 0, 0, 0)
        }
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
            FlagAlignment.LEFT ->         Rect(0, 0, hOffset.toInt(), 0)
            FlagAlignment.CENTER_LEFT ->  Rect((1f * hOffset / 3f).toInt(), 0, (2f * hOffset / 3f).toInt(), 0)
            FlagAlignment.CENTER ->       Rect((hOffset / 2f).toInt(), 0, (hOffset / 2f).toInt(), 0)
            FlagAlignment.CENTER_RIGHT -> Rect((2f * hOffset / 3f).toInt(), 0, (1f * hOffset / 3f).toInt(), 0)
            FlagAlignment.RIGHT ->        Rect(hOffset.toInt(), 0, 0, 0)
            FlagAlignment.STRETCH ->      Rect(0, 0, 0, 0)
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
                it.key to FlagAlignment.valueOf(it.value.replace("-","_").toUpperCase(Locale.US))
            }.toMap()
    }

    private enum class FlagAlignment {
        LEFT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        RIGHT,
        STRETCH
    }
}
