package com.noydra.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.noydra.api.AuthException
import com.noydra.api.DuolingoClient
import com.noydra.api.DuolingoException
import com.noydra.i18n.t
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class TokenFetchException(message: String) : Exception(message)

object BrowserTokenFetcher {
    private const val LOGIN_URL = "https://www.duolingo.com/?isLoggingIn=true"

    private data class Browser(val name: String, val binary: String)

    private interface Session {
        val process: Process
        suspend fun jwtCookie(): String?
    }

    suspend fun fetch(timeoutMillis: Long = 600_000, onStatus: (String) -> Unit): String {
        val browser = candidates().firstOrNull() ?: throw TokenFetchException(t("fetch.no_browser"))
        onStatus(t("fetch.opening", "browser" to browser.name))
        val session = withContext(Dispatchers.IO) { start(browser) }
        onStatus(t("fetch.login", "browser" to browser.name))
        try {
            var checked: String? = null
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                val token = session.jwtCookie()
                if (token != null && token != checked) {
                    try {
                        if (!DuolingoClient(token).getUser().username.isNullOrEmpty()) return token
                        checked = token
                    } catch (e: AuthException) {
                        checked = token
                    } catch (e: DuolingoException) {
                        onStatus(t("fetch.verify_retry", "error" to e.message))
                        delay(3_000)
                    }
                }
                if (!session.process.isAlive) throw TokenFetchException(t("fetch.closed", "browser" to browser.name))
                delay(2_000)
            }
            throw TokenFetchException(t("fetch.timeout"))
        } finally {
            if (session.process.isAlive) session.process.destroy()
        }
    }

    private fun candidates(): List<Browser> {
        val home = Paths.get(System.getProperty("user.home"))
        val paths: Map<String, List<Path>> = when (currentOs) {
            Os.MAC -> {
                val apps = listOf(Paths.get("/Applications"), home.resolve("Applications"))
                mapOf(
                    "Chrome" to apps.map { it.resolve("Google Chrome.app/Contents/MacOS/Google Chrome") },
                    "Edge" to apps.map { it.resolve("Microsoft Edge.app/Contents/MacOS/Microsoft Edge") },
                    "Firefox" to apps.map { it.resolve("Firefox.app/Contents/MacOS/firefox") },
                )
            }
            Os.WINDOWS -> {
                val roots = listOf("PROGRAMFILES", "PROGRAMFILES(X86)", "LOCALAPPDATA").mapNotNull { System.getenv(it) }.map { Paths.get(it) }
                mapOf(
                    "Chrome" to roots.map { it.resolve("Google/Chrome/Application/chrome.exe") },
                    "Edge" to roots.map { it.resolve("Microsoft/Edge/Application/msedge.exe") },
                    "Firefox" to roots.map { it.resolve("Mozilla Firefox/firefox.exe") },
                )
            }
            Os.LINUX -> mapOf(
                "Chrome" to listOf("/usr/bin/google-chrome", "/usr/bin/chromium").map { Paths.get(it) },
                "Firefox" to listOf(Paths.get("/usr/bin/firefox")),
            )
        }
        return paths.mapNotNull { (name, options) -> options.firstOrNull(Files::exists)?.let { Browser(name, it.toString()) } }
    }

    private fun start(browser: Browser): Session {
        val profile = dataDir().resolve("browser-${browser.name.lowercase()}").also { Files.createDirectories(it) }
        return if (browser.name == "Firefox") FirefoxSession(browser.binary, profile) else ChromiumSession(browser.binary, profile)
    }

    private class FirefoxSession(binary: String, private val profile: Path) : Session {
        override val process: Process = ProcessBuilder(binary, "-profile", profile.toString(), "-no-remote", LOGIN_URL)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()

        override suspend fun jwtCookie(): String? = withContext(Dispatchers.IO) {
            val db = profile.resolve("cookies.sqlite")
            if (!Files.exists(db)) return@withContext null
            val tmp = Files.createTempDirectory("streakingo-cookies")
            try {
                for (suffix in listOf("", "-wal")) {
                    val src = profile.resolve("cookies.sqlite$suffix")
                    if (Files.exists(src)) Files.copy(src, tmp.resolve("cookies.sqlite$suffix"), StandardCopyOption.REPLACE_EXISTING)
                }
                BundledSQLiteDriver().open(tmp.resolve("cookies.sqlite").toString()).use { conn ->
                    conn.prepare(
                        "SELECT value FROM moz_cookies WHERE name = 'jwt_token' " +
                            "AND host LIKE '%duolingo.com' ORDER BY lastAccessed DESC LIMIT 1"
                    ).use { st -> if (st.step()) st.getText(0) else null }
                }
            } catch (e: Exception) {
                null
            } finally {
                tmp.toFile().deleteRecursively()
            }
        }
    }

    private class ChromiumSession(binary: String, profile: Path) : Session {
        private val port = ServerSocket(0).use { it.localPort }
        private val client = HttpClient(CIO) { install(WebSockets) }

        override val process: Process = ProcessBuilder(
            binary, "--user-data-dir=$profile", "--remote-debugging-port=$port",
            "--no-first-run", "--no-default-browser-check", LOGIN_URL,
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()

        override suspend fun jwtCookie(): String? = try {
            val version = Json.parseToJsonElement(client.get("http://127.0.0.1:$port/json/version").bodyAsText()).jsonObject
            val url = version["webSocketDebuggerUrl"]!!.jsonPrimitive.content
            var cookies: JsonArray? = null
            withTimeoutOrNull(5_000) {
                client.webSocket(url) {
                    send(Frame.Text("""{"id":1,"method":"Storage.getCookies"}"""))
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val msg = Json.parseToJsonElement(frame.readText()).jsonObject
                        if (msg["id"]?.jsonPrimitive?.content == "1") {
                            cookies = (msg["result"] as? JsonObject)?.get("cookies") as? JsonArray
                            break
                        }
                    }
                }
            }
            cookies?.map { it.jsonObject }?.firstOrNull {
                it["name"]?.jsonPrimitive?.content == "jwt_token" &&
                    it["domain"]?.jsonPrimitive?.content.orEmpty().endsWith("duolingo.com")
            }?.get("value")?.jsonPrimitive?.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
