package de.westnordost.streetcomplete.map.tangram

import android.animation.*
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Property
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.core.animation.addListener
import com.mapzen.tangram.CameraUpdateFactory
import com.mapzen.tangram.MapController
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.ktx.runImmediate
import java.lang.ref.WeakReference
import kotlin.math.PI

/**
 *  Controls the camera of a Tangram MapController. Use in place of the
 *  MapController.updateCameraPosition methods to enable parallel animations with an easy API.
 *  Usage example:
 *
 *  // move to new position within 500ms and at the same time zoom to level 18.5 and tilt within 1000ms
 *  cameraManager.updateCamera(500) {
 *    position = LngLat(2.0, 3.0)
 *  }
 *  cameraManager.updateCamera(1000) {
 *    zoom = 18.5
 *    tilt = 0.4
 *  }
 *
 *  See https://github.com/tangrams/tangram-es/issues/1962
 *  */
class CameraManager(ctrl: MapController, private val contentResolver: ContentResolver) {
    private val weakC = WeakReference(ctrl)
    private val c get() = weakC.get()

    private val defaultInterpolator = AccelerateDecelerateInterpolator()
    private val doubleTypeEvaluator = DoubleTypeEvaluator()
    private val currentAnimations = mutableMapOf<String, Animator>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAnimator: ValueAnimator? = null
    set(value) {
        if (field == value) return
        mainHandler.post {
            if (field == null) {
                listener?.onAnimationsStarted()
            } else if(value == null) {
                listener?.onAnimationsEnded()
            }
        }
        field = value
    }
    private var lastAnimatorEndTime: Long = 0

    private val _tangramCamera = com.mapzen.tangram.CameraPosition()
    val camera: CameraPosition
        get() {
            synchronized(_tangramCamera) {
                pullCameraPositionFromController()
                return CameraPosition(_tangramCamera)
            }
        }

    val isAnimating: Boolean get() = lastAnimator != null

    interface AnimationsListener {
        @UiThread fun onAnimationsStarted()
        @UiThread fun onAnimating()
        @UiThread fun onAnimationsEnded()
    }
    var listener: AnimationsListener? = null

    @AnyThread fun updateCamera(duration: Long = 0, interpolator: Interpolator = defaultInterpolator, update: CameraUpdate) {
        synchronized(_tangramCamera) {
            pullCameraPositionFromController()
            update.resolveDeltas(_tangramCamera)
            cancelCameraAnimations(update)
            if (duration == 0L || isAnimationsOff) {
                applyCameraUpdate(update)
            } else {
                animateCameraUpdate(update, duration, interpolator)
            }
        }
    }

    @AnyThread fun cancelAllCameraAnimations() {
        mainHandler.runImmediate {
            synchronized(currentAnimations) {
                for (animator in currentAnimations.values.toSet()) {
                    animator.cancel()
                }
                currentAnimations.clear()
            }
        }
    }

    private val isAnimationsOff get() =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    @AnyThread private fun cancelCameraAnimations(update: CameraUpdate) {
        if(update.rotation != null) cancelAnimation("rotation")
        if(update.tilt != null) cancelAnimation("tilt")
        if(update.zoom != null) cancelAnimation("zoom")
        if(update.position != null) cancelAnimation("position")
    }

    private fun applyCameraUpdate(update: CameraUpdate) {
        update.position?.let {
            _tangramCamera.latitude = it.latitude
            _tangramCamera.longitude = it.longitude
        }
        update.rotation?.let { _tangramCamera.rotation = it }
        update.tilt?.let { _tangramCamera.tilt = it }
        update.zoom?.let { _tangramCamera.zoom = it }
        pushCameraPositionToController()
    }

    @AnyThread private fun animateCameraUpdate(update: CameraUpdate, duration: Long, interpolator: Interpolator) {
        val animator = ObjectAnimator()
        val propValues = mutableListOf<PropertyValuesHolder>()
        update.rotation?.let {
            val currentRotation = _tangramCamera.rotation
            var targetRotation = it
            while (targetRotation - PI > currentRotation) targetRotation -= 2*PI.toFloat()
            while (targetRotation + PI < currentRotation) targetRotation += 2*PI.toFloat()

            propValues.add(PropertyValuesHolder.ofFloat(TangramRotationProperty, targetRotation))
            assignAnimation("rotation", animator)
        }
        update.tilt?.let {
            propValues.add(PropertyValuesHolder.ofFloat(TangramTiltProperty, it))
            assignAnimation("tilt", animator)
        }
        update.zoom?.let {
            propValues.add(PropertyValuesHolder.ofFloat(TangramZoomProperty, it))
            assignAnimation("zoom", animator)
        }
        update.position?.let {
            propValues.add(PropertyValuesHolder.ofObject(TangramLatitudeProperty, doubleTypeEvaluator, it.latitude))
            propValues.add(PropertyValuesHolder.ofObject(TangramLongitudeProperty, doubleTypeEvaluator, it.longitude))
            assignAnimation("position", animator)
        }
        animator.target = _tangramCamera
        animator.setValues(*propValues.toTypedArray())
        animator.duration = duration
        animator.interpolator = interpolator
        animator.addListener(onEnd = {
            // for some reason, the ObjectAnimator does not properly animate to the end value, so
            // we need to set it to the end value on finish manually
            applyCameraUpdate(update)
            unassignAnimation(animator)
        })

        val endTime = System.currentTimeMillis() + duration
        if (lastAnimatorEndTime < endTime) {
            lastAnimator?.removeAllUpdateListeners()
            lastAnimator = animator
            animator.addUpdateListener(this::animate)
            lastAnimatorEndTime = endTime
        }
        mainHandler.runImmediate { animator.start() }
    }

