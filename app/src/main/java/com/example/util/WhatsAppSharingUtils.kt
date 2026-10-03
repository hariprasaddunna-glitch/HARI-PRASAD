package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.entity.DocumentEntity
import java.io.File
import java.net.URLEncoder

object WhatsAppSharingUtils {

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return isPackageInstalled(pm, WHATSAPP_PACKAGE) || isPackageInstalled(pm, WHATSAPP_BUSINESS_PACKAGE)
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Builds a comprehensive, cleanly formatted text payload containing the download URL and file info.
     */
    fun buildDocumentShareMessage(
        doc: DocumentEntity,
        stationBaseUrl: String,
        includePin: Boolean = true,
        plainPin: String? = null
    ): String {
        val cleanBaseUrl = stationBaseUrl.trim().trimEnd('/')
        val directDownloadUrl = "$cleanBaseUrl/download?docId=${doc.id}&phone=${doc.uploaderPhone}"

        val pinSection = when {
            doc.isProtected && !plainPin.isNullOrBlank() && includePin ->
                "• *Security*: 🔒 PIN Protected\n• *Access PIN*: `$plainPin`\n"
            doc.isProtected ->
                "• *Security*: 🔒 PIN Protected (PIN required from uploader)\n"
            else ->
                "• *Security*: 🌐 Public Access\n"
        }

        val expirySection = if (doc.expiryTimestamp != null) {
            "• *Expires*: ${SecurityUtils.formatDate(doc.expiryTimestamp)}\n"
        } else ""

        return buildString {
            appendLine("📄 *DocShare Document Transfer*")
            appendLine("━━━━━━━━━━━━━━━━━━━")
            appendLine("📁 *File*: ${doc.fileName}")
            appendLine("📊 *Size*: ${SecurityUtils.formatFileSize(doc.sizeBytes)}")
            appendLine("👤 *Uploader*: ${doc.uploaderName}")
            appendLine("☁️ *Storage*: ${doc.storageProvider}")
            append(pinSection)
            append(expirySection)
            appendLine("━━━━━━━━━━━━━━━━━━━")
            appendLine("🔗 *Direct Download Link*:")
            appendLine(directDownloadUrl)
            appendLine()
            appendLine("📱 *Portal Search*:")
            appendLine("Open $cleanBaseUrl and search by phone number: *${doc.uploaderPhone}*")
        }
    }

    /**
     * Constructs an Android Share Intent configured for WhatsApp.
     */
    fun createWhatsAppShareIntent(
        message: String,
        subject: String = "DocShare Download URL"
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage(WHATSAPP_PACKAGE)
        }
    }

    /**
     * Sends the download URL and complete file info directly via WhatsApp using an Android Share Intent.
     * Falls back seamlessly to Android Share Chooser if WhatsApp is not directly available.
     */
    fun shareDocumentViaWhatsAppIntent(
        context: Context,
        doc: DocumentEntity,
        stationBaseUrl: String,
        plainPin: String? = null,
        targetPhoneNumber: String? = null
    ) {
        val message = buildDocumentShareMessage(doc, stationBaseUrl, plainPin = plainPin)
        shareTextToWhatsApp(context, message, targetPhoneNumber)
    }

    /**
     * Shares a text payload directly via WhatsApp Intent or System Chooser.
     */
    fun shareTextToWhatsApp(
        context: Context,
        message: String,
        targetPhoneNumber: String? = null
    ) {
        try {
            val cleanPhone = targetPhoneNumber?.filter { it.isDigit() }
            if (!cleanPhone.isNullOrBlank() && cleanPhone.length >= 7) {
                // If a phone number is provided, try direct chat intent
                val encodedMessage = URLEncoder.encode(message, "UTF-8")
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
                val chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(WHATSAPP_PACKAGE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (chatIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(chatIntent)
                    return
                }
            }

            // Standard Android Intent.ACTION_SEND targeting WhatsApp
            val sendIntent = createWhatsAppShareIntent(message)
            sendIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            if (isPackageInstalled(context.packageManager, WHATSAPP_PACKAGE)) {
                context.startActivity(sendIntent)
            } else if (isPackageInstalled(context.packageManager, WHATSAPP_BUSINESS_PACKAGE)) {
                sendIntent.setPackage(WHATSAPP_BUSINESS_PACKAGE)
                context.startActivity(sendIntent)
            } else {
                // Launch Android Share Chooser with pre-filled message
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    putExtra(Intent.EXTRA_SUBJECT, "DocShare Download Link")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(chooserIntent, "Share via WhatsApp or other apps"))
                Toast.makeText(context, "WhatsApp not installed, opening share options", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            val fallbackChooser = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(fallbackChooser, "Share via..."))
        }
    }

    /**
     * Shares a local file via Android FileProvider Intent.
     */
    fun shareFileToWhatsApp(
        context: Context,
        file: File,
        caption: String = ""
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = context.contentResolver.getType(uri) ?: "*/*"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(WHATSAPP_PACKAGE)
            }

            if (isPackageInstalled(context.packageManager, WHATSAPP_PACKAGE)) {
                context.startActivity(shareIntent)
            } else if (isPackageInstalled(context.packageManager, WHATSAPP_BUSINESS_PACKAGE)) {
                shareIntent.setPackage(WHATSAPP_BUSINESS_PACKAGE)
                context.startActivity(shareIntent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (caption.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, caption)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Share file"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
