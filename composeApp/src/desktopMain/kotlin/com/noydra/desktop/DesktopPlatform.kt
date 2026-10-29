package com.noydra.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.noydra.Platform
import com.noydra.data.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class Os { MAC, WINDOWS, LINUX }

val currentOs: Os = System.getProperty("os.name").lowercase().let {
    when {
        "mac" in it -> Os.MAC
        "win" in it -> Os.WINDOWS
        else -> Os.LINUX
    }
}

fun dataDir(): Path {
    val home = Paths.get(System.getProperty("user.home"))
    val base = when (currentOs) {
        Os.WINDOWS -> System.getenv("APPDATA")?.let { Paths.get(it) } ?: home.resolve("AppData/Roaming")
        Os.MAC -> home.resolve("Library/Application Support")
        Os.LINUX -> System.getenv("XDG_DATA_HOME")?.let { Paths.get(it) } ?: home.resolve(".local/share")
    }
    return base.resolve("Streakingo").also { Files.createDirectories(it) }
}

class DesktopPlatform : Platform {
    override val isMobile = false
    override val storage = Storage(dataDir().resolve("streakingo.db").toString())

    override fun openUrl(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else if (currentOs == Os.LINUX) {
            ProcessBuilder("xdg-open", url).start()
        }
    }

    override suspend fun applySchedule(times: List<String>) = withContext(Dispatchers.IO) {
        DesktopScheduler.apply(times)
    }

    override val browserTokenFetcher: suspend (onStatus: (String) -> Unit) -> String = { onStatus ->
        BrowserTokenFetcher.fetch(onStatus = onStatus)
    }

    override val qrCode: @Composable (data: String, modifier: Modifier) -> Unit = { data, modifier ->
        QrCode(data, modifier)
    }
}
