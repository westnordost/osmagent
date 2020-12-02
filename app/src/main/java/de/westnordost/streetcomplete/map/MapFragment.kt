package de.westnordost.streetcomplete.map

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.mapzen.tangram.MapView
import com.mapzen.tangram.SceneUpdate
import com.mapzen.tangram.TouchInput.*
import com.mapzen.tangram.networking.DefaultHttpHandler
import com.mapzen.tangram.networking.HttpHandler
import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.LatLon
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.awaitLayout
import de.westnordost.streetcomplete.ktx.containsAll
import de.westnordost.streetcomplete.ktx.tryStartActivity
import de.westnordost.streetcomplete.map.tangram.*
import kotlinx.android.synthetic.main.fragment_map.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.internal.Version
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Manages a map that remembers its last location*/
open class MapFragment : Fragment(),
    CoroutineScope by CoroutineScope(Dispatchers.Main),
    TapResponder, DoubleTapResponder, LongPressResponder,
    PanResponder, ScaleResponder, ShoveResponder, RotateResponder {

    protected lateinit var mapView: MapView
    private set

    protected var controller: KtMapController? = null

    private val defaultCameraInterpolator = AccelerateDecelerateInterpolator()

    private var loadedSceneFilePath: String? = null

    private var isMapInitialized: Boolean = false

    @Inject internal lateinit var vectorTileProvider: VectorTileProvider

    interface Listener {
        /** Called when the map has been completely initialized */
        fun onMapInitialized()
        /** Called during camera animation and while the map is being controlled by a user */
        fun onMapIsChanging(position: LatLon, rotation: Float, tilt: Float, zoom: Float)
        /** Called after camera animation or after the map was controlled by a user */
        fun onMapDidChange(position: LatLon, rotation: Float, tilt: Float, zoom: Float)
        /** Called when the user begins to pan the map */
        fun onPanBegin()
        /** Called when the user long-presses the map */
        fun onLongPress(x: Float, y: Float)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    /* ------------------------------------ Lifecycle ------------------------------------------- */

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.map)
        mapView.onCreate(savedInstanceState)

        openstreetmapLink.setOnClickListener { showOpenUrlDialog("https://www.openstreetmap.org/copyright") }
        mapTileProviderLink.text = vectorTileProvider.copyrightText
        mapTileProviderLink.setOnClickListener { showOpenUrlDialog(vectorTileProvider.copyrightLink) }

        setupFittingToSystemWindowInsets()

        launch { initMap() }
    }

    private fun showOpenUrlDialog(url: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.open_url)
            .setMessage(url)
            .setPositiveButton(android.R.string.ok) { _,_ ->
                openUrl(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        return tryStartActivity(intent)
    }

    private fun setupFittingToSystemWindowInsets() {
        view?.setOnApplyWindowInsetsListener { _, insets ->
            attributionContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                setMargins(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        launch { reinitializeMapIfNecessary() }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        saveMapState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mapView.onDestroy()
        } catch (e : Exception) {
            // workaround for https://github.com/tangrams/tangram-es/issues/2136
            Log.e(TAG, "Error on disposing map", e)
        }
        controller = null
        coroutineContext.cancel()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    /* ------------------------------------------- Map  ----------------------------------------- */

    private suspend fun initMap() {
        val ctrl = mapView.initMap(createHttpHandler())
        controller = ctrl
        if (ctrl == null) return
        registerResponders()

        val sceneFilePath = getSceneFilePath()
        ctrl.loadSceneFile(sceneFilePath, getSceneUpdates())
        loadedSceneFilePath = sceneFilePath

        ctrl.glViewHolder!!.view.awaitLayout()

        onMapReady()
        listener?.onMapInitialized()
    }

    private suspend fun reinitializeMapIfNecessary() {
        if (loadedSceneFilePath != null) {
            val sceneFilePath = getSceneFilePath()
            if (sceneFilePath != loadedSceneFilePath) {
                controller?.loadSceneFile(sceneFilePath, getSceneUpdates())
                loadedSceneFilePath = sceneFilePath
            }
        }
    }

    private fun registerResponders() {
        controller?.setTapResponder(this)
        controller?.setDoubleTapResponder(this)
        controller?.setLongPressResponder(this)
        controller?.setRotateResponder(this)
        controller?.setPanResponder(this)
        controller?.setScaleResponder(this)
        controller?.setShoveResponder(this)


        controller?.setMapChangingListener(object : MapChangingListener {
            override fun onMapWillChange() {}
            override fun onMapIsChanging() {
                val camera = cameraPosition ?: return
                onMapIsChanging(camera.position, camera.rotation, camera.tilt, camera.zoom)
                listener?.onMapIsChanging(camera.position, camera.rotation, camera.tilt, camera.zoom)
            }
            override fun onMapDidChange() {
                val camera = cameraPosition ?: return
                onMapDidChange(camera.position, camera.rotation, camera.tilt, camera.zoom)
                listener?.onMapDidChange(camera.position, camera.rotation, camera.tilt, camera.zoom)
            }
        })
    }

    protected open suspend fun getSceneUpdates(): List<SceneUpdate> {
        val updates = mutableListOf(
            SceneUpdate("global.language", Locale.getDefault().language),
            SceneUpdate("global.text_size_scaling", "${resources.configuration.fontScale}"),
            SceneUpdate("global.api_key", vectorTileProvider.apiKey)
        )
        updates.add(SceneUpdate("global.language_script", Locale.getDefault().script))
        return updates
    }

    protected open fun getSceneFilePath(): String {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val scene = if (isNightMode) "scene-dark.yaml" else "scene-light.yaml"
        return "${vectorTileProvider.sceneFilePath}/$scene"
    }

    private fun createHttpHandler(): HttpHandler {
        val builder = DefaultHttpHandler.getClientBuilder()
        val cacheDir = requireContext().externalCacheDir
        val tileCacheDir: File?
        if (cacheDir != null) {
            tileCacheDir = File(cacheDir, "tile_cache")
            if (!tileCacheDir.exists()) tileCacheDir.mkdir()
        } else {
            tileCacheDir = null
        }
        if (tileCacheDir?.exists() == true) {
            val cacheSize = PreferenceManager.getDefaultSharedPreferences(context).getInt(Prefs.MAP_TILECACHE_IN_MB, 50)
            builder.cache(Cache(tileCacheDir, cacheSize * 1024L * 1024L))
        }

        return object : DefaultHttpHandler(builder) {
            val cacheControl = CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build()
            override fun configureRequest(url: HttpUrl, builder: Request.Builder) {
                builder
                    .cacheControl(cacheControl)
                    .header("User-Agent", ApplicationConstants.USER_AGENT + " / " + Version.userAgent())
            }
        }
    }

    /* ----------------------------- Overrideable map callbacks --------------------------------- */

    @CallSuper protected open fun onMapReady() {
        restoreMapState()
    }

    protected open fun onMapIsChanging(position: LatLon, rotation: Float, tilt: Float, zoom: Float) {}

    protected open fun onMapDidChange(position: LatLon, rotation: Float, tilt: Float, zoom: Float) {}

    /* ---------------------- Overrideable callbacks for map interaction ------------------------ */

    override fun onPanBegin(): Boolean {
        listener?.onPanBegin()
        return false
    }
    override fun onPan(startX: Float, startY: Float, endX: Float, endY: Float): Boolean { return false }
    override fun onPanEnd(): Boolean { return false }

    override fun onFling(posX: Float, posY: Float, velocityX: Float, velocityY: Float): Boolean { return false }
    override fun onCancelFling(): Boolean { return false }

    override fun onScaleBegin(): Boolean { return false }
    override fun onScale(x: Float, y: Float, scale: Float, velocity: Float): Boolean { return false }
    override fun onScaleEnd(): Boolean { return false }

    override fun onShoveBegin(): Boolean { return false }
    override fun onShove(distance: Float): Boolean { return false }
    override fun onShoveEnd(): Boolean { return false }

    override fun onRotateBegin(): Boolean { return false }
    override fun onRotate(x: Float, y: Float, rotation: Float): Boolean { return false }
    override fun onRotateEnd(): Boolean { return false }

    override fun onSingleTapUp(x: Float, y: Float): Boolean { return false }

    override fun onSingleTapConfirmed(x: Float, y: Float): Boolean { return false }

    override fun onDoubleTap(x: Float, y: Float): Boolean {
        val pos = controller?.screenPositionToLatLon(PointF(x, y))
        if (pos != null) {
            controller?.updateCameraPosition(300L) {
                zoomBy = 1f
                position = pos
            }
        }
        return true
    }

    override fun onLongPress(x: Float, y: Float) {
        listener?.onLongPress(x, y)
    }

    /* -------------------------------- Save and Restore State ---------------------------------- */

    private fun restoreMapState() {
        val camera = loadCameraPosition() ?: return
        controller?.setCameraPosition(camera)
    }

    private fun saveMapState() {
        val camera = controller?.cameraPosition ?: return
        saveCameraPosition(camera, false)
    }

    private fun loadCameraPosition(): CameraPosition? {
        val prefs = activity?.getPreferences(Activity.MODE_PRIVATE) ?: return null
        if (!prefs.containsAll(listOf(PREF_LAT, PREF_LON, PREF_ROTATION, PREF_TILT, PREF_ZOOM))) return null

        return CameraPosition(
            OsmLatLon(
                java.lang.Double.longBitsToDouble(prefs.getLong(PREF_LAT, 0)),
                java.lang.Double.longBitsToDouble(prefs.getLong(PREF_LON, 0))
            ),
            prefs.getFloat(PREF_ROTATION, 0f),
            prefs.getFloat(PREF_TILT, 0f),
            prefs.getFloat(PREF_ZOOM, 0f)
        )
    }

    private fun saveCameraPosition(camera: CameraPosition, saveNow: Boolean) {
        activity?.getPreferences(Activity.MODE_PRIVATE)?.edit(saveNow) {
            putFloat(PREF_ROTATION, camera.rotation)
            putFloat(PREF_TILT, camera.tilt)
            putFloat(PREF_ZOOM, camera.zoom)
            putLong(PREF_LAT, java.lang.Double.doubleToRawLongBits(camera.position.latitude))
            putLong(PREF_LON, java.lang.Double.doubleToRawLongBits(camera.position.longitude))
        }
    }

    /* ------------------------------- Controlling the map -------------------------------------- */

    public fun adjustToOffsets(oldOffset: RectF, newOffset: RectF) {
        controller?.screenCenterToLatLon(oldOffset)?.let { pos ->
            controller?.updateCameraPosition {
                position = controller?.getLatLonThatCentersLatLon(pos, newOffset)
            }
        }
    }

    fun getPositionAt(point: PointF): LatLon? = controller?.screenPositionToLatLon(point)

    fun getPointOf(pos: LatLon): PointF? = controller?.latLonToScreenPosition(pos)

    fun getClippedPointOf(pos: LatLon): PointF? {
        val screenPositionOut = PointF()
        controller?.latLonToScreenPosition(pos, screenPositionOut, true) ?: return null
        return screenPositionOut
    }

    val cameraPosition: CameraPosition?
        get() = controller?.cameraPosition

    fun updateCameraPosition(
        duration: Long = 0,
        interpolator: Interpolator = defaultCameraInterpolator,
        builder: CameraUpdate.() -> Unit) {

        controller?.updateCameraPosition(duration, interpolator, builder)
    }

    fun setInitialCameraPosition(camera: CameraPosition) {
        val controller = controller
        if (controller != null) {
            controller.setCameraPosition(camera)
        } else {
            saveCameraPosition(camera, true)
        }
    }

    fun getPositionThatCentersPosition(pos: LatLon, offset: RectF): LatLon? {
        return controller?.getLatLonThatCentersLatLon(pos, offset)
    }

    var show3DBuildings: Boolean = true
    set(value) {
        if (field == value) return
        field = value
        launch {
            val sceneFile = loadedSceneFilePath
            if (sceneFile != null) {
                val toggle = if (value) "true" else "false"
                controller?.loadSceneFile(
                    sceneFile, getSceneUpdates() + listOf(
                        SceneUpdate("layers.buildings.draw.buildings-style.extrude", toggle),
                        SceneUpdate("layers.buildings.draw.buildings-outline-style.extrude", toggle)
                    )
                )
            }
        }
    }

    fun getDisplayedArea(): BoundingBox? = controller?.screenAreaToBoundingBox(RectF())

    companion object {
        const val PREF_ROTATION = "map_rotation"
        const val PREF_TILT = "map_tilt"
        const val PREF_ZOOM = "map_zoom"
        const val PREF_LAT = "map_lat"
        const val PREF_LON = "map_lon"

        private const val TAG = "MapFragment"
    }
}
