package de.westnordost.streetcomplete.data.osm.mapdata

import android.util.Log
import de.westnordost.osmapi.common.errors.OsmQueryTooBigException
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.ktx.format
import de.westnordost.streetcomplete.util.enlargedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.System.currentTimeMillis
import javax.inject.Inject

/** Takes care of downloading all note and osm quests */
class MapDataDownloader @Inject constructor(
    private val mapDataApi: MapDataApi,
    private val mapDataController: MapDataController
) {
    suspend fun download(bbox: BoundingBox) = withContext(Dispatchers.IO) {
        val time = currentTimeMillis()

        val mapData = MutableMapData()
        val expandedBBox = bbox.enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING)
        getMapAndHandleTooBigQuery(expandedBBox, mapData)
        /* The map data might be filled with several bboxes one after another if the download is
           split up in several, so lets set the bbox back to the bbox of the complete download */
        mapData.boundingBox = expandedBBox

        val seconds = (currentTimeMillis() - time) / 1000.0
        Log.i(TAG,"Downloaded ${mapData.nodes.size} nodes, ${mapData.ways.size} ways and ${mapData.relations.size} relations in ${seconds.format(1)}s")

        mapDataController.putAllForBBox(bbox, mapData)
    }

    private fun getMapAndHandleTooBigQuery(bounds: BoundingBox, mutableMapData: MutableMapData) {
        try {
            mapDataApi.getMap(bounds, mutableMapData, ApplicationConstants.IGNORED_RELATION_TYPES)
        } catch (e : OsmQueryTooBigException) {
            for (subBounds in bounds.splitIntoFour()) {
                getMapAndHandleTooBigQuery(subBounds, mutableMapData)
            }
        }
    }

    companion object {
        private const val TAG = "MapDataDownload"
    }
}

private fun BoundingBox.splitIntoFour(): List<BoundingBox> {
    val center = LatLon((max.latitude + min.latitude) / 2, (max.longitude + min.longitude) / 2)
    return listOf(
        BoundingBox(min.latitude,    min.longitude,    center.latitude, center.longitude),
        BoundingBox(min.latitude,    center.longitude, center.latitude, max.longitude),
        BoundingBox(center.latitude, min.longitude,    max.latitude,    center.longitude),
        BoundingBox(center.latitude, center.longitude, max.latitude,    max.longitude)
    )
}
