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
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Polyline
import com.baidu.mapapi.map.PolylineOptions
import com.baidu.mapapi.model.LatLng
import com.suku616miao.meow.miaowuwu.R
import com.suku616miao.meow.miaowuwu.service.ServiceGo
import com.suku616miao.meow.miaowuwu.utils.MapUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Owns the draft and its polyline; camera callbacks never change confirmed vertices. */
class RouteEditor(
    private val activity: Activity,
    private val map: BaiduMap,
    private val panel: View,
    private val crosshair: View,
    private val listener: Listener
) {
    interface Listener {
        fun onEditorVisibilityChanged(visible: Boolean)
        fun onStartRoute(points: List<RoutePoint>, speedMetersPerSecond: Double): Boolean
        fun onPauseRoute()
        fun onResumeRoute()
        fun onStopRoute()
    }

    private val draft = RouteDraft()
    private val preferences = activity.getSharedPreferences("routes", Context.MODE_PRIVATE)
    private val status: TextView = panel.findViewById(R.id.route_status)
    private val confirm: Button = panel.findViewById(R.id.route_confirm)
    private val undo: Button = panel.findViewById(R.id.route_undo)
    private val saved: Button = panel.findViewById(R.id.route_saved)
    private val speed: EditText = panel.findViewById(R.id.route_speed_value)
    private val play: Button = panel.findViewById(R.id.route_play)
    private val stop: Button = panel.findViewById(R.id.route_stop)
    private var polyline: Polyline? = null
    private var playbackState = ServiceGo.ROUTE_NONE
    private var starting = false
    var isOpen = false
        private set
    val isSelectingPoints: Boolean
        get() = isOpen && !isLocked
    private val isLocked: Boolean
        get() = starting || playbackState == ServiceGo.ROUTE_PLAYING || playbackState == ServiceGo.ROUTE_PAUSED

    init {
        confirm.setOnClickListener {
            if (!draft.confirmCandidate()) {
                showMessage(if (draft.isMapMoving) R.string.route_wait_map else R.string.route_duplicate)
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
        saved.setOnClickListener { showSavedRoutes() }
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
        if (isLocked) {
            showMessage(R.string.route_stop_before_edit)
            return
        }
        isOpen = !isOpen
        panel.visibility = if (isOpen) View.VISIBLE else View.GONE
        crosshair.visibility = if (isOpen) View.VISIBLE else View.GONE
        polyline?.isVisible = isOpen
        listener.onEditorVisibilityChanged(isOpen)
        if (isOpen) {
            if (!draft.isMapMoving) draft.finishMapMove(map.mapStatus.target.toRoutePoint())
            updatePolyline()
            updateControls()
        }
    }

    fun onMapMoveStarted() {
        draft.beginMapMove()
        if (isOpen) confirm.isEnabled = false
    }

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
        if (isOpen) updateControls()
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
        val requestedSpeed = speed.text.toString().toDoubleOrNull()
        if (requestedSpeed == null || !requestedSpeed.isFinite() || requestedSpeed <= 0.0) {
            showMessage(R.string.route_invalid_speed)
            return
        }
        // Confirmed map vertices are BD-09; convert once for the service's WGS-84 route.
        val route = draft.points.map { point ->
            val converted = MapUtils.bd2wgs(point.longitude, point.latitude)
            RoutePoint(converted[1], converted[0])
        }
        if (listener.onStartRoute(route, requestedSpeed)) {
            starting = true
            updateControls()
        }
    }

    private fun updatePolyline() {
        val points = draft.points.map { LatLng(it.latitude, it.longitude) }
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
                currentLine.isVisible = isOpen
            }
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
        status.text = activity.getString(R.string.route_status_text, summary, activity.getString(hint))
        confirm.isEnabled = !isLocked && !draft.isMapMoving
        undo.isEnabled = !isLocked && points.isNotEmpty()
        saved.isEnabled = !isLocked
        speed.isEnabled = !isLocked
        play.isEnabled = !starting && (isLocked || points.size >= 2)
        play.setText(when (playbackState) {
            ServiceGo.ROUTE_PLAYING -> R.string.route_pause
            ServiceGo.ROUTE_PAUSED -> R.string.route_resume
            else -> R.string.route_play
        })
        stop.isEnabled = starting || playbackState != ServiceGo.ROUTE_NONE
        crosshair.visibility = if (isSelectingPoints) View.VISIBLE else View.GONE
    }

    private fun persistDraft() {
        preferences.edit().putString("draft", encodePoints(draft.points)).apply()
    }

    private fun showSavedRoutes() {
        val routeKeys = preferences.all.keys.filter { it.startsWith("route:") }.sorted()
        val labels = listOf(activity.getString(R.string.route_save), activity.getString(R.string.route_new)) +
            routeKeys.map { it.removePrefix("route:") }
        AlertDialog.Builder(activity).setTitle(R.string.route_saved)
            .setItems(labels.toTypedArray()) { _, selected ->
                when (selected) {
                    0 -> saveNamedRoute()
                    1 -> {
                        draft.replace(emptyList())
                        playbackState = ServiceGo.ROUTE_NONE
                        persistDraft()
                        updatePolyline()
                        updateControls()
                    }
                    else -> loadNamedRoute(routeKeys[selected - 2])
                }
            }.show()
    }

    private fun saveNamedRoute() {
        if (draft.points.size < 2) {
            showMessage(R.string.route_need_points)
            return
        }
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.route_name)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.route_save).setView(input)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = activity.getString(R.string.route_empty_name)
                } else {
                    preferences.edit().putString("route:$name", encodePoints(draft.points)).apply()
                    showMessage(R.string.route_saved_ok)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun loadNamedRoute(key: String) {
        val encoded = preferences.getString(key, null) ?: return
        val points = try {
            decodePoints(encoded, allowSinglePoint = false)
        } catch (error: JSONException) {
            showMessage(R.string.route_saved_bad)
            return
        } catch (error: IllegalArgumentException) {
            showMessage(R.string.route_saved_bad)
            return
        }
        AlertDialog.Builder(activity).setTitle(key.removePrefix("route:"))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                draft.replace(points)
                playbackState = ServiceGo.ROUTE_NONE
                persistDraft()
                updatePolyline()
                updateControls()
                selectCandidate(LatLng(points.first().latitude, points.first().longitude))
            }
            .setNeutralButton(R.string.route_delete) { _, _ -> preferences.edit().remove(key).apply() }
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
        polyline = null
    }
}
