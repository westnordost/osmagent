package de.westnordost.streetcomplete.map.tangram

import android.animation.TimeAnimator
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.mapzen.tangram.*
import com.mapzen.tangram.networking.HttpHandler
import com.mapzen.tangram.viewholder.GLSurfaceViewHolderFactory
import com.mapzen.tangram.viewholder.GLViewHolder
import com.mapzen.tangram.viewholder.GLViewHolderFactory
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.*
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Wrapper around the Tangram MapController. Features over the Tangram MapController (0.12.0):
 *  <br><br>
 *  <ul>
 *      <li>Markers survive a scene updates</li>
 *      <li>Simultaneous camera animations are possible with a short and easy interface</li>
 *      <li>A simpler interface to touchInput - easy defaulting to default touch gesture behavior</li>
 *      <li>Uses suspend functions instead of callbacks (Kotlin coroutines)</li>
 *      <li>Use LatLon instead of LngLat</li>
 *      <li>All calls are safe. I.e. if the map view happens to be destroyed already, the calls do
 *          nothing</li>
 *  </ul>
 *  */
class KtMapController(ctrl: MapController, contentResolver: ContentResolver):
    LifecycleObserver {

    private val weakCtrl = WeakReference(ctrl)
    private val c: MapController? get() = weakCtrl.get()

    private val cameraManager = CameraManager(ctrl, contentResolver)
    private val markerManager = MarkerManager(ctrl)
    private val gestureManager = TouchGestureManager(ctrl)

    private val defaultInterpolator = AccelerateDecelerateInterpolator()

    private val sceneUpdateContinuations = mutableMapOf<Int, Continuation<Int>>()
    private val pickLabelContinuations = ConcurrentLinkedQueue<Continuation<LabelPickResult?>>()
    private val featurePickContinuations = ConcurrentLinkedQueue<Continuation<FeaturePickResult?>>()

    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mapChangingListener: MapChangingListener? = null

    private val flingAnimator: TimeAnimator = TimeAnimator()

    init {
        ctrl.setSceneLoadListener { sceneId, sceneError ->
            val cont = sceneUpdateContinuations.remove(sceneId)
            if (sceneError != null) {
                cont?.resumeWithException(sceneError.toException())
            } else {
                markerManager.recreateMarkers()
                cont?.resume(sceneId)
            }
        }

        ctrl.setLabelPickListener { labelPickResult: LabelPickResult? ->
            pickLabelContinuations.poll()?.resume(labelPickResult)
        }

        ctrl.setFeaturePickListener { featurePickResult: FeaturePickResult? ->
            featurePickContinuations.poll()?.resume(featurePickResult)
        }

        flingAnimator.setTimeListener { _, _, _ ->
            mapChangingListener?.onMapIsChanging()
        }

        cameraManager.listener = object : CameraManager.AnimationsListener {
            override fun onAnimationsStarted() {
                mapChangingListener?.onMapWillChange()
            }

            override fun onAnimating() {
                mapChangingListener?.onMapIsChanging()
            }

            override fun onAnimationsEnded() {
                mapChangingListener?.onMapDidChange()
            }
        }

        ctrl.setMapChangeListener(object : MapChangeListener {
            private var calledOnMapIsChangingOnce = false

            override fun onViewComplete() { /* not interested*/ }

            override fun onRegionWillChange(animated: Boolean) {
                // could be called not on the ui thread, see https://github.com/tangrams/tangram-es/issues/2157
                lifecycleScope.launch {
                    calledOnMapIsChangingOnce = false
                    if (!cameraManager.isAnimating) {
                        mapChangingListener?.onMapWillChange()
                        if (animated) flingAnimator.start()
                    }
                }
            }

            override fun onRegionIsChanging() {
                lifecycleScope.launch {
                    if (!cameraManager.isAnimating) mapChangingListener?.onMapIsChanging()
                    calledOnMapIsChangingOnce = true
                }
            }

            override fun onRegionDidChange(animated: Boolean) {
                lifecycleScope.launch {
                    if (!cameraManager.isAnimating) {
                        if (!calledOnMapIsChangingOnce) mapChangingListener?.onMapIsChanging()
                        mapChangingListener?.onMapDidChange()
                        if (animated) flingAnimator.end()
                    }
                }
            }
        })
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY) fun onDestroy() {
        lifecycleScope.cancel()
        cameraManager.cancelAllCameraAnimations()
    }

    /* ----------------------------- Loading and Updating Scene --------------------------------- */

    suspend fun loadSceneFile(
        path: String,
        sceneUpdates: List<SceneUpdate>? = null
    ): Int? = c?.let { c ->
        suspendCancellableCoroutine { cont ->
            markerManager.invalidateMarkers()
            val sceneId = c.loadSceneFileAsync(path, sceneUpdates)
            sceneUpdateContinuations[sceneId] = cont
            cont.invokeOnCancellation { sceneUpdateContinuations.remove(sceneId) }
        }
    }

    suspend fun loadSceneYaml(
        yaml: String,
        resourceRoot: String,
        sceneUpdates: List<SceneUpdate>? = null
    ): Int? = c?.let { c ->
        suspendCancellableCoroutine { cont ->
            markerManager.invalidateMarkers()
            val sceneId = c.loadSceneYamlAsync(yaml, resourceRoot, sceneUpdates)
            sceneUpdateContinuations[sceneId] = cont
            cont.invokeOnCancellation { sceneUpdateContinuations.remove(sceneId) }
        }
    }

    /* ----------------------------------------- Camera ----------------------------------------- */

    val cameraPosition: CameraPosition get() = cameraManager.camera

    fun updateCameraPosition(duration: Long = 0, interpolator: Interpolator = defaultInterpolator, builder: CameraUpdate.() -> Unit) {
        updateCameraPosition(duration, interpolator, CameraUpdate().apply(builder))
    }

    fun updateCameraPosition(duration: Long = 0, interpolator: Interpolator = defaultInterpolator, update: CameraUpdate) {
        cameraManager.updateCamera(duration, interpolator, update)
    }

    fun setCameraPosition(camera: CameraPosition) {
        val update = CameraUpdate()
        update.position = camera.position
        update.rotation = camera.rotation
        update.tilt = camera.tilt
        update.zoom = camera.zoom
        updateCameraPosition(0L, defaultInterpolator, update)
    }

    var cameraType: MapController.CameraType
        set(value) { c?.cameraType = value }
        get() = c?.cameraType ?: MapController.CameraType.PERSPECTIVE

    var minimumZoomLevel: Float
        set(value) { c?.minimumZoomLevel = value }
        get() = c?.minimumZoomLevel ?: 0f

    var maximumZoomLevel: Float
        set(value) { c?.maximumZoomLevel = value }
        get() = c?.maximumZoomLevel ?: 20.5f

    fun screenPositionToLatLon(screenPosition: PointF): LatLon? = c?.screenPositionToLngLat(screenPosition)?.toLatLon()
    fun latLonToScreenPosition(latLon: LatLon): PointF? = c?.lngLatToScreenPosition(latLon.toLngLat())
    fun latLonToScreenPosition(latLon: LatLon, screenPositionOut: PointF, clipToViewport: Boolean) =
        c?.lngLatToScreenPosition(latLon.toLngLat(), screenPositionOut, clipToViewport)

    fun screenCenterToLatLon(padding: RectF): LatLon? {
        val view = glViewHolder?.view ?: return null
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return null

        return screenPositionToLatLon(PointF(
            padding.left + (w - padding.left - padding.right)/2f,
            padding.top + (h - padding.top - padding.bottom)/2f
        ))
    }

    fun screenAreaToBoundingBox(padding: RectF): BoundingBox? {
        val view = glViewHolder?.view ?: return null
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return null

        val size = PointF(w - padding.left - padding.right, h - padding.top - padding.bottom)

        // the special cases here are: map tilt and map rotation:
        // * map tilt makes the screen area -> world map area into a trapezoid
        // * map rotation makes the screen area -> world map area into a rotated rectangle
        // dealing with tilt: this method is just not defined if the tilt is above a certain limit
        if (cameraPosition.tilt > Math.PI / 4f) return null // 45°

        val positions = arrayOf(
            screenPositionToLatLon(PointF(padding.left, padding.top)),
            screenPositionToLatLon(PointF(padding.left + size.x, padding.top)),
            screenPositionToLatLon(PointF(padding.left, padding.top + size.y)),
            screenPositionToLatLon(PointF(padding.left + size.x, padding.top + size.y))
        ).filterNotNull()

        return positions.enclosingBoundingBox()
    }

    fun getEnclosingCameraPosition(bounds: BoundingBox, padding: RectF): CameraPosition? {
        val zoom = getMaxZoomThatContainsBounds(bounds, padding) ?: return null
        val boundsCenter = listOf(bounds.min, bounds.max).centerPointOfPolyline()
        val pos = getLatLonThatCentersLatLon(boundsCenter, padding, zoom) ?: return null
        val camera = cameraPosition
        return CameraPosition(pos, camera.rotation, camera.tilt, zoom)
    }

    private fun getMaxZoomThatContainsBounds(bounds: BoundingBox, padding: RectF): Float? {
        val c = c ?: return null
        val screenBounds: BoundingBox
        val currentZoom: Float
        synchronized(c) {
            screenBounds = screenAreaToBoundingBox(padding) ?: return null
            currentZoom = cameraPosition.zoom
        }
        val screenWidth = normalizeLongitude(screenBounds.max.longitude - screenBounds.min.longitude)
        val screenHeight = screenBounds.max.latitude - screenBounds.min.latitude
        val objectWidth = normalizeLongitude(bounds.max.longitude - bounds.min.longitude)
        val objectHeight = bounds.max.latitude - bounds.min.latitude

        val zoomDeltaX = log10(screenWidth / objectWidth) / log10(2.0)
        val zoomDeltaY = log10(screenHeight / objectHeight) / log10(2.0)
        val zoomDelta = min(zoomDeltaX, zoomDeltaY)
        return max( 1.0, min(currentZoom + zoomDelta, 21.0)).toFloat()
    }

    fun getLatLonThatCentersLatLon(position: LatLon, padding: RectF, zoom: Float = cameraPosition.zoom): LatLon? {
        val view = glViewHolder?.view ?: return null
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return null

        val screenCenter = screenPositionToLatLon(PointF(w / 2f, h / 2f)) ?: return null
        val offsetScreenCenter = screenPositionToLatLon(
            PointF(
                padding.left + (w - padding.left - padding.right) / 2,
                padding.top + (h - padding.top - padding.bottom) / 2
            )
        ) ?: return null

        val zoomDelta = zoom.toDouble() - cameraPosition.zoom
        val distance = offsetScreenCenter.distanceTo(screenCenter)
        val angle = offsetScreenCenter.initialBearingTo(screenCenter)
        val distanceAfterZoom = distance * (2.0).pow(-zoomDelta)
        return position.translate(distanceAfterZoom, angle)
    }

    /* -------------------------------------- Data Layers --------------------------------------- */

    fun addDataLayer(name: String, generateCentroid: Boolean = false): MapData? =
        c?.addDataLayer(name, generateCentroid)

    /* ---------------------------------------- Markers ----------------------------------------- */

    fun addMarker(): Marker? = markerManager.addMarker()
    fun removeMarker(marker: Marker): Boolean = removeMarker(marker.markerId)
    fun removeMarker(markerId: Long): Boolean = markerManager.removeMarker(markerId)
    fun removeAllMarkers() = markerManager.removeAllMarkers()

    /* ------------------------------------ Map interaction ------------------------------------- */

    fun setPickRadius(radius: Float) = c?.setPickRadius(radius)

    suspend fun pickLabel(posX: Float, posY: Float): LabelPickResult? = c?.let { c ->
        suspendCancellableCoroutine { cont ->
            pickLabelContinuations.offer(cont)
            cont.invokeOnCancellation { pickLabelContinuations.remove(cont) }
            c.pickLabel(posX, posY)
        }
    }

    suspend fun pickMarker(posX: Float, posY: Float): MarkerPickResult? =
        markerManager.pickMarker(posX, posY)

    suspend fun pickFeature(posX: Float, posY: Float): FeaturePickResult? = c?.let { c ->
        suspendCancellableCoroutine { cont ->
            featurePickContinuations.offer(cont)
            cont.invokeOnCancellation { featurePickContinuations.remove(cont) }
            c.pickFeature(posX, posY)
        }
    }

    fun setMapChangingListener(listener: MapChangingListener?) { mapChangingListener = listener }

    /* -------------------------------------- Touch input --------------------------------------- */

    fun setShoveResponder(responder: TouchInput.ShoveResponder?) { gestureManager.setShoveResponder(responder) }
    fun setScaleResponder(responder: TouchInput.ScaleResponder?) { gestureManager.setScaleResponder(responder) }
    fun setRotateResponder(responder: TouchInput.RotateResponder?) { gestureManager.setRotateResponder(responder) }
    fun setPanResponder(responder: TouchInput.PanResponder?) { gestureManager.setPanResponder(responder) }
    fun setTapResponder(responder: TouchInput.TapResponder?) { c?.touchInput?.setTapResponder(responder) }
    fun setDoubleTapResponder(responder: TouchInput.DoubleTapResponder?) { c?.touchInput?.setDoubleTapResponder(responder) }
    fun setLongPressResponder(responder: TouchInput.LongPressResponder?) { c?.touchInput?.setLongPressResponder(responder) }

    fun isGestureEnabled(g: TouchInput.Gestures): Boolean = c?.touchInput?.isGestureEnabled(g) == true
    fun setGestureEnabled(g: TouchInput.Gestures) { c?.touchInput?.setGestureEnabled(g) }
    fun setGestureDisabled(g: TouchInput.Gestures) { c?.touchInput?.setGestureDisabled(g) }
    fun setAllGesturesEnabled() { c?.touchInput?.setAllGesturesEnabled() }
    fun setAllGesturesDisabled() { c?.touchInput?.setAllGesturesDisabled() }

    fun setSimultaneousDetectionEnabled(first: TouchInput.Gestures, second: TouchInput.Gestures) {
        c?.touchInput?.setSimultaneousDetectionEnabled(first, second)
    }
    fun setSimultaneousDetectionDisabled(first: TouchInput.Gestures, second: TouchInput.Gestures) {
        c?.touchInput?.setSimultaneousDetectionDisabled(first, second)
    }
    fun isSimultaneousDetectionAllowed(first: TouchInput.Gestures, second: TouchInput.Gestures): Boolean =
        c?.touchInput?.isSimultaneousDetectionAllowed(first, second) == true

    /* ------------------------------------------ Misc ------------------------------------------ */

    suspend fun captureFrame(waitForCompleteView: Boolean): Bitmap = suspendCancellableCoroutine { cont ->
        c?.captureFrame({ bitmap -> cont.resume(bitmap) }, waitForCompleteView)
    }

    fun requestRender() = c?.requestRender()
    fun setRenderMode(renderMode: Int) = c?.setRenderMode(renderMode)

    fun queueEvent(block: () -> Unit) = c?.queueEvent(block)

    val glViewHolder: GLViewHolder? get() = c?.glViewHolder

    fun setDebugFlag(flag: MapController.DebugFlag, on: Boolean) = c?.setDebugFlag(flag, on)

    fun useCachedGlState(use: Boolean) = c?.useCachedGlState(use)

    fun setDefaultBackgroundColor(red: Float, green: Float, blue: Float) = c?.setDefaultBackgroundColor(red, green, blue)
}

class LoadSceneException(message: String, val sceneUpdate: SceneUpdate) : RuntimeException(message)

private fun SceneError.toException() =
    LoadSceneException(error.name.toLowerCase(Locale.US).replace("_", " "), sceneUpdate)


suspend fun MapView.initMap(
    httpHandler: HttpHandler? = null,
    glViewHolderFactory: GLViewHolderFactory = GLSurfaceViewHolderFactory()
) = suspendCancellableCoroutine<KtMapController?> { cont ->
    getMapAsync({ mapController ->
        cont.resume(mapController?.let {
            KtMapController(it, context.contentResolver)
        })
    }, glViewHolderFactory, httpHandler)
}

interface MapChangingListener {
    fun onMapWillChange()
    fun onMapIsChanging()
    fun onMapDidChange()
}

