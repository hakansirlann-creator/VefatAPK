package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.KayitKaynagi
import com.example.model.ReklamAyari
import com.example.model.VefatKaydi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.batmanDataStore: DataStore<Preferences> by preferencesDataStore(name = "batman_vefat_cache")

class LocalSnapshotCache(private val context: Context) {

    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")
    private val keyLastBelediyeJson = stringPreferencesKey("last_belediye_json")
    private val keyLastBelediyeSyncMs = longPreferencesKey("last_belediye_sync_ms")
    private val keyLastReklamJson = stringPreferencesKey("last_reklam_json")
    private val keySeenBildirimIds = stringSetPreferencesKey("seen_bildirim_ids")

    val onboardingCompleted: Flow<Boolean> = context.batmanDataStore.data.map { prefs ->
        prefs[keyOnboardingDone] ?: false
    }

    suspend fun getSeenBildirimIds(): Set<String> {
        val prefs = context.batmanDataStore.data.first()
        return prefs[keySeenBildirimIds] ?: emptySet()
    }

    suspend fun markBildirimlerSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.batmanDataStore.edit { prefs ->
            val current = prefs[keySeenBildirimIds] ?: emptySet()
            val merged = (current + ids).toList()
            prefs[keySeenBildirimIds] = (if (merged.size > 150) merged.subList(merged.size - 150, merged.size) else merged).toSet()
        }
    }

    suspend fun setOnboardingCompleted() {
        context.batmanDataStore.edit { prefs ->
            prefs[keyOnboardingDone] = true
        }
    }

    suspend fun saveBelediyeRecords(records: List<VefatKaydi>, updatedAtMs: Long) {
        val arr = JSONArray()
        for (item in records) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("adSoyad", item.adSoyad)
                    put("dogumTarihi", item.dogumTarihi)
                    put("dogumYeri", item.dogumYeri)
                    put("vefatTarihi", item.vefatTarihi)
                    put("vefatTarihiIso", item.vefatTarihiIso)
                    put("definYeri", item.definYeri)
                    put("definTarihi", item.definTarihi)
                    put("cenazeYakini", item.cenazeYakini)
                    put("yakinTelefonu", item.yakinTelefonu)
                    put("taziyeYeri", item.taziyeYeri)
                    put("taziyeTarihi", item.taziyeTarihi)
                }
            )
        }
        context.batmanDataStore.edit { prefs ->
            prefs[keyLastBelediyeJson] = arr.toString()
            prefs[keyLastBelediyeSyncMs] = updatedAtMs
        }
    }

    suspend fun loadBelediyeRecords(): Pair<List<VefatKaydi>, Long> {
        val prefs = context.batmanDataStore.data.first()
        val raw = prefs[keyLastBelediyeJson] ?: return emptyList<VefatKaydi>() to 0L
        val syncMs = prefs[keyLastBelediyeSyncMs] ?: 0L
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<VefatKaydi>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VefatKaydi(
                        id = obj.optString("id"),
                        adSoyad = obj.optString("adSoyad"),
                        dogumTarihi = obj.optString("dogumTarihi"),
                        dogumYeri = obj.optString("dogumYeri"),
                        vefatTarihi = obj.optString("vefatTarihi"),
                        vefatTarihiIso = obj.optString("vefatTarihiIso"),
                        definYeri = obj.optString("definYeri"),
                        definTarihi = obj.optString("definTarihi"),
                        cenazeYakini = obj.optString("cenazeYakini"),
                        yakinTelefonu = obj.optString("yakinTelefonu"),
                        taziyeYeri = obj.optString("taziyeYeri"),
                        taziyeTarihi = obj.optString("taziyeTarihi"),
                        kaynak = KayitKaynagi.BELEDIYE
                    )
                )
            }
            list to syncMs
        } catch (_: Exception) {
            emptyList<VefatKaydi>() to 0L
        }
    }

    suspend fun saveReklam(reklam: ReklamAyari) {
        val json = JSONObject().apply {
            put("aktif", reklam.aktif)
            put("baslik", reklam.baslik)
            put("metin", reklam.metin)
            put("gorselUrl", reklam.gorselUrl)
            put("telefon", reklam.telefon)
            put("linkUrl", reklam.linkUrl)
            put("arkaPlanRengi", reklam.arkaPlanRengi)
        }
        context.batmanDataStore.edit { prefs ->
            prefs[keyLastReklamJson] = json.toString()
        }
    }

    suspend fun loadReklam(): ReklamAyari? {
        val prefs = context.batmanDataStore.data.first()
        val raw = prefs[keyLastReklamJson] ?: return null
        return try {
            val obj = JSONObject(raw)
            ReklamAyari(
                aktif = obj.optBoolean("aktif", true),
                baslik = obj.optString("baslik"),
                metin = obj.optString("metin"),
                gorselUrl = obj.optString("gorselUrl"),
                telefon = obj.optString("telefon"),
                linkUrl = obj.optString("linkUrl"),
                arkaPlanRengi = obj.optString("arkaPlanRengi", "#1B4332")
            )
        } catch (_: Exception) {
            null
        }
    }
}
