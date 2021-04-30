package de.westnordost.streetcomplete.data.osm.mapdata

object NodeTable {
    const val NAME = "osm_nodes"

    object Columns {
        const val ID = "id"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val VERSION = "version"
        const val TAGS = "tags"
        const val TIMESTAMP = "timestamp"
        const val LAST_SYNC = "last_sync"
    }

    const val CREATE = """
        CREATE TABLE $NAME (
            ${Columns.ID} int PRIMARY KEY,
            ${Columns.VERSION} int NOT NULL,
            ${Columns.LATITUDE} double NOT NULL,
            ${Columns.LONGITUDE} double NOT NULL,
            ${Columns.TAGS} text,
            ${Columns.TIMESTAMP} int NOT NULL,
            ${Columns.LAST_SYNC} int NOT NULL
        );"""

    const val SPATIAL_INDEX_CREATE = """
        CREATE INDEX osm_nodes_spatial_index ON $NAME (
            ${Columns.LATITUDE},
            ${Columns.LONGITUDE}
        );
    """
}
