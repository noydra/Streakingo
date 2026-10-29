package com.noydra.desktop

import com.noydra.i18n.t
import java.nio.file.Files
import java.nio.file.Paths

object DesktopScheduler {
    private const val LABEL = "com.streakingo.saver"
    private const val WIN_TASK_PREFIX = "Streakingo_"

    fun apply(times: List<String>) {
        val sorted = times.distinct().sorted()
        when (currentOs) {
            Os.MAC -> applyMac(sorted)
            Os.WINDOWS -> applyWindows(sorted)
            Os.LINUX -> throw UnsupportedOperationException(t("sched.unsupported"))
        }
    }

    private fun runCommand(): List<String> {
        System.getProperty("jpackage.app-path")?.let { return listOf(it, "--run") }

        val java = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        val main = "com.noydra.MainKt"
        return if (currentOs == Os.WINDOWS) {
            val javaw = Paths.get(java).resolveSibling("javaw.exe").takeIf { Files.exists(it) }?.toString() ?: java
            val script = dataDir().resolve("run-scheduled.cmd")
            Files.writeString(script, "@echo off\r\n\"$javaw\" -cp \"$classpath\" $main --run\r\n")
            listOf(script.toString())
        } else {
            val script = dataDir().resolve("run-scheduled.sh")
            Files.writeString(script, "#!/bin/sh\nexec '$java' -cp '$classpath' $main --run\n")
            script.toFile().setExecutable(true)
            listOf("/bin/sh", script.toString())
        }
    }

    private fun applyMac(times: List<String>) {
        val plist = Paths.get(System.getProperty("user.home"), "Library/LaunchAgents/$LABEL.plist")
        val uid = run("id", "-u").output.trim()
        val domain = "gui/$uid"
        run("launchctl", "bootout", "$domain/$LABEL")

        if (times.isEmpty()) {
            Files.deleteIfExists(plist)
            return
        }

        val log = dataDir().resolve("scheduled.log").toString()
        Files.createDirectories(plist.parent)
        Files.writeString(plist, launchdPlist(runCommand(), times, log))
        val result = run("launchctl", "bootstrap", domain, plist.toString())
        if (result.code != 0) throw RuntimeException(t("sched.launchd_failed", "error" to result.output.trim()))
    }

    private fun launchdPlist(command: List<String>, times: List<String>, log: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val args = command.joinToString("\n") { "        <string>${esc(it)}</string>" }
        val intervals = times.joinToString("\n") { at ->
            val (h, m) = at.split(":").map(String::toInt)
            "        <dict>\n            <key>Hour</key><integer>$h</integer>\n" +
                "            <key>Minute</key><integer>$m</integer>\n        </dict>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
$args
    </array>
    <key>StartCalendarInterval</key>
    <array>
$intervals
    </array>
    <key>StandardOutPath</key>
    <string>${esc(log)}</string>
    <key>StandardErrorPath</key>
    <string>${esc(log)}</string>
</dict>
</plist>
"""
    }

    private fun applyWindows(times: List<String>) {
        val existing = run("schtasks", "/Query", "/FO", "CSV", "/NH").output.lines()
            .map { it.split(",").firstOrNull().orEmpty().trim('"').trimStart('\\') }
            .filter { it.startsWith(WIN_TASK_PREFIX) }
            .toSet()
        for (name in existing) run("schtasks", "/Delete", "/F", "/TN", name)

        val command = runCommand().joinToString(" ") { if (it.startsWith("--")) it else "\"$it\"" }
        for (at in times) {
            val result = run(
                "schtasks", "/Create", "/F", "/SC", "DAILY", "/ST", at,
                "/TN", WIN_TASK_PREFIX + at.replace(":", ""), "/TR", command,
            )
            if (result.code != 0) throw RuntimeException(t("sched.windows_failed", "error" to result.output.trim()))
        }
    }

    private data class Result(val code: Int, val output: String)

    private fun run(vararg command: String): Result {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return Result(process.waitFor(), output)
    }
}
