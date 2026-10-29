package com.noydra

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.noydra.data.Storage

const val APP_VERSION = "1.0.0"
const val DEVELOPER = "noydra"
const val DEVELOPER_URL = "https://github.com/noydra"
const val REPO_URL = "https://github.com/noydra/Streakingo"

const val QR_PREFIX = "streakingo:jwt:"

interface Platform {
    val isMobile: Boolean
    val storage: Storage

    fun openUrl(url: String)

    suspend fun applySchedule(times: List<String>)

    fun scheduleNeedsPermission(): Boolean = false
    fun requestSchedulePermission() {}

    val browserTokenFetcher: (suspend (onStatus: (String) -> Unit) -> String)? get() = null

    val qrScanner: (suspend () -> String?)? get() = null

    val webLogin: (@Composable (onToken: (String) -> Unit, onClose: () -> Unit) -> Unit)? get() = null

    val qrCode: (@Composable (data: String, modifier: Modifier) -> Unit)? get() = null

    val backHandler: (@Composable (enabled: Boolean, onBack: () -> Unit) -> Unit)? get() = null
}
