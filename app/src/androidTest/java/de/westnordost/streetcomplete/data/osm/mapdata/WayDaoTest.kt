package de.westnordost.streetcomplete.data.osm.mapdata

import org.junit.Before
import org.junit.Test

import de.westnordost.streetcomplete.data.ApplicationDbTestCase
import de.westnordost.streetcomplete.ktx.containsExactlyInAnyOrder
import org.junit.Assert.*

class WayDaoTest : ApplicationDbTestCase() {
    private lateinit var dao: WayDao

    @Before fun createDao() {
        dao = WayDao(database)
    }

    @Test fun putGetNoTags() {
        val way = way(5, 1, listOf(1L, 2L, 3L, 4L))
        dao.put(way)
        val dbWay = dao.get(5)

        assertEquals(way, dbWay!!)
    }

    @Test fun putGetWithTags() {
        val way = way(5, 1, listOf(1L, 2L, 3L, 4L), mapOf("a key" to "a value"))
        dao.put(way)
        val dbWay = dao.get(5)

        assertEquals(way, dbWay!!)
    }

    @Test fun putOverwrites() {
        dao.put(way(6, 0))
        dao.put(way(6, 5))
        assertEquals(5, dao.get(6)!!.version)
    }

    @Test fun putOverwritesAlsoNodeIds() {
        dao.put(way(0, nodeIds = listOf(1,2,3)))
        dao.put(way(0, nodeIds = listOf(5,3,1,132)))
        assertEquals(listOf<Long>(5,3,1,132), dao.get(0)!!.nodeIds.toList())
    }

    @Test fun getNull() {
        assertNull(dao.get(6))
    }

    @Test fun delete() {
        assertFalse(dao.delete(6))
        dao.put(way(6))
        assertTrue(dao.delete(6))
        assertNull(dao.get(6))
        assertFalse(dao.delete(6))
    }

    @Test fun putAll() {
        dao.putAll(listOf(way(1), way(2)))
        assertNotNull(dao.get(1))
        assertNotNull(dao.get(2))
    }

    @Test fun getAll() {
        val e1 = way(1, nodeIds = listOf(1,2,3,12))
        val e2 = way(2, nodeIds = listOf(5,1233,564))
        val e3 = way(3, nodeIds = listOf(8,1654))
        dao.putAll(listOf(e1,e2,e3))
        assertEquals(
            listOf(e1, e2),
            dao.getAll(listOf(1,2,4)).sortedBy { it.id }
        )
        assertEquals(
            listOf(e1,e2,e3),
            dao.getAll(listOf(1,2,3)).sortedBy { it.id }
        )
    }

    @Test fun deleteAll() {
        dao.putAll(listOf(way(1), way(2), way(3)))
        assertEquals(2, dao.deleteAll(listOf(1,2,4)))
        assertNotNull(dao.get(3))
        assertNull(dao.get(1))
        assertNull(dao.get(2))
    }

    @Test fun getAllForNodes() {
        val e1 = way(1, nodeIds = listOf(1,2,3))
        val e2 = way(2, nodeIds = listOf(5,1233,1))
        val e3 = way(3, nodeIds = listOf(8,1654))
        dao.putAll(listOf(e1,e2,e3))
        assertEquals(
            listOf(e1, e2),
            dao.getAllForNode(1).sortedBy { it.id }
        )
        assertEquals(
            listOf(e2, e3),
            dao.getAllForNodes(listOf(5,8)).sortedBy { it.id }
        )
    }

    @Test fun getUnusedAndOldIds() {
        dao.putAll(listOf(way(1L), way(2L), way(3L)))
        val unusedIds = dao.getIdsOlderThan(System.currentTimeMillis() + 10)
        assertTrue(unusedIds.containsExactlyInAnyOrder(listOf(1L, 2L, 3L)))
    }
}

private fun way(
    id: Long = 1L,
    version: Int = 1,
    nodeIds: List<Long> = listOf(1,2,3),
    tags: Map<String,String> = emptyMap(),
    timestamp: Long = 123L
) = Way(id, nodeIds, tags, version, timestamp)
