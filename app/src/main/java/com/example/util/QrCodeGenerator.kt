package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.entity.DocumentEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

object QrCodeGenerator {

    /**
     * Constructs the unique shareable link for a specific uploaded document.
     */
    fun getDocumentShareableUrl(doc: DocumentEntity, baseUrl: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        return "$cleanBase/download?docId=${doc.id}&phone=${doc.uploaderPhone}"
    }

    /**
     * Generates a high-resolution Bitmap QR code using the ZXing library.
     */
    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
            }

            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height,
                hints
            )

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }

            Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a branded QR Code specifically for an uploaded document with customized styling.
     */
    fun generateDocumentQr(
        doc: DocumentEntity,
        baseUrl: String,
        size: Int = 512
    ): Bitmap? {
        val uniqueUrl = getDocumentShareableUrl(doc, baseUrl)
        return generateQrBitmap(
            content = uniqueUrl,
            width = size,
            height = size,
            darkColor = Color.parseColor("#0284C7"), // DocShare Brand Blue
            lightColor = Color.WHITE
        )
    }

    /**
     * Saves a generated QR Code bitmap to the app cache directory for sharing via FileProvider.
     */
    fun saveQrBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val qrFile = File(cacheDir, "qr_$cleanName.png")
            FileOutputStream(qrFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            qrFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Shares the QR code image directly using Android Share Intent (FileProvider).
     */
    fun shareQrCodeImage(
        context: Context,
        bitmap: Bitmap,
        doc: DocumentEntity,
        shareUrl: String
    ) {
        try {
            val qrFile = saveQrBitmapToCache(context, bitmap, "${doc.id}_${doc.fileName}")
            if (qrFile == null) {
                Toast.makeText(context, "Failed to prepare QR code image", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                qrFile
            )

            val caption = "📄 Scan to download \"${doc.fileName}\" on DocShare:\n$shareUrl"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Document QR Code"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing QR code: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
