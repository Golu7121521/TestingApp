package com.newtube.player

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Watch history aur subscriptions ko phone mein save karta hai (SharedPreferences + JSON).
 * App band karne ke baad bhi data rehta hai.
 */
object LocalStore {
    private const val PREFS = "newtube_store"
    private const val KEY_HISTORY = "history"
    private const val KEY_SUBS = "subscriptions"
    private const val MAX_HISTORY = 200

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ---------- Watch history ----------

    fun getHistory(): List<VideoItem> {
        val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        return (0 until arr.length()).map { videoFromJson(arr.getJSONObject(it)) }
    }

    fun addToHistory(video: VideoItem) {
        val list = getHistory().filter { it.videoUrl != video.videoUrl }.toMutableList()
        list.add(0, video)
        val arr = JSONArray()
        list.take(MAX_HISTORY).forEach { arr.put(videoToJson(it)) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // ---------- Subscriptions ----------

    fun getSubscriptions(): List<Subscription> {
        val arr = JSONArray(prefs.getString(KEY_SUBS, "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Subscription(o.getString("name"), o.optString("url", ""))
        }
    }

    fun isSubscribed(sub: Subscription): Boolean =
        getSubscriptions().any { it.key == sub.key }

    /** Returns true agar ab subscribed hai, false agar unsubscribe ho gaya */
    fun toggleSubscription(sub: Subscription): Boolean {
        return if (isSubscribed(sub)) {
            unsubscribe(sub)
            false
        } else {
            val list = getSubscriptions().toMutableList()
            list.add(0, sub)
            saveSubscriptions(list)
            true
        }
    }

    fun unsubscribe(sub: Subscription) {
        saveSubscriptions(getSubscriptions().filter { it.key != sub.key })
    }

    private fun saveSubscriptions(list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString(KEY_SUBS, arr.toString()).apply()
    }

    // ---------- JSON helpers ----------

    private fun videoToJson(v: VideoItem): JSONObject = JSONObject()
        .put("title", v.title)
        .put("uploaderName", v.uploaderName)
        .put("viewCountText", v.viewCountText)
        .put("durationText", v.durationText)
        .put("thumbnailUrl", v.thumbnailUrl)
        .put("videoUrl", v.videoUrl)
        .put("isShort", v.isShort)
        .put("uploaderUrl", v.uploaderUrl)

    private fun videoFromJson(o: JSONObject): VideoItem = VideoItem(
        title = o.optString("title"),
        uploaderName = o.optString("uploaderName"),
        viewCountText = o.optString("viewCountText"),
        durationText = o.optString("durationText"),
        thumbnailUrl = o.optString("thumbnailUrl"),
        videoUrl = o.getString("videoUrl"),
        isShort = o.optBoolean("isShort", false),
        uploaderUrl = o.optString("uploaderUrl", "")
    )
}
