package com.noydra.api

import com.noydra.i18n.t
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

open class DuolingoException(message: String) : Exception(message)

class AuthException(message: String) : DuolingoException(message)

data class DuolingoUser(
    val username: String?,
    val streak: Int?,
    val fromLanguage: String?,
    val learningLanguage: String?,
    val lastExtendedDate: String?,
)

private const val BASE_URL = "https://www.duolingo.com/2017-06-30"
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private val CHALLENGE_TYPES = listOf(
    "assist", "characterIntro", "characterMatch", "characterPuzzle",
    "characterSelect", "characterTrace", "characterWrite",
    "completeReverseTranslation", "definition", "dialogue", "extendedMatch",
    "extendedListenMatch", "form", "freeResponse", "gapFill", "judge",
    "listen", "listenComplete", "listenMatch", "match", "name",
    "listenComprehension", "listenIsolation", "listenSpeak", "listenTap",
    "orderTapComplete", "partialListen", "partialReverseTranslate",
    "patternTapComplete", "radioBinary", "radioImageSelect",
    "radioListenMatch", "radioListenRecognize", "radioSelect",
    "readComprehension", "reverseAssist", "sameDifferent", "select",
    "selectPronunciation", "selectTranscription", "svgPuzzle", "syllableTap",
    "syllableListenTap", "speak", "tapCloze", "tapClozeTable", "tapComplete",
    "tapCompleteTable", "tapDescribe", "translate", "transliterate",
    "transliterationAssist", "typeCloze", "typeClozeTable", "typeComplete",
    "typeCompleteTable", "writeComprehension",
)

private val http by lazy {
    HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        expectSuccess = false
    }
}

fun userIdFromJwt(token: String): String {
    try {
        val payload = token.split(".")[1]
        val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
        return Json.parseToJsonElement(decoded.decodeToString()).jsonObject["sub"]!!.jsonPrimitive.content
    } catch (e: Exception) {
        throw AuthException(t("err.invalid_token"))
    }
}

class DuolingoClient(token: String) {
    val token = token.trim()
    val userId = userIdFromJwt(this.token)

    private suspend fun request(method: HttpMethod, path: String, body: JsonObject? = null): JsonObject {
        val response = try {
            http.request(BASE_URL + path) {
                this.method = method
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
                if (body != null) setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DuolingoException(t("err.connection", "reason" to (e.message ?: e::class.simpleName)))
        }
        val text = response.bodyAsText()
        val code = response.status.value
        if (code == 401 || code == 403) throw AuthException(t("err.unauthorized", "code" to code))
        if (!response.status.isSuccess()) throw DuolingoException("$method $path -> HTTP $code: ${text.take(200)}")
        return if (text.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(text).jsonObject
    }

    suspend fun getUser(): DuolingoUser {
        val json = request(
            HttpMethod.Get,
            "/users/$userId?fields=username,streak,streakData,fromLanguage,learningLanguage",
        )
        val current = (json["streakData"] as? JsonObject)?.get("currentStreak") as? JsonObject
        return DuolingoUser(
            username = json.string("username"),
            streak = (json["streak"] as? JsonPrimitive)?.intOrNull,
            fromLanguage = json.string("fromLanguage"),
            learningLanguage = json.string("learningLanguage"),
            lastExtendedDate = current?.string("lastExtendedDate"),
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun completeLesson(fromLang: String, learningLang: String): Int {
        val session = request(HttpMethod.Post, "/sessions", buildJsonObject {
            put("challengeTypes", JsonArray(CHALLENGE_TYPES.map(::JsonPrimitive)))
            put("fromLanguage", JsonPrimitive(fromLang))
            put("learningLanguage", JsonPrimitive(learningLang))
            put("isFinalLevel", JsonPrimitive(false))
            put("isV2", JsonPrimitive(true))
            put("juicy", JsonPrimitive(true))
            put("smartTipsVersion", JsonPrimitive(2))
            put("type", JsonPrimitive("GLOBAL_PRACTICE"))
        })
        val now = Clock.System.now().toEpochMilliseconds() / 1000.0
        val finished = JsonObject(session + mapOf(
            "heartsLeft" to JsonPrimitive(0),
            "startTime" to JsonPrimitive(now - 60),
            "endTime" to JsonPrimitive(now),
            "enableBonusPoints" to JsonPrimitive(false),
            "failed" to JsonPrimitive(false),
            "maxInLessonStreak" to JsonPrimitive(9),
            "shouldLearnThings" to JsonPrimitive(true),
        ))
        val id = session.string("id") ?: throw DuolingoException("POST /sessions -> id yok")
        val result = request(HttpMethod.Put, "/sessions/$id", finished)
        return (result["xpGain"] as? JsonPrimitive)?.intOrNull ?: 0
    }

    suspend fun doLesson(retries: Int = 3): Pair<Int, DuolingoUser> {
        val user = getUser()
        var xp = 0
        for (attempt in 1..retries) {
            try {
                xp = completeLesson(user.fromLanguage.orEmpty(), user.learningLanguage.orEmpty())
                break
            } catch (e: AuthException) {
                throw e
            } catch (e: DuolingoException) {
                if (attempt == retries) throw e
                delay(5_000L * attempt)
            }
        }
        return xp to getUser()
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
