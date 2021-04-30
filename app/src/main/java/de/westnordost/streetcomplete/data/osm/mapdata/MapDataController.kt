package de.westnordost.streetcomplete.data.osm.mapdata

import android.util.Log
import de.westnordost.streetcomplete.data.osm.geometry.*
import de.westnordost.streetcomplete.ktx.format
import java.lang.System.currentTimeMillis
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/** Controller to access element data and its geometry and handle updates to it (from OSM API) */
@Singleton class MapDataController @Inject internal constructor(
    private val nodeDB: NodeDao,
    private val wayDB: WayDao,
    private val relationDB: RelationDao,
    private val elementDB: ElementDao,
    private val geometryDB: ElementGeometryDao,
    private val elementGeometryCreator: ElementGeometryCreator
) {

    /* Must be a singleton because there is a listener that should respond to a change in the
     * database table */

    /** Interface to be notified of new or updated OSM elements */
    interface Listener {
        /** Called when a number of elements have been updated or deleted */
        fun onUpdated(updated: MutableMapDataWithGeometry, deleted: Collection<ElementKey>)

        /** Called when all elements in the given bounding box should be replaced with the elements
         *  in the mapDataWithGeometry */
        fun onReplacedForBBox(bbox: BoundingBox, mapDataWithGeometry: MutableMapDataWithGeometry)
    }
    private val listeners: MutableList<Listener> = CopyOnWriteArrayList()

    /** update element data because in the given bounding box, fresh data from the OSM API has been
     *  downloaded */
    @Synchronized fun putAllForBBox(bbox: BoundingBox, mapData: MutableMapData) {
        val time = currentTimeMillis()

        // for incompletely downloaded relations, complete the map data (as far as possible) with
        // local data, i.e. with local nodes and ways (still) in local storage
        completeMapData(mapData)

        val geometries = mapData.mapNotNull { element ->
            val geometry = elementGeometryCreator.create(element, mapData, true)
            geometry?.let { ElementGeometryEntry(element.type, element.id, it) }
        }

        val oldElementKeys = elementDB.getAllKeys(mapData.boundingBox!!).toMutableSet()
        for (element in mapData) {
            oldElementKeys.remove(ElementKey(element.type, element.id))
        }
        elementDB.deleteAll(oldElementKeys)
        geometryDB.deleteAll(oldElementKeys)
        geometryDB.putAll(geometries)
        elementDB.putAll(mapData)

        Log.i(TAG,
            "Persisted ${geometries.size} and deleted ${oldElementKeys.size} elements and geometries" +
            " in ${((currentTimeMillis() - time) / 1000.0).format(1)}s"
        )

        val mapDataWithGeometry = MutableMapDataWithGeometry(mapData, geometries)
        onUpdateForBBox(bbox, mapDataWithGeometry)
    }

    @Synchronized fun updateAll(mapDataUpdates: MapDataUpdates) {
        val elements = mapDataUpdates.updated
        // need mapData in order to create (updated) geometry
        val mapData = MutableMapData(elements)
        completeMapData(mapData)

        val elementGeometryEntries = elements.mapNotNull { element ->
            val geometry = elementGeometryCreator.create(element, mapData, true)
            geometry?.let { ElementGeometryEntry(element.type, element.id, geometry) }
        }

        val oldElementKeys = mapDataUpdates.idUpdates.map { ElementKey(it.elementType, it.oldElementId) }
        val deleted = mapDataUpdates.deleted + oldElementKeys

        val mapDataWithGeom = MutableMapDataWithGeometry(mapData, elementGeometryEntries)

        elementDB.deleteAll(deleted)
        geometryDB.deleteAll(deleted)
        geometryDB.putAll(elementGeometryEntries)
        elementDB.putAll(elements)

        onUpdated(updated = mapDataWithGeom, deleted = deleted)
    }

    private fun completeMapData(mapData: MutableMapData) {
        val missingNodeIds = mutableListOf<Long>()
        val missingWayIds = mutableListOf<Long>()
        for (relation in mapData.relations) {
            for (member in relation.members) {
                if (member.type == ElementType.NODE && mapData.getNode(member.ref) == null) {
                    missingNodeIds.add(member.ref)
                }
                if (member.type == ElementType.WAY && mapData.getWay(member.ref) == null) {
                    missingWayIds.add(member.ref)
                }
                /* deliberately not recursively looking for relations of relations
                   because that is also not how the OSM API works */
            }
        }

        val ways = wayDB.getAll(missingWayIds)
        for (way in mapData.ways + ways) {
            for (nodeId in way.nodeIds) {
                if (mapData.getNode(nodeId) == null) {
                    missingNodeIds.add(nodeId)
                }
            }
        }
        val nodes = nodeDB.getAll(missingNodeIds)

        mapData.addAll(nodes)
        mapData.addAll(ways)
    }

    fun get(type: ElementType, id: Long) : Element? = elementDB.get(type, id)

    fun getGeometry(type: ElementType, id: Long) : ElementGeometry? = geometryDB.get(type, id)

    fun getGeometries(keys: Collection<ElementKey>): List<ElementGeometryEntry> =
        geometryDB.getAllEntries(keys)

    fun getMapDataWithGeometry(bbox: BoundingBox): MutableMapDataWithGeometry {
        val time = currentTimeMillis()
        val elements = elementDB.getAll(bbox)
        // todo not really necessary to fetch all geometry here...
        val elementGeometryEntries = geometryDB.getAllEntries(elements.map { ElementKey(it.type, it.id) })
        val result = MutableMapDataWithGeometry(elements, elementGeometryEntries)
        result.boundingBox = bbox
        Log.i(TAG, "Fetched ${elements.size} elements and geometries in ${currentTimeMillis() - time}ms")
        return result
    }

    fun getNode(id: Long): Node? = nodeDB.get(id)
    fun getWay(id: Long): Way? = wayDB.get(id)
    fun getRelation(id: Long): Relation? = relationDB.get(id)

    fun getAll(elementKeys: Iterable<ElementKey>): List<Element> = elementDB.getAll(elementKeys)

    fun getNodes(ids: Collection<Long>): List<Node> = nodeDB.getAll(ids)
    fun getWays(ids: Collection<Long>): List<Way> = wayDB.getAll(ids)
    fun getRelations(ids: Collection<Long>): List<Relation> = relationDB.getAll(ids)

    fun getWaysForNode(id: Long): List<Way> = wayDB.getAllForNode(id)
    fun getRelationsForNode(id: Long): List<Relation> = relationDB.getAllForNode(id)
    fun getRelationsForWay(id: Long): List<Relation> = relationDB.getAllForWay(id)
    fun getRelationsForRelation(id: Long): List<Relation> = relationDB.getAllForRelation(id)

    @Synchronized fun deleteOlderThan(timestamp: Long): Int {
        val elements = elementDB.getIdsOlderThan(timestamp)
        if (elements.isEmpty()) return 0

        onUpdated(deleted = elements)

        val elementCount = elementDB.deleteAll(elements)
        val geometryCount = geometryDB.deleteAll(elements)
        Log.i(TAG,"Deleted $elementCount old elements and $geometryCount geometries")

        return elementCount
    }

    fun addListener(listener: Listener) {
        this.listeners.add(listener)
    }
    fun removeListener(listener: Listener) {
        this.listeners.remove(listener)
    }

    private fun onUpdated(
        updated: MutableMapDataWithGeometry = MutableMapDataWithGeometry(),
        deleted: Collection<ElementKey> = emptyList()
    ) {
        if (updated.nodes.isEmpty() && updated.ways.isEmpty() && updated.relations.isEmpty() && deleted.isEmpty()) return

        listeners.forEach { it.onUpdated(updated, deleted) }
    }

    private fun onUpdateForBBox(bbox: BoundingBox, mapDataWithGeometry: MutableMapDataWithGeometry) {

        listeners.forEach { it.onReplacedForBBox(bbox, mapDataWithGeometry) }
    }

    companion object {
        private const val TAG = "MapDataController"
    }
}
