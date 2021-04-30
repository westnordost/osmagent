package de.westnordost.streetcomplete.data.osm.mapdata

import javax.inject.Inject

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.ID
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.INDEX
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.LAST_SYNC
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.REF
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.ROLE
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.TAGS
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.TIMESTAMP
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.TYPE
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.Columns.VERSION
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.NAME
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables.NAME_MEMBERS
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.System.currentTimeMillis

/** Stores OSM relations */
class RelationDao @Inject constructor(private val db: Database) {
    fun put(relation: Relation) {
        putAll(listOf(relation))
    }

    fun get(id: Long): Relation? =
        getAll(listOf(id)).firstOrNull()

    fun delete(id: Long): Boolean =
        deleteAll(listOf(id)) == 1

    fun putAll(relations: Collection<Relation>) {
        if (relations.isEmpty()) return
        val idsString = relations.joinToString(",") { it.id.toString() }

        val time = currentTimeMillis()

        db.transaction {
            db.delete(NAME_MEMBERS, "$ID IN ($idsString)")

            db.insertMany(NAME_MEMBERS,
                arrayOf(ID, INDEX, REF, TYPE, ROLE),
                relations.flatMap { relation ->
                    relation.members.mapIndexed { index, member ->
                        arrayOf(
                            relation.id,
                            index,
                            member.ref,
                            member.type.name,
                            member.role
                        )
                    }
                }
            )
            db.replaceMany(NAME,
                arrayOf(ID, VERSION, TAGS, TIMESTAMP, LAST_SYNC),
                relations.map { relation ->
                    arrayOf(
                        relation.id,
                        relation.version,
                        if (relation.tags.isNotEmpty()) Json.encodeToString(relation.tags) else null,
                        relation.timestampEdited,
                        time
                    )
                }
            )
        }
    }

    fun getAll(ids: Collection<Long>): List<Relation> {
        if (ids.isEmpty()) return emptyList()
        val idsString = ids.joinToString(",")

        val membersByRelationId = mutableMapOf<Long, MutableList<RelationMember>>()
        db.query(NAME_MEMBERS, where = "$ID IN ($idsString)", orderBy = "$ID, $INDEX") { cursor ->
            val members = membersByRelationId.getOrPut(cursor.getLong(ID)) { ArrayList() }
            members.add(RelationMember(
                ElementType.valueOf(cursor.getString(TYPE)),
                cursor.getLong(REF),
                cursor.getString(ROLE)
            ))
        }

        return db.query(NAME, where = "$ID IN ($idsString)") { cursor ->
            Relation(
                cursor.getLong(ID),
                membersByRelationId.getValue(cursor.getLong(ID)),
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
            db.delete(NAME_MEMBERS, "$ID IN ($idsString)")
            db.delete(NAME, "$ID IN ($idsString)")
        }
    }

    fun getAllForElements(
        nodeIds: Collection<Long> = emptyList(),
        wayIds: Collection<Long> = emptyList(),
        relationIds: Collection<Long> = emptyList()
    ) : List<Relation> =
        getAll(getAllIdsForElements(nodeIds, wayIds, relationIds).toSet())

    fun getAllIdsForElements(
        nodeIds: Collection<Long> = emptyList(),
        wayIds: Collection<Long> = emptyList(),
        relationIds: Collection<Long> = emptyList()
    ): List<Long> {
        if (nodeIds.isEmpty() && wayIds.isEmpty() && relationIds.isEmpty()) return emptyList()

        val where = ArrayList<String>()
        if (nodeIds.isNotEmpty()) {
            val nodeIdsStr = nodeIds.joinToString(",")
            val elementTypeName = ElementType.NODE.name
            where.add("($TYPE = '$elementTypeName' AND $REF IN ($nodeIdsStr))")
        }
        if (wayIds.isNotEmpty()) {
            val wayIdsStr = wayIds.joinToString(",")
            val elementTypeName = ElementType.WAY.name
            where.add("($TYPE = '$elementTypeName' AND $REF IN ($wayIdsStr))")
        }
        if (relationIds.isNotEmpty()) {
            val relationIdsStr = relationIds.joinToString(",")
            val elementTypeName = ElementType.RELATION.name
            where.add("($TYPE = '$elementTypeName' AND $REF IN ($relationIdsStr))")
        }
        return db.query(
            NAME_MEMBERS,
            columns = arrayOf(ID),
            where = where.joinToString(" OR ")) { it.getLong(ID) }
    }

    fun getAllForNode(nodeId: Long) : List<Relation> =
        getAllForElement(ElementType.NODE, nodeId)

    fun getAllForWay(wayId: Long) : List<Relation> =
        getAllForElement(ElementType.WAY, wayId)

    fun getAllForRelation(relationId: Long) : List<Relation> =
        getAllForElement(ElementType.RELATION, relationId)

    fun getIdsOlderThan(timestamp: Long): List<Long> =
        db.query(NAME, columns = arrayOf(ID), where = "$LAST_SYNC < $timestamp") { it.getLong(ID) }

    private fun getAllForElement(elementType: ElementType, elementId: Long): List<Relation> {
        val ids = db.query(
            NAME_MEMBERS,
            where = "$TYPE = ? AND $REF = $elementId",
            args = arrayOf(elementType.name)) { it.getLong(ID) }.toSet()
        return getAll(ids)
    }
}
