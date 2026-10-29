package com.noydra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noydra.APP_VERSION
import com.noydra.DEVELOPER
import com.noydra.DEVELOPER_URL
import com.noydra.QR_PREFIX
import com.noydra.REPO_URL
import com.noydra.i18n.I18n
import com.noydra.i18n.t

@Composable
fun Dialogs(state: AppState) {
    val close = { state.dialog = Dialog.None }
    state.platform.backHandler?.invoke(state.dialog != Dialog.None || state.errorMessage != null) {
        when {
            state.errorMessage != null -> state.errorMessage = null
            state.dialog == Dialog.About || state.dialog == Dialog.Transfer -> state.dialog = Dialog.Settings
            else -> close()
        }
    }
    when (state.dialog) {
        Dialog.None -> {}
        Dialog.Settings -> SettingsDialog(state, close)
        Dialog.About -> AboutDialog(state, onBack = { state.dialog = Dialog.Settings })
        Dialog.Connect -> ConnectDialog(state, close)
        Dialog.ManualToken -> ManualTokenDialog(state, close)
        Dialog.Disconnect -> ConfirmDialog(t("dialog.disconnect_confirm"), state::disconnect, close)
        Dialog.Transfer -> TransferDialog(state, onBack = { state.dialog = Dialog.Settings })
        Dialog.WebLogin -> state.platform.webLogin?.invoke(state::onWebLoginToken, close)
    }
    state.errorMessage?.let { message ->
        Modal({ state.errorMessage = null }) {
            AppText("Streakingo", 18, bold = true)
            Spacer(Modifier.height(8.dp))
            AppText(message, 13)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(t("btn.close"), { state.errorMessage = null }, height = 44.dp)
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    AppText(text, 22, bold = true, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    AppCard(padding = if (dimens.compact) 14.dp else 16.dp) {
        AppText(title, 14, bold = true, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun SettingsDialog(state: AppState, onClose: () -> Unit) {
    Modal(onClose) {
        DialogTitle(t("settings.title"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(t("settings.theme")) {
                Segmented(
                    listOf("system", "light", "dark").map { it to t("theme.$it") },
                    state.theme, state::updateTheme,
                )
            }
            SectionCard(t("settings.language")) {
                Segmented(I18n.LANGUAGES.toList(), I18n.language, state::updateLanguage, FontFamily.Default)
            }
            if (state.platform.qrCode != null) {
                SectionCard(t("settings.mobile")) {
                    SecondaryButton(t("transfer.button"), {
                        if (state.connected) state.dialog = Dialog.Transfer
                        else state.errorMessage = t("transfer.no_account")
                    }, Modifier.fillMaxWidth(), height = 42.dp, size = 13)
                }
            }
            SecondaryButton(t("settings.about"), { state.dialog = Dialog.About },
                Modifier.fillMaxWidth(), height = 44.dp, size = 14)
        }
    }
}

@Composable
private fun AboutDialog(state: AppState, onBack: () -> Unit) {
    Modal(onBack, maxWidth = 380.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppText("🔥", 44, fontFamily = FontFamily.Default)
            AppText("Streakingo", 24, bold = true)
            AppText(t("about.version", "version" to APP_VERSION), 12, color = Colors.muted)
            Spacer(Modifier.height(12.dp))
            AppText(t("about.description"), 13, align = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            AppCard(padding = if (dimens.compact) 14.dp else 16.dp) {
                InfoRow(t("about.developer"), DEVELOPER) { state.platform.openUrl(DEVELOPER_URL) }
                Divider()
                InfoRow(t("about.source"), REPO_URL.removePrefix("https://")) { state.platform.openUrl(REPO_URL) }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(t("btn.open_github"), { state.platform.openUrl(REPO_URL) }, height = 44.dp)
            Spacer(Modifier.height(10.dp))
            AppText(t("about.disclaimer"), 11, color = Colors.muted, align = TextAlign.Center)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    AdaptiveRow(
        Modifier.fillMaxWidth(),
        gap = 12.dp,
        stackedGap = 2.dp,
        start = { AppText(label, 12, color = Colors.muted, maxLines = 1) },
        end = {
            AppText(value, 13, bold = true, color = Colors.link,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp))
        },
    )
}

@Composable
private fun ConnectDialog(state: AppState, onClose: () -> Unit) {
    Modal(onClose) {
        DialogTitle(t("connect.title"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.platform.webLogin != null) {
                OptionCard("🌐", t("connect.web"), t("connect.web_sub")) { state.dialog = Dialog.WebLogin }
            }
            if (state.platform.qrScanner != null) {
                OptionCard("📷", t("connect.qr"), t("connect.qr_sub"), state::scanQr)
            }
            OptionCard("🔑", t("btn.manual_token"), t("connect.manual_sub")) { state.dialog = Dialog.ManualToken }
        }
    }
}

@Composable
private fun OptionCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Colors.card).border(1.dp, Colors.border, shape)
            .clickable(onClick = onClick).padding(if (dimens.compact) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(icon, 24, fontFamily = FontFamily.Default, maxLines = 1)
        Spacer(Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f)) {
            AppText(title, 14, bold = true)
            AppText(subtitle, 12, color = Colors.muted)
        }
        Spacer(Modifier.padding(start = 8.dp))
        AppText("›", 20, color = Colors.muted, fontFamily = FontFamily.Default, maxLines = 1)
    }
}

@Composable
private fun ManualTokenDialog(state: AppState, onClose: () -> Unit) {
    var value by remember { mutableStateOf("") }
    Modal(onClose) {
        AppText(t("dialog.token_prompt"), 14, bold = true)
        Spacer(Modifier.height(10.dp))
        val shape = RoundedCornerShape(10.dp)
        BasicTextField(
            value, { value = it },
            textStyle = TextStyle(color = Colors.text, fontSize = scaledSp(13), fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Colors.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 160.dp).clip(shape).background(Colors.card)
                .border(1.dp, Colors.border, shape).padding(10.dp),
        )
        Spacer(Modifier.height(14.dp))
        AdaptiveButtonRow(Modifier.fillMaxWidth()) {
            SecondaryButton(t("btn.cancel"), onClose, height = 44.dp, size = 14)
            PrimaryButton(t("btn.save"), {
                if (value.isNotBlank() && state.saveToken(value)) {
                    state.status = t("status.token_saved")
                    onClose()
                }
            }, height = 44.dp)
        }
    }
}

@Composable
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onClose: () -> Unit) {
    Modal(onClose) {
        AppText(message, 14)
        Spacer(Modifier.height(16.dp))
        AdaptiveButtonRow(Modifier.fillMaxWidth()) {
            SecondaryButton(t("btn.cancel"), onClose, height = 44.dp, size = 14)
            PrimaryButton(t("btn.confirm"), onConfirm, height = 44.dp)
        }
    }
}

@Composable
private fun TransferDialog(state: AppState, onBack: () -> Unit) {
    val qrCode = state.platform.qrCode ?: return
    Modal(onBack, maxWidth = 380.dp) {
        DialogTitle(t("transfer.button"))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        ) {
            qrCode(QR_PREFIX + state.token, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(12.dp))
        AppText(t("transfer.hint"), 13)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Colors.warnBg).padding(12.dp)) {
            AppText(t("transfer.warning"), 12, color = Colors.warn)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(t("btn.close"), onBack, height = 44.dp)
    }
}
