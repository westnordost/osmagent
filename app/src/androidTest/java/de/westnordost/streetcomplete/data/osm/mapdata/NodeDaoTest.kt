package de.westnordost.streetcomplete.data.osm.mapdata

import org.junit.Before
import org.junit.Test

import de.westnordost.streetcomplete.data.ApplicationDbTestCase
import de.westnordost.streetcomplete.ktx.containsExactlyInAnyOrder
import org.junit.Assert.*
import java.lang.System.currentTimeMillis

class NodeDaoTest : ApplicationDbTestCase() {
    private lateinit var dao: NodeDao

    @Before fun createDao() {
        dao = NodeDao(database)
    }

    @Test fun putGetNoTags() {
        val node = nd(5)
        dao.put(node)
        val dbNode = dao.get(5)

        assertEquals(node, dbNode!!)
    }

    @Test fun putGetWithTags() {
        val node = nd(5, 1, 2.0, 2.0, mapOf("a key" to "a value"))
        dao.put(node)
        val dbNode = dao.get(5)

        assertEquals(node, dbNode!!)
    }

    @Test fun putOverwrites() {
        dao.put(nd(6, 0))
        dao.put(nd(6, 5))
        assertEquals(5, dao.get(6)!!.version)
    }

    @Test fun getNull() {
        assertNull(dao.get(6))
    }

    @Test fun delete() {
        assertFalse(dao.delete(6))
        dao.put(nd(6))
        assertTrue(dao.delete(6))
        assertNull(dao.get(6))
        assertFalse(dao.delete(6))
    }

    @Test fun putAll() {
        dao.putAll(listOf(nd(1), nd(2)))
        assertNotNull(dao.get(1))
        assertNotNull(dao.get(2))
    }

    @Test fun getAll() {
        val e1 = nd(1)
        val e2 = nd(2)
        val e3 = nd(3)
        dao.putAll(listOf(e1,e2,e3))
        assertEquals(listOf(e1, e2), dao.getAll(listOf(1,2,4)))
    }

    @Test fun getAllByBbox() {
        val e1 = nd(1, lat = 0.5, lon = -0.5)
        val e2 = nd(2, lat = -0.5, lon = 0.5)
        val e3 = nd(3, lat = 1.5, lon = 0.5)
        val e4 = nd(4, lat = 0.5, lon = 1.5)
        val e5 = nd(5, lat = 0.5, lon = 0.5)
        val bbox = BoundingBox(0.0, 0.0, 1.0, 1.0)
        dao.putAll(listOf(e1,e2,e3,e4,e5))
        assertEquals(
            listOf(e5),
            dao.getAll(bbox)
        )
        assertEquals(
            listOf(5L),
            dao.getAllIds(bbox)
        )
    }

    @Test fun deleteAll() {
        dao.putAll(listOf(nd(1), nd(2), nd(3)))
        assertEquals(2,dao.deleteAll(listOf(1,2,4)))
        assertNotNull(dao.get(3))
        assertNull(dao.get(1))
        assertNull(dao.get(2))
    }

    @Test fun getUnusedAndOldIds() {
        dao.putAll(listOf(nd(1L), nd(2L), nd(3L)))
        val unusedIds = dao.getIdsOlderThan(currentTimeMillis() + 10)
        assertTrue(unusedIds.containsExactlyInAnyOrder(listOf(1L, 2L, 3L)))
    }
}

private fun nd(
    id: Long = 1L,
    version: Int = 1,
    lat: Double = 1.0,
    lon: Double = 2.0,
    tags: Map<String,String> = emptyMap(),
    timestamp: Long = 123
) = Node(id, LatLon(lat, lon), tags, version, timestamp)
