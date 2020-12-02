package de.westnordost.streetcomplete.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.getMode
import android.view.View.MeasureSpec.getSize
import android.view.ViewOutlineProvider
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withRotation
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.toPx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


/** A view for the pointer pin that ought to be displayed at the edge of the screen.
 *  Can be rotated with the pinRotation field. As opposed to normal rotation, it ensures that the
 *  pin icon always stays upright  */
class PointerPinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val pointerPin: Drawable = context.getDrawable(R.drawable.quest_pin_pointer)!!
    private var pointerPinBitmap: Bitmap? = null
    private val antiAliasPaint: Paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    /** rotation of the pin in degrees. Similar to rotation, only that the pointy end of the pin
     *  is always located at the edge of the view */
    var pinRotation: Float = 0f
        set(value) {
            field = value
            invalidate()
            invalidateOutline()
        }

    var pinIconDrawable: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }

    private fun setPinIconResource(resId: Int) {
        pinIconDrawable = context.getDrawable(resId)
    }

    init {
        context.withStyledAttributes(attrs, R.styleable.PointerPinView) {
            pinRotation = getFloat(R.styleable.PointerPinView_pinRotation, 0f)
            val resId = getResourceId(R.styleable.PointerPinView_iconSrc, 0)
            if (resId != 0)
                setPinIconResource(resId)
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val size = min(width, height)
                val pinCircleSize = (size * (1 - PIN_CENTER_OFFSET_FRACTION * 2)).toInt()
                val arrowOffset = size * PIN_CENTER_OFFSET_FRACTION
                val a = pinRotation.toDouble().normalizeAngle().toRadians()
                val x = (-sin(a) * arrowOffset).toInt()
                val y = (+cos(a) * arrowOffset).toInt()
                outline.setOval(
                    width / 2 - pinCircleSize / 2 + x,
                    height / 2 - pinCircleSize / 2 + y,
                    width / 2 + pinCircleSize / 2 + x,
                    height / 2 + pinCircleSize / 2 + y
                )
            }
        }
    }

    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
        if (drawable == pinIconDrawable) {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = DEFAULT_SIZE.toPx(context).toInt()
        val width = reconcileSize(desiredSize, widthMeasureSpec)
        val height = reconcileSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pointerPinBitmap?.recycle()

        val size = min(width, height)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        pointerPin.setBounds(0,0, size, size)
        pointerPin.draw(canvas)
        pointerPinBitmap = bmp
    }

    override fun onDraw(canvas: Canvas?) {
        val c = canvas ?: return

        val size = min(width, height)
        val r = pinRotation

        c.withRotation(r, width/2f, height/2f) {
            pointerPinBitmap?.let { canvas.drawBitmap(it, 0f, 0f, antiAliasPaint) }
        }

        val icon = pinIconDrawable
        if (icon != null) {
            val iconSize = (size * ICON_SIZE_FRACTION).toInt()
            val arrowOffset = size * PIN_CENTER_OFFSET_FRACTION
            val a = r.toDouble().normalizeAngle().toRadians()
            val x = (-sin(a) * arrowOffset).toInt()
            val y = (+cos(a) * arrowOffset).toInt()
            icon.setBounds(
                width/2 - iconSize/2 + x,
                height/2 - iconSize/2 + y,
                width/2 + iconSize/2 + x,
                height/2 + iconSize/2 + y)
            icon.draw(c)
        }
    }

    private fun reconcileSize(contentSize: Int, measureSpec: Int): Int {
        val mode = getMode(measureSpec)
        val size = getSize(measureSpec)
        return when (mode) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> min(contentSize, size)
            else -> contentSize
        }
    }

    companion object {
        // half size of the sharp end of pin, depends on the pin drawable: using quest_pin_pointer
        private const val PIN_CENTER_OFFSET_FRACTION = 14f / 124f
        // size of the icon part of the pin, depends on the pin drawable: using quest_pin_pointer
        private const val ICON_SIZE_FRACTION = 84f / 124f
        // intrinsic/default size
        private const val DEFAULT_SIZE = 64f // in dp
    }
}

private fun Double.toRadians(): Double = this / 180.0 * PI

private fun Double.normalizeAngle(): Double {
    var r = this % 360 // r is -360..360
    r = (r + 360) % 360 // r is 0..360
    return r
}
