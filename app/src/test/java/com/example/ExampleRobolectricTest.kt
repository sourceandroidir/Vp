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
  fun `inspect PsiphonTunnel methods`() {
    try {
      val clazz = Class.forName("ca.psiphon.PsiphonTunnel")
      val hostServiceClazz = Class.forName("ca.psiphon.PsiphonTunnel\$HostService")
      println("--- HOST SERVICE METHODS ---")
      for (method in hostServiceClazz.methods) {
        val params = method.parameterTypes.map { it.simpleName }.joinToString(", ")
        println("${method.returnType.simpleName} ${method.name}($params)")
      }
      println("--- PSIPHON TUNNEL METHODS ---")
      for (method in clazz.declaredMethods) {
        val params = method.parameterTypes.map { it.simpleName }.joinToString(", ")
        println("${java.lang.reflect.Modifier.toString(method.modifiers)} ${method.returnType.simpleName} ${method.name}($params)")
      }
      println("--- PSIPHON TUNNEL FIELDS ---")
      for (field in clazz.declaredFields) {
        println("${java.lang.reflect.Modifier.toString(field.modifiers)} ${field.type.simpleName} ${field.name}")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
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
