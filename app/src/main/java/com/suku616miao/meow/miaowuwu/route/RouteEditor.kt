package com.suku616miao.meow.miaowuwu.route

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.map.Dot
import com.baidu.mapapi.map.DotOptions
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.suku616miao.meow.miaowuwu.R
import com.suku616miao.meow.miaowuwu.service.ServiceGo
import com.suku616miao.meow.miaowuwu.utils.MapUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal enum class RouteSpeedSource { SPEED, PACE }

/** The last edited field owns playback speed; incomplete input cannot reuse an old value. */
internal class RouteSpeedInput(initialSpeed: Double = 1.2) {
    private var restoredSpeed: Double? = initialSpeed
    var source = RouteSpeedSource.SPEED
        private set
    var speedText = formatSpeed(initialSpeed)
        private set
    var paceText = RoutePace.paceFromSpeed(initialSpeed)
        private set
    val speedMetersPerSecond: Double?
        get() = when (source) {
            RouteSpeedSource.SPEED -> restoredSpeed ?: speedText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            RouteSpeedSource.PACE -> RoutePace.speedFromPace(paceText)
        }

    fun editSpeed(text: String) {
        restoredSpeed = null
        source = RouteSpeedSource.SPEED
        speedText = text
        speedMetersPerSecond?.let { paceText = RoutePace.paceFromSpeed(it) }
    }

    fun editPace(text: String) {
        restoredSpeed = null
        source = RouteSpeedSource.PACE
        paceText = text
        speedMetersPerSecond?.let { speedText = formatSpeed(it) }
    }

    fun restore(speed: Double) {
        require(speed.isFinite() && speed > 0)
        restoredSpeed = speed
        source = RouteSpeedSource.SPEED
        speedText = formatSpeed(speed)
        paceText = RoutePace.paceFromSpeed(speed)
    }

    private fun formatSpeed(speed: Double): String {
        val formatted = java.lang.String.format(java.util.Locale.ROOT, "%.4f", speed)
        // Very small valid speeds must not be presented as zero after rounding.
        return if (formatted.toDouble() == 0.0) speed.toString() else formatted
    }
}

