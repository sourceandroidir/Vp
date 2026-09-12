package com.example.util

import android.content.Context
import com.example.data.ServerRegions
import com.example.data.model.ServerRegion
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object PsiphonServerEntriesParser {

    /**
     * Parses hex-encoded JSON server entries from assets/server_entries.txt
     * and aggregates server counts per region.
     */
    fun parseServerEntriesCounts(context: Context): Map<String, Int> {
        val regionCounts = mutableMapOf<String, Int>()
        try {
            context.assets.open("server_entries.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue

                        val region = extractRegionFromHexLine(trimmed)
                        if (region != null && region.isNotEmpty()) {
                            val upper = region.uppercase()
                            regionCounts[upper] = (regionCounts[upper] ?: 0) + 1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Error reading or parsing
        }
        return regionCounts
    }

    /**
     * Extracts regions and builds ServerRegion models with exact server counts.
     */
    fun getParsedServerRegions(context: Context): List<ServerRegion> {
        val counts = parseServerEntriesCounts(context)
        val regions = mutableListOf<ServerRegion>()
        
        // Auto region with total embedded server count
        val totalServers = counts.values.sum()
        regions.add(ServerRegions.autoRegion.copy(activeServerCount = if (totalServers > 0) totalServers else null))

        // Sorted by server count descending, then by name
        val sortedEntries = counts.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key }
        )

        for ((code, count) in sortedEntries) {
            regions.add(ServerRegions.getByCode(code, count))
        }

        return regions
    }

    private fun extractRegionFromHexLine(hexLine: String): String? {
        return try {
            val bytes = hexStringToByteArray(hexLine)
            val decoded = String(bytes, Charsets.UTF_8)
            val jsonStart = decoded.indexOf('{')
            if (jsonStart != -1) {
                val jsonStr = decoded.substring(jsonStart)
                val json = JSONObject(jsonStr)
                if (json.has("region")) {
                    json.getString("region")
                } else if (json.has("egressRegion")) {
                    json.getString("egressRegion")
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
