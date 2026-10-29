package com.noydra

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.noydra.data.Storage
import com.noydra.desktop.DesktopPlatform
import com.noydra.desktop.dataDir
import com.noydra.ui.App
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if ("--run" in args) {
        System.setProperty("apple.awt.UIElement", "true")
        val ok = Storage(dataDir().resolve("streakingo.db").toString()).use { storage ->
            runBlocking { runScheduledLesson(storage, System.getenv("DUOLINGO_JWT")) }
        }
        exitProcess(if (ok) 0 else 1)
    }

    System.setProperty("apple.awt.application.appearance", "system")
    application {
        val platform = remember { DesktopPlatform() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Streakingo",
            state = rememberWindowState(size = DpSize(500.dp, 860.dp)),
        ) {
            window.minimumSize = Dimension(420, 640)
            App(platform, onDarkThemeChange = { dark ->
                window.rootPane.putClientProperty(
                    "apple.awt.windowAppearance",
                    if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
                )
            })
        }
    }
}
