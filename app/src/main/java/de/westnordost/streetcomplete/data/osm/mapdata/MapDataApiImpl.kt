package de.westnordost.streetcomplete.data.osm.mapdata

import de.westnordost.osmapi.OsmConnection
import de.westnordost.osmapi.common.errors.OsmNotFoundException
import de.westnordost.osmapi.map.data.*

import de.westnordost.osmapi.map.MapDataApi as OsmApiMapDataApi
import de.westnordost.osmapi.map.data.Element as OsmApiElement
import de.westnordost.osmapi.map.data.Node as OsmApiNode
import de.westnordost.osmapi.map.data.Way as OsmApiWay
import de.westnordost.osmapi.map.data.Relation as OsmApiRelation
import de.westnordost.osmapi.map.data.RelationMember as OsmApiRelationMember
import de.westnordost.osmapi.map.data.BoundingBox as OsmApiBoundingBox
import de.westnordost.osmapi.map.changes.DiffElement as OsmApiDiffElement

import de.westnordost.osmapi.map.handler.MapDataHandler
import java.time.Instant

class MapDataApiImpl(osm: OsmConnection) : MapDataApi {

    private val api: OsmApiMapDataApi = OsmApiMapDataApi(osm)

    override fun uploadChanges(changesetId: Long, changes: MapDataChanges): MapDataUpdates {
        val handler = UpdatedElementsHandler()
        api.uploadChanges(changesetId, changes.toOsmApiElements()) {
            handler.handle(it.toDiffElement())
        }
        val allChangedElements = changes.creations + changes.modifications + changes.deletions
        return handler.getElementUpdates(allChangedElements)
    }

    override fun openChangeset(tags: Map<String, String?>): Long = api.openChangeset(tags)

    override fun closeChangeset(changesetId: Long) = api.closeChangeset(changesetId)

    override fun getMap(bounds: BoundingBox, mutableMapData: MutableMapData, ignoreRelationTypes: Set<String?>) =
        api.getMap(
            bounds.toOsmApiBoundingBox(),
            MapDataApiHandler(mutableMapData, ignoreRelationTypes)
        )

    override fun getWayComplete(id: Long): MapData? =
        try {
            val result = MutableMapData()
            api.getWayComplete(id, MapDataApiHandler(result))
            result
        } catch (e: OsmNotFoundException) {
            null
        }

    override fun getRelationComplete(id: Long): MapData? =
        try {
            val result = MutableMapData()
            api.getRelationComplete(id, MapDataApiHandler(result))
            result
        } catch (e: OsmNotFoundException) {
            null
        }

    override fun getNode(id: Long): Node? = api.getNode(id)?.toNode()

    override fun getWay(id: Long): Way? = api.getWay(id)?.toWay()

    override fun getRelation(id: Long): Relation? = api.getRelation(id)?.toRelation()

    override fun getWaysForNode(id: Long): List<Way> =
        api.getWaysForNode(id).map { it.toWay() }

    override fun getRelationsForNode(id: Long): List<Relation> =
        api.getRelationsForNode(id).map { it.toRelation() }

    override fun getRelationsForWay(id: Long): List<Relation> =
        api.getRelationsForWay(id).map { it.toRelation() }

    override fun getRelationsForRelation(id: Long): List<Relation> =
        api.getRelationsForRelation(id).map { it.toRelation() }
}

/* --------------------------------- Element -> OsmApiElement ----------------------------------- */

private fun MapDataChanges.toOsmApiElements(): List<OsmApiElement> =
    creations.map { it.toOsmApiElement().apply { isNew = true } } +
    modifications.map { it.toOsmApiElement().apply { isModified = true } } +
    deletions.map { it.toOsmApiElement().apply { isDeleted = true } }

private fun Element.toOsmApiElement(): OsmElement = when(this) {
    is Node -> toOsmApiNode()
    is Way -> toOsmApiWay()
    is Relation -> toOsmApiRelation()
}

private fun Node.toOsmApiNode() = OsmNode(
    id,
    version,
    OsmLatLon(position.latitude, position.longitude),
    tags,
    null,
    Instant.ofEpochMilli(timestampEdited)
)

private fun Way.toOsmApiWay() = OsmWay(
    id,
    version,
    nodeIds,
    tags,
    null,
    Instant.ofEpochMilli(timestampEdited)
)

private fun Relation.toOsmApiRelation() = OsmRelation(
    id,
    version,
    members.map { it.toOsmRelationMember() },
    tags,
    null,
    Instant.ofEpochMilli(timestampEdited)
)

private fun RelationMember.toOsmRelationMember() = OsmRelationMember(
    ref,
    role,
    type.toOsmElementType()
)

private fun ElementType.toOsmElementType(): OsmApiElement.Type = when(this) {
    ElementType.NODE        -> OsmApiElement.Type.NODE
    ElementType.WAY         -> OsmApiElement.Type.WAY
    ElementType.RELATION    -> OsmApiElement.Type.RELATION
}

private fun BoundingBox.toOsmApiBoundingBox() =
    OsmApiBoundingBox(min.latitude, min.longitude, max.latitude, max.longitude)

/* --------------------------------- OsmApiElement -> Element ----------------------------------- */

private fun OsmApiNode.toNode() =
    Node(id, LatLon(position.latitude, position.longitude), tags, version, editedAt.toEpochMilli())

private fun OsmApiWay.toWay() =
    Way(id, nodeIds, tags, version, editedAt.toEpochMilli())

private fun OsmApiRelation.toRelation() = Relation(
    id,
    members.map { it.toRelationMember() }.toMutableList(),
    tags,
    version,
    editedAt.toEpochMilli()
)

private fun OsmApiRelationMember.toRelationMember() =
    RelationMember(type.toElementType(), ref, role)

private fun OsmApiElement.Type.toElementType(): ElementType = when(this) {
    OsmApiElement.Type.NODE     -> ElementType.NODE
    OsmApiElement.Type.WAY      -> ElementType.WAY
    OsmApiElement.Type.RELATION -> ElementType.RELATION
}

private fun OsmApiDiffElement.toDiffElement() = DiffElement(
    type.toElementType(),
    clientId,
    serverId,
    serverVersion
)

private fun OsmApiBoundingBox.toBoundingBox() =
    BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude)

/* ---------------------------------------------------------------------------------------------- */

private class MapDataApiHandler(
    val data: MutableMapData,
    val ignoreRelationTypes: Set<String?> = emptySet()
) : MapDataHandler {

    override fun handle(bounds: OsmApiBoundingBox) {
        data.boundingBox = bounds.toBoundingBox()
    }

    override fun handle(node: OsmApiNode) {
        data.add(node.toNode())
    }

    override fun handle(way: OsmApiWay) {
        data.add(way.toWay())
    }

    override fun handle(relation: OsmApiRelation) {
        val relationType = relation.tags?.get("type")
        if (relationType !in ignoreRelationTypes) {
            data.add(relation.toRelation())
        }
    }
}
