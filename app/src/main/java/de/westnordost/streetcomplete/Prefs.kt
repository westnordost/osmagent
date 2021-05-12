package de.westnordost.streetcomplete

import androidx.appcompat.app.AppCompatDelegate

/** Constant class to have all the identifiers for shared preferences in one place  */
object Prefs {
    const val OAUTH = "oauth"
    const val OAUTH_ACCESS_TOKEN = "oauth.accessToken"
    const val OAUTH_ACCESS_TOKEN_SECRET = "oauth.accessTokenSecret"
    const val MAP_TILECACHE_IN_MB = "map.tilecache"
    const val SHOW_NOTES_NOT_PHRASED_AS_QUESTIONS = "display.nonQuestionNotes"
    const val AUTOSYNC = "autosync"
    const val KEEP_SCREEN_ON = "display.keepScreenOn"
    const val UNGLUE_HINT_TIMES_SHOWN = "unglueHint.shown"
    const val THEME_SELECT = "theme.select"
    const val THEME_BACKGROUND = "theme.background_type"

    const val RESURVEY_INTERVALS = "quests.resurveyIntervals"

    const val OSM_USER_ID = "osm.userid"
    const val OSM_USER_NAME = "osm.username"
    const val OSM_UNREAD_MESSAGES = "osm.unread_messages"
    const val USER_DAYS_ACTIVE = "days_active"
    const val USER_GLOBAL_RANK = "user_global_rank"
    const val USER_LAST_TIMESTAMP_ACTIVE = "last_timestamp_active"
    const val IS_SYNCHRONIZING_STATISTICS = "is_synchronizing_statistics"
    const val TEAM_MODE_INDEX_IN_TEAM = "team_mode.index_in_team"
    const val TEAM_MODE_TEAM_SIZE = "team_mode.team_size"

    // not shown anywhere directly
    const val QUEST_ORDER = "quests.order"
    const val LAST_EDIT_TIME = "changesets.lastChangeTime"
    const val MAP_LATITUDE = "map.latitude"
    const val MAP_LONGITUDE = "map.longitude"
    const val LAST_PICKED_PREFIX = "imageListLastPicked."
    const val LAST_LOCATION_REQUEST_DENIED = "location.denied"
    const val LAST_VERSION = "lastVersion"
    const val LAST_VERSION_DATA = "lastVersion_data"
    const val HAS_SHOWN_TUTORIAL = "hasShownTutorial"

    const val PIN_SPRITES_VERSION = "TangramPinsSpriteSheet.version"
    const val PIN_SPRITES = "TangramPinsSpriteSheet.sprites"

    enum class Autosync {
        ON, WIFI, OFF
    }

    enum class Theme(val appCompatNightMode: Int) {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        AUTO(AppCompatDelegate.MODE_NIGHT_AUTO),
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    enum class ResurveyIntervals {
        LESS_OFTEN, DEFAULT, MORE_OFTEN
    }
}
