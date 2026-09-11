package com.example.data

import com.example.data.model.ServerRegion

object ServerRegions {
    val autoRegion = ServerRegion(
        code = "",
        nameEn = "Best Location (Automatic)",
        nameFa = "بهترین موقعیت (خودکار)",
        flag = "🌐",
        activeServerCount = null
    )

    private val countryNames = mapOf(
        "US" to Pair("United States", "ایالات متحده آمریکا"),
        "CA" to Pair("Canada", "کانادا"),
        "DE" to Pair("Germany", "آلمان"),
        "NL" to Pair("Netherlands", "هلند"),
        "GB" to Pair("United Kingdom", "انگلستان"),
        "UK" to Pair("United Kingdom", "انگلستان"),
        "FR" to Pair("France", "فرانسه"),
        "CH" to Pair("Switzerland", "سوئیس"),
        "JP" to Pair("Japan", "ژاپن"),
        "SG" to Pair("Singapore", "سنگاپور"),
        "SE" to Pair("Sweden", "سوئد"),
        "PL" to Pair("Poland", "لهستان"),
        "AT" to Pair("Austria", "اتریش"),
        "ES" to Pair("Spain", "اسپانیا"),
        "IT" to Pair("Italy", "ایتالیا"),
        "FI" to Pair("Finland", "فنلاند"),
        "BE" to Pair("Belgium", "بلژیک"),
        "NO" to Pair("Norway", "نروژ"),
        "IE" to Pair("Ireland", "ایرلند"),
        "CZ" to Pair("Czech Republic", "جمهوری چک"),
        "DK" to Pair("Denmark", "دانمارک"),
        "RO" to Pair("Romania", "رومانی"),
        "AU" to Pair("Australia", "استرالیا"),
        "IN" to Pair("India", "هند"),
        "BR" to Pair("Brazil", "برزیل"),
        "TR" to Pair("Turkey", "ترکیه"),
        "UA" to Pair("Ukraine", "اوکراین")
    )

    fun getByCode(code: String, count: Int? = null): ServerRegion {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isEmpty()) {
            return autoRegion.copy(activeServerCount = count)
        }
        val names = countryNames[cleanCode]
        val enName = names?.first ?: cleanCode
        val faName = names?.second ?: cleanCode

        return ServerRegion(
            code = cleanCode,
            nameEn = enName,
            nameFa = faName,
            flag = getFlagForCountryCode(cleanCode),
            activeServerCount = count
        )
    }

    fun getFlagForCountryCode(countryCode: String): String {
        val code = countryCode.trim().uppercase()
        if (code.length != 2) return "🌐"
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        if (firstChar < 0x1F1E6 || firstChar > 0x1F1FF || secondChar < 0x1F1E6 || secondChar > 0x1F1FF) {
            return "🌐"
        }
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}

