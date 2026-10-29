package com.noydra.ui

import com.noydra.i18n.t
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object HistoryFormat {
    private val legacyLesson = Regex("""Ders tamamlandı \(\+(\d+) XP\)\. Streak: (\d+)""")

    fun format(message: String): String {
        val data = try {
            Json.parseToJsonElement(message).jsonObject
        } catch (e: Exception) {
            val legacy = legacyLesson.matchEntire(message) ?: return message
            return t("history.lesson_done", "xp" to legacy.groupValues[1], "streak" to legacy.groupValues[2])
        }
        val key = (data["key"] as? JsonPrimitive)?.content ?: return message
        val params = (data["params"] as? JsonObject).orEmpty()
            .map { (k, v) -> k to ((v as? JsonPrimitive)?.content ?: v.toString()) }
        if (key == "raw") return params.toMap()["text"].orEmpty()
        return t(key, *params.toTypedArray())
    }
}
