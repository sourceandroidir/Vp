package com.example

import com.example.data.ServerRegions
import com.example.data.model.SplitTunnelConfig
import com.example.data.model.SplitTunnelMode
import com.example.data.model.TrafficStats
import com.example.data.model.UpstreamProxyConfig
import com.example.data.model.VpnState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PsiphonIntegrationTest {

    @Test
    fun testUpstreamProxyFormatting_disabled() {
        val proxy = UpstreamProxyConfig(
            enabled = false,
            protocol = "HTTP",
            host = "proxy.example.com",
            port = 8080
        )
        assertNull(proxy.formattedUrl)
    }

    @Test
    fun testUpstreamProxyFormatting_httpWithoutAuth() {
        val proxy = UpstreamProxyConfig(
            enabled = true,
            protocol = "HTTP",
            host = "10.0.0.1",
            port = 8080
        )
        assertEquals("http://10.0.0.1:8080", proxy.formattedUrl)
    }

    @Test
    fun testUpstreamProxyFormatting_socks5WithAuth() {
        val proxy = UpstreamProxyConfig(
            enabled = true,
            protocol = "SOCKS5",
            host = "myproxy.net",
            port = 1080,
            username = "admin",
            password = "secretpassword"
        )
        assertEquals("socks5://admin:secretpassword@myproxy.net:1080", proxy.formattedUrl)
    }

    @Test
    fun testSplitTunnelModes() {
        val bypassConfig = SplitTunnelConfig(
            mode = SplitTunnelMode.BYPASS_SELECTED,
            excludedPackages = setOf("com.google.android.youtube", "org.telegram.messenger")
        )
        assertEquals(2, bypassConfig.excludedPackages.size)
        assertEquals(SplitTunnelMode.BYPASS_SELECTED, bypassConfig.mode)

        val onlySelectedConfig = SplitTunnelConfig(
            mode = SplitTunnelMode.ONLY_SELECTED,
            excludedPackages = setOf("com.android.chrome")
        )
        assertEquals(1, onlySelectedConfig.excludedPackages.size)
    }

    @Test
    fun testServerRegions() {
        val autoRegion = ServerRegions.getByCode("")
        assertEquals("", autoRegion.code)
        assertTrue(autoRegion.displayName.contains("🌐"))

        val usRegion = ServerRegions.getByCode("US", 15)
        assertEquals("US", usRegion.code)
        assertTrue(usRegion.displayName.contains("🇺🇸"))
        assertEquals(15, usRegion.activeServerCount)

        val deRegion = ServerRegions.getByCode("DE")
        assertEquals("DE", deRegion.code)
        assertTrue(deRegion.displayName.contains("🇩🇪"))
    }

    @Test
    fun testTrafficStatsFormatting() {
        val statsZero = TrafficStats(0L, 0L)
        assertEquals("0 B", statsZero.formattedBytesIn())
        assertEquals("0 B", statsZero.formattedBytesOut())

        val statsKb = TrafficStats(2048L, 4096L)
        assertEquals("2 KB", statsKb.formattedBytesIn())
        assertEquals("4 KB", statsKb.formattedBytesOut())

        val statsMb = TrafficStats(10485760L, 5242880L)
        assertEquals("10.0 MB", statsMb.formattedBytesIn())
        assertEquals("5.0 MB", statsMb.formattedBytesOut())
    }

    @Test
    fun testVpnStateEnum() {
        assertEquals("قطع شده", VpnState.DISCONNECTED.titleFa)
        assertEquals("در حال اتصال...", VpnState.CONNECTING.titleFa)
        assertEquals("متصل شد", VpnState.CONNECTED.titleFa)
        assertEquals("در حال قطع اتصال...", VpnState.STOPPING.titleFa)
    }

    @Test
    fun testPsiphonConfigJsonStructure() {
        val baseConfig = """
        {
            "PropagationChannelId": "92AACC5BABE0944C",
            "SponsorId": "1BC527D3D09985CF",
            "UseIndistinguishableTLS": true,
            "EstablishTunnelTimeoutSeconds": 20
        }
        """.trimIndent()

        val json = JSONObject(baseConfig)
        assertEquals("92AACC5BABE0944C", json.getString("PropagationChannelId"))
        assertEquals("1BC527D3D09985CF", json.getString("SponsorId"))
        assertTrue(json.getBoolean("UseIndistinguishableTLS"))
        assertEquals(20, json.getInt("EstablishTunnelTimeoutSeconds"))
        assertFalse(json.has("UpstreamProxyURL"))
    }
}
