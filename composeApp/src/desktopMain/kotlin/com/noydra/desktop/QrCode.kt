package com.noydra.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun QrCode(data: String, modifier: Modifier = Modifier) {
    val matrix = remember(data) {
        QRCodeWriter().encode(
            data, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0),
        )
    }
    Canvas(modifier) {
        val cell = minOf(size.width / matrix.width, size.height / matrix.height)
        val offset = Offset((size.width - cell * matrix.width) / 2, (size.height - cell * matrix.height) / 2)
        drawRect(Color.White)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) {
                drawRect(Color.Black, offset + Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
