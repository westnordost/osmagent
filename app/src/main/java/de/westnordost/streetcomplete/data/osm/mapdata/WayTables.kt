package de.westnordost.streetcomplete.data.osm.mapdata

object WayTables {
    const val NAME = "osm_ways"
    const val NAME_NODES = "osm_way_nodes"

    object Columns {
        const val ID = "id"
        const val VERSION = "version"
        const val TAGS = "tags"
        const val TIMESTAMP = "timestamp"
        const val LAST_SYNC = "last_sync"

        const val NODE_ID = "node_id"
        const val INDEX = "idx"
    }

    const val CREATE = """
        CREATE TABLE $NAME (
            ${Columns.ID} int PRIMARY KEY,
            ${Columns.VERSION} int NOT NULL,
            ${Columns.TAGS} text,
            ${Columns.TIMESTAMP} int NOT NULL,
            ${Columns.LAST_SYNC} int NOT NULL
        );
    """

    const val NODES_CREATE = """
        CREATE TABLE $NAME_NODES (
            ${Columns.ID} int NOT NULL,
            ${Columns.INDEX} int NOT NULL,
            ${Columns.NODE_ID} int NOT NULL
        );
    """

    const val NODES_INDEX_CREATE = """
        CREATE INDEX osm_way_nodes_index ON $NAME_NODES (${Columns.ID});
    """

    const val WAYS_BY_NODE_ID_INDEX_CREATE = """
        CREATE INDEX osm_way_by_node_id_index ON $NAME_NODES (${Columns.NODE_ID});
    """
}
