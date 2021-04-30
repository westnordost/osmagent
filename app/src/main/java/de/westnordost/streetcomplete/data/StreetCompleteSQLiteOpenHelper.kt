package de.westnordost.streetcomplete.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsTable
import de.westnordost.streetcomplete.data.user.achievements.UserLinksTable

import javax.inject.Singleton

import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.OpenChangesetsTable
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryTable
import de.westnordost.streetcomplete.data.osm.mapdata.NodeTable
import de.westnordost.streetcomplete.data.osmnotes.NoteTable
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables
import de.westnordost.streetcomplete.data.visiblequests.VisibleQuestTypeTable
import de.westnordost.streetcomplete.data.user.QuestStatisticsTable
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesTable
import de.westnordost.streetcomplete.data.notifications.NewUserAchievementsTable
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsTable
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProviderTable
import de.westnordost.streetcomplete.data.osm.osmquests.*
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsTable
import de.westnordost.streetcomplete.data.osmnotes.notequests.NoteQuestsHiddenTable
import de.westnordost.streetcomplete.data.user.CountryStatisticsTable
import de.westnordost.streetcomplete.quests.oneway_suspects.data.WayTrafficFlowTable

@Singleton class StreetCompleteSQLiteOpenHelper(context: Context, dbName: String) :
    SQLiteOpenHelper(context, dbName, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // OSM notes
        db.execSQL(NoteTable.CREATE)
        db.execSQL(NoteTable.SPATIAL_INDEX_CREATE)

        // changes made on OSM notes
        db.execSQL(NoteEditsTable.CREATE)
        db.execSQL(NoteEditsTable.SPATIAL_INDEX_CREATE)
        db.execSQL(NoteEditsTable.NOTE_ID_INDEX_CREATE)

        // OSM map data
        db.execSQL(ElementGeometryTable.CREATE)
        db.execSQL(ElementGeometryTable.SPATIAL_INDEX_CREATE)

        db.execSQL(NodeTable.CREATE)
        db.execSQL(NodeTable.SPATIAL_INDEX_CREATE)

        db.execSQL(WayTables.CREATE)
        db.execSQL(WayTables.NODES_CREATE)
        db.execSQL(WayTables.NODES_INDEX_CREATE)
        db.execSQL(WayTables.WAYS_BY_NODE_ID_INDEX_CREATE)

        db.execSQL(RelationTables.CREATE)
        db.execSQL(RelationTables.MEMBERS_CREATE)
        db.execSQL(RelationTables.MEMBERS_INDEX_CREATE)
        db.execSQL(RelationTables.MEMBERS_BY_ELEMENT_INDEX_CREATE)

        // changes made on OSM map data
        db.execSQL(ElementEditsTable.CREATE)
        db.execSQL(ElementEditsTable.ELEMENT_INDEX_CREATE)
        db.execSQL(ElementIdProviderTable.CREATE)
        db.execSQL(ElementIdProviderTable.INDEX_CREATE)

        // quests
        db.execSQL(VisibleQuestTypeTable.CREATE)

        // quests based on OSM elements
        db.execSQL(OsmQuestTable.CREATE)
        db.execSQL(OsmQuestTable.SPATIAL_INDEX_CREATE)
        db.execSQL(OsmQuestsHiddenTable.CREATE)

        // quests based on OSM notes
        db.execSQL(NoteQuestsHiddenTable.CREATE)

        // for upload / download
        db.execSQL(OpenChangesetsTable.CREATE)
        db.execSQL(DownloadedTilesTable.CREATE)

        // user statistics
        db.execSQL(QuestStatisticsTable.CREATE)
        db.execSQL(CountryStatisticsTable.CREATE)
        db.execSQL(UserAchievementsTable.CREATE)
        db.execSQL(UserLinksTable.CREATE)
        db.execSQL(NewUserAchievementsTable.CREATE)

        // quest specific tables
        db.execSQL(WayTrafficFlowTable.CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // for later changes to the DB
        // ...
    }

}

private const val DB_VERSION = 1
