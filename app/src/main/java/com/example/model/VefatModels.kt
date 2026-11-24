package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class KayitKaynagi {
    BELEDIYE,
    MANUEL
}

data class VefatKaydi(
    val id: String,
    val adSoyad: String,
    val dogumTarihi: String = "",
    val dogumYeri: String = "",
    val vefatTarihi: String = "",
    val vefatTarihiIso: String = "",
    val definYeri: String = "",
    val definTarihi: String = "",
    val cenazeYakini: String = "",
    val yakinTelefonu: String = "",
    val taziyeYeri: String = "",
    val taziyeTarihi: String = "",
    val kaynak: KayitKaynagi = KayitKaynagi.BELEDIYE,
    val yayinda: Boolean = true,
    val ekleyenUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val dogumYili: String
        get() {
            val match = Regex("""\b(19\d{2}|20\d{2})\b""").find(dogumTarihi)
            return match?.value ?: dogumTarihi.trim()
        }

    val isTelefonMaskeli: Boolean
        get() = yakinTelefonu.isBlank() ||
            yakinTelefonu.contains("..") ||
            yakinTelefonu.contains("*")
}

@IgnoreExtraProperties
data class ManuelVefatDto(
    @DocumentId val id: String = "",
    val adSoyad: String = "",
    val dogumTarihi: String = "",
    val dogumYeri: String = "",
    val vefatTarihi: String = "",
    val vefatTarihiIso: String = "",
    val definYeri: String = "",
    val definTarihi: String = "",
    val cenazeYakini: String = "",
    val yakinTelefonu: String = "",
    val taziyeYeri: String = "",
    val taziyeTarihi: String = "",
    val yayinda: Boolean = true,
    val ekleyenUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): VefatKaydi = VefatKaydi(
        id = id,
        adSoyad = adSoyad,
        dogumTarihi = dogumTarihi,
        dogumYeri = dogumYeri,
        vefatTarihi = vefatTarihi,
        vefatTarihiIso = vefatTarihiIso,
        definYeri = definYeri,
        definTarihi = definTarihi,
        cenazeYakini = cenazeYakini,
        yakinTelefonu = yakinTelefonu,
        taziyeYeri = taziyeYeri,
        taziyeTarihi = taziyeTarihi,
        kaynak = KayitKaynagi.MANUEL,
        yayinda = yayinda,
        ekleyenUid = ekleyenUid,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@IgnoreExtraProperties
data class ReklamAyari(
    @DocumentId val id: String = "reklam",
    val aktif: Boolean = true,
    val baslik: String = "Örnek İlan: Batman Taziye İkram Hizmetleri",
    val metin: String = "Taziye yemeği ve ikram hizmeti – Bizi arayın (Yönetici panelinden düzenlenebilir)",
    val gorselUrl: String = "",
    val telefon: String = "",
    val linkUrl: String = "",
    val arkaPlanRengi: String = "#1B4332",
    val guncelleyenUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        val DEFAULT_SAMPLE = ReklamAyari(
            aktif = true,
            baslik = "Örnek İlan: Taziye İkram Hizmetleri",
            metin = "Taziye yemeği ve ikram hizmeti – Bizi arayın (Yönetici panelinden değiştirilebilir)",
            gorselUrl = "",
            telefon = "",
            linkUrl = "",
            arkaPlanRengi = "#1B4332"
        )
    }
}

@IgnoreExtraProperties
data class VefatBildirim(
    @DocumentId val id: String = "",
    val baslik: String = "",
    val mesaj: String = "",
    val adSoyad: String = "",
    val vefatKayitId: String = "",
    val yayinda: Boolean = true,
    val olusturmaMs: Long = 0L,
    val gonderenUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class KullaniciKaydi(
    val uid: String = "",
    val email: String = "",
    val adSoyad: String = "",
    val cihaz: String = "",
    val aktif: Boolean = false,
    val ilkGirisMs: Long = 0L,
    val sonGirisMs: Long = 0L,
    val sonAktifMs: Long = 0L,
    val girisSayisi: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return aktif && sonAktifMs > 0L && (nowMs - sonAktifMs) in 0L..ACTIVE_WINDOW_MS
    }

    companion object {
        const val ACTIVE_WINDOW_MS = 2 * 60 * 1000L
    }
}

@IgnoreExtraProperties
data class GirisLogu(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val adSoyad: String = "",
    val cihaz: String = "",
    val olay: String = "",
    val zamanMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

