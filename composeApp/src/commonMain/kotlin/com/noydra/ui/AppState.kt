package com.noydra.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.noydra.NOON
import com.noydra.Platform
import com.noydra.QR_PREFIX
import com.noydra.api.AuthException
import com.noydra.api.DuolingoClient
import com.noydra.api.DuolingoException
import com.noydra.api.DuolingoUser
import com.noydra.data.HistoryRow
import com.noydra.i18n.I18n
import com.noydra.i18n.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Dialog { None, Settings, About, Connect, ManualToken, Disconnect, Transfer, WebLogin }

class AppState(val platform: Platform, private val scope: CoroutineScope) {
    private val db = platform.storage

    var theme by mutableStateOf(db.get("theme"))
        private set
    var token by mutableStateOf(db.get("token"))
        private set
    var user by mutableStateOf<DuolingoUser?>(null)
        private set
    var accountError by mutableStateOf<String?>(null)
        private set
    var history by mutableStateOf(db.recentHistory())
        private set
    var status by mutableStateOf("")
    var fetching by mutableStateOf(false)
        private set
    var lessonRunning by mutableStateOf(false)
        private set
    var dialog by mutableStateOf(Dialog.None)
    var errorMessage by mutableStateOf<String?>(null)

    var noonEnabled by mutableStateOf(db.getBool("noon_enabled"))
        private set
    var customEnabled by mutableStateOf(db.getBool("custom_enabled"))
        private set
    var customTime by mutableStateOf(db.get("custom_time"))
        private set
    var scheduleNeedsPermission by mutableStateOf(false)
        private set

    private var scheduleJob: Job? = null

    init {
        I18n.changeLanguage(db.get("language"))
        refreshSchedulePermission()
    }

    val connected get() = token.isNotBlank()

    fun refreshHistory() {
        history = db.recentHistory()
    }

    fun refreshAccount() {
        user = null
        accountError = null
        val current = token
        if (current.isBlank()) return
        scope.launch {
            try {
                val loaded = DuolingoClient(current).getUser()
                if (token == current) user = loaded
            } catch (e: DuolingoException) {
                if (token == current) accountError = e.message
            }
        }
    }

    fun saveToken(value: String): Boolean {
        val cleaned = value.trim().trim('"')
        return try {
            DuolingoClient(cleaned)
            db.set("token", cleaned)
            token = cleaned
            refreshAccount()
            true
        } catch (e: AuthException) {
            errorMessage = e.message
            false
        }
    }

    fun connect() {
        val fetcher = platform.browserTokenFetcher
        if (fetcher == null) {
            dialog = Dialog.Connect
            return
        }
        fetching = true
        scope.launch {
            try {
                val fetched = fetcher { status = it }
                if (saveToken(fetched)) status = t("status.connected")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = t("status.connect_failed")
                errorMessage = e.message ?: e.toString()
            } finally {
                fetching = false
            }
        }
    }

    fun onWebLoginToken(value: String) {
        dialog = Dialog.None
        if (saveToken(value)) status = t("status.connected")
    }

    fun scanQr() {
        val scanner = platform.qrScanner ?: return
        dialog = Dialog.None
        scope.launch {
            try {
                val raw = scanner() ?: return@launch
                if (!raw.startsWith(QR_PREFIX)) {
                    errorMessage = t("qr.invalid")
                    return@launch
                }
                if (saveToken(raw.removePrefix(QR_PREFIX))) status = t("status.connected")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = t("qr.scan_failed", "error" to (e.message ?: e.toString()))
            }
        }
    }

    fun disconnect() {
        dialog = Dialog.None
        db.set("token", "")
        token = ""
        refreshAccount()
        status = t("status.disconnected")
    }

    fun lessonNow() {
        val current = token
        lessonRunning = true
        scope.launch {
            try {
                val (xp, updated) = DuolingoClient(current).doLesson()
                user = updated
                db.addHistory("manual", true, "history.lesson_done", "xp" to xp, "streak" to updated.streak)
                status = t("status.lesson_done", "xp" to xp)
            } catch (e: DuolingoException) {
                db.addHistory("manual", false, "raw", "text" to e.message)
                status = t("status.lesson_failed")
            } finally {
                lessonRunning = false
                refreshHistory()
            }
        }
    }

    fun updateTheme(value: String) {
        db.set("theme", value)
        theme = value
    }

    fun updateLanguage(value: String) {
        db.set("language", value)
        I18n.changeLanguage(value)
        if (accountError != null) refreshAccount()
    }

    fun updateSchedule(noon: Boolean = noonEnabled, custom: Boolean = customEnabled, time: String = customTime) {
        noonEnabled = noon
        customEnabled = custom
        customTime = time
        scheduleJob?.cancel()
        scheduleJob = scope.launch {
            delay(600)
            val times = buildList {
                if (noon) add(NOON)
                if (custom) add(time)
            }.distinct().sorted()
            try {
                platform.applySchedule(times)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = t("dialog.schedule_failed", "error" to (e.message ?: e.toString()))
                noonEnabled = db.getBool("noon_enabled")
                customEnabled = db.getBool("custom_enabled")
                customTime = db.get("custom_time")
                return@launch
            }
            db.set("noon_enabled", noon)
            db.set("custom_enabled", custom)
            db.set("custom_time", time)
            refreshSchedulePermission()
            status = t("status.schedule", "times" to (times.joinToString(", ").ifEmpty { t("status.off") }))
        }
    }

    fun refreshSchedulePermission() {
        scheduleNeedsPermission = (noonEnabled || customEnabled) && platform.scheduleNeedsPermission()
    }
}

fun historyText(row: HistoryRow): String = HistoryFormat.format(row.message)

fun historySource(source: String): String =
    t("source." + (mapOf("manuel" to "manual", "zamanlayıcı" to "scheduled")[source] ?: source))
