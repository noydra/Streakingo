package com.noydra.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class HistoryRow(val ts: String, val source: String, val success: Boolean, val message: String)

class Storage(path: String) : AutoCloseable {
    private val conn: SQLiteConnection = BundledSQLiteDriver().open(path)

    init {
        conn.execSQL("PRAGMA busy_timeout = 5000")
        conn.execSQL(
            """CREATE TABLE IF NOT EXISTS settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )"""
        )
        conn.execSQL(
            """CREATE TABLE IF NOT EXISTS history (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                ts      TEXT NOT NULL,
                source  TEXT NOT NULL,
                success INTEGER NOT NULL,
                message TEXT NOT NULL
            )"""
        )
    }

    fun get(key: String): String =
        conn.prepare("SELECT value FROM settings WHERE key = ?").use { st ->
            st.bindText(1, key)
            if (st.step()) st.getText(0) else DEFAULTS[key].orEmpty()
        }

    fun getBool(key: String) = get(key) == "1"

    fun set(key: String, value: String) {
        conn.prepare(
            "INSERT INTO settings (key, value) VALUES (?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value"
        ).use { st ->
            st.bindText(1, key)
            st.bindText(2, value)
            st.step()
        }
    }

    fun set(key: String, value: Boolean) = set(key, if (value) "1" else "0")

    @OptIn(ExperimentalTime::class)
    fun addHistory(source: String, success: Boolean, key: String, vararg params: Pair<String, Any?>) {
        val message = buildJsonObject {
            put("key", JsonPrimitive(key))
            put("params", JsonObject(params.associate { (k, v) ->
                k to when (v) {
                    is Number -> JsonPrimitive(v)
                    null -> JsonPrimitive("")
                    else -> JsonPrimitive(v.toString())
                }
            }))
        }.toString()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun p(n: Int) = n.toString().padStart(2, '0')
        val ts = "${now.year}-${p(now.month.ordinal + 1)}-${p(now.day)} ${p(now.hour)}:${p(now.minute)}:${p(now.second)}"
        conn.prepare("INSERT INTO history (ts, source, success, message) VALUES (?, ?, ?, ?)").use { st ->
            st.bindText(1, ts)
            st.bindText(2, source)
            st.bindLong(3, if (success) 1 else 0)
            st.bindText(4, message)
            st.step()
        }
    }

    fun recentHistory(limit: Int = 50): List<HistoryRow> =
        conn.prepare("SELECT ts, source, success, message FROM history ORDER BY id DESC LIMIT ?").use { st ->
            st.bindLong(1, limit.toLong())
            buildList {
                while (st.step()) add(HistoryRow(st.getText(0), st.getText(1), st.getLong(2) != 0L, st.getText(3)))
            }
        }

    override fun close() = conn.close()

    companion object {
        val DEFAULTS = mapOf(
            "token" to "",
            "noon_enabled" to "0",
            "custom_enabled" to "0",
            "custom_time" to "20:00",
            "theme" to "system",
            "language" to "en",
        )
    }
}
