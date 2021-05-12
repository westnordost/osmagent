package de.westnordost.streetcomplete.data.osm.osmquests

import android.util.Log
import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.countryboundaries.intersects
import de.westnordost.countryboundaries.isInAny
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.ktx.format
import de.westnordost.streetcomplete.util.contains
import de.westnordost.streetcomplete.util.enclosingBoundingBox
import de.westnordost.streetcomplete.util.enlargedBy
import de.westnordost.streetcomplete.util.measuredLength
import kotlinx.coroutines.*
import java.lang.System.currentTimeMillis
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.FutureTask
import javax.inject.Inject
import javax.inject.Singleton

/** Controller for managing OsmQuests. Takes care of persisting OsmQuest objects and notifying
 *  listeners about changes */
@Singleton class OsmQuestController @Inject internal constructor(
    private val db: OsmQuestDao,
    private val hiddenDB: OsmQuestsHiddenDao,
    private val mapDataSource: MapDataWithEditsSource,
    private val notesSource: NotesWithEditsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val countryBoundariesFuture: FutureTask<CountryBoundaries>
): OsmQuestSource {

    /* Must be a singleton because there is a listener that should respond to a change in the
     *  database table */

    interface HideOsmQuestListener {
        fun onHid(edit: OsmQuestHidden)
        fun onUnhid(edit: OsmQuestHidden)
        fun onUnhidAll()
    }
    private val hideListeners: MutableList<HideOsmQuestListener> = CopyOnWriteArrayList()

    private val listeners: MutableList<OsmQuestSource.Listener> = CopyOnWriteArrayList()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val allQuestTypes get() = questTypeRegistry.all.filterIsInstance<OsmElementQuestType<*>>()

    private val mapDataSourceListener = object : MapDataWithEditsSource.Listener {

        /** For the given elements, replace the current quests with the given ones. Called when
         *  OSM elements are updated, so the quests that reference that element need to be updated
         *  as well. */
        @Synchronized override fun onUpdated(updated: MapDataWithGeometry, deleted: Collection<ElementKey>) {
            val time = currentTimeMillis()

            val deferredQuests = mutableListOf<Deferred<OsmQuest?>>()
            val previousQuests = mutableListOf<OsmQuestDaoEntry>()
            var count = 0

            for (element in updated) {
                previousQuests.addAll(db.getAllForElement(element.type, element.id))
                val geometry = updated.getGeometry(element.type, element.id) ?: continue
                deferredQuests.addAll(createQuestsForElementDeferred(element, geometry, allQuestTypes))
                count++
            }

            val deleteQuestKeys = mutableListOf<OsmQuestKey>()
            for (key in deleted) {
                // quests that refer to elements that have been deleted shall be deleted
                deleteQuestKeys.addAll(db.getAllForElement(key.type, key.id).map { it.key })
            }

            val quests = runBlocking { deferredQuests.awaitAll().filterNotNull() }

            for (quest in quests) {
                val questTypeName = quest.type::class.simpleName!!
                Log.d(TAG, "Created $questTypeName for ${quest.elementType.name}#${quest.elementId}")
            }
            val seconds = (currentTimeMillis() - time) / 1000.0
            Log.i(TAG,"Created ${quests.size} quests for $count updated elements in ${seconds.format(1)}s")

            updateQuests(quests, previousQuests, deleteQuestKeys)
        }

        /** Replace all quests of the given types in the given bounding box with the given quests.
         *  Called on download of a quest type for a bounding box. */
        @Synchronized override fun onReplacedForBBox(bbox: BoundingBox, mapDataWithGeometry: MapDataWithGeometry) {
            val quests = createQuestsForBBox(bbox, mapDataWithGeometry, allQuestTypes)
            val previousQuests = db.getAllInBBox(bbox)
            updateQuests(quests, previousQuests, emptyList())
        }
    }

    private val notesSourceListener = object : NotesWithEditsSource.Listener {
        @Synchronized override fun onUpdated(added: Collection<Note>, updated: Collection<Note>, deleted: Collection<Long>) {
            onInvalidated()
        }
    }

    init {
        mapDataSource.addListener(mapDataSourceListener)
        notesSource.addListener(notesSourceListener)
    }

    private fun createQuestsForBBox(
        bbox: BoundingBox,
        mapDataWithGeometry: MapDataWithGeometry,
        questTypes: Collection<OsmElementQuestType<*>>,
    ): Collection<OsmQuest> {
        val time = currentTimeMillis()

        val countryBoundaries = countryBoundariesFuture.get()

        val deferredQuests: List<Deferred<List<OsmQuest>>> = questTypes.map { questType ->
            scope.async {
                val questsForType = ArrayList<OsmQuest>()
                val questTypeName = questType::class.simpleName!!
                if (!countryBoundaries.intersects(bbox, questType.enabledInCountries)) {
                    Log.d(TAG, "$questTypeName: Skipped because it is disabled for this country")
                    emptyList()
                } else {
                    val questTime = currentTimeMillis()
                    var questCount = 0
                    for (element in questType.getApplicableElements(mapDataWithGeometry)) {
                        val geometry = mapDataWithGeometry.getGeometry(element.type, element.id)
                            ?: continue
                        if (!mayCreateQuest(questType, geometry, bbox)) continue
                        questsForType.add(OsmQuest(questType, element.type, element.id, geometry))
                        questCount++
                    }

                    val questSeconds = currentTimeMillis() - questTime
                    Log.d(TAG, "$questTypeName: Found $questCount quests in ${questSeconds}ms")
                    questsForType
                }
            }
        }
        val quests = runBlocking { deferredQuests.awaitAll().flatten() }

        val seconds = (currentTimeMillis() - time) / 1000.0
        Log.i(TAG,"Created ${quests.size} quests for bbox in ${seconds.format(1)}s")

        return quests
    }

    private fun createQuestsForElementDeferred(
        element: Element,
        geometry: ElementGeometry,
        questTypes: Collection<OsmElementQuestType<*>>
    ): List<Deferred<OsmQuest?>> {
        val paddedBounds = geometry.getBounds().enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING)
        val lazyMapData by lazy { mapDataSource.getMapDataWithGeometry(paddedBounds) }

        return questTypes.map { questType ->
            scope.async {
                /* shortcut: if the element has no tags, it is just part of the geometry of another
                *  element, so no need to check for quests for that element */
                if (element.tags.isNullOrEmpty()) return@async null

                var appliesToElement = questType.isApplicableTo(element)
                if (appliesToElement == null) {
                    Log.d(TAG, "${questType::class.simpleName!!} requires surrounding map data to determine applicability to ${element.type.name}#${element.id}")
                    val mapData = withContext(Dispatchers.IO) { lazyMapData }
                    appliesToElement = questType.getApplicableElements(mapData)
                        .any{ it.id == element.id && it.type == element.type }
                }
                if (!appliesToElement) return@async null

                if (mayCreateQuest(questType, geometry, null)) {
                    OsmQuest(questType, element.type, element.id, geometry)
                } else {
                    null
                }
            }
        }
    }

    private fun updateQuests(
        questsNow: Collection<OsmQuest>,
        questsPreviously: Collection<OsmQuestDaoEntry>,
        deletedQuestKeys: Collection<OsmQuestKey>
    ) {

        val time = currentTimeMillis()

        val previousQuestsByKey = mutableMapOf<OsmQuestKey, OsmQuestDaoEntry>()
        questsPreviously.associateByTo(previousQuestsByKey) { it.key }

        for (quest in questsNow) {
            previousQuestsByKey.remove(quest.key)
        }
        // quests that were created previously for an element but now not anymore shall be deleted
        val obsoleteQuestKeys = previousQuestsByKey.values.map { it.key } + deletedQuestKeys

        db.deleteAll(obsoleteQuestKeys)
        db.putAll(questsNow)

        val seconds = (currentTimeMillis() - time) / 1000.0
        Log.i(TAG, "Persisted ${questsNow.size} new and removed ${obsoleteQuestKeys.size} already resolved quests in ${seconds.format(1)}s")

        onUpdated(added = questsNow, deletedKeys = obsoleteQuestKeys)
    }

    private fun mayCreateQuest(
        questType: OsmElementQuestType<*>,
        geometry: ElementGeometry,
        downloadedBoundingBox: BoundingBox?
    ): Boolean {
        val pos = geometry.center

        // outside downloaded area: skip
        if (downloadedBoundingBox != null && !downloadedBoundingBox.contains(pos)) return false

        // do not create quests in countries where the quest is not activated
        val countries = questType.enabledInCountries
        if (!countryBoundariesFuture.get().isInAny(pos, countries))  return false

        // do not create quests that refer to geometry that is too long for a surveyor to be expected to survey
        if (geometry is ElementPolylinesGeometry) {
            val totalLength = geometry.polylines.sumByDouble { it.measuredLength() }
            if (totalLength > MAX_GEOMETRY_LENGTH_IN_METERS) {
                return false
            }
        }
        return true
    }

    override fun get(key: OsmQuestKey): OsmQuest? {
        val entry = db.get(key) ?: return null
        if (hiddenDB.contains(entry.key)) return null
        val geometry = mapDataSource.getGeometry(entry.elementType, entry.elementId) ?: return null
        if (isBlacklistedPosition(geometry.center)) return null
        return createOsmQuest(entry, geometry)
    }

    override fun getAllVisibleInBBox(bbox: BoundingBox, questTypes: Collection<String>?): List<OsmQuest> {
        val hiddenIds = getHiddenQuests()
        val hiddenPositions = getBlacklistedPositions(bbox)
        val entries = db.getAllInBBox(bbox, questTypes).filter {
            it.key !in hiddenIds && it.position.truncateTo5Decimals() !in hiddenPositions
        }

        val elementKeys = HashSet<ElementKey>()
        entries.mapTo(elementKeys) { ElementKey(it.elementType, it.elementId) }

        val geometriesByKey = mapDataSource.getGeometries(elementKeys)
            .associateBy { ElementKey(it.elementType, it.elementId) }

        return entries.mapNotNull { entry ->
            val geometryEntry = geometriesByKey[ElementKey(entry.elementType, entry.elementId)]
            createOsmQuest(entry, geometryEntry?.geometry)
        }
    }

    private fun createOsmQuest(entry: OsmQuestDaoEntry, geometry: ElementGeometry?): OsmQuest? {
        if (geometry == null) return null
        val questType = questTypeRegistry.getByName(entry.questTypeName) as? OsmElementQuestType<*> ?: return null
        return OsmQuest(questType, entry.elementType, entry.elementId, geometry)
    }

    /* ----------------------------------- Hiding / Unhiding  ----------------------------------- */

    private fun getBlacklistedPositions(bbox: BoundingBox): Set<LatLon> =
        notesSource
            .getAllPositions(bbox.enlargedBy(1.2))
            .map { it.truncateTo5Decimals() }
            .toSet()

    private fun isBlacklistedPosition(pos: LatLon): Boolean =
        pos.truncateTo5Decimals() in getBlacklistedPositions(BoundingBox(pos, pos))

    private fun getHiddenQuests(): Set<OsmQuestKey> =
        hiddenDB.getAllIds().toSet()

    /** Mark the quest as hidden by user interaction */
    @Synchronized fun hide(key: OsmQuestKey) {
        hiddenDB.add(key)
        val hidden = getHidden(key)
        if (hidden != null) onHid(hidden)
        onUpdated(deletedKeys = listOf(key))
    }

    @Synchronized fun unhide(key: OsmQuestKey): Boolean {
        val hidden = getHidden(key)
        if (!hiddenDB.delete(key)) return false
        if (hidden != null) onUnhid(hidden)
        val quest = get(key)
        if (quest != null) onUpdated(added = listOf(quest))
        return true
    }

    /** Un-hides all previously hidden quests by user interaction */
    @Synchronized fun unhideAll(): Int {
        val result = hiddenDB.deleteAll()
        onUnhidAll()
        onInvalidated()
        return result
    }

    fun getHidden(key: OsmQuestKey): OsmQuestHidden? {
        val timestamp = hiddenDB.getTimestamp(key) ?: return null
        val pos = mapDataSource.getGeometry(key.elementType, key.elementId)?.center
        return createOsmQuestHidden(key, pos, timestamp)
    }

    fun getAllHiddenNewerThan(timestamp: Long): List<OsmQuestHidden> {
        val questKeysWithTimestamp = hiddenDB.getNewerThan(timestamp)

        val elementKeys = HashSet<ElementKey>()
        questKeysWithTimestamp.mapTo(elementKeys) {
            ElementKey(it.osmQuestKey.elementType, it.osmQuestKey.elementId)
        }

        val geometriesByKey = mapDataSource.getGeometries(elementKeys)
            .associateBy { ElementKey(it.elementType, it.elementId) }

        return questKeysWithTimestamp.mapNotNull { (key, timestamp) ->
            val pos = geometriesByKey[ElementKey(key.elementType, key.elementId)]?.geometry?.center
            createOsmQuestHidden(key, pos, timestamp)
        }
    }

    private fun createOsmQuestHidden(key: OsmQuestKey, position: LatLon?, timestamp: Long): OsmQuestHidden? {
        if (position == null) return null
        val questType = questTypeRegistry.getByName(key.questTypeName) as? OsmElementQuestType<*> ?: return null
        return OsmQuestHidden(key.elementType, key.elementId, questType, position, timestamp)
    }

    /* ---------------------------------------- Listeners --------------------------------------- */

    override fun addListener(listener: OsmQuestSource.Listener) {
        listeners.add(listener)
    }
    override fun removeListener(listener: OsmQuestSource.Listener) {
        listeners.remove(listener)
    }

    private fun onUpdated(
        added: Collection<OsmQuest> = emptyList(),
        deletedKeys: Collection<OsmQuestKey> = emptyList()
    ) {
        if (added.isEmpty() && deletedKeys.isEmpty()) return

        val visibleAdded = if (added.isNotEmpty()) {
            val hiddenIds = getHiddenQuests()
            val bbox = added.map { it.position }.enclosingBoundingBox()
            val hiddenPositions = getBlacklistedPositions(bbox)
            added.filter { it.key !in hiddenIds && it.position.truncateTo5Decimals() !in hiddenPositions }
        } else {
            added
        }

        listeners.forEach { it.onUpdated(visibleAdded, deletedKeys) }
    }
    private fun onInvalidated() {
        listeners.forEach { it.onInvalidated() }
    }

    /* ------------------------------------- Hide Listeners ------------------------------------- */

    fun addHideQuestsListener(listener: HideOsmQuestListener) {
        hideListeners.add(listener)
    }
    fun removeHideQuestsListener(listener: HideOsmQuestListener) {
        hideListeners.remove(listener)
    }

    private fun onHid(edit: OsmQuestHidden) {
        hideListeners.forEach { it.onHid(edit) }
    }
    private fun onUnhid(edit: OsmQuestHidden) {
        hideListeners.forEach { it.onUnhid(edit) }
    }
    private fun onUnhidAll() {
        hideListeners.forEach { it.onUnhidAll() }
    }

    companion object {
        private const val TAG = "OsmQuestController"
    }
}

// the resulting precision is about ~1 meter (see #1089)
private fun LatLon.truncateTo5Decimals() = LatLon(latitude.truncateTo5Decimals(), longitude.truncateTo5Decimals())

private fun Double.truncateTo5Decimals() = (this * 1e5).toInt().toDouble() / 1e5

const val MAX_GEOMETRY_LENGTH_IN_METERS = 600
