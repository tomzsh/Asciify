package com.asciify.studio.data

import android.content.Context
import com.asciify.studio.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class EditorSession(
    val video: VideoInfo? = null, val ascii: AsciiSettings = AsciiSettings(),
    val export: ExportSettings = ExportSettings(), val startMs: Long = 0, val endMs: Long = 0,
)
data class ExportRequest(val video: VideoInfo, val ascii: AsciiSettings, val settings: ExportSettings, val startMs: Long, val endMs: Long) {
    fun json(): String = encodeSession(EditorSession(video, ascii, settings, startMs, endMs)).toString()
    companion object {
        fun parse(json: String): ExportRequest {
            val s = decodeSession(JSONObject(json))
            return ExportRequest(requireNotNull(s.video), s.ascii, s.export, s.startMs, s.endMs)
        }
    }
}
data class ExportRecord(
    val uri: String, val name: String, val style: String, val width: Int, val height: Int,
    val fps: Float, val bytes: Long, val created: Long, val processor: String,
)
data class ExportState(val running: Boolean = false, val progress: Int = 0,
    val stage: String = "", val error: String? = null, val completed: ExportRecord? = null)

class StudioStore(context: Context) {
    private val prefs = context.getSharedPreferences("asciify", Context.MODE_PRIVATE)
    fun loadSession() = runCatching { decodeSession(JSONObject(prefs.getString("session", "{}")!!)) }.getOrDefault(EditorSession())
    fun saveSession(session: EditorSession) { prefs.edit().putString("session", encodeSession(session).toString()).apply() }
    fun favorites(): Set<String> = prefs.getStringSet("favorites", setOf("classic", "matrix", "color"))!!.toSet()
    fun favorites(ids: Set<String>) { prefs.edit().putStringSet("favorites", ids).apply() }
    fun wasExporting() = prefs.getBoolean("exporting", false)
    fun exporting(value: Boolean) { prefs.edit().putBoolean("exporting", value).apply() }
    fun loadRecords(): List<ExportRecord> = runCatching {
        val array = JSONArray(prefs.getString("exports", "[]"))
        (0 until array.length()).map { index ->
            val j = array.getJSONObject(index)
            ExportRecord(j.getString("uri"), j.getString("name"), j.getString("style"), j.getInt("width"), j.getInt("height"),
                j.getDouble("fps").toFloat(), j.getLong("bytes"), j.getLong("created"), j.getString("processor"))
        }
    }.getOrDefault(emptyList())
    fun saveRecords(records: List<ExportRecord>) {
        val array = JSONArray()
        records.take(100).forEach { r -> array.put(JSONObject().apply {
            put("uri", r.uri); put("name", r.name); put("style", r.style); put("width", r.width); put("height", r.height)
            put("fps", r.fps.toDouble()); put("bytes", r.bytes); put("created", r.created); put("processor", r.processor)
        }) }
        prefs.edit().putString("exports", array.toString()).apply()
    }
}

class ExportRepository(private val store: StudioStore) {
    private val _state = MutableStateFlow(if (store.wasExporting()) ExportState(error = "The previous export was interrupted. Your video and settings are safe; start the export again.") else ExportState())
    val state = _state.asStateFlow()
    private val _records = MutableStateFlow(store.loadRecords())
    val records = _records.asStateFlow()
    init { store.exporting(false) }
    fun update(state: ExportState) { _state.value = state; store.exporting(state.running) }
    fun progress(value: Int, stage: String) { _state.value = ExportState(running = true, progress = value, stage = stage) }
    fun completed(record: ExportRecord) {
        _records.value = (listOf(record) + _records.value).take(100)
        store.saveRecords(_records.value)
        update(ExportState(progress = 100, completed = record))
    }
    fun forget(uri: String) { _records.value = _records.value.filterNot { it.uri == uri }; store.saveRecords(_records.value) }
    fun dismiss() { if (!_state.value.running) update(ExportState()) }
}

private fun encodeSession(s: EditorSession) = JSONObject().apply {
    s.video?.let { v -> put("video", JSONObject().apply {
        put("uri", v.uri); put("name", v.name); put("width", v.width); put("height", v.height)
        put("duration", v.durationMs); put("fps", v.frameRate.toDouble()); put("audio", v.hasAudio)
    }) }
    put("ascii", JSONObject().apply {
        put("style", s.ascii.styleId); put("columns", s.ascii.columns); put("contrast", s.ascii.contrast.toDouble())
        put("brightness", s.ascii.brightness.toDouble()); put("invert", s.ascii.invert); put("color", s.ascii.originalColor)
        put("foreground", s.ascii.foregroundColor ?: JSONObject.NULL)
        put("background", s.ascii.backgroundColor ?: JSONObject.NULL)
        put("gamma", s.ascii.gamma.toDouble()); put("ditherColumns", s.ascii.ditherColumns)
        put("ditherAmount", s.ascii.ditherAmount.toDouble()); put("ditherTones", s.ascii.ditherTones)
    })
    put("export", JSONObject().apply {
        put("height", s.export.height); put("fps", s.export.fps); put("bitrate", s.export.bitrateMbps)
        put("audio", s.export.keepAudio); put("mode", s.export.mode.name)
    })
    put("start", s.startMs); put("end", s.endMs)
}

private fun decodeSession(j: JSONObject): EditorSession {
    val v = j.optJSONObject("video")
    val a = j.optJSONObject("ascii") ?: JSONObject()
    val e = j.optJSONObject("export") ?: JSONObject()
    return EditorSession(
        v?.let { VideoInfo(it.getString("uri"), it.getString("name"), it.getInt("width"), it.getInt("height"), it.getLong("duration"), it.getDouble("fps").toFloat(), it.getBoolean("audio")) },
        AsciiSettings(a.optString("style", "classic"), a.optInt("columns", 100).coerceIn(24, 240),
            a.optDouble("contrast", 1.15).toFloat().coerceIn(0.5f, 2.5f), a.optDouble("brightness", 0.0).toFloat().coerceIn(-0.4f, 0.4f), a.optBoolean("invert"), a.optBoolean("color"),
            if (a.isNull("foreground")) null else a.getInt("foreground"),
            if (a.isNull("background")) null else a.getInt("background"),
            a.optDouble("gamma", 1.0).toFloat().coerceIn(0.5f, 2f), a.optInt("ditherColumns", 160).coerceIn(48, 480),
            a.optDouble("ditherAmount", 1.0).toFloat().coerceIn(0f, 1f),
            a.optInt("ditherTones", 0).let { if (it in listOf(0, 2, 4, 8)) it else 0 }),
        ExportSettings(e.optInt("height", 720), e.optInt("fps", 30), e.optInt("bitrate", 8), e.optBoolean("audio", true),
            runCatching { ProcessingMode.valueOf(e.optString("mode", "AUTO")) }.getOrDefault(ProcessingMode.AUTO)),
        j.optLong("start"), j.optLong("end"))
}
