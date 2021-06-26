package de.westnordost.streetcomplete.map

data class VectorTileProvider(
    val sceneFilePath: String,
    val baseTileSource: TileSource,
    val aerialLayerSource: TileSource,
)

abstract class TileSource(
    val title: String,
    val maxZoom: Int,
    val copyrightText: String,
    val copyrightLink: String,
    val privacyStatementLink: String,
    val apiKey: String?,
) {
    abstract fun getTileUrl(zoom: Int, x: Int, y: Int): String
}
