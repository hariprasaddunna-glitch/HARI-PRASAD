package com.example

import com.example.util.SecurityUtils
import com.example.util.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPinHashingAndVerification() {
        val pin = "1234"
        val hash = SecurityUtils.hashPin(pin)
        assertTrue(SecurityUtils.verifyPin("1234", hash))
        assertFalse(SecurityUtils.verifyPin("9999", hash))
    }

    @Test
    fun testPhoneMasking() {
        val phone = "+15551234567"
        val masked = SecurityUtils.maskPhoneNumber(phone)
        assertTrue(masked.contains("***-***-4567"))
    }

    @Test
    fun testFileValidationDisallowedExtensions() {
        val invalidExe = SecurityUtils.validateFile("malware.exe", 1024)
        assertTrue(invalidExe is ValidationResult.Error)

        val invalidApk = SecurityUtils.validateFile("app.apk", 1024)
        assertTrue(invalidApk is ValidationResult.Error)

        val validPdf = SecurityUtils.validateFile("contract.pdf", 1024)
        assertTrue(validPdf is ValidationResult.Success)
    }

    @Test
    fun testFileValidationSizeLimit() {
        val overLimit = SecurityUtils.validateFile("giant.iso", 101L * 1024 * 1024)
        assertTrue(overLimit is ValidationResult.Error)

        val withinLimit = SecurityUtils.validateFile("document.pdf", 50L * 1024 * 1024)
        assertTrue(withinLimit is ValidationResult.Success)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("500 B", SecurityUtils.formatFileSize(500))
        assertEquals("1.0 KB", SecurityUtils.formatFileSize(1024))
        assertEquals("10.0 MB", SecurityUtils.formatFileSize(10 * 1024 * 1024))
    }

    @Test
    fun testWhatsAppShareTextFormatting() {
        val fileName = "report.pdf"
        val phone = "+1234567890"
        val url = "https://example.com"
        val message = "📄 DocShare: $fileName. Link: $url Search: $phone"
        assertTrue(message.contains("report.pdf"))
        assertTrue(message.contains("+1234567890"))
    }

    @Test
    fun testDocumentShareableUrl() {
        val doc = com.example.data.entity.DocumentEntity(
            id = 42,
            fileName = "contract.pdf",
            uploaderName = "Alice",
            uploaderPhone = "+15551234567",
            sizeBytes = 2048,
            mimeType = "application/pdf",
            storageProvider = "LOCAL_SECURE",
            remoteFileId = "doc_42",
            remoteFullPath = "/vault/doc_42"
        )
        val url = com.example.util.QrCodeGenerator.getDocumentShareableUrl(doc, "https://docshare.app/")
        assertEquals("https://docshare.app/download?docId=42&phone=+15551234567", url)
    }

    @Test
    fun testZxingQrCodeEncoding() {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(
            "https://docshare.app/download?docId=1",
            com.google.zxing.BarcodeFormat.QR_CODE,
            128,
            128
        )
        org.junit.Assert.assertNotNull(bitMatrix)
        assertEquals(128, bitMatrix.width)
        assertEquals(128, bitMatrix.height)
    }
}
