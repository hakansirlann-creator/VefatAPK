package com.example.data

import android.util.Log
import androidx.core.text.HtmlCompat
import com.example.model.KayitKaynagi
import com.example.model.VefatKaydi
import com.example.util.TurkishUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BelediyeScraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun fetchLatestRecords(): Result<List<VefatKaydi>> = withContext(Dispatchers.IO) {
        val urlsToTry = listOf(
            TARGET_URL_100,
            TARGET_URL_BASE,
            "https://api.allorigins.win/raw?url=${URLEncoder.encode(TARGET_URL_100, "UTF-8")}",
            "https://corsproxy.io/?${URLEncoder.encode(TARGET_URL_100, "UTF-8")}"
        )

        var lastError: Throwable? = null
        for (url in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val html = response.body?.string().orEmpty()
                    if (html.isBlank()) {
                        throw IllegalStateException("Empty HTML body")
                    }
                    val parsed = parseHtml(html)
                    if (parsed.isNotEmpty()) {
                        return@withContext Result.success(parsed)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch failed from $url: ${e.message}")
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("Belediye verisi alınamadı"))
    }

    fun parseHtml(html: String): List<VefatKaydi> {
        val modalsById = parseModalsById(html)
        val positionalModals = parseModalsInOrder(html)

        val tableMatch = Regex(
            """<table[^>]*class="[^"]*vefat-table[^"]*"[^>]*>(.*?)</table>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html) ?: Regex(
            """<table[^>]*>(.*?)</table>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html) ?: return emptyList()

        val tableBody = tableMatch.groupValues[1]
        val rowMatches = Regex(
            """<tr[^>]*>(.*?)</tr>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(tableBody).toList()

        val parsed = mutableListOf<VefatKaydi>()
        val dedupKeys = HashSet<String>()
        var dataRowIndex = 0

        for (rowMatch in rowMatches) {
            val rowHtml = rowMatch.groupValues[1]
            if (rowHtml.contains("<th", ignoreCase = true) && !rowHtml.contains("<td", ignoreCase = true)) {
                continue
            }
            val cells = Regex(
                """<td[^>]*>(.*?)</td>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).findAll(rowHtml).map { decodeHtml(it.groupValues[1]) }.toList()

            if (cells.size < 6) continue

            val vefatTarihi = TurkishUtils.formatDateDdMmYyyy(cells[0])
            val adSoyad = cells[1].trim()
            val rowDogumTarihi = cells[2].trim()
            val rowDogumYeri = cells[3].trim()
            val rowDefinYeri = cells[4].trim()
            val rowDefinTarihi = TurkishUtils.formatDateDdMmYyyy(cells[5])

            if (adSoyad.isBlank()) continue

            val modalIdMatch = Regex("""data-bs-target\s*=\s*["']#(taziyeModal\d+)["']""", RegexOption.IGNORE_CASE)
                .find(rowHtml)
            val modalId = modalIdMatch?.groupValues?.get(1)
            val modalFields = if (modalId != null && modalsById.containsKey(modalId)) {
                modalsById[modalId].orEmpty()
            } else {
                positionalModals.getOrNull(dataRowIndex).orEmpty()
            }
            dataRowIndex++

            val dogumTarihi = modalFields["doğum tarihi"].orEmpty().ifBlank { rowDogumTarihi }
            val dogumYeri = modalFields["doğum yeri"].orEmpty().ifBlank { rowDogumYeri }
            val definYeri = modalFields["defin yeri"].orEmpty().ifBlank { rowDefinYeri }
            val definTarihi = TurkishUtils.formatDateDdMmYyyy(
                modalFields["defin tarihi"].orEmpty().ifBlank { rowDefinTarihi }
            )
            val cenazeYakini = modalFields["cenaze yakını"].orEmpty()
            val yakinTelefonu = modalFields["c.yakınının tel"].orEmpty()
                .ifBlank { modalFields["cenaze yakını tel"].orEmpty() }
            val taziyeYeri = modalFields["taziye yeri"].orEmpty()
            val taziyeTarihi = modalFields["taziye tarihi"].orEmpty()

            val dedupKey = "${TurkishUtils.normalizeForSearch(adSoyad)}|${dogumTarihi.trim()}|${vefatTarihi.trim()}"
            if (!dedupKeys.add(dedupKey)) {
                continue
            }

            val stableId = "bel_${modalId ?: dedupKey.hashCode().toUInt()}"
            parsed.add(
                VefatKaydi(
                    id = stableId,
                    adSoyad = adSoyad,
                    dogumTarihi = dogumTarihi,
                    dogumYeri = dogumYeri,
                    vefatTarihi = vefatTarihi,
                    vefatTarihiIso = TurkishUtils.toIsoDate(vefatTarihi),
                    definYeri = definYeri,
                    definTarihi = definTarihi,
                    cenazeYakini = cenazeYakini,
                    yakinTelefonu = yakinTelefonu,
                    taziyeYeri = taziyeYeri,
                    taziyeTarihi = taziyeTarihi,
                    kaynak = KayitKaynagi.BELEDIYE,
                    yayinda = true
                )
            )
        }

        return parsed.sortedByDescending { it.vefatTarihiIso }
    }

    private fun parseModalsById(html: String): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        val modalRegex = Regex(
            """id=["'](taziyeModal\d+)["'][^>]*>.*?<table[^>]*>(.*?)</table>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in modalRegex.findAll(html)) {
            val modalId = match.groupValues[1]
            val tableHtml = match.groupValues[2]
            result[modalId] = extractKeyValues(tableHtml)
        }
        return result
    }

    private fun parseModalsInOrder(html: String): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val modalRegex = Regex(
            """class=["'][^"']*modal-taziye-title[^"']*["'].*?<table[^>]*>(.*?)</table>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in modalRegex.findAll(html)) {
            list.add(extractKeyValues(match.groupValues[1]))
        }
        return list
    }

    private fun extractKeyValues(tableHtml: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val rowRegex = Regex(
            """<tr[^>]*>\s*<th[^>]*>(.*?)</th>\s*<td[^>]*>(.*?)</td>\s*</tr>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (row in rowRegex.findAll(tableHtml)) {
            val key = decodeHtml(row.groupValues[1]).lowercase(java.util.Locale.forLanguageTag("tr-TR")).trim()
            val value = decodeHtml(row.groupValues[2]).trim()
            if (key.isNotBlank()) {
                map[key] = value
            }
        }
        return map
    }

    private fun decodeHtml(raw: String): String {
        val plain = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        return TurkishUtils.sanitizeText(plain)
    }

    companion object {
        private const val TAG = "BelediyeScraper"
        const val TARGET_URL_100 = "https://www.batman.bel.tr/index.php?sayfa=vefat_edenler&p=1&limit=100"
        const val TARGET_URL_BASE = "https://www.batman.bel.tr/vefat_edenler"
    }
}
