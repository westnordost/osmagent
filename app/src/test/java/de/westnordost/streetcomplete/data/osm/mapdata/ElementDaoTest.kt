package de.westnordost.streetcomplete.data.osm.mapdata

import org.junit.Before
import org.junit.Test

import de.westnordost.streetcomplete.data.osm.mapdata.ElementType.*
import de.westnordost.streetcomplete.testutils.*
import org.junit.Assert.assertEquals
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.verify

class ElementDaoTest {
    private lateinit var nodeDao: NodeDao
    private lateinit var wayDao: WayDao
    private lateinit var relationDao: RelationDao
    private lateinit var dao: ElementDao

    @Before fun setUp() {
        nodeDao = mock()
        wayDao = mock()
        relationDao = mock()
        dao = ElementDao(nodeDao, wayDao, relationDao)
    }

    @Test fun putNode() {
        val node = node(1)
        dao.put(node)
        verify(nodeDao).put(node)
    }

    @Test fun getNode() {
        dao.get(NODE, 1L)
        verify(nodeDao).get(1L)
    }

    @Test fun deleteNode() {
        dao.delete(NODE, 1L)
        verify(nodeDao).delete(1L)
    }

    @Test fun putWay() {
        val way = way()
        dao.put(way)
        verify(wayDao).put(way)
    }

    @Test fun getWay() {
        dao.get(WAY, 1L)
        verify(wayDao).get(1L)
    }

    @Test fun deleteWay() {
        dao.delete(WAY, 1L)
        verify(wayDao).delete(1L)
    }

    @Test fun putRelation() {
        val relation = rel()
        dao.put(relation)
        verify(relationDao).put(relation)
    }

    @Test fun getRelation() {
        dao.get(RELATION, 1L)
        verify(relationDao).get(1L)
    }

    @Test fun deleteRelation() {
        dao.delete(RELATION, 1L)
        verify(relationDao).delete(1L)
    }

    @Test fun putAllRelations() {
        dao.putAll(listOf(rel()))
        verify(relationDao).putAll(anyCollection())
    }

    @Test fun putAllWays() {
        dao.putAll(listOf(way()))
        verify(wayDao).putAll(anyCollection())
    }

    @Test fun putAllNodes() {
        dao.putAll(listOf(node()))
        verify(nodeDao).putAll(anyCollection())
    }

    @Test fun putAllElements() {
        dao.putAll(listOf(node(), way(), rel()))

        verify(nodeDao).putAll(anyCollection())
        verify(wayDao).putAll(anyCollection())
        verify(relationDao).putAll(anyCollection())
    }

    @Test fun deleteAllElements() {
        dao.deleteAll(listOf(
            ElementKey(NODE,0),
            ElementKey(WAY,0),
            ElementKey(RELATION,0)
        ))

        verify(nodeDao).deleteAll(listOf(0L))
        verify(wayDao).deleteAll(listOf(0L))
        verify(relationDao).deleteAll(listOf(0L))
    }

    @Test fun getAllElements() {
        dao.getAll(listOf(
            ElementKey(NODE,0),
            ElementKey(WAY,0),
            ElementKey(RELATION,0)
        ))

        verify(nodeDao).getAll(listOf(0L))
        verify(wayDao).getAll(listOf(0L))
        verify(relationDao).getAll(listOf(0L))
    }

    @Test fun getAllElementsByBbox() {
        val bbox = BoundingBox(0.0,0.0,1.0,1.0)
        val nodes = listOf(node(1), node(2), node(3))
        val nodeIds = nodes.map { it.id }
        val ways = listOf(way(1), way(2))
        val wayIds = ways.map { it.id }
        val relations = listOf(rel(1))

        on(nodeDao.getAll(bbox)).thenReturn(nodes)
        on(wayDao.getAllForNodes(eq(nodeIds))).thenReturn(ways)
        on(relationDao.getAllForElements(nodeIds = eq(nodeIds), wayIds = eq(wayIds))).thenReturn(relations)
        assertEquals(
            nodes + ways + relations,
            dao.getAll(bbox)
        )
    }

    @Test fun getAllElementKeysByBbox() {
        val bbox = BoundingBox(0.0,0.0,1.0,1.0)
        val nodeIds = listOf<Long>(1,2,3)
        val wayIds = listOf<Long>(1,2)
        val relationIds = listOf<Long>(1)

        on(nodeDao.getAllIds(bbox)).thenReturn(nodeIds)
        on(wayDao.getAllIdsForNodes(eq(nodeIds))).thenReturn(wayIds)
        on(relationDao.getAllIdsForElements(nodeIds = eq(nodeIds), wayIds = eq(wayIds))).thenReturn(relationIds)
        assertEquals(
            nodeIds.map { ElementKey(NODE, it) } +
                wayIds.map { ElementKey(WAY, it) } +
                relationIds.map { ElementKey(RELATION, it) },
            dao.getAll(bbox)
        )
    }
}