    private fun pullCameraPositionFromController() {
        c?.getCameraPosition(_tangramCamera)
    }

    private fun pushCameraPositionToController() {
        LatLon.checkValidity(_tangramCamera.latitude, _tangramCamera.longitude)
        c?.updateCameraPosition(CameraUpdateFactory.newCameraPosition(_tangramCamera))
    }

    @UiThread private fun animate(animator: ValueAnimator) {
        synchronized(_tangramCamera) {
            pushCameraPositionToController()
        }
        listener?.onAnimating()
    }

    @AnyThread private fun cancelAnimation(key: String) {
        var animator: Animator?
        synchronized(currentAnimations) {
            animator = currentAnimations[key]
            animator?.let { unassignAnimation(it) }
        }
        mainHandler.runImmediate { animator?.cancel() }
    }

    @AnyThread private fun unassignAnimation(animator: Animator) {
        synchronized(currentAnimations) {
            currentAnimations.entries.removeAll { (_, anim) -> animator == anim }
        }
        if (animator == lastAnimator) {
            lastAnimator = null
        }
    }

    @AnyThread private fun assignAnimation(key: String, animator: ObjectAnimator) {
        synchronized(currentAnimations) {
            currentAnimations[key] = animator
        }
    }
}

class CameraUpdate {
    var position: LatLon? = null
    var rotation: Float? = null
    var tilt: Float? = null
    var zoom: Float? = null

    var zoomBy: Float? = null
    var tiltBy: Float? = null
    var rotationBy: Float? = null
}

typealias TangramCameraPosition = com.mapzen.tangram.CameraPosition

data class CameraPosition(
    val position: LatLon,
    val rotation: Float,
    val tilt: Float,
    val zoom: Float) {

    constructor(p: TangramCameraPosition) : this(LatLon(p.latitude, p.longitude), p.rotation, p.tilt, p.zoom)
}

private fun CameraUpdate.resolveDeltas(pos: TangramCameraPosition) {
    zoomBy?.let { zoom = pos.zoom + (zoom ?: 0f) + it }
    tiltBy?.let { tilt = pos.tilt + (tilt ?: 0f) + it }
    rotationBy?.let { rotation = pos.rotation + (rotation ?: 0f) + it }
}

class DoubleTypeEvaluator : TypeEvaluator<Double> {
    override fun evaluate(t: Float, x0: Double, x1: Double) = x0 + t * (x1 - x0)
}

object TangramRotationProperty : Property<TangramCameraPosition, Float>(Float::class.java, "rotation") {
    override fun get(obj: TangramCameraPosition) = obj.rotation
    override fun set(obj: TangramCameraPosition, value: Float) { obj.rotation = value }
}

object TangramTiltProperty : Property<TangramCameraPosition, Float>(Float::class.java, "tilt") {
    override fun get(obj: TangramCameraPosition) = obj.tilt
    override fun set(obj: TangramCameraPosition, value: Float) { obj.tilt = value }
}

object TangramZoomProperty : Property<TangramCameraPosition, Float>(Float::class.java, "zoom") {
    override fun get(obj: TangramCameraPosition) = obj.zoom
    override fun set(obj: TangramCameraPosition, value: Float) { obj.zoom = value }
}

object TangramLatitudeProperty : Property<TangramCameraPosition, Double>(Double::class.java, "latitude") {
    override fun get(obj: TangramCameraPosition) = obj.latitude
    override fun set(obj: TangramCameraPosition, value: Double) { obj.latitude = value }
}

object TangramLongitudeProperty : Property<TangramCameraPosition, Double>(Double::class.java, "longitude") {
    override fun get(obj: TangramCameraPosition) = obj.longitude
    override fun set(obj: TangramCameraPosition, value: Double) { obj.longitude = value }
}
