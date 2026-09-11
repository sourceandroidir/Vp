package com.example.data

import com.example.data.model.ServerRegion

object ServerRegions {
    val defaultRegions: List<ServerRegion> = listOf(
        ServerRegion(
            code = "",
            nameEn = "Best Location (Automatic)",
            nameFa = "بهترین موقعیت (خودکار)",
            flag = "🌐",
            activeServerCount = 430
        ),
        ServerRegion(
            code = "US",
            nameEn = "United States",
            nameFa = "ایالات متحده آمریکا",
            flag = "🇺🇸",
            activeServerCount = 65
        ),
        ServerRegion(
            code = "CA",
            nameEn = "Canada",
            nameFa = "کانادا",
            flag = "🇨🇦",
            activeServerCount = 65
        ),
        ServerRegion(
            code = "DE",
            nameEn = "Germany",
            nameFa = "آلمان",
            flag = "🇩🇪",
            activeServerCount = 40
        ),
        ServerRegion(
            code = "NL",
            nameEn = "Netherlands",
            nameFa = "هلند",
            flag = "🇳🇱",
            activeServerCount = 35
        ),
        ServerRegion(
            code = "GB",
            nameEn = "United Kingdom",
            nameFa = "انگلستان",
            flag = "🇬🇧",
            activeServerCount = 30
        ),
        ServerRegion(
            code = "FR",
            nameEn = "France",
            nameFa = "فرانسه",
            flag = "🇫🇷",
            activeServerCount = 25
        ),
        ServerRegion(
            code = "CH",
            nameEn = "Switzerland",
            nameFa = "سوئیس",
            flag = "🇨🇭",
            activeServerCount = 20
        ),
        ServerRegion(
            code = "JP",
            nameEn = "Japan",
            nameFa = "ژاپن",
            flag = "🇯🇵",
            activeServerCount = 20
        ),
        ServerRegion(
            code = "SG",
            nameEn = "Singapore",
            nameFa = "سنگاپور",
            flag = "🇸🇬",
            activeServerCount = 18
        ),
        ServerRegion(
            code = "SE",
            nameEn = "Sweden",
            nameFa = "سوئد",
            flag = "🇸🇪",
            activeServerCount = 15
        ),
        ServerRegion(
            code = "PL",
            nameEn = "Poland",
            nameFa = "لهستان",
            flag = "🇵🇱",
            activeServerCount = 15
        ),
        ServerRegion(
            code = "AT",
            nameEn = "Austria",
            nameFa = "اتریش",
            flag = "🇦🇹",
            activeServerCount = 12
        ),
        ServerRegion(
            code = "ES",
            nameEn = "Spain",
            nameFa = "اسپانیا",
            flag = "🇪🇸",
            activeServerCount = 12
        ),
        ServerRegion(
            code = "IT",
            nameEn = "Italy",
            nameFa = "ایتالیا",
            flag = "🇮🇹",
            activeServerCount = 12
        ),
        ServerRegion(
            code = "FI",
            nameEn = "Finland",
            nameFa = "فنلاند",
            flag = "🇫🇮",
            activeServerCount = 10
        ),
        ServerRegion(
            code = "BE",
            nameEn = "Belgium",
            nameFa = "بلژیک",
            flag = "🇧🇪",
            activeServerCount = 10
        ),
        ServerRegion(
            code = "NO",
            nameEn = "Norway",
            nameFa = "نروژ",
            flag = "🇳🇴",
            activeServerCount = 10
        ),
        ServerRegion(
            code = "IE",
            nameEn = "Ireland",
            nameFa = "ایرلند",
            flag = "🇮🇪",
            activeServerCount = 8
        ),
        ServerRegion(
            code = "CZ",
            nameEn = "Czech Republic",
            nameFa = "جمهوری چک",
            flag = "🇨🇿",
            activeServerCount = 8
        )
    )

    fun getDefaultServerCount(code: String): Int {
        return defaultRegions.find { it.code.equals(code, ignoreCase = true) }?.activeServerCount ?: 10
    }

    fun getByCode(code: String): ServerRegion {
        return defaultRegions.find { it.code.equals(code, ignoreCase = true) }
            ?: ServerRegion(
                code = code,
                nameEn = code,
                nameFa = code,
                flag = getFlagForCountryCode(code),
                activeServerCount = 10
            )
    }

    fun getFlagForCountryCode(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }
}

