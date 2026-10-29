package com.noydra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noydra.Platform
import com.noydra.i18n.I18n
import com.noydra.i18n.courseLanguage
import com.noydra.i18n.t
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val HOURS = (0..23).map { it.toString().padStart(2, '0') }
private val MINUTES = (0..55 step 5).map { it.toString().padStart(2, '0') }

@Composable
fun App(platform: Platform, resumeCount: Int = 0, onDarkThemeChange: (Boolean) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(platform, scope) }
    LaunchedEffect(Unit) { state.refreshAccount() }
    LaunchedEffect(resumeCount) {
        state.refreshSchedulePermission()
        state.refreshHistory()
    }

    val dark = isDark(state.theme)
    SideEffect { onDarkThemeChange(dark) }

    AppTheme(dark, I18n.language) {
        ResponsiveRoot {
            Box(Modifier.fillMaxSize().background(Colors.bg)) {
                MainScreen(state)
                Dialogs(state)
            }
        }
    }
}

@Composable
private fun MainScreen(state: AppState) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
        ) {
            Header(state)
            HeroCard(state)
            ScheduleCard(state)
            HistoryCard(state)
            if (state.status.isNotEmpty()) AppText(state.status, 12, color = Colors.muted)
        }
    }
}

@Composable
private fun Header(state: AppState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            AppText("Streakingo", 26, bold = true, maxLines = 1)
            AppText(t("app.subtitle"), 13, color = Colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        IconButton("⚙") { state.dialog = Dialog.Settings }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun HeroCard(state: AppState) {
    val user = state.user
    AppCard {
        AdaptiveRow(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Colors.hero)
                .padding(horizontal = if (dimens.compact) 14.dp else 18.dp, vertical = 16.dp),
            gap = 10.dp,
            start = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText("🔥", 40, fontFamily = FontFamily.Default, maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        AppText(if (state.connected && user != null) "${user.streak ?: "—"}" else "—", 40,
                            bold = true, color = Colors.flame, maxLines = 1)
                        AppText(t("streak.days"), 13, color = Colors.muted)
                    }
                }
            },
            end = {
                if (state.connected && user != null) {
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
                    if (user.lastExtendedDate == today) Pill(t("today.done"), Colors.ok, Colors.okBg)
                    else Pill(t("today.pending"), Colors.warn, Colors.warnBg)
                }
            },
        )

        Spacer(Modifier.height(14.dp))
        val (title, subtitle) = when {
            !state.connected -> t("account.none") to t("account.none_hint")
            state.accountError != null -> t("account.error") to state.accountError.orEmpty()
            user == null -> t("account.loading") to ""
            else -> "@${user.username}" to "${courseLanguage(user.fromLanguage)} → ${courseLanguage(user.learningLanguage)}"
        }
        AppText(title, 15, bold = true)
        if (subtitle.isNotEmpty()) AppText(subtitle, 12, color = Colors.muted)

        Spacer(Modifier.height(16.dp))
        val busy = state.fetching || state.lessonRunning
        if (state.connected) {
            PrimaryButton(if (state.lessonRunning) t("btn.lesson_running") else t("btn.lesson"),
                state::lessonNow, enabled = !busy)
        } else {
            PrimaryButton(t("btn.connect_primary"), state::connect, enabled = !busy)
        }

        Spacer(Modifier.height(10.dp))
        AdaptiveButtonRow(Modifier.fillMaxWidth()) {
            if (state.connected) {
                SecondaryButton(t("btn.refresh_token"), state::connect, enabled = !state.fetching)
            }
            SecondaryButton(t("btn.manual_token"), { state.dialog = Dialog.ManualToken })
            if (state.connected) {
                SecondaryButton(t("btn.disconnect"), { state.dialog = Dialog.Disconnect })
            }
        }
    }
}

@Composable
private fun ScheduleCard(state: AppState) {
    AppCard {
        AppText(t("sched.title"), 16, bold = true, modifier = Modifier.padding(horizontal = 2.dp))
        AppText(t("sched.subtitle"), 12, color = Colors.muted, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(10.dp))

        ScheduleRow(t("sched.noon"), t("sched.noon_sub"), state.noonEnabled, { state.updateSchedule(noon = it) })
        Divider()
        val (hour, minute) = state.customTime.split(":").let { it[0] to it.getOrElse(1) { "00" } }
        ScheduleRow(t("sched.custom"), t("sched.custom_sub"), state.customEnabled, { state.updateSchedule(custom = it) }) {
            DropdownPill(hour, HOURS) { state.updateSchedule(time = "$it:$minute") }
            AppText(":", 14, bold = true, modifier = Modifier.padding(horizontal = 4.dp))
            DropdownPill(minute, if (minute in MINUTES) MINUTES else (MINUTES + minute).sorted()) {
                state.updateSchedule(time = "$hour:$it")
            }
        }

        if (state.scheduleNeedsPermission) {
            Spacer(Modifier.height(10.dp))
            AdaptiveRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Colors.warnBg).padding(12.dp),
                gap = 8.dp,
                stackedEndAlignment = Alignment.End,
                start = { AppText(t("sched.exact_denied"), 12, color = Colors.warn) },
                end = { SecondaryButton(t("sched.exact_grant"), { state.platform.requestSchedulePermission() }, height = 34.dp) },
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val titles = @Composable {
        Column {
            AppText(title, 14, bold = true)
            AppText(subtitle, 12, color = Colors.muted)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (extra == null) titles()
            else AdaptiveRow(
                Modifier.fillMaxWidth(),
                gap = 10.dp,
                start = titles,
                end = { Row(verticalAlignment = Alignment.CenterVertically) { extra() } },
            )
        }
        Spacer(Modifier.width(10.dp))
        AppSwitch(checked, onChange)
    }
}

@Composable
private fun HistoryCard(state: AppState) {
    AppCard {
        AppText(t("history.title"), 16, bold = true, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(6.dp))
        if (state.history.isEmpty()) {
            AppText(t("history.empty"), 13, color = Colors.muted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                align = androidx.compose.ui.text.style.TextAlign.Center)
        }
        for (row in state.history) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 2.dp)) {
                Box(Modifier.padding(top = 5.dp).size(9.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (row.success) Colors.ok else Colors.err))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    AppText(historyText(row), 13)
                    AppText("${formatTimestamp(row.ts)}  ·  ${historySource(row.source)}", 11, color = Colors.muted)
                }
            }
        }
    }
}

private fun formatTimestamp(ts: String): String {
    val parts = ts.split(" ")
    val date = parts[0].split("-")
    if (date.size != 3 || parts.size < 2) return ts
    return "${date[2]}.${date[1]}.${date[0]}  ${parts[1].take(5)}"
}
