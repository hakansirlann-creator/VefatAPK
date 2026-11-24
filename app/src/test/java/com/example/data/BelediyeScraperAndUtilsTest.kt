package com.example.data

import com.example.util.TurkishUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BelediyeScraperAndUtilsTest {

    @Test
    fun parseHtml_pairsRowsWithModalsAndDeduplicates() {
        val sampleHtml = """
            <table class="table table-hover table-bordered vefat-table">
              <thead><tr><th>Vefat Tarihi</th><th>Vefat Eden</th><th>D. Tarihi</th><th>D. Yeri</th><th>Defin Yeri</th><th>Defin Tarihi</th><th>Detay</th></tr></thead>
              <tbody>
                <tr>
                  <td>15.11.2026</td>
                  <td>İsmail UÇAR</td>
                  <td>01.03.1954</td>
                  <td>Öteyaka</td>
                  <td>Asri Mezarlık</td>
                  <td>15.11.2026</td>
                  <td><button data-bs-target="#taziyeModal7954">Taziye Bilgileri</button></td>
                </tr>
                <tr>
                  <td>15.11.2026</td>
                  <td>İsmail UÇAR</td>
                  <td>01.03.1954</td>
                  <td>Öteyaka</td>
                  <td>Asri Mezarlık</td>
                  <td>15.11.2026</td>
                  <td><button data-bs-target="#taziyeModal7954">Taziye Bilgileri</button></td>
                </tr>
              </tbody>
            </table>
            <div id="taziyeModal7954">
              <table>
                <tbody>
                  <tr><th>Doğum Tarihi</th><td>01.03.1954</td></tr>
                  <tr><th>Doğum Yeri</th><td>Öteyaka</td></tr>
                  <tr><th>Defin Yeri</th><td>Asri Mezarlık</td></tr>
                  <tr><th>Defin Tarihi</th><td>15.11.2026</td></tr>
                  <tr><th>Cenaze Yakını</th><td>Mehmet UÇAR</td></tr>
                  <tr><th>C.Yakınının Tel</th><td>0538 ... .. 07</td></tr>
                  <tr><th>Taziye Yeri</th><td>M.Şirin Öndaş Taziye Evi</td></tr>
                  <tr><th>Taziye Tarihi</th><td>15-17.11.2026</td></tr>
                </tbody>
              </table>
            </div>
        """.trimIndent()

        val scraper = BelediyeScraper()
        val parsed = scraper.parseHtml(sampleHtml)

        assertEquals(1, parsed.size)
        val item = parsed.first()
        assertEquals("İsmail UÇAR", item.adSoyad)
        assertEquals("1954", item.dogumYili)
        assertEquals("Mehmet UÇAR", item.cenazeYakini)
        assertEquals("0538 ... .. 07", item.yakinTelefonu)
        assertEquals(true, item.isTelefonMaskeli)
        assertEquals("M.Şirin Öndaş Taziye Evi", item.taziyeYeri)
        assertEquals("15-17.11.2026", item.taziyeTarihi)
        assertEquals("2026-11-15", item.vefatTarihiIso)
    }

    @Test
    fun turkishUtils_handlesTurkishFoldingAndWhatsAppPhonesSafely() {
        assertEquals(
            TurkishUtils.normalizeForSearch("İsmail ŞAHİN Çığır ÖĞÜT"),
            TurkishUtils.normalizeForSearch("ismail sahin cigir ogut")
        )
        assertEquals("905321234567", TurkishUtils.formatWhatsAppPhone("0532 123 45 67"))
        assertEquals("905321234567", TurkishUtils.formatWhatsAppPhone("5321234567"))
        assertNull(TurkishUtils.formatWhatsAppPhone("0538 ... .. 07"))
        assertNull(TurkishUtils.formatDialablePhone("0538 ... .. 07"))
    }
}
