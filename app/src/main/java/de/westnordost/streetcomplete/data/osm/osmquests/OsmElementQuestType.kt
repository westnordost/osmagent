package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.quest.AllCountries
import de.westnordost.streetcomplete.data.quest.Countries
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.ktx.arrayOfNotNull

/** Quest type where each quest refers to an OSM element */
interface OsmElementQuestType<T> : QuestType<T> {

    fun getTitleArgs(tags: Map<String, String>, featureName: Lazy<String?>): Array<String> =
        arrayOfNotNull(tags["name"] ?: tags["brand"])

    /** the commit message to be used for this quest type */
    val commitMessage: String

    /** the OpenStreetMap wiki page with the documentation */
    val wikiLink: String?

    /** in which countries the quest should be shown */
    val enabledInCountries: Countries get() = AllCountries

    /** returns whether the markers should be at the ends instead of the center */
    val hasMarkersAtEnds: Boolean get() = false

    /** returns whether the user should be able to split the way instead */
    val isSplitWayEnabled: Boolean get() = false

    /** returns whether the user should be able to delete this element instead. Only elements that
     *  are not expected...
     *  - to be part of a relation
     *  - to be part of a network (f.e. roads, power lines, ...)
     *  - to be part of a(nother) way
     *  - to house a second POI on the same element
     *  - to be a kind of element where deletion is not recommended, (f.e. a shop should rather
     *    be set to disused:shop=yes until there is another one)
     *  ...should be deletable */
    val isDeleteElementEnabled: Boolean get() = false

    /** returns whether the user should be able to replace this element with another preset. Only
     *  elements that are expected to be some kind of shop/amenity should be replaceable this way,
     *  i.e. anything that when it's gone, there is a vacant shop then.
     *  */
    val isReplaceShopEnabled: Boolean get() = false

    /** returns title resource for when the element has the specified [tags]. The tags are unmodifiable */
    fun getTitle(tags: Map<String, String>): Int

    override val title: Int get() = getTitle(emptyMap())

    /** return all elements within the given map data that are applicable to this quest type. */
    fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element>

    /** returns whether a quest of this quest type could be created out of the given [element]. If the
     * element alone does not suffice to find this out (but f.e. is determined by the data around
     * it), this should return null.
     *
     * The implications of returning null here is that this quest will never be created directly
     * as consequence of solving another quest and also after reverting an input, the quest will
     * not immediately pop up again.*/
    fun isApplicableTo(element: Element): Boolean?

    /** applies the data from [answer] to the given element. The element is not directly modified,
     *  instead, a map of [changes] is built */
    fun applyAnswerTo(answer: T, changes: StringMapChangesBuilder)

    @Suppress("UNCHECKED_CAST")
    fun applyAnswerToUnsafe(answer: Any, changes: StringMapChangesBuilder) {
        applyAnswerTo(answer as T, changes)
    }
}
