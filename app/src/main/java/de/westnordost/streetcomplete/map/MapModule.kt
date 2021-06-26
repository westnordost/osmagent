package de.westnordost.streetcomplete.map

import dagger.Module
import dagger.Provides

@Module object MapModule {

    @Provides fun jawg(): VectorTileProvider = VectorTileProvider(
        sceneFilePath = "map_theme/jawg",
        baseTileSource = object : TileSource(
            title = "JawgMaps",
            maxZoom = 16,
            copyrightText = "© JawgMaps",
            copyrightLink = "https://www.jawg.io",
            privacyStatementLink = "https://www.jawg.io/en/confidentiality/",
            apiKey = String(byteArrayOf(
                109,76,57,88,52,83,119,120,102,115,65,71,102,111,106,118,71,105,105,111,110,57,104,80,
                75,117,71,76,75,120,80,98,111,103,76,121,77,98,116,97,107,65,50,103,74,51,88,56,56,103,
                99,86,108,84,83,81,55,79,68,54,79,102,98,90
            )),
        ) {
            override fun getTileUrl(zoom: Int, x: Int, y: Int): String =
                "https://tile.jawg.io/streets-v2/$zoom/$x/$y.pbf?access-token=$apiKey"
        },
        aerialLayerSource = object : TileSource(
            title = "ESRI",
            maxZoom = 17,
            copyrightText = "© Esri",
            copyrightLink = "https://wiki.openstreetmap.org/wiki/Esri",
            privacyStatementLink = "https://www.esri.com/en-us/privacy/privacy-statements",
            apiKey = null,
        ) {
            override fun getTileUrl(zoom: Int, x: Int, y: Int): String =
                "https://server.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
        },
    )
}
