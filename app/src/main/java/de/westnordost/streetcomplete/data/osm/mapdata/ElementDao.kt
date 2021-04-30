package de.westnordost.streetcomplete.data.osm.mapdata

import javax.inject.Inject

import de.westnordost.streetcomplete.data.osm.mapdata.ElementType.*

/** Stores OSM elements. Actually, stores nothing, but delegates the work to a NodeDao, WayDao and
 *  a RelationDao. :-P */
class ElementDao @Inject constructor(
    private val nodeDao: NodeDao,
    private val wayDao: WayDao,
    private val relationDao: RelationDao
) {
    fun put(element: Element) {
        when (element) {
            is Node -> nodeDao.put(element)
            is Way -> wayDao.put(element)
            is Relation -> relationDao.put(element)
        }
    }

    fun get(type: ElementType, id: Long): Element? {
        return when (type) {
            NODE -> nodeDao.get(id)
            WAY -> wayDao.get(id)
            RELATION -> relationDao.get(id)
        }
    }

    fun delete(type: ElementType, id: Long) {
        when (type) {
            NODE -> nodeDao.delete(id)
            WAY -> wayDao.delete(id)
            RELATION -> relationDao.delete(id)
        }
    }

    fun putAll(elements: Iterable<Element>) {
        nodeDao.putAll(elements.filterIsInstance<Node>())
        wayDao.putAll(elements.filterIsInstance<Way>())
        relationDao.putAll(elements.filterIsInstance<Relation>())
    }

    fun getAll(bbox: BoundingBox): List<Element> {
        val nodes = nodeDao.getAll(bbox)
        val nodeIds = nodes.map { it.id }
        val ways = wayDao.getAllForNodes(nodeIds)
        val wayIds = ways.map { it.id }
        val relations = relationDao.getAllForElements(nodeIds = nodeIds, wayIds = wayIds)
        return nodes + ways + relations
    }

    fun getAll(keys: Iterable<ElementKey>): List<Element> {
        val elementIds = keys.toElementIds()
        if (elementIds.size == 0) return emptyList()

        val result = ArrayList<Element>(elementIds.size)
        result.addAll(nodeDao.getAll(elementIds.nodes))
        result.addAll(wayDao.getAll(elementIds.ways))
        result.addAll(relationDao.getAll(elementIds.relations))
        return result
    }

    fun deleteAll(keys: Iterable<ElementKey>): Int {
        val elementIds = keys.toElementIds()
        if (elementIds.size == 0) return 0

        return nodeDao.deleteAll(elementIds.nodes) +
            wayDao.deleteAll(elementIds.ways) +
            relationDao.deleteAll(elementIds.relations)
    }

    fun getIdsOlderThan(timestamp: Long): List<ElementKey> {
        val result = mutableListOf<ElementKey>()
        result.addAll(nodeDao.getIdsOlderThan(timestamp).map { ElementKey(NODE, it) })
        result.addAll(wayDao.getIdsOlderThan(timestamp).map { ElementKey(WAY, it) })
        result.addAll(relationDao.getIdsOlderThan(timestamp).map { ElementKey(RELATION, it) })
        return result
    }

}

private data class ElementIds(val nodes: List<Long>, val ways: List<Long>, val relations: List<Long>) {
    val size: Int get() = nodes.size + ways.size + relations.size
}

private fun Iterable<ElementKey>.toElementIds(): ElementIds {
    val nodes = ArrayList<Long>()
    val ways = ArrayList<Long>()
    val relations = ArrayList<Long>()
    for (key in this) {
        when(key.type) {
            NODE -> nodes.add(key.id)
            WAY -> ways.add(key.id)
            RELATION -> relations.add(key.id)
        }
    }
    return ElementIds(nodes, ways, relations)
}
