package de.westnordost.streetcomplete.data.maptiles

import android.util.Log
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.ktx.format
import de.westnordost.streetcomplete.map.TileSource
import de.westnordost.streetcomplete.map.VectorTileProvider
import de.westnordost.streetcomplete.util.enclosingTilesRect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.internal.Version
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.system.measureTimeMillis

class MapTilesDownloader @Inject constructor(
    private val vectorTileProvider: VectorTileProvider,
    private val cacheConfig: MapTilesDownloadCacheConfig
) {
    private val okHttpClient = OkHttpClient.Builder().cache(cacheConfig.cache).build()

    private data class DownloadStats(
        var tileCount: Int = 0,
        var failureCount: Int = 0,
        var downloadedSize: Int = 0,
        var cachedSize: Int = 0,
    ) {
        fun add(result: DownloadResult) {
            ++tileCount
            when (result) {
                is DownloadFailure -> ++failureCount
                is DownloadSuccess -> {
                    if (result.alreadyCached) cachedSize += result.size
                    else downloadedSize += result.size
                }
            }
        }
    }

    suspend fun download(bbox: BoundingBox) = withContext(Dispatchers.IO) {
        val stats = DownloadStats()

        val seconds = measureTimeMillis {
            downloadTilesFlow(vectorTileProvider.baseTileSource, bbox).collect(stats::add)
            downloadTilesFlow(vectorTileProvider.aerialLayerSource, bbox).collect(stats::add)
        } / 1000.0

        stats.run {
            val failureText = if (failureCount > 0) ". $failureCount tiles failed to download" else ""
            Log.i(TAG, "Downloaded $tileCount tiles (${downloadedSize / 1000}kB downloaded, ${cachedSize / 1000}kB already cached) in ${seconds.format(1)}s$failureText")
        }
    }

    private fun downloadTilesFlow(source: TileSource, bbox: BoundingBox): Flow<DownloadResult> = flow {
        /* tiles for the highest zoom (=likely current or near current zoom) first,
           because those are the tiles that are likely to be useful first */
        (source.maxZoom downTo 0).forEach { zoom ->
            bbox.enclosingTilesRect(zoom).asTilePosSequence().forEach { pos ->
                emit(downloadTile(source, zoom, pos.x, pos.y))
            }
        }
    }

    private suspend fun downloadTile(
        source: TileSource,
        zoom: Int,
        x: Int,
        y: Int
    ): DownloadResult = suspendCancellableCoroutine { cont ->
        /* adding trailing "&" because Tangram-ES also puts this at the end and the URL needs to be
           identical in order for the cache to work */
        val url = source.getTileUrl(zoom, x, y) + if (source.title == "JawgMaps") "&" else ""
        val httpUrl = HttpUrl.parse(url)
        require(httpUrl != null) { "Invalid URL: $url" }

        val builder = Request.Builder()
            .url(httpUrl)
            .cacheControl(cacheConfig.cacheControl)
        builder.header("User-Agent", ApplicationConstants.USER_AGENT + " / " + Version.userAgent())
        val call = okHttpClient.newCall(builder.build())

        /* since we use coroutines and this is in the background anyway, why not use call.execute()?
        *  Because we want to let the OkHttp dispatcher control how many HTTP requests are made in
        *  parallel */
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Error retrieving ${source.title} tile $zoom/$x/$y: ${e.message}")
                cont.resume(DownloadFailure)
            }

            override fun onResponse(call: Call, response: Response) {
                var size = 0
                response.body()?.use { body ->
                    // just get the bytes and let the cache magic do the rest...
                    size = body.bytes().size
                }
                val alreadyCached = response.cacheResponse() != null
                val logText = if (alreadyCached) "in cache" else "downloaded"
                Log.v(TAG, "${source.title} tile $zoom/$x/$y $logText")
                cont.resume(DownloadSuccess(alreadyCached, size))
            }
        }
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(callback)
    }

    companion object {
        private const val TAG = "MapTilesDownload"
    }
}

private sealed class DownloadResult
private data class DownloadSuccess(val alreadyCached: Boolean, val size: Int) : DownloadResult()
private object DownloadFailure : DownloadResult()
