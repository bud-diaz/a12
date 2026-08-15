package com.paperweight.os.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** QR generation only (com.google.zxing:core) — no scanning, per plan decision #3. */
@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier, sizePx: Int = 480) {
    val bitmap = remember(content, sizePx) { generateQrBitmap(content, sizePx) }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR code linking to $content", modifier = modifier)
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
