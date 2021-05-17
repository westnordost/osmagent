package de.westnordost.streetcomplete.map.components

import android.content.Context
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import com.mapzen.tangram.MapData
import com.mapzen.tangram.geometry.Point
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.ktx.getBitmapDrawable
import de.westnordost.streetcomplete.ktx.toDp
import de.westnordost.streetcomplete.map.tangram.KtMapController
import de.westnordost.streetcomplete.map.tangram.Marker
import de.westnordost.streetcomplete.map.tangram.toLngLat

/** Takes care of displaying pins on the map and displaying pins as selected */
class PinsMapComponent(private val ctx: Context, private val ctrl: KtMapController) {

    private val pinsLayer: MapData
    private val selectedPinsLayer: MapData

    private val pinSelectionMarkers: MutableList<Marker> = mutableListOf()

    private val selectionDrawable: BitmapDrawable
    private val selectionDrawableSize: PointF

    init {
        selectionDrawable = ctx.resources.getBitmapDrawable(R.drawable.pin_selection_ring)
        selectionDrawableSize = PointF(
            selectionDrawable.intrinsicWidth.toFloat().toDp(ctx),
            selectionDrawable.intrinsicHeight.toFloat().toDp(ctx)
        )

        pinsLayer = ctrl.addDataLayer(PINS_LAYER)
        selectedPinsLayer = ctrl.addDataLayer(SELECTED_PINS_LAYER)
    }

    fun clear() {
        clearPins()
        clearSelectedPins()
    }

    /* ------------------------------------------  Pins ----------------------------------------- */

    /** Show given pins. Previously shown pins are replaced with these.  */
    fun showPins(pins: Collection<Pin>) {
        pinsLayer.setFeatures(pins.map { pin ->
            Point(pin.position.toLngLat(), mapOf(
                "type" to "point",
                "kind" to pin.iconName,
                "importance" to pin.importance.toString()
            ) + pin.properties)
        })
    }

    /** Hide pins */
    private fun clearPins() {
        pinsLayer.clear()
    }

    /* -------------------------------------  Selected pins ------------------------------------- */

    /** Show selected pins with the given icon at the given positions. "Selected pins" are not
     *  related to pins, they are just visuals that are displayed on top of the normal pins and look
     *  highlighted/selected. */
    fun showSelectedPins(@DrawableRes iconResId: Int, pinPositions: Collection<LatLon>) {
        putSelectedPins(iconResId, pinPositions)
        showPinSelectionMarkers(pinPositions)
    }

    /** Clear the display of any selected pins */
    fun clearSelectedPins() {
        selectedPinsLayer.clear()
        clearPinSelectionMarkers()
    }

    private fun putSelectedPins(@DrawableRes iconResId: Int, pinPositions: Collection<LatLon>) {
        val points = pinPositions.map { position ->
            Point(position.toLngLat(), mapOf(
                "type" to "point",
                "kind" to ctx.resources.getResourceEntryName(iconResId)
            ))
        }
        selectedPinsLayer.setFeatures(points)
    }

    private fun showPinSelectionMarkers(positions: Collection<LatLon>) {
        clearPinSelectionMarkers()
        for (position in positions) {
            createPinSelectionMarker(position)?.let { pinSelectionMarkers.add(it) }
        }
    }

    private fun clearPinSelectionMarkers() {
        pinSelectionMarkers.forEach { ctrl.removeMarker(it) }
        pinSelectionMarkers.clear()
    }

    private fun createPinSelectionMarker(pos: LatLon): Marker? =
        ctrl.addMarker()?.also {
            it.setStylingFromString("""
            {
                style: 'pin-selection',
                color: 'white',
                size: [${selectionDrawableSize.x}px, ${selectionDrawableSize.y}px],
                flat: false,
                collide: false,
                offset: ['0px', '-38px']
            }""".trimIndent())
            it.setDrawable(selectionDrawable)
            it.isVisible = true
            it.setPoint(pos)
        }

    companion object {
        // see streetcomplete.yaml for the definitions of the below layers
        private const val PINS_LAYER = "streetcomplete_pins"
        private const val SELECTED_PINS_LAYER = "streetcomplete_selected_pins"
    }
}

data class Pin(
    val position: LatLon,
    val iconName: String,
    val properties: Map<String, String> = emptyMap(),
    val importance: Int = 0
)
