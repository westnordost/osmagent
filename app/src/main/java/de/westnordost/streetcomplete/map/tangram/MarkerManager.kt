package de.westnordost.streetcomplete.map.tangram

import android.graphics.drawable.BitmapDrawable
import com.mapzen.tangram.LngLat
import com.mapzen.tangram.MapController
import com.mapzen.tangram.geometry.Polygon
import com.mapzen.tangram.geometry.Polyline
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/** Controls the marker management. Use in place of the Tangram MapController.addMarker, pickMarker
 *  etc. methods to enable markers that survive a scene reload.
 *
 *  The Tangram Markers are wrapped into own Marker objects with a similar interface that are able
 *  to recreate the Tangram Markers after the scene has successfully loaded.
 *
 *  See https://github.com/tangrams/tangram-es/issues/1756
 *  */
class MarkerManager(ctrl: MapController) {
    private val weakC = WeakReference(ctrl)
    private val c get() = weakC.get()

    private var markerIdCounter = 0L
    private val markers = mutableMapOf<Long, Marker>()

    private val markerPickContinuations = ConcurrentLinkedQueue<Continuation<MarkerPickResult?>>()

    init {
        ctrl.setMarkerPickListener { tangramMarkerPickResult: com.mapzen.tangram.MarkerPickResult? ->
            val tangramMarkerId = tangramMarkerPickResult?.marker?.markerId
            var markerPickResult: MarkerPickResult? = null
            if (tangramMarkerId != null) {
                val marker = markers.values.find { it.tangramMarker?.markerId == tangramMarkerId }
                if (marker != null) {
                    markerPickResult = MarkerPickResult(marker, tangramMarkerPickResult.coordinates.toLatLon())
                }
            }
            markerPickContinuations.poll()?.resume(markerPickResult)
        }
    }

    suspend fun pickMarker(posX: Float, posY: Float): MarkerPickResult? = suspendCancellableCoroutine { cont ->
        markerPickContinuations.offer(cont)
        cont.invokeOnCancellation { markerPickContinuations.remove(cont) }
        c?.pickMarker(posX, posY)
    }

    fun addMarker(): Marker? {
        val m = c?.addMarker() ?: return null

        val marker = Marker(markerIdCounter++, m)
        markers[marker.markerId] = marker
        return marker
    }

    fun removeMarker(markerId: Long): Boolean {
        val marker = markers.remove(markerId) ?: return false
        val tangramMarkerId = marker.tangramMarker?.markerId
        if (tangramMarkerId != null) {
            c?.removeMarker(tangramMarkerId)
        }
        return true
    }

    fun removeAllMarkers() {
        markers.clear()
        c?.removeAllMarkers()
    }

    fun recreateMarkers() {
        for (marker in markers.values) {
            c?.addMarker()?.let { marker.tangramMarker = it }
        }
    }

    fun invalidateMarkers() {
        for (marker in markers.values) {
            marker.tangramMarker = null
        }
    }
}

/** Wrapper around com.mapzen.tangram.Marker
 *
 *  Tangram Markers are invalidated and can't be used anymore on each scene update. This class keeps
 *  the necessary data to automatically reinstantiate them after the scene update is done.
 *  */
class Marker(val markerId: Long, tangramMarker: com.mapzen.tangram.Marker) {

    internal var tangramMarker: com.mapzen.tangram.Marker? = null
        set(value) {
            field = value
            if (value != null) {
                value.isVisible = isVisible
                drawOrder?.let { value.setDrawOrder(it) }
                stylingFromPath?.let { value.setStylingFromPath(it) }
                stylingFromString?.let { value.setStylingFromString(it) }

                point?.let {
                    val duration = pointEaseDuration
                    val ease = pointEaseType
                    if (duration != null && ease != null) {
                        value.setPointEased(it, duration, ease)
                    }  else {
                        value.setPoint(it)
                    }
                }
                value.setPolyline(polyline)
                value.setPolygon(polygon)

                drawableId?.let { value.setDrawable(it) }
                drawable?.let { value.setDrawable(it) }
            }
            drawOrder
        }

    init {
        this.tangramMarker = tangramMarker
    }

    var isVisible: Boolean
        set(value) { _isVisible = value }
        get() = _isVisible != false

    // this construct is necessary because isVisible is not initialized to its initial value yet
    // when tangramMarker is set in the constructor. But in the constructor, tangramMarker.isVisible
    // is set to isVisible.
    private var _isVisible: Boolean? = null
        set(value) {
            field = value
            tangramMarker?.isVisible = value != false
        }

    var userData: Any? = null

    private var stylingFromPath: String? = null
    private var stylingFromString: String? = null

    private var drawOrder: Int? = null

    private var pointEaseDuration: Int? = null
    private var pointEaseType: MapController.EaseType? = null
    private var point: LngLat? = null
    private var polyline: Polyline? = null
    private var polygon: Polygon? = null

    private var drawable: BitmapDrawable? = null
    private var drawableId: Int? = null

    fun setStylingFromPath(stylingFromPath: String) {
        this.stylingFromPath = stylingFromPath
        tangramMarker?.setStylingFromPath(stylingFromPath)
    }

    fun setStylingFromString(stylingFromString: String) {
        this.stylingFromString = stylingFromString
        tangramMarker?.setStylingFromString(stylingFromString)
    }

    fun setDrawOrder(drawOrder: Int) {
        this.drawOrder = drawOrder
        tangramMarker?.setDrawOrder(drawOrder)
    }

    fun setPointEased(point: LatLon?, duration: Int, ease: MapController.EaseType) {
        val lngLat = point?.toLngLat()
        this.point = lngLat
        this.pointEaseDuration = duration
        this.pointEaseType = ease
        tangramMarker?.setPointEased(lngLat, duration, ease)
    }

    fun setPoint(point: LatLon) {
        val lngLat = point.toLngLat()
        this.point = lngLat
        tangramMarker?.setPoint(lngLat)
    }

    fun setPolyline(polyline: Polyline?) {
        this.polyline = polyline
        tangramMarker?.setPolyline(polyline)
    }

    fun setPolygon(polygon: Polygon?) {
        this.polygon = polygon
        tangramMarker?.setPolygon(polygon)
    }

    fun setDrawable(drawableId: Int) {
        this.drawableId = drawableId
        tangramMarker?.setDrawable(drawableId)
    }

    fun setDrawable(drawable: BitmapDrawable) {
        this.drawable = drawable
        tangramMarker?.setDrawable(drawable)
    }
}

class MarkerPickResult internal constructor(
    val marker: Marker,
    val coordinates: LatLon
)
