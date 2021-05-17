package de.westnordost.streetcomplete.map.components

import android.content.Context
import android.graphics.PointF
import android.location.Location
import com.mapzen.tangram.MapController
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.ktx.getBitmapDrawable
import de.westnordost.streetcomplete.ktx.toDp
import de.westnordost.streetcomplete.map.tangram.KtMapController
import de.westnordost.streetcomplete.map.tangram.Marker
import de.westnordost.streetcomplete.util.EARTH_CIRCUMFERENCE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/** Takes care of showing the location + direction + accuracy marker on the map */
class CurrentLocationMapComponent(ctx: Context, private val ctrl: KtMapController) {

    // markers showing the user's location, direction and accuracy of location
    private val locationMarker: Marker?
    private val accuracyMarker: Marker?
    private val directionMarker: Marker?

    private val directionMarkerSize: PointF

    /** Whether the whole thing is visible. True by default. It is only visible if both this flag
     *  is true and location is not null. */
    var isVisible: Boolean = true
    set(value) {
        if (field == value) return
        field = value
        if (!value) hide() else show()
    }

    /** The location of the GPS location dot on the map. Null if none (yet) */
    var location: Location? = null
    set(value) {
        if (field == value) return
        field = value
        updateLocation()
    }

    /** The view rotation angle in degrees. Null if not set (yet) */
    var rotation: Double? = null
    set(value) {
        if (field == value) return
        field = value
        updateDirection()
    }

    /** Tell this component the current map zoom. Why does it need to know this at all? It doesn't,
     *  but it needs to know when it changed. There is no specific event for that. Whenever the
     *  zoom changed, the marker showing the accuracy must be updated because the accuracy's marker
     *  size is calculated programmatically using the current zoom. */
    var currentMapZoom: Float? = null
    set(value) {
        if (field == value) return
        field = value
        updateAccuracy()
    }

    init {
        val dotImg = ctx.resources.getBitmapDrawable(R.drawable.location_dot)
        val dotSize = PointF(
            dotImg.intrinsicWidth.toFloat().toDp(ctx),
            dotImg.intrinsicHeight.toFloat().toDp(ctx)
        )

        val directionImg = ctx.resources.getBitmapDrawable(R.drawable.location_direction)
        directionMarkerSize = PointF(
            directionImg.intrinsicWidth.toFloat().toDp(ctx),
            directionImg.intrinsicHeight.toFloat().toDp(ctx)
        )

        val accuracyImg = ctx.resources.getBitmapDrawable(R.drawable.accuracy_circle)

        locationMarker = ctrl.addMarker()?.also {
            it.setStylingFromString("""
            {
                style: 'points',
                color: 'white',
                size: [${dotSize.x}px, ${dotSize.y}px],
                order: 2000,
                flat: true,
                collide: false,
                interactive: true
            }""".trimIndent())
            it.setDrawable(dotImg)
            it.setDrawOrder(3)
        }

        directionMarker = ctrl.addMarker()?.also {
            it.setDrawable(directionImg)
            it.setDrawOrder(2)
        }
        accuracyMarker =  ctrl.addMarker()?.also {
            it.setDrawable(accuracyImg)
            it.setDrawOrder(1)
        }
    }

    private fun hide() {
        locationMarker?.isVisible = false
        accuracyMarker?.isVisible = false
        directionMarker?.isVisible = false
    }

    private fun show() {
        updateLocation()
        updateDirection()
    }

    /** Update the GPS position shown on the map */
    private fun updateLocation() {
        if (!isVisible) return
        val pos = location?.let { LatLon(it.latitude, it.longitude) } ?: return

        accuracyMarker?.isVisible = true
        accuracyMarker?.setPointEased(pos, 1000, MapController.EaseType.CUBIC)
        locationMarker?.isVisible = true
        locationMarker?.setPointEased(pos, 1000, MapController.EaseType.CUBIC)
        directionMarker?.isVisible = rotation != null
        directionMarker?.setPointEased(pos, 1000, MapController.EaseType.CUBIC)

        updateAccuracy()
    }

    /** Update the circle that shows the GPS accuracy on the map */
    private fun updateAccuracy() {
        if (!isVisible) return
        val location = location ?: return

        val size = location.accuracy * pixelsPerMeter(location.latitude, ctrl.cameraPosition.zoom)
        accuracyMarker?.setStylingFromString("""
        {
            style: 'points',
            color: 'white',
            size: ${size}px,
            order: 2000,
            flat: true,
            collide: false
        }""")
    }

    /** Update the marker that shows the direction in which the smartphone is held */
    private fun updateDirection() {
        if (!isVisible) return
        // no sense to display direction if there is no location yet
        if (rotation == null || location == null) return

        directionMarker?.isVisible = true
        directionMarker?.setStylingFromString("""
        {
            style: 'points',
            color: '#cc536dfe',
            size: [${directionMarkerSize.x}px, ${directionMarkerSize.y}px],
            order: 2000,
            collide: false,
            flat: true,
            angle: $rotation
        }""")
    }

    private fun pixelsPerMeter(latitude: Double, zoom: Float): Double {
        val numberOfTiles = (2.0).pow(zoom.toDouble())
        val metersPerTile = cos(latitude * PI / 180.0) * EARTH_CIRCUMFERENCE / numberOfTiles
        return 256 / metersPerTile
    }
}