/** Owns the draft and its polyline; camera callbacks never change confirmed vertices. */
class RouteEditor(
    private val activity: Activity,
    private val map: BaiduMap,
    private val panel: View,
    private val crosshair: View,
    private val markerIcon: BitmapDescriptor,
    private val listener: Listener
) {
    interface Listener {
        fun onEditorVisibilityChanged(visible: Boolean)
        fun onStartRoute(points: List<RoutePoint>, speedMetersPerSecond: Double, repeat: Boolean): Boolean
        fun onPauseRoute()
        fun onResumeRoute()
        fun onStopRoute()
        fun onReplaceRoute()
    }

    private val draft = RouteDraft()
    private val preferences = activity.getSharedPreferences("routes", Context.MODE_PRIVATE)
    private val status: TextView = panel.findViewById(R.id.route_status)
    private val hintLabel: TextView = panel.findViewById(R.id.route_hint)
    private val confirm: Button = panel.findViewById(R.id.route_confirm)
    private val undo: Button = panel.findViewById(R.id.route_undo)
    private val save: Button = panel.findViewById(R.id.route_save)
    private val select: Button = panel.findViewById(R.id.route_select)
    private val newRoute: Button = panel.findViewById(R.id.route_new)
    private val speed: EditText = panel.findViewById(R.id.route_speed_value)
    private val pace: EditText = panel.findViewById(R.id.route_pace_value)
    private val repeat: MaterialSwitch = panel.findViewById(R.id.route_repeat)
    private val estimate: TextView = panel.findViewById(R.id.route_estimate)
    private val allocation: Button = panel.findViewById(R.id.route_allocation)
    private var syncingSpeed = false
    private val speedInput = RouteSpeedInput()
    private val play: Button = panel.findViewById(R.id.route_play)
    private val stop: Button = panel.findViewById(R.id.route_stop)
    private var polyline: Polyline? = null
    private var firstMarker: Marker? = null
    private var lastMarker: Marker? = null
    private var playbackDot: Dot? = null
    private var playbackHalo: Dot? = null
    private var playbackPosition: LatLng? = null
    private var lastSnapshot: RoutePlaybackSnapshot? = null
    private var currentName: String? = null
    private var playbackState = ServiceGo.ROUTE_NONE
    private var starting = false
    var isOpen = false
        private set
    val isSelectingPoints: Boolean
        get() = isOpen && !isLocked
    private val isLocked: Boolean
        get() = starting || playbackState == ServiceGo.ROUTE_PLAYING || playbackState == ServiceGo.ROUTE_PAUSED

    init {
        val savedSpeed = preferences.getString("draft_speed", "1.2")!!.toDoubleOrNull()
        if (savedSpeed != null && savedSpeed.isFinite() && savedSpeed > 0) speedInput.restore(savedSpeed)
        else {
            preferences.edit().remove("draft_speed").apply()
            showMessage(R.string.route_saved_bad)
        }
        synchronizeFields()
        repeat.isChecked = preferences.getBoolean("draft_repeat", false)
        currentName = preferences.getString("draft_name", null)
        speed.doAfterTextChanged {
            if (!syncingSpeed) {
                speedInput.editSpeed(it.toString())
                synchronizeFields(updateSpeed = false)
                speed.error = null
                pace.error = null
                updateControls()
                persistOptions()
            }
        }
        pace.doAfterTextChanged {
            if (!syncingSpeed) {
                speedInput.editPace(it.toString())
                synchronizeFields(updatePace = false)
                pace.error = null
                updateControls()
                persistOptions()
            }
        }
        repeat.setOnCheckedChangeListener { _, _ ->
            updatePolyline()
            updateEstimate()
            persistOptions()
        }
        allocation.setOnClickListener { showAllocation() }
        confirm.setOnClickListener {
            draft.updateCandidate(map.mapStatus.target.toRoutePoint())
            if (!draft.confirmCandidate()) {
                showMessage(R.string.route_duplicate)
            } else {
                persistDraft()
                updatePolyline()
                updateControls()
            }
        }
        undo.setOnClickListener {
            draft.undo()
            persistDraft()
            updatePolyline()
            updateControls()
        }
        save.setOnClickListener { saveNamedRoute() }
        select.setOnClickListener { showSavedRoutes() }
        newRoute.setOnClickListener {
            if (isLocked) {
                MaterialAlertDialogBuilder(activity).setTitle(R.string.route_new)
                    .setMessage(R.string.route_replace_playing)
                    .setPositiveButton(android.R.string.ok) { _, _ -> replaceDraft(emptyList(), null, 1.2, false) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            } else {
                replaceDraft(emptyList(), null, 1.2, false)
            }
        }
        play.setOnClickListener {
            when (playbackState) {
                ServiceGo.ROUTE_PLAYING -> listener.onPauseRoute()
                ServiceGo.ROUTE_PAUSED -> listener.onResumeRoute()
                else -> startPlayback()
            }
        }
        stop.setOnClickListener { listener.onStopRoute() }
        val encodedDraft = preferences.getString("draft", null)
        if (encodedDraft != null) {
            try {
                draft.replace(decodePoints(encodedDraft, allowSinglePoint = true))
            } catch (error: JSONException) {
                preferences.edit().remove("draft").apply()
                showMessage(R.string.route_saved_bad)
            } catch (error: IllegalArgumentException) {
                preferences.edit().remove("draft").apply()
                showMessage(R.string.route_saved_bad)
            }
        }
    }

    fun toggle() {
        isOpen = !isOpen
        // Navigation owns the closing animation and hides the panel only after it slides away.
        if (isOpen) panel.visibility = View.VISIBLE
        crosshair.visibility = if (isOpen) View.VISIBLE else View.GONE
        updateRouteVisibility()
        updatePlaybackDot()
        listener.onEditorVisibilityChanged(isOpen)
        if (isOpen) {
            if (!draft.isMapMoving) draft.finishMapMove(map.mapStatus.target.toRoutePoint())
            updatePolyline()
            updateControls()
        }
    }

    fun onMapMoveStarted() {
        draft.beginMapMove()
        draft.updateCandidate(map.mapStatus.target.toRoutePoint())
    }

    fun onMapMoving(mapStatus: MapStatus) { draft.updateCandidate(mapStatus.target.toRoutePoint()) }

    fun onMapMoveFinished(mapStatus: MapStatus) {
        draft.finishMapMove(mapStatus.target.toRoutePoint())
        if (isOpen) updateControls()
    }

    fun selectCandidate(point: LatLng) {
        if (!isSelectingPoints) return
        onMapMoveStarted()
        map.animateMapStatus(MapStatusUpdateFactory.newLatLng(point))
    }

    fun setPlaybackState(state: Int) {
        playbackState = state
        starting = false
        if (state == ServiceGo.ROUTE_NONE) {
            playbackPosition = null
            lastSnapshot = null
        }
        updateRouteVisibility()
        updatePlaybackDot()
        if (isOpen) updateControls()
    }

    fun updatePlayback(snapshot: RoutePlaybackSnapshot) {
        val oldState = playbackState
        val wasStarting = starting
        playbackState = snapshot.state
        if (snapshot.state != ServiceGo.ROUTE_NONE) starting = false
        if (snapshot.state == ServiceGo.ROUTE_NONE) {
            playbackPosition = null
        } else if (playbackPosition == null || lastSnapshot == null || lastSnapshot?.latitude != snapshot.latitude ||
            lastSnapshot?.longitude != snapshot.longitude) {
            val converted = MapUtils.wgs2bd09(snapshot.longitude, snapshot.latitude)
            playbackPosition = LatLng(converted[1], converted[0])
        }
        lastSnapshot = snapshot
        if (oldState != playbackState) updateRouteVisibility()
        updatePlaybackDot()
        if (isOpen && (oldState != playbackState || wasStarting != starting)) updateControls()
    }

    fun setConnecting() {
        starting = true
        if (isOpen) updateControls()
    }

    private fun startPlayback() {
        if (draft.points.size < 2) {
            showMessage(R.string.route_need_points)
            return
        }
        val requestedSpeed = validatedSpeed() ?: return
        // Confirmed map vertices are BD-09; convert once for the service's WGS-84 route.
        val route = draft.points.map { point ->
            val converted = MapUtils.bd2wgs(point.longitude, point.latitude)
            RoutePoint(converted[1], converted[0])
        }
        if (listener.onStartRoute(route, requestedSpeed, repeat.isChecked)) {
            starting = true
            showRouteOnMap()
            updateControls()
        }
    }

    private fun showRouteOnMap() {
        val bounds = LatLngBounds.Builder()
        draft.points.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        val padding = (48 * activity.resources.displayMetrics.density).toInt()
        map.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds.build(), padding, padding, padding, padding))
    }

    private fun updatePolyline() {
        val confirmed = draft.points
        val displayed = if (repeat.isChecked && confirmed.size >= 2) RoutePath(confirmed).closed().points else confirmed
        val points = displayed.map { LatLng(it.latitude, it.longitude) }
        updateNodeMarkers(confirmed.map { LatLng(it.latitude, it.longitude) })
        if (points.size < 2) {
            // Keep the overlay for reuse when undo temporarily leaves one vertex.
            polyline?.isVisible = false
        } else {
            val currentLine = polyline
            if (currentLine == null) {
                polyline = map.addOverlay(
                    PolylineOptions().points(points).isGeodesic(true).color(Color.rgb(0, 121, 107)).width(8)
                ) as Polyline
            } else {
                currentLine.points = points
                currentLine.isVisible = isOpen || playbackState != ServiceGo.ROUTE_NONE
            }
        }
    }

    private fun updateNodeMarkers(points: List<LatLng>) {
        if (points.isEmpty()) {
            firstMarker?.isVisible = false
            lastMarker?.isVisible = false
            return
        }
        firstMarker = updateMarker(firstMarker, points.first())
        if (points.size >= 2) {
            lastMarker = updateMarker(lastMarker, points.last())
        } else {
            lastMarker?.isVisible = false
        }
    }

    private fun updateMarker(existing: Marker?, point: LatLng): Marker {
        val marker = existing ?: map.addOverlay(
            MarkerOptions().position(point).icon(markerIcon)
        ) as Marker
        marker.position = point
        marker.isVisible = isOpen || playbackState != ServiceGo.ROUTE_NONE
        return marker
    }

    private fun updatePlaybackDot() {
        val position = playbackPosition
        val visible = playbackState != ServiceGo.ROUTE_NONE && position != null
        if (!visible) {
            playbackDot?.isVisible = false
            playbackHalo?.isVisible = false
            return
        }
        val center = checkNotNull(position)
        val density = activity.resources.displayMetrics.density
        if (playbackDot == null) {
            playbackHalo = map.addOverlay(DotOptions().center(center).color(Color.WHITE)
                .radius((9 * density).toInt()).zIndex(10)) as Dot
            playbackDot = map.addOverlay(DotOptions().center(center).color(Color.rgb(36, 190, 89))
                .radius((6 * density).toInt()).zIndex(11)) as Dot
        } else {
            if (playbackDot?.center != center) {
                playbackHalo?.center = center
                playbackDot?.center = center
            }
            playbackHalo?.isVisible = true
            playbackDot?.isVisible = true
        }
    }

    private fun updateControls() {
        val points = draft.points
        val distance = if (points.size >= 2) RoutePath(points).totalDistanceMeters else 0.0
        val summary = activity.getString(R.string.route_summary, points.size, distance)
        val hint = when {
            starting || playbackState == ServiceGo.ROUTE_PLAYING -> R.string.route_running
            playbackState == ServiceGo.ROUTE_PAUSED -> R.string.route_paused
            playbackState == ServiceGo.ROUTE_FINISHED -> R.string.route_finished
            draft.isMapMoving -> R.string.route_wait_map
            else -> R.string.route_select_hint
        }
        status.text = summary
        hintLabel.setText(hint)
        confirm.isEnabled = !isLocked
        undo.isEnabled = !isLocked && points.isNotEmpty()
        save.isEnabled = points.size >= 2 && speedInput.speedMetersPerSecond != null
        select.isEnabled = !starting
        newRoute.isEnabled = !starting
        speed.isEnabled = !isLocked
        pace.isEnabled = !isLocked
        repeat.isEnabled = !isLocked
        allocation.isEnabled = points.size >= 2 && speedInput.speedMetersPerSecond != null
        play.isEnabled = !starting && (isLocked || (points.size >= 2 && speedInput.speedMetersPerSecond != null))
        play.setText(when (playbackState) {
            ServiceGo.ROUTE_PLAYING -> R.string.route_pause
            ServiceGo.ROUTE_PAUSED -> R.string.route_resume
            else -> R.string.route_play
        })
        stop.isEnabled = starting || playbackState != ServiceGo.ROUTE_NONE
        crosshair.visibility = if (isSelectingPoints) View.VISIBLE else View.GONE
        updateEstimate()
    }

    private fun synchronizeFields(updateSpeed: Boolean = true, updatePace: Boolean = true) {
        syncingSpeed = true
        if (updateSpeed && speed.text.toString() != speedInput.speedText) speed.setText(speedInput.speedText)
        if (updatePace && pace.text.toString() != speedInput.paceText) pace.setText(speedInput.paceText)
        syncingSpeed = false
    }

    private fun validatedSpeed(): Double? {
        val value = speedInput.speedMetersPerSecond
        if (value == null) {
            if (speedInput.source == RouteSpeedSource.PACE) pace.error = activity.getString(R.string.route_invalid_pace)
            else speed.error = activity.getString(R.string.route_invalid_speed)
        }
        return value
    }

    private fun updateRouteVisibility() {
        val visible = isOpen || playbackState != ServiceGo.ROUTE_NONE
        polyline?.isVisible = visible && draft.points.size >= 2
        firstMarker?.isVisible = visible && draft.points.isNotEmpty()
        lastMarker?.isVisible = visible && draft.points.size >= 2
    }

    private fun persistOptions() {
        val value = speedInput.speedMetersPerSecond
        if (value != null) {
            preferences.edit().putString("draft_speed", value.toString())
                .putBoolean("draft_repeat", repeat.isChecked).putString("draft_name", currentName).apply()
        }
    }

    private fun updateEstimate() {
        val points = draft.points
        val value = speedInput.speedMetersPerSecond
        if (points.size < 2 || value == null) {
            estimate.text = activity.getString(if (points.size < 2) R.string.route_need_points
                else if (speedInput.source == RouteSpeedSource.PACE) R.string.route_invalid_pace else R.string.route_invalid_speed)
            return
        }
        val path = if (repeat.isChecked) RoutePath(points).closed() else RoutePath(points)
        estimate.text = activity.getString(R.string.route_estimate, path.totalDistanceMeters / 1000,
            RoutePace.duration(path.totalDistanceMeters, value), RoutePace.paceFromSpeed(value))
    }

    private fun showAllocation() {
        val value = validatedSpeed() ?: return
        if (draft.points.size < 2) return
        val path = RoutePath(draft.points)
        val lines = path.segments.mapIndexed { index, segment ->
            activity.getString(R.string.route_leg, index + 1, segment.lengthMeters, RoutePace.duration(segment.lengthMeters, value))
        }.toMutableList()
        if (repeat.isChecked) {
            val closed = path.closed()
            val returnDistance = closed.totalDistanceMeters - path.totalDistanceMeters
            if (returnDistance > 0) lines.add(activity.getString(R.string.route_return_leg, returnDistance, RoutePace.duration(returnDistance, value)))
            lines.add(activity.getString(R.string.route_circle, RoutePace.duration(closed.totalDistanceMeters, value)))
        }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.route_allocation)
            .setMessage(lines.joinToString("\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun persistDraft() {
        preferences.edit().putString("draft", encodePoints(draft.points)).apply()
    }

    private fun showSavedRoutes() {
        val routeKeys = preferences.all.keys.filter { it.startsWith("route:") }.sorted()
        if (routeKeys.isEmpty()) {
            showMessage(R.string.route_no_saved_routes)
            return
        }
        val labels = routeKeys.map { it.removePrefix("route:") }
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.route_select_title, routeKeys.size))
            .setItems(labels.toTypedArray()) { _, selected ->
                loadNamedRoute(routeKeys[selected])
            }.show()
    }

    private fun replaceDraft(points: List<RoutePoint>, name: String?, speedValue: Double, repeatValue: Boolean) {
        if (isLocked || playbackState != ServiceGo.ROUTE_NONE) listener.onReplaceRoute()
        setPlaybackState(ServiceGo.ROUTE_NONE)
        draft.replace(points)
        currentName = name
        speedInput.restore(speedValue)
        synchronizeFields()
        repeat.isChecked = repeatValue
        persistDraft()
        persistOptions()
        updatePolyline()
        updateControls()
    }

    private fun saveNamedRoute() {
        if (draft.points.size < 2) {
            showMessage(R.string.route_need_points)
            return
        }
        if (validatedSpeed() == null) return
        val input = TextInputEditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setText(currentName)
            selectAll()
        }
        val inputLayout = TextInputLayout(activity).apply {
            hint = activity.getString(R.string.route_name)
            val inset = (24 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset / 2, inset, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.route_save).setView(inputLayout)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = activity.getString(R.string.route_empty_name)
                } else {
                    val points = draft.points
                    if (preferences.contains("route:$name")) {
                        MaterialAlertDialogBuilder(activity).setTitle(R.string.route_overwrite_title)
                            .setMessage(activity.getString(R.string.route_overwrite_message, name))
                            .setPositiveButton(R.string.route_overwrite) { _, _ ->
                                persistNamedRoute(name, points)
                                dialog.dismiss()
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    } else {
                        persistNamedRoute(name, points)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun persistNamedRoute(name: String, points: List<RoutePoint>) {
        val value = validatedSpeed() ?: return
        preferences.edit().putString("route:$name", encodePoints(points))
            .putString("route_options:$name", JSONObject().put("speed", value.toString()).put("repeat", repeat.isChecked).toString()).apply()
        currentName = name
        persistOptions()
        showMessage(R.string.route_saved_ok)
    }

    private fun loadNamedRoute(key: String) {
        val encoded = preferences.getString(key, null) ?: return
        val name = key.removePrefix("route:")
        var loadedSpeed = 1.2
        var loadedRepeat = false
        val points = try {
            val options = preferences.getString("route_options:$name", null)
            if (options != null) {
                val decodedOptions = JSONObject(options)
                loadedSpeed = decodedOptions.getString("speed").toDouble()
                require(loadedSpeed.isFinite() && loadedSpeed > 0) { "Invalid saved route speed" }
                loadedRepeat = decodedOptions.getBoolean("repeat")
            }
            decodePoints(encoded, allowSinglePoint = false)
        } catch (error: JSONException) {
            showMessage(R.string.route_saved_bad)
            return
        } catch (error: IllegalArgumentException) {
            showMessage(R.string.route_saved_bad)
            return
        }
        MaterialAlertDialogBuilder(activity).setTitle(name)
            .setMessage(if (isLocked) activity.getString(R.string.route_replace_playing)
                else activity.getString(R.string.route_summary, points.size, RoutePath(points).totalDistanceMeters))
            .setPositiveButton(R.string.route_load) { _, _ ->
                replaceDraft(points, name, loadedSpeed, loadedRepeat)
                showRouteOnMap()
            }
            .setNeutralButton(R.string.route_delete) { _, _ -> preferences.edit().remove(key)
                .remove("route_options:$name").apply() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun encodePoints(points: List<RoutePoint>): String {
        val array = JSONArray()
        for (point in points) {
            array.put(JSONObject().put("latitude", point.latitude).put("longitude", point.longitude))
        }
        return array.toString()
    }

    private fun decodePoints(encoded: String, allowSinglePoint: Boolean): List<RoutePoint> {
        val array = JSONArray(encoded)
        val points = ArrayList<RoutePoint>(array.length())
        for (index in 0 until array.length()) {
            val point = array.getJSONObject(index)
            points.add(RoutePoint(point.getDouble("latitude"), point.getDouble("longitude")))
        }
        if (!allowSinglePoint || points.size >= 2) RoutePath(points)
        return points
    }

    private fun LatLng.toRoutePoint() = RoutePoint(latitude, longitude)

    private fun showMessage(message: Int) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    fun destroy() {
        polyline?.remove()
        firstMarker?.remove()
        lastMarker?.remove()
        playbackHalo?.remove()
        playbackDot?.remove()
        polyline = null
        firstMarker = null
        lastMarker = null
        playbackHalo = null
        playbackDot = null
    }
}
