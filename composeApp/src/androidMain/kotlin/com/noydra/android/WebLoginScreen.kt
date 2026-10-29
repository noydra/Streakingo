package com.noydra.android

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.noydra.api.AuthException
import com.noydra.api.DuolingoClient
import com.noydra.api.DuolingoException
import com.noydra.i18n.t
import com.noydra.ui.AppText
import com.noydra.ui.Colors
import com.noydra.ui.IconButton
import kotlinx.coroutines.delay

private const val LOGIN_URL = "https://www.duolingo.com/?isLoggingIn=true"

private fun jwtFromCookies(): String? =
    CookieManager.getInstance().getCookie("https://www.duolingo.com")
        ?.split(";")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("jwt_token=") }
        ?.removePrefix("jwt_token=")
        ?.takeIf { it.isNotBlank() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(onToken: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        var checked: String? = null
        while (true) {
            val token = jwtFromCookies()
            if (token != null && token != checked) {
                try {
                    if (!DuolingoClient(token).getUser().username.isNullOrEmpty()) {
                        onToken(token)
                        return@LaunchedEffect
                    }
                    checked = token
                } catch (e: AuthException) {
                    checked = token
                } catch (e: DuolingoException) {
                }
            }
            delay(1_500)
        }
    }

    Column(Modifier.fillMaxSize().background(Colors.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton("✕", onClose)
            Spacer(Modifier.width(12.dp))
            AppText(t("weblogin.title"), 18, bold = true)
        }
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Colors.warnBg).padding(12.dp),
        ) {
            AppText(t("weblogin.google_note"), 12, color = Colors.warn)
        }
        Spacer(Modifier.padding(top = 12.dp))
        AndroidView(
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(LOGIN_URL)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
