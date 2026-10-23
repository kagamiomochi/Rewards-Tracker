package com.example.rewardstracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Tiny JSON-in-SharedPreferences store. A few hundred rows per year is far
// below the size where a real database would matter, and it avoids KSP/Room.
object PointStore {
    private const val PREFS = "rewards_points"
    private const val KEY = "entries"
    private const val KEY_STATUS = "status"
    private const val KEY_FETCH_OK = "fetch_ok"

    private val lock = Any()
    private val state = MutableStateFlow<List<PointEntry>>(emptyList())
    private val statusState = MutableStateFlow("")
    private val fetchOkState = MutableStateFlow(false)
    private var loaded = false

    fun entries(context: Context): StateFlow<List<PointEntry>> {
        ensureLoaded(context)
        return state
    }

    // Human-readable result of the last fetch, shown on the home screen for debugging.
    fun status(context: Context): StateFlow<String> {
        ensureLoaded(context)
        return statusState
    }

    // True while the most recent fetch succeeded. The login button is shown only when false.
    fun fetchOk(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return fetchOkState
    }

    fun setFetchOk(context: Context, ok: Boolean) {
        synchronized(lock) {
            ensureLoaded(context)
            prefs(context).edit().putBoolean(KEY_FETCH_OK, ok).apply()
            fetchOkState.value = ok
        }
    }

    fun setStatus(context: Context, text: String) {
        synchronized(lock) {
            ensureLoaded(context)
            prefs(context).edit().putString(KEY_STATUS, text).apply()
            statusState.value = text
        }
    }

    suspend fun upsert(context: Context, entry: PointEntry) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            ensureLoaded(context)
            val updated = (state.value.filter { it.fetchedAt != entry.fetchedAt } + entry)
                .sortedBy { it.fetchedAt }
            prefs(context).edit().putString(KEY, encode(updated)).commit()
            state.value = updated
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val p = prefs(context)
        val raw = p.getString(KEY, null)
        state.value = if (raw == null) emptyList() else decode(raw)
        statusState.value = p.getString(KEY_STATUS, "") ?: ""
        // Before the first result is recorded, treat existing data as a past success.
        fetchOkState.value = p.getBoolean(KEY_FETCH_OK, state.value.isNotEmpty())
        loaded = true
    }

    private fun encode(list: List<PointEntry>): String {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("d", it.epochDay)
                    .put("a", it.available)
                    .put("l", it.lifetime)
                    .put("t", it.fetchedAt)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<PointEntry> = try {
        val array = JSONArray(raw)
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            PointEntry(o.getLong("d"), o.getInt("a"), o.getInt("l"), o.getLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }
}
