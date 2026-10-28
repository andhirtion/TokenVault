package com.yg.tokenvault.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** 位图 / dataURL 里的二维码解码，相机路径之外的第二、第三条通道 */
object QrDecoder {

    private val hints = mapOf<DecodeHintType, Any>(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    fun fromBitmap(src: Bitmap): String? {
        var bmp = src
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide > 1200) {
            val s = 1200f / maxSide
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * s).toInt().coerceAtLeast(1),
                (bmp.height * s).toInt().coerceAtLeast(1),
                true
            )
        }
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        return try {
            val source = RGBLuminanceSource(w, h, pixels)
            MultiFormatReader().apply { setHints(hints) }
                .decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                .text
        } catch (e: Throwable) {
            null
        }
    }

    fun fromBytes(bytes: ByteArray): String? {
        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Throwable) {
            null
        } ?: return null
        return try {
            fromBitmap(bmp)
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    /** data:image/png;base64,xxxx */
    fun fromDataUrl(dataUrl: String): String? {
        val i = dataUrl.indexOf("base64,")
        if (i < 0) return null
        val bytes = try {
            Base64.decode(dataUrl.substring(i + 7), Base64.DEFAULT)
        } catch (e: Throwable) {
            return null
        }
        return fromBytes(bytes)
    }
}