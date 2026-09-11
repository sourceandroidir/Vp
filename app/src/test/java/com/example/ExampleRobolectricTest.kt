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
    assertEquals("Psiphon VPN", appName)
  }

  @Test
  fun `verify PsiphonTunnel default directory`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val clazz = Class.forName("ca.psiphon.PsiphonTunnel")
    val defaultDirMethod = clazz.getDeclaredMethod("defaultDataRootDirectory", Context::class.java)
    defaultDirMethod.isAccessible = true
    val defaultDir = defaultDirMethod.invoke(null, context) as java.io.File
    org.junit.Assert.assertTrue(defaultDir.path.contains("ca.psiphon.PsiphonTunnel.tunnel-core"))
  }

  @Test
  fun `verify default server regions available`() {
    val auto = com.example.data.ServerRegions.autoRegion
    org.junit.Assert.assertEquals("", auto.code)
    org.junit.Assert.assertTrue(auto.displayName.contains("🌐"))

    val us = com.example.data.ServerRegions.getByCode("US", 10)
    org.junit.Assert.assertEquals("US", us.code)
    org.junit.Assert.assertEquals(10, us.activeServerCount)
  }
}
