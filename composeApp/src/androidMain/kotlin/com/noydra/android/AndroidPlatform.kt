package com.noydra.android

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.noydra.Platform
import com.noydra.data.Storage
import kotlinx.coroutines.tasks.await

fun openStorage(context: Context): Storage {
    val file = context.getDatabasePath("streakingo.db")
    file.parentFile?.mkdirs()
    return Storage(file.path)
}

private object UiStorage {
    @Volatile private var instance: Storage? = null
    fun get(context: Context): Storage =
        instance ?: synchronized(this) { instance ?: openStorage(context.applicationContext).also { instance = it } }
}

class AndroidPlatform(private val activity: ComponentActivity) : Platform {
    override val isMobile = true
    override val storage = UiStorage.get(activity)

    override fun openUrl(url: String) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override suspend fun applySchedule(times: List<String>) = AlarmScheduler.apply(activity, times)

    override fun scheduleNeedsPermission(): Boolean =
        Build.VERSION.SDK_INT >= 31 && !activity.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    override fun requestSchedulePermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}"))
            )
        }
    }

    override val qrScanner: suspend () -> String? = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(activity, options).startScan().await().rawValue
    }

    override val webLogin: @Composable (onToken: (String) -> Unit, onClose: () -> Unit) -> Unit = { onToken, onClose ->
        WebLoginScreen(onToken, onClose)
    }

    override val backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { enabled, onBack ->
        BackHandler(enabled, onBack)
    }
}
