package com.noydra

import com.noydra.api.DuolingoClient
import com.noydra.api.DuolingoException
import com.noydra.data.Storage
import com.noydra.i18n.I18n

const val NOON = "12:00"

suspend fun runScheduledLesson(storage: Storage, envToken: String? = null): Boolean {
    I18n.changeLanguage(storage.get("language"))
    val token = envToken?.takeIf { it.isNotBlank() } ?: storage.get("token")
    if (token.isBlank()) {
        storage.addHistory("scheduled", false, "history.no_token")
        return false
    }
    return try {
        val (xp, user) = DuolingoClient(token).doLesson()
        storage.addHistory("scheduled", true, "history.lesson_done", "xp" to xp, "streak" to user.streak)
        true
    } catch (e: DuolingoException) {
        storage.addHistory("scheduled", false, "raw", "text" to e.message)
        false
    }
}

fun scheduledTimes(storage: Storage): List<String> = buildList {
    if (storage.getBool("noon_enabled")) add(NOON)
    if (storage.getBool("custom_enabled")) add(storage.get("custom_time"))
}.distinct().sorted()
