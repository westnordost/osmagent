package de.westnordost.streetcomplete.data.osm.geometry

import javax.inject.Inject

import de.westnordost.streetcomplete.data.CursorPosition
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.Columns.ELEMENT_ID
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.Columns.ELEMENT_TYPE
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.Columns.GEOMETRY_POLYGONS
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.Columns.GEOMETRY_POLYLINES
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.Columns.CENTER_LATITUDE
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.Columns.CENTER_LONGITUDE
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.NAME
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.NAME_TEMPORARY_LOOKUP
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.NAME_TEMPORARY_LOOKUP_MERGED_VIEW
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.TEMPORARY_LOOKUP_CREATE
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable.TEMPORARY_LOOKUP_MERGED_VIEW_CREATE
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon

/** Stores the geometry of elements */
class ElementGeometryDao @Inject constructor(
    private val db: Database,
    private val polylinesSerializer: PolylinesSerializer
) {
    fun put(entry: ElementGeometryEntry) {
        db.replace(NAME, entry.toPairs())
    }

    fun get(type: ElementType, id: Long): ElementGeometry? =
        db.queryOne(NAME,
            where = "$ELEMENT_TYPE = ? AND $ELEMENT_ID = ?",
            args = arrayOf(type.name, id)
        ) { it.toElementGeometry() }

    fun delete(type: ElementType, id: Long): Boolean =
        db.delete(NAME,
            where = "$ELEMENT_TYPE = ? AND $ELEMENT_ID = ?",
            args = arrayOf(type.name, id)
        ) == 1

    fun putAll(entries: Collection<ElementGeometryEntry>) {
        if (entries.isEmpty()) return

        db.replaceMany(NAME,
            arrayOf(
                ELEMENT_TYPE,
                ELEMENT_ID,
                CENTER_LATITUDE,
                CENTER_LONGITUDE,
                GEOMETRY_POLYGONS,
                GEOMETRY_POLYLINES
            ),
            entries.map {
                val bbox = it.geometry.getBounds()
                val g = it.geometry
                arrayOf(
                    it.elementType.name,
                    it.elementId,
                    g.center.latitude,
                    g.center.longitude,
                    if (g is ElementPolygonsGeometry) polylinesSerializer.serialize(g.polygons) else null,
                    if (g is ElementPolylinesGeometry) polylinesSerializer.serialize(g.polylines) else null
            ) }
        )
    }

    fun getAllEntries(keys: Collection<ElementKey>): List<ElementGeometryEntry> {
        if (keys.isEmpty()) return emptyList()
        return db.transaction {
            /* this looks a little complicated. Basically, this is a workaround for SQLite not
               supporting the "SELECT id FROM foo WHERE (a,b) IN ((1,2), (3,4), (5,6))" syntax:
               Instead, we insert the values into a temporary table and inner join on that table then
               https://stackoverflow.com/questions/18363276/how-do-you-do-an-in-query-that-has-multiple-columns-in-sqlite
             */
            db.exec(TEMPORARY_LOOKUP_CREATE)
            db.exec(TEMPORARY_LOOKUP_MERGED_VIEW_CREATE)
            db.insertOrIgnoreMany(NAME_TEMPORARY_LOOKUP,
                arrayOf(ELEMENT_TYPE, ELEMENT_ID),
                keys.map { arrayOf(it.type.name, it.id) }
            )
            val result = db.query(NAME_TEMPORARY_LOOKUP_MERGED_VIEW) { it.toElementGeometryEntry() }
            db.exec("DROP VIEW $NAME_TEMPORARY_LOOKUP_MERGED_VIEW")
            db.exec("DROP TABLE $NAME_TEMPORARY_LOOKUP")
            result
        }
    }

    fun deleteAll(entries: Collection<ElementKey>):Int {
        if (entries.isEmpty()) return 0
        var deletedCount = 0
        db.transaction {
            for (entry in entries) {
                if (delete(entry.type, entry.id)) deletedCount++
            }
        }
        return deletedCount
    }

    private fun ElementGeometryEntry.toPairs() = listOf(
        ELEMENT_TYPE to elementType.name,
        ELEMENT_ID to elementId
    ) + geometry.toPairs()

    private fun CursorPosition.toElementGeometryEntry() = ElementGeometryEntry(
        ElementType.valueOf(getString(ELEMENT_TYPE)),
        getLong(ELEMENT_ID),
        toElementGeometry()
    )

    private fun ElementGeometry.toPairs() = listOf(
        CENTER_LATITUDE to center.latitude,
        CENTER_LONGITUDE to center.longitude,
        GEOMETRY_POLYGONS to if (this is ElementPolygonsGeometry) polylinesSerializer.serialize(polygons) else null,
        GEOMETRY_POLYLINES to if (this is ElementPolylinesGeometry) polylinesSerializer.serialize(polylines) else null,
    )

    private fun CursorPosition.toElementGeometry(): ElementGeometry {
        val polylines: PolyLines? = getBlobOrNull(GEOMETRY_POLYLINES)?.let { polylinesSerializer.deserialize(it) }
        val polygons: PolyLines? = getBlobOrNull(GEOMETRY_POLYGONS)?.let { polylinesSerializer.deserialize(it) }
        val center = LatLon(getDouble(CENTER_LATITUDE), getDouble(CENTER_LONGITUDE))

        return when {
            polygons != null -> ElementPolygonsGeometry(polygons, center)
            polylines != null -> ElementPolylinesGeometry(polylines, center)
            else -> ElementPointGeometry(center)
        }
    }
}

private fun CursorPosition.toElementKey() = ElementKey(
    ElementType.valueOf(getString(ELEMENT_TYPE)),
    getLong(ELEMENT_ID)
)

data class ElementGeometryEntry(
    val elementType: ElementType,
    val elementId: Long,
    val geometry: ElementGeometry
)

private typealias PolyLines = List<List<LatLon>>
