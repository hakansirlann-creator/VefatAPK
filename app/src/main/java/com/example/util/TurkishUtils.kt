package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TurkishUtils {

    private val localeTr = Locale.forLanguageTag("tr-TR")

    fun normalizeForSearch(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'İ', 'I', 'ı', 'i' -> sb.append('i')
                'Ş', 'ş', 'S', 's' -> sb.append('s')
                'Ğ', 'ğ', 'G', 'g' -> sb.append('g')
                'Ü', 'ü', 'U', 'u' -> sb.append('u')
                'Ö', 'ö', 'O', 'o' -> sb.append('o')
                'Ç', 'ç', 'C', 'c' -> sb.append('c')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun sanitizeText(input: String): String {
        return input
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun todayDdMmYyyy(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", localeTr)
        return sdf.format(Date())
    }

    fun formatDateDdMmYyyy(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return ""
        val dmyMatch = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$""").find(cleaned)
        if (dmyMatch != null) {
            val d = dmyMatch.groupValues[1].padStart(2, '0')
            val m = dmyMatch.groupValues[2].padStart(2, '0')
            val y = dmyMatch.groupValues[3]
            return "$d.$m.$y"
        }
        val isoMatch = Regex("""^(\d{4})[./-](\d{1,2})[./-](\d{1,2})$""").find(cleaned)
        if (isoMatch != null) {
            val y = isoMatch.groupValues[1]
            val m = isoMatch.groupValues[2].padStart(2, '0')
            val d = isoMatch.groupValues[3].padStart(2, '0')
            return "$d.$m.$y"
        }
        return cleaned
    }

    fun toIsoDate(raw: String): String {
        val cleaned = raw.trim()
        val dmyMatch = Regex("""(\d{1,2})[./-](\d{1,2})[./-](\d{4})""").find(cleaned)
        if (dmyMatch != null) {
            val d = dmyMatch.groupValues[1].padStart(2, '0')
            val m = dmyMatch.groupValues[2].padStart(2, '0')
            val y = dmyMatch.groupValues[3]
            return "$y-$m-$d"
        }
        val isoMatch = Regex("""(\d{4})[./-](\d{1,2})[./-](\d{1,2})""").find(cleaned)
        if (isoMatch != null) {
            val y = isoMatch.groupValues[1]
            val m = isoMatch.groupValues[2].padStart(2, '0')
            val d = isoMatch.groupValues[3].padStart(2, '0')
            return "$y-$m-$d"
        }
        return "0000-00-00"
    }

    fun isWithinDays(isoDate: String, daysBackInclusive: Int): Boolean {
        val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(isoDate) ?: return false
        val year = match.groupValues[1].toIntOrNull() ?: return false
        val month = (match.groupValues[2].toIntOrNull() ?: return false) - 1
        val day = match.groupValues[3].toIntOrNull() ?: return false

        val recordCal = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val minCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -daysBackInclusive)
        }
        val maxCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return !recordCal.before(minCal) && !recordCal.after(maxCal)
    }

    /**
     * Converts a complete Turkish phone number to WhatsApp international format (90XXXXXXXXXX).
     * Never guesses or completes masked phone numbers (returns null if masked or invalid).
     */
    fun formatWhatsAppPhone(phone: String): String? {
        if (phone.isBlank() || phone.contains("..") || phone.contains("*")) {
            return null
        }
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("0") -> "9" + digits
            digits.length == 10 && digits.startsWith("5") -> "90$digits"
            digits.length == 12 && digits.startsWith("90") -> digits
            digits.length == 13 && digits.startsWith("090") -> digits.drop(1)
            else -> null
        }
    }

    fun formatDialablePhone(phone: String): String? {
        if (phone.isBlank() || phone.contains("..") || phone.contains("*")) {
            return null
        }
        val digits = phone.filter { it.isDigit() || it == '+' }
        return if (digits.count { it.isDigit() } >= 7) digits else null
    }
}
