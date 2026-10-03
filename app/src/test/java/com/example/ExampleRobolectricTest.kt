package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DocShare", appName)
  }

  @Test
  fun testGenerateQrCodeBitmap() {
    val bitmap = com.example.util.QrCodeGenerator.generateQrBitmap("https://docshare.app/download?docId=1", 128, 128)
    org.junit.Assert.assertNotNull(bitmap)
    assertEquals(128, bitmap!!.width)
    assertEquals(128, bitmap.height)
  }
}
