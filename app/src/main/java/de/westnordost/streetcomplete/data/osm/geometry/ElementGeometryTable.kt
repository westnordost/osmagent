package de.westnordost.streetcomplete.data.osm.geometry

object ElementGeometryTable {
    const val NAME = "elements_geometry"
    const val NAME_TEMPORARY_LOOKUP = "elements_geometry_lookup"
    const val NAME_TEMPORARY_LOOKUP_MERGED_VIEW = "elements_geometry_lookup_view"

    object Columns {
        const val ELEMENT_ID = "element_id"
        const val ELEMENT_TYPE = "element_type"
        const val GEOMETRY_POLYGONS = "geometry_polygons"
        const val GEOMETRY_POLYLINES = "geometry_polylines"
        const val CENTER_LATITUDE = "latitude"
        const val CENTER_LONGITUDE = "longitude"
    }

    const val CREATE = """
        CREATE TABLE $NAME (
            ${Columns.ELEMENT_TYPE} varchar(255) NOT NULL,
            ${Columns.ELEMENT_ID} int NOT NULL,
            ${Columns.GEOMETRY_POLYLINES} blob,
            ${Columns.GEOMETRY_POLYGONS} blob,
            ${Columns.CENTER_LATITUDE} double NOT NULL,
            ${Columns.CENTER_LONGITUDE} double NOT NULL,
            CONSTRAINT primary_key PRIMARY KEY (
                ${Columns.ELEMENT_TYPE},
                ${Columns.ELEMENT_ID}
            )
        );"""

    const val TEMPORARY_LOOKUP_CREATE = """
        CREATE TEMPORARY TABLE $NAME_TEMPORARY_LOOKUP(
            ${Columns.ELEMENT_TYPE} varchar(255) NOT NULL,
            ${Columns.ELEMENT_ID} int NOT NULL,
            CONSTRAINT primary_key PRIMARY KEY (
                ${Columns.ELEMENT_TYPE},
                ${Columns.ELEMENT_ID}
            )
        );
    """

    const val TEMPORARY_LOOKUP_MERGED_VIEW_CREATE = """
        CREATE TEMPORARY VIEW $NAME_TEMPORARY_LOOKUP_MERGED_VIEW AS
            SELECT * FROM $NAME
            INNER JOIN $NAME_TEMPORARY_LOOKUP
            USING (${Columns.ELEMENT_TYPE}, ${Columns.ELEMENT_ID})
        ;
    """
}

