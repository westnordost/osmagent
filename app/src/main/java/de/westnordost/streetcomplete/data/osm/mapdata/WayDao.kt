package de.westnordost.streetcomplete.data.osm.mapdata

import javax.inject.Inject

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.ID
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.INDEX
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.LAST_SYNC
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.NODE_ID
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.TAGS
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.TIMESTAMP
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.Columns.VERSION
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.NAME
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables.NAME_NODES
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.System.currentTimeMillis

/** Stores OSM ways */
class WayDao @Inject constructor(private val db: Database) {
    fun put(way: Way) {
        putAll(listOf(way))
    }

    fun get(id: Long): Way? =
        getAll(listOf(id)).firstOrNull()

    fun delete(id: Long): Boolean =
        deleteAll(listOf(id)) > 0

    fun putAll(ways: Collection<Way>) {
        if (ways.isEmpty()) return
        val idsString = ways.joinToString(",") { it.id.toString() }

        val time = currentTimeMillis()

        db.transaction {
            db.delete(NAME_NODES, "$ID IN ($idsString)")

            db.insertMany(NAME_NODES,
                arrayOf(ID, NODE_ID, INDEX),
                ways.flatMap { way ->
                    way.nodeIds.mapIndexed { index, nodeId ->
                        arrayOf(way.id, nodeId, index)
                    }
                }
            )

            db.replaceMany(NAME,
                arrayOf(ID, VERSION, TAGS, TIMESTAMP, LAST_SYNC),
                ways.map { way ->
                    arrayOf(
                        way.id,
                        way.version,
                        if (way.tags.isNotEmpty()) Json.encodeToString(way.tags) else null,
                        way.timestampEdited,
                        time
                    )
                }
            )
        }
    }

    fun getAll(ids: Collection<Long>): List<Way> {
        if (ids.isEmpty()) return emptyList()
        val idsString = ids.joinToString(",")

        val nodeIdsByWayId = mutableMapOf<Long, MutableList<Long>>()
        db.query(NAME_NODES, where = "$ID IN ($idsString)", orderBy = "$ID, $INDEX") { c ->
            val nodeIds = nodeIdsByWayId.getOrPut(c.getLong(ID)) { ArrayList() }
            nodeIds.add(c.getLong(NODE_ID))
        }

        return db.query(NAME, where = "$ID IN ($idsString)") { cursor ->
            Way(
                cursor.getLong(ID),
                nodeIdsByWayId.getValue(cursor.getLong(ID)),
                cursor.getStringOrNull(TAGS)?.let { Json.decodeFromString(it) } ?: emptyMap(),
                cursor.getInt(VERSION),
                cursor.getLong(TIMESTAMP)
            )
        }
    }

    fun deleteAll(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val idsString = ids.joinToString(",")
        return db.transaction {
            db.delete(NAME_NODES, "$ID IN ($idsString)")
            db.delete(NAME, "$ID IN ($idsString)")
        }
    }

    fun getAllForNode(nodeId: Long): List<Way> =
        getAllForNodes(listOf(nodeId))

    fun getAllForNodes(nodeIds: Collection<Long>): List<Way> =
        getAll(getAllIdsForNodes(nodeIds).toSet())

    fun getAllIdsForNodes(nodeIds: Collection<Long>): List<Long> {
        if (nodeIds.isEmpty()) return emptyList()
        val idsString = nodeIds.joinToString(",")
        return db.query(
            NAME_NODES,
            columns = arrayOf(ID),
            where = "$NODE_ID IN ($idsString)"
        ) { it.getLong(ID) }
    }

    fun getIdsOlderThan(timestamp: Long): List<Long> =
        db.query(NAME, columns = arrayOf(ID), where = "$LAST_SYNC < $timestamp") { it.getLong(ID) }
}
