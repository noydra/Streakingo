package com.noydra.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noydra.runScheduledLesson
import com.noydra.scheduledTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {
    private const val PREFS = "alarms"
    private const val KEY_TIMES = "times"
    const val EXTRA_TIME = "time"

    fun apply(context: Context, times: List<String>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (old in prefs.getStringSet(KEY_TIMES, emptySet()).orEmpty()) {
            alarmManager.cancel(pendingIntent(context, old))
        }
        for (time in times) scheduleNext(context, time)
        prefs.edit().putStringSet(KEY_TIMES, times.toSet()).apply()
    }

    fun scheduleNext(context: Context, time: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val (hour, minute) = time.split(":").map(String::toInt)
        var next = LocalDate.now().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pending = pendingIntent(context, time)
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pending)
        }
    }

    private fun pendingIntent(context: Context, time: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        time.replace(":", "").toInt(),
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TIME, time),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME) ?: return
        AlarmScheduler.scheduleNext(context, time)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(50_000) {
                    openStorage(context).use { runScheduledLesson(it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        openStorage(context).use { AlarmScheduler.apply(context, scheduledTimes(it)) }
    }
}
