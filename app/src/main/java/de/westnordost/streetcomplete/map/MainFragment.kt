package de.westnordost.streetcomplete.map

import android.annotation.SuppressLint
import android.content.*
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.getSystemService
import androidx.core.graphics.minus
import androidx.core.graphics.toPointF
import androidx.core.graphics.toRectF
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.commit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.LatLon
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.map.data.Way
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Injector
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.controls.MainMenuDialog
import de.westnordost.streetcomplete.data.download.QuestDownloadController
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.osmquest.OsmQuest
import de.westnordost.streetcomplete.data.osm.splitway.SplitPolylineAtPosition
import de.westnordost.streetcomplete.data.quest.*
import de.westnordost.streetcomplete.ktx.childFragmentManagerOrNull
import de.westnordost.streetcomplete.ktx.getLocationInWindow
import de.westnordost.streetcomplete.ktx.toPx
import de.westnordost.streetcomplete.ktx.toast
import de.westnordost.streetcomplete.location.FineLocationManager
import de.westnordost.streetcomplete.location.LocationRequestFragment
import de.westnordost.streetcomplete.location.LocationState
import de.westnordost.streetcomplete.location.LocationUtil
import de.westnordost.streetcomplete.map.tangram.CameraPosition
import de.westnordost.streetcomplete.quests.*
import de.westnordost.streetcomplete.util.*
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Contains the quests map and the controls for it. */
class MainFragment : Fragment(R.layout.fragment_main),
    MapFragment.Listener, LocationAwareMapFragment.Listener, QuestsMapFragment.Listener,
    AbstractQuestAnswerFragment.Listener,
    SplitWayFragment.Listener, LeaveNoteInsteadFragment.Listener, CreateNoteFragment.Listener,
    VisibleQuestListener,
    CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @Inject internal lateinit var questController: QuestController
    @Inject internal lateinit var questDownloadController: QuestDownloadController
    @Inject internal lateinit var isSurveyChecker: QuestSourceIsSurveyChecker
    @Inject internal lateinit var visibleQuestsSource: VisibleQuestsSource
    @Inject internal lateinit var soundFx: SoundFx
    @Inject internal lateinit var prefs: SharedPreferences

    private val random = Random()

    private lateinit var locationManager: FineLocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wasFollowingPosition = true
    private var wasCompassMode = false

    private var locationWhenOpenedQuest: Location? = null

    private var windowInsets: WindowInsets? = null

    private var mapFragment: QuestsMapFragment? = null
    private val bottomSheetFragment: Fragment? get() = childFragmentManagerOrNull?.findFragmentByTag(BOTTOM_SHEET)

    private var mapOffsetWithOpenBottomSheet: RectF = RectF(0f, 0f, 0f, 0f)

    interface Listener {
        fun onQuestSolved(quest: Quest?, source: String?)
        fun onCreatedNote(screenPosition: Point)
    }
    private val listener: Listener? get() = parentFragment as? Listener ?: activity as? Listener

    /* +++++++++++++++++++++++++++++++++++++++ CALLBACKS ++++++++++++++++++++++++++++++++++++++++ */

    //region Lifecycle - Android Lifecycle Callbacks

    private val locationAvailabilityReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateLocationAvailability()
        }
    }

    private val locationRequestFinishedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = LocationState.valueOf(intent.getStringExtra(LocationRequestFragment.STATE)!!)
            onLocationRequestFinished(state)
        }
    }

    init {
        Injector.applicationComponent.inject(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        locationManager = FineLocationManager(context.getSystemService<LocationManager>()!!, this::onLocationChanged)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFittingToSystemWindowInsets()

        locationPointerPin.setOnClickListener { onClickLocationPointer() }

        compassView.setOnClickListener { onClickCompassButton() }
        gpsTrackingButton.setOnClickListener { onClickTrackingButton() }
        zoomInButton.setOnClickListener { onClickZoomIn() }
        zoomOutButton.setOnClickListener { onClickZoomOut() }
        mainMenuButton.setOnClickListener { onClickMainMenu() }

        updateMapQuestOffsets()
    }

    private fun setupFittingToSystemWindowInsets() {
        view?.setOnApplyWindowInsetsListener { _, insets ->
            mapControls.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                setMargins(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            windowInsets = insets
            insets
        }
    }

    override fun onAttachFragment(childFragment: Fragment) {
        super.onAttachFragment(childFragment)
        if (childFragment is QuestsMapFragment) {
            mapFragment = childFragment
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val mapFragment = this.mapFragment ?: return
        /* when rotating the screen and the bottom sheet is open, the view
           should not rotate around its proper center but around the center
           of the part of the map that is not occluded by the bottom sheet */
        val previousOffset = mapOffsetWithOpenBottomSheet
        updateMapQuestOffsets()
        if (bottomSheetFragment != null) {
            mapFragment.adjustToOffsets(previousOffset, mapOffsetWithOpenBottomSheet)
        }
        updateLocationPointerPin()
    }

    override fun onStart() {
        super.onStart()
        visibleQuestsSource.addListener(this)
        requireContext().registerReceiver(
            locationAvailabilityReceiver,
            LocationUtil.createLocationAvailabilityIntentFilter()
        )
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            locationRequestFinishedReceiver,
            IntentFilter(LocationRequestFragment.ACTION_FINISHED)
        )
        updateLocationAvailability()
    }

    /** Called by the activity when the user presses the back button.
     *  Returns true if the event should be consumed. */
    fun onBackPressed(): Boolean {
        val f = bottomSheetFragment
        if (f !is IsCloseableBottomSheet) return false

        f.onClickClose { closeBottomSheet() }
        return true
    }

    override fun onStop() {
        super.onStop()
        wasFollowingPosition = mapFragment?.isFollowingPosition ?: true
        wasCompassMode = mapFragment?.isCompassMode ?: false
        visibleQuestsSource.removeListener(this)
        requireContext().unregisterReceiver(locationAvailabilityReceiver)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationRequestFinishedReceiver)
        locationManager.removeUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        coroutineContext.cancel()
    }

    private fun updateMapQuestOffsets() {
        mapOffsetWithOpenBottomSheet = Rect(
            resources.getDimensionPixelSize(R.dimen.quest_form_leftOffset),
            0,
            resources.getDimensionPixelSize(R.dimen.quest_form_rightOffset),
            resources.getDimensionPixelSize(R.dimen.quest_form_bottomOffset)
        ).toRectF()
    }

    //endregion

    //region QuestsMapFragment - Callbacks from the map with its quest pins

    /* ---------------------------------- MapFragment.Listener ---------------------------------- */

    override fun onMapInitialized() {
        val isFollowingPosition = mapFragment?.isFollowingPosition ?: false
        val isPositionKnown = mapFragment?.displayedLocation != null
        gpsTrackingButton.isActivated = isFollowingPosition
        gpsTrackingButton.visibility = if (isFollowingPosition && isPositionKnown) View.INVISIBLE else View.VISIBLE
        updateLocationPointerPin()
    }

    override fun onMapIsChanging(position: LatLon, rotation: Float, tilt: Float, zoom: Float) {
        compassNeedleView.rotation = (180 * rotation / Math.PI).toFloat()
        compassNeedleView.rotationX = (180 * tilt / Math.PI).toFloat()

        updateLocationPointerPin()

        val f = bottomSheetFragment
        if (f is AbstractQuestAnswerFragment<*>) f.onMapOrientation(rotation, tilt)
    }

    override fun onPanBegin() {
        setIsFollowingPosition(false)
    }

    override fun onMapDidChange(position: LatLon, rotation: Float, tilt: Float, zoom: Float) { }

    override fun onLongPress(x: Float, y: Float) {
        val point = PointF(x, y)
        val position = mapFragment?.getPositionAt(point) ?: return
        if (bottomSheetFragment != null) return

        contextMenuView.translationX = x
        contextMenuView.translationY = y

        showMapContextMenu(position)
    }

    /* ---------------------------- LocationAwareMapFragment.Listener --------------------------- */

    override fun onLocationDidChange() {
        updateLocationPointerPin()
    }

    /* ---------------------------- QuestsMapFragment.Listener --------------------------- */

    override fun onClickedQuest(questGroup: QuestGroup, questId: Long) {
        if (isQuestDetailsCurrentlyDisplayedFor(questId, questGroup)) return
        val f = bottomSheetFragment
        if (f is IsCloseableBottomSheet) f.onClickClose { showQuestDetails(questId, questGroup) }
        else showQuestDetails(questId, questGroup)
    }

    override fun onClickedMapAt(position: LatLon, clickAreaSizeInMeters: Double) {
        val f = bottomSheetFragment
        if (f is IsCloseableBottomSheet) {
            if (!f.onClickMapAt(position, clickAreaSizeInMeters))
                f.onClickClose { closeBottomSheet() }
        }
    }

    //endregion

    //region Bottom Sheet - Callbacks from the bottom sheet (quest forms, split way form, create note form, ...)

    /* -------------------------- AbstractQuestAnswerFragment.Listener -------------------------- */

    override fun onAnsweredQuest(questId: Long, group: QuestGroup, answer: Any) {
        val ctx = context ?: return

        val checkLocations = listOfNotNull(mapFragment?.displayedLocation, locationWhenOpenedQuest)

        isSurveyChecker.assureIsSurvey(ctx, questId, group, checkLocations) {
            val quest = questController.get(questId, group)
            closeQuestDetailsFor(questId, group)
            if (questController.solve(questId, group, answer, "survey")) {
                listener?.onQuestSolved(quest, "survey")
                quest?.let { showQuestSolvedAnimation(it) }
            }
        }
    }

    override fun onComposeNote(questId: Long, group: QuestGroup, questTitle: String) {
        showInBottomSheet(LeaveNoteInsteadFragment.create(questId, group, questTitle))
    }

    override fun onSplitWay(osmQuestId: Long) {
        val quest = questController.get(osmQuestId, QuestGroup.OSM)!!
        val element = questController.getOsmElement(quest as OsmQuest)
        if (element !is Way) return
        val geometry = quest.geometry
        if (geometry !is ElementPolylinesGeometry) return
        mapFragment?.isShowingQuestPins = false
        showInBottomSheet(SplitWayFragment.create(osmQuestId, element, geometry))
    }

    override fun onSkippedQuest(questId: Long, group: QuestGroup) {
        closeQuestDetailsFor(questId, group)
        questController.hide(questId, group)
    }

    /* ------------------------------- SplitWayFragment.Listener -------------------------------- */

    override fun onSplittedWay(osmQuestId: Long, splits: List<SplitPolylineAtPosition>) {
        val ctx = context ?: return

        val checkLocations = listOfNotNull(mapFragment?.displayedLocation, locationWhenOpenedQuest)
        isSurveyChecker.assureIsSurvey(ctx, osmQuestId, QuestGroup.OSM, checkLocations) {
            val quest = questController.get(osmQuestId, QuestGroup.OSM)
            closeQuestDetailsFor(osmQuestId, QuestGroup.OSM)
            if (questController.splitWay(osmQuestId, splits, "survey")) {
                listener?.onQuestSolved(quest, "survey")
                quest?.let { showQuestSolvedAnimation(it) }
            }
        }
    }

    override fun onAddSplit(point: LatLon) {
        mapFragment?.putMarkerForCurrentQuest(point)
    }

    override fun onRemoveSplit(point: LatLon) {
        mapFragment?.deleteMarkerForCurrentQuest(point)
    }

    /* --------------------------- LeaveNoteInsteadFragment.Listener ---------------------------- */

    override fun onCreatedNoteInstead(questId: Long, group: QuestGroup, questTitle: String, note: String, imagePaths: List<String>?) {
        closeQuestDetailsFor(questId, group)
        // the quest is deleted from DB on creating a note, so need to fetch quest before
        val quest = questController.get(questId, group)
        if (questController.createNote(questId, questTitle, note, imagePaths)) {
            listener?.onQuestSolved(quest, null)
            quest?.let { showQuestSolvedAnimation(it) }
        }
    }

    /* ------------------------------- CreateNoteFragment.Listener ------------------------------ */

    override fun onCreatedNote(note: String, imagePaths: List<String>?, screenPosition: Point) {
        closeBottomSheet()

        val mapFragment = mapFragment ?: return
        val mapView = mapFragment.view ?: return

        val mapPosition = mapView.getLocationInWindow().toPointF()
        val notePosition = PointF(screenPosition)
        notePosition.offset(-mapPosition.x, -mapPosition.y)
        val position = mapFragment.getPositionAt(notePosition) ?: throw NullPointerException()

        questController.createNote(note, imagePaths, position)

        listener?.onCreatedNote(screenPosition)
        showMarkerSolvedAnimation(R.drawable.ic_quest_create_note, PointF(screenPosition))
    }

    //endregion

    //region Data Updates - Callbacks for when data changed in the local database

    /* ---------------------------------- VisibleQuestListener ---------------------------------- */

    @AnyThread override fun onUpdatedVisibleQuests(
        added: Collection<Quest>,
        removed: Collection<Long>,
        group: QuestGroup
    ) {
        val f = bottomSheetFragment
        if (f !is IsShowingQuestDetails) return
        if (group != f.questGroup) return

        // open quest does not exist anymore!
        if (removed.contains(f.questId)) {
            mainHandler.post { closeBottomSheet() }
        }
    }

    //endregion

    /* ++++++++++++++++++++++++++++++++++++++ VIEW CONTROL ++++++++++++++++++++++++++++++++++++++ */

    //region Location - Request location and update location status

    private fun updateLocationAvailability() {
        if (LocationUtil.isLocationOn(activity)) {
            onLocationIsEnabled()
        } else {
            onLocationIsDisabled()
        }
    }

    @SuppressLint("MissingPermission")
    private fun onLocationIsEnabled() {
        gpsTrackingButton.visibility = View.VISIBLE
        gpsTrackingButton.state = LocationState.SEARCHING
        mapFragment!!.startPositionTracking()

        setIsFollowingPosition(wasFollowingPosition)
        locationManager.requestSingleUpdate()
    }

    private fun onLocationIsDisabled() {
        gpsTrackingButton.visibility = View.VISIBLE
        gpsTrackingButton.state = if (LocationUtil.hasLocationPermission(activity)) LocationState.ALLOWED else LocationState.DENIED
        locationPointerPin.visibility = View.GONE
        mapFragment!!.stopPositionTracking()
        locationManager.removeUpdates()
    }

    private fun onLocationRequestFinished(state: LocationState) {
        if (activity == null) return
        gpsTrackingButton.visibility = View.VISIBLE
        gpsTrackingButton.state = state
        if (state.isEnabled) {
            updateLocationAvailability()
        }
    }

    private fun onLocationChanged(location: Location) {
        val isFollowingPosition = mapFragment?.isFollowingPosition ?: false
        gpsTrackingButton?.visibility = if (isFollowingPosition) View.INVISIBLE else View.VISIBLE
        gpsTrackingButton?.state = LocationState.UPDATING
        updateLocationPointerPin()
    }

    //endregion

    //region Buttons - Functionality for the buttons in the main view

    fun onClickMainMenu() {
        context?.let { MainMenuDialog(it, this::onClickDownload).show() }
    }

    private fun onClickZoomOut() {
        mapFragment?.updateCameraPosition(300) { zoomBy = -1f }
    }

    private fun onClickZoomIn() {
        mapFragment?.updateCameraPosition(300) { zoomBy = +1f }
    }

    private fun onClickCompassButton() {
        val mapFragment = mapFragment ?: return
        // Allow a small margin of error around north/flat. This both matches
        // UX expectations ("it looks straight..") and works around a bug where
        // the rotation/tilt are not set to perfectly 0 during animation
        val margin = 0.025f // About 4%
        // 2PI radians = full circle of rotation = also north
        val isNorthUp = mapFragment.cameraPosition?.rotation?.let {
            it <= margin || 2f*PI.toFloat()-it <= margin
        } ?: false
        // Camera cannot rotate upside down => full circle check not needed
        val isFlat = mapFragment.cameraPosition?.tilt?.let { it <= margin } ?: false

        if (mapFragment.isFollowingPosition) {
            setIsCompassMode(!mapFragment.isCompassMode)
        } else {
            if (isNorthUp) {
                mapFragment.updateCameraPosition(300) {
                    tilt = if (isFlat) PI.toFloat() / 5f else 0f
                }
            } else {
                mapFragment.updateCameraPosition(300) {
                    rotation = 0f
                    tilt = 0f
                }
            }
        }
    }

    private fun setIsCompassMode(compassMode: Boolean) {
        val mapFragment = mapFragment ?: return
        mapFragment.isCompassMode = compassMode
    }

    private fun onClickTrackingButton() {
        val mapFragment = mapFragment ?: return
        if (gpsTrackingButton.state.isEnabled) {
            setIsFollowingPosition(!mapFragment.isFollowingPosition)
        } else {
            val tag = LocationRequestFragment::class.java.simpleName
            val locationRequestFragment = activity?.supportFragmentManager?.findFragmentByTag(tag) as LocationRequestFragment?
            locationRequestFragment?.startRequest()
        }
    }

    private fun setIsFollowingPosition(follow: Boolean) {
        val mapFragment = mapFragment ?: return
        mapFragment.isFollowingPosition = follow
        gpsTrackingButton.isActivated = follow
        val isPositionKnown = mapFragment.displayedLocation != null
        gpsTrackingButton?.visibility = if (isPositionKnown && follow) View.INVISIBLE else View.VISIBLE
        if (!follow) setIsCompassMode(false)
    }

    /* -------------------------------------- Context Menu -------------------------------------- */

    private fun showMapContextMenu(position: LatLon) {
        val popupMenu = PopupMenu(requireContext(), contextMenuView)
        popupMenu.inflate(R.menu.menu_map_context)
        popupMenu.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.action_create_note -> onClickCreateNote(position)
                R.id.action_open_location -> onClickOpenLocationInOtherApp(position)
            }
            true
        }
        popupMenu.show()
    }

    private fun onClickOpenLocationInOtherApp(pos: LatLon) {
        val ctx = context ?: return

        val zoom = mapFragment?.cameraPosition?.zoom
        val uri = buildGeoUri(pos.latitude, pos.longitude, zoom)

        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(ctx.packageManager) != null) {
            startActivity(intent)
        } else {
            ctx.toast(R.string.map_application_missing, Toast.LENGTH_LONG)
        }
    }

    private fun onClickCreateNote(pos: LatLon) {
        if (mapFragment?.cameraPosition?.zoom ?: 0f < ApplicationConstants.NOTE_MIN_ZOOM) {
            context?.toast(R.string.create_new_note_unprecise)
            return
        }

        val f = bottomSheetFragment
        if (f is IsCloseableBottomSheet) f.onClickClose { composeNote(pos) }
        else composeNote(pos)
    }

    private fun composeNote(pos: LatLon) {
        val mapFragment = mapFragment ?: return
        mapFragment.show3DBuildings = false
        val offsetPos = mapFragment.getPositionThatCentersPosition(pos, mapOffsetWithOpenBottomSheet)
        mapFragment.updateCameraPosition { position = offsetPos }

        freezeMap()
        showInBottomSheet(CreateNoteFragment())
    }

    /* ------------------------------------ Download Button  ------------------------------------ */

    private fun onClickDownload() {
        if (isConnected()) downloadDisplayedArea()
        else context?.toast(R.string.offline)
    }

    private fun isConnected(): Boolean {
        val connectivityManager = context?.getSystemService<ConnectivityManager>()
        val activeNetworkInfo = connectivityManager?.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    private fun downloadDisplayedArea() {
        val displayArea = mapFragment?.getDisplayedArea()
        if (displayArea == null) {
            context?.toast(R.string.cannot_find_bbox_or_reduce_tilt, Toast.LENGTH_LONG)
        } else {
            val enclosingBBox = displayArea.asBoundingBoxOfEnclosingTiles(ApplicationConstants.QUEST_TILE_ZOOM)
            val areaInSqKm = enclosingBBox.area() / 1000000
            if (areaInSqKm > ApplicationConstants.MAX_DOWNLOADABLE_AREA_IN_SQKM) {
                context?.toast(R.string.download_area_too_big, Toast.LENGTH_LONG)
            } else {
                if (questDownloadController.isPriorityDownloadInProgress) {
                    context?.let {
                        AlertDialog.Builder(it)
                            .setMessage(R.string.confirmation_cancel_prev_download_title)
                            .setPositiveButton(R.string.confirmation_cancel_prev_download_confirmed) { _, _ ->
                                downloadAreaConfirmed(enclosingBBox)
                            }
                            .setNegativeButton(R.string.confirmation_cancel_prev_download_cancel, null)
                            .show()
                    }
                } else {
                    downloadAreaConfirmed(enclosingBBox)
                }
            }
        }
    }

    private fun downloadAreaConfirmed(bbox: BoundingBox) {
        var bbox = bbox
        val areaInSqKm = bbox.area() / 1000000
        // below a certain threshold, it does not make sense to download, so let's enlarge it
        if (areaInSqKm < ApplicationConstants.MIN_DOWNLOADABLE_AREA_IN_SQKM) {
            val cameraPosition = mapFragment?.cameraPosition
            if (cameraPosition != null) {
                val radius = sqrt( 1000000 * ApplicationConstants.MIN_DOWNLOADABLE_AREA_IN_SQKM / PI)
                bbox = cameraPosition.position.enclosingBoundingBox(radius)
            }
        }
        questDownloadController.download(bbox, true)
    }

    // ---------------------------------- Location Pointer Pin  --------------------------------- */

    private fun updateLocationPointerPin() {
        val mapFragment = mapFragment ?: return
        val camera = mapFragment.cameraPosition ?: return
        val position = camera.position
        val rotation = camera.rotation

        val location = mapFragment.displayedLocation
        if (location == null) {
            locationPointerPin.visibility = View.GONE
            return
        }
        val displayedPosition = OsmLatLon(location.latitude, location.longitude)

        var target = mapFragment.getClippedPointOf(displayedPosition) ?: return
        windowInsets?.let {
            target -= PointF(it.systemWindowInsetLeft.toFloat(), it.systemWindowInsetTop.toFloat())
        }
        val intersection = findClosestIntersection(mapControls, target)
        if (intersection != null) {
            val intersectionPosition = mapFragment.getPositionAt(intersection)
            locationPointerPin.isGone = intersectionPosition == null
            if (intersectionPosition != null) {
                val angleAtIntersection = position.initialBearingTo(intersectionPosition)
                locationPointerPin.pinRotation = angleAtIntersection.toFloat() + (180 * rotation / PI).toFloat()

                val a = angleAtIntersection * PI / 180f + rotation
                val offsetX = (sin(a) / 2.0 + 0.5) * locationPointerPin.width
                val offsetY = (-cos(a) / 2.0 + 0.5) * locationPointerPin.height
                locationPointerPin.x = intersection.x - offsetX.toFloat()
                locationPointerPin.y = intersection.y - offsetY.toFloat()
            }
        } else {
            locationPointerPin.visibility = View.GONE
        }
    }

    private fun onClickLocationPointer() {
        mapFragment?.centerCurrentPosition()
    }

    //endregion

    //region Bottom Sheet - Controlling and managing the bottom sheet contents

    @UiThread private fun closeBottomSheet() {
        hideKeyboard()
        childFragmentManager.popBackStackImmediate(BOTTOM_SHEET, POP_BACK_STACK_INCLUSIVE)
        unfreezeMap()
    }

    private fun hideKeyboard() {
        val view: View? = activity?.currentFocus
        if (view != null) {
            val inputMethodManager = context?.getSystemService<InputMethodManager>()
            inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun showQuestDetails(questId: Long, group: QuestGroup) {
        val quest = questController.get(questId, group)
        if (quest != null) {
            showQuestDetails(quest, group)
        }
    }

    @UiThread private fun showQuestDetails(quest: Quest, group: QuestGroup) {
        val mapFragment = mapFragment ?: return
        if (isQuestDetailsCurrentlyDisplayedFor(quest.id!!, group)) return
        if (bottomSheetFragment != null) {
            hideKeyboard()
            childFragmentManager.popBackStackImmediate(BOTTOM_SHEET, POP_BACK_STACK_INCLUSIVE)
            resetFreezeMap()
            mapFragment.startFocusQuest(quest, mapOffsetWithOpenBottomSheet)
        } else {
            mapFragment.startFocusQuest(quest, mapOffsetWithOpenBottomSheet)
            freezeMap()
            locationWhenOpenedQuest = mapFragment.displayedLocation
        }

        val f = quest.type.createForm()
        val element = if (quest is OsmQuest) questController.getOsmElement(quest) else null
        val camera = mapFragment.cameraPosition
        val rotation = camera?.rotation ?: 0f
        val tilt = camera?.tilt ?: 0f
        f.arguments = AbstractQuestAnswerFragment.createArguments(quest, group, element, rotation, tilt)
        showInBottomSheet(f)
    }

    private fun showInBottomSheet(f: Fragment) {
        val appearAnim = if (bottomSheetFragment == null) R.animator.quest_answer_form_appear else 0
        val disappearAnim = R.animator.quest_answer_form_disappear
        childFragmentManager.commit {
            setCustomAnimations(appearAnim, disappearAnim, appearAnim, disappearAnim)
            replace(R.id.map_bottom_sheet_container, f, BOTTOM_SHEET)
            addToBackStack(BOTTOM_SHEET)
        }
    }

    private fun closeQuestDetailsFor(questId: Long, group: QuestGroup) {
        if (isQuestDetailsCurrentlyDisplayedFor(questId, group)) {
            closeBottomSheet()
        }
    }

    private fun isQuestDetailsCurrentlyDisplayedFor(questId: Long, group: QuestGroup): Boolean {
        val f = bottomSheetFragment
        return f is IsShowingQuestDetails && f.questId == questId && f.questGroup == group
    }

    private fun freezeMap() {
        val mapFragment = mapFragment ?: return

        wasFollowingPosition = mapFragment.isFollowingPosition
        wasCompassMode = mapFragment.isCompassMode
        mapFragment.isFollowingPosition = false
        mapFragment.isCompassMode = false
    }

    private fun resetFreezeMap() {
        val mapFragment = mapFragment ?: return

        mapFragment.clearFocusQuest()
        mapFragment.show3DBuildings = true
        mapFragment.isShowingQuestPins = true
    }

    private fun unfreezeMap() {
        val mapFragment = mapFragment ?: return

        mapFragment.isFollowingPosition = wasFollowingPosition
        mapFragment.isCompassMode = wasCompassMode
        mapFragment.endFocusQuest()
        mapFragment.show3DBuildings = true
        mapFragment.isShowingQuestPins = true
    }

    //endregion

    //region Animation - Animation(s) for when a quest is solved

    private fun showQuestSolvedAnimation(quest: Quest) {
        val ctx = context ?: return
        val offset = view?.getLocationInWindow() ?: return
        val startPos = mapFragment?.getPointOf(quest.center) ?: return

        val size = 42f.toPx(ctx).toInt()
        startPos.x += offset.x - size / 2f
        startPos.y += offset.y - size * 1.5f

        showMarkerSolvedAnimation(quest.type.icon, startPos)
    }

    private fun showMarkerSolvedAnimation(@DrawableRes iconResId: Int, startScreenPos: PointF) {
        val ctx = context ?: return
        val activity = activity ?: return
        val view = view ?: return

        launch {
            soundFx.play(resources.getIdentifier("plop" + random.nextInt(4), "raw", ctx.packageName))
        }

        val root = activity.window.decorView as ViewGroup
        val img = layoutInflater.inflate(R.layout.effect_quest_plop, root, false) as ImageView
        img.x = startScreenPos.x
        img.y = startScreenPos.y
        img.setImageResource(iconResId)
        root.addView(img)

        val answerTarget = view.findViewById<View>(
            if (isAutosync) R.id.answers_counter_fragment else R.id.upload_button_fragment)
        flingQuestMarkerTo(img, answerTarget) { root.removeView(img) }
    }

    private val isAutosync: Boolean get() =
        Prefs.Autosync.valueOf(prefs.getString(Prefs.AUTOSYNC, "ON")!!) == Prefs.Autosync.ON

    private fun flingQuestMarkerTo(quest: View, target: View, onFinished: () -> Unit) {
        val targetPos = target.getLocationInWindow().toPointF()
        quest.animate()
            .scaleX(1.6f).scaleY(1.6f)
            .setInterpolator(OvershootInterpolator(8f))
            .setDuration(250)
            .withEndAction {
                quest.animate()
                    .scaleX(0.2f).scaleY(0.2f)
                    .alpha(0.8f)
                    .x(targetPos.x).y(targetPos.y)
                    .setDuration(250)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction(onFinished)
            }
    }

    //endregion

    /* ++++++++++++++++++++++++++++++++++++++++ INTERFACE +++++++++++++++++++++++++++++++++++++++ */

    //region Interface - For the parent fragment / activity

    fun getCameraPosition(): CameraPosition? {
        return mapFragment?.cameraPosition
    }

    fun setCameraPosition(position: LatLon, zoom: Float) {
        mapFragment?.isFollowingPosition = false
        mapFragment?.isCompassMode = false
        mapFragment?.setInitialCameraPosition(CameraPosition(position, 0f, 0f, zoom))
        setIsFollowingPosition(false)
    }

    //endregion

    companion object {
        private const val BOTTOM_SHEET = "bottom_sheet"
    }
}
