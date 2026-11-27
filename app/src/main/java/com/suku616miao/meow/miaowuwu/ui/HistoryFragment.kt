package com.suku616miao.meow.miaowuwu.ui

import android.content.ContentValues
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.suku616miao.meow.miaowuwu.MainActivity
import com.suku616miao.meow.miaowuwu.R
import com.suku616miao.meow.miaowuwu.database.DataBaseHistoryLocation
import com.suku616miao.meow.miaowuwu.utils.GoUtils

object HistoryColumns {
    const val KEY_ID = "KEY_ID"
    const val KEY_LOCATION = "KEY_LOCATION"
    const val KEY_TIME = "KEY_TIME"
    const val KEY_LNG_LAT_WGS = "KEY_LNG_LAT_WGS"
    const val KEY_LNG_LAT_CUSTOM = "KEY_LNG_LAT_CUSTOM"
}

class HistoryFragment : Fragment() {
    private data class Record(val id: String, val name: String, val time: String,
        val wgsLng: String, val wgsLat: String, val bdLng: String, val bdLat: String)
    private var database: DataBaseHistoryLocation? = null
    private var records = emptyList<Record>()
    private var visibleRecords = emptyList<Record>()
    private var recordList: ListView? = null
    private var emptyLabel: TextView? = null
    private var query = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dp, 12.dp, 16.dp, 0) }
        val searchBox = TextInputLayout(context).apply { hint = getString(R.string.history_search_hint) }
        val search = TextInputEditText(context).apply { isSingleLine = true }
        searchBox.addView(search)
        layout.addView(searchBox)
        val clear = MaterialButton(context).apply {
            setText(R.string.history_clear)
            setOnClickListener {
                MaterialAlertDialogBuilder(context).setTitle(R.string.history_clear).setMessage("确定删除全部位置历史？")
                    .setPositiveButton(android.R.string.ok) { _, _ -> database!!.writableDatabase.delete(DataBaseHistoryLocation.TABLE_NAME, null, null); refresh() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        layout.addView(clear)
        val empty = TextView(context).apply { setText(R.string.history_idle); gravity = android.view.Gravity.CENTER; setPadding(0, 32.dp, 0, 0) }
        emptyLabel = empty
        layout.addView(empty)
        val list = ListView(context).apply { divider = null; dividerHeight = 8.dp }
        recordList = list
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        database = DataBaseHistoryLocation(context)
        // The existing DB is retained; expiration uses the actual preference key.
        val days = PreferenceManager.getDefaultSharedPreferences(context).getString("setting_history_expiration", "7")!!.toDouble()
        database!!.writableDatabase.delete(DataBaseHistoryLocation.TABLE_NAME,
            DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " < ?", arrayOf((System.currentTimeMillis() / 1000 - (days * 86400).toLong()).toString()))
        search.doAfterTextChanged { query = it.toString(); showRecords() }
        list.setOnItemClickListener { _, _, position, _ ->
            val record = visibleRecords[position]
            (requireActivity() as MainActivity).selectHistoryLocation(record.name, record.bdLng, record.bdLat)
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val record = visibleRecords[position]
            MaterialAlertDialogBuilder(context).setTitle(record.name)
                .setItems(arrayOf(getString(R.string.history_edit), getString(R.string.history_remove))) { _, item ->
                    if (item == 0) rename(record) else MaterialAlertDialogBuilder(context).setTitle(R.string.history_remove)
                        .setPositiveButton(android.R.string.ok) { _, _ -> database!!.writableDatabase.delete(DataBaseHistoryLocation.TABLE_NAME,
                            DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", arrayOf(record.id)); refresh() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }.show()
            true
        }
        refresh()
        return layout
    }

    private fun refresh() {
        val loaded = ArrayList<Record>()
        database!!.readableDatabase.query(DataBaseHistoryLocation.TABLE_NAME, null, null, null, null, null,
            DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC").use { cursor ->
            while (cursor.moveToNext()) loaded.add(Record(cursor.getString(0), cursor.getString(1) ?: "位置",
                GoUtils.timeStamp2Date(cursor.getLong(4).toString()), cursor.getString(2), cursor.getString(3), cursor.getString(5), cursor.getString(6)))
        }
        records = loaded
        showRecords()
    }

    private fun showRecords() {
        visibleRecords = records.filter { query.isEmpty() || it.name.contains(query, true) || "${it.bdLng},${it.bdLat}".contains(query) }
        val data = visibleRecords.map { record -> mapOf("name" to record.name, "details" to "${record.time}\n${record.bdLng}, ${record.bdLat}") }
        checkNotNull(recordList).adapter = SimpleAdapter(requireContext(), data, android.R.layout.simple_list_item_2,
            arrayOf("name", "details"), intArrayOf(android.R.id.text1, android.R.id.text2))
        checkNotNull(emptyLabel).visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun rename(record: Record) {
        val input = TextInputEditText(requireContext()).apply { setText(record.name); isSingleLine = true }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.history_edit).setView(input)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) input.error = getString(R.string.route_empty_name)
                else {
                    database!!.writableDatabase.update(DataBaseHistoryLocation.TABLE_NAME, ContentValues().apply { put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, name) },
                        DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", arrayOf(record.id))
                    dialog.dismiss(); refresh()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        recordList?.setOnItemClickListener(null)
        recordList?.setOnItemLongClickListener(null)
        recordList?.adapter = null
        recordList = null
        emptyLabel = null
        database?.close(); database = null
        super.onDestroyView()
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
