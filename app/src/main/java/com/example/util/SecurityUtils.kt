package com.example.util

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SecurityUtils {

    const val MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB limit from user brief

    val DISALLOWED_EXTENSIONS = setOf(
        "exe", "bat", "sh", "cmd", "vbs", "msi", "scr", "apk", "jar", "bin", "elf"
    )

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.trim().toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return true
        return hashPin(inputPin) == storedHash
    }

    fun validateFile(fileName: String, sizeBytes: Long): ValidationResult {
        if (sizeBytes <= 0) {
            return ValidationResult.Error("Selected file is empty")
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            return ValidationResult.Error("File exceeds 100 MB maximum platform limit (${formatFileSize(sizeBytes)})")
        }
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (DISALLOWED_EXTENSIONS.contains(ext)) {
            return ValidationResult.Error("Security policy error: executable or script file types (.$ext) are disallowed.")
        }
        return ValidationResult.Success
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        val value = bytes / Math.pow(1024.0, exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, pre)
    }

    fun maskPhoneNumber(phone: String): String {
        val clean = phone.filter { it.isDigit() || it == '+' }
        if (clean.length <= 4) return clean
        val last4 = clean.takeLast(4)
        val prefix = if (clean.startsWith("+")) clean.substring(0, minOf(3, clean.length - 4)) else ""
        return "$prefix***-***-$last4"
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatShortDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
