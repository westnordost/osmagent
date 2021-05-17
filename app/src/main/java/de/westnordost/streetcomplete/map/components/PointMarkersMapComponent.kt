package de.westnordost.streetcomplete.map.components

import androidx.annotation.DrawableRes
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.map.tangram.KtMapController

/** Manages putting some generic point markers with a drawable on the map */
class PointMarkersMapComponent(private val ctrl: KtMapController) {

    // markers: LatLon -> Marker Id
    private val markerIds: MutableMap<LatLon, Long> = HashMap()

    @Synchronized fun put(pos: LatLon, @DrawableRes drawableResId: Int) {
        delete(pos)
        ctrl.addMarker()?.also {
            it.setDrawable(drawableResId)
            it.setStylingFromString("""
            {
                style: 'points',
                color: 'white',
                size: 48px,
                order: 2000,
                collide: false
            }
            """)
            it.setPoint(pos)
            markerIds[pos] = it.markerId
        }
    }

    @Synchronized fun delete(pos: LatLon): Boolean {
        val markerId = markerIds[pos] ?: return false
        markerIds.remove(pos)
        return ctrl.removeMarker(markerId)
    }

    @Synchronized fun clear() {
        for (markerId in markerIds.values) {
            ctrl.removeMarker(markerId)
        }
        markerIds.clear()
    }
}
