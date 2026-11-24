package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BelediyeCacheSnapshot
import com.example.data.BelediyeRefreshOutcome
import com.example.data.LocalSnapshotCache
import com.example.data.VefatRepository
import com.example.model.GirisLogu
import com.example.model.KullaniciKaydi
import com.example.model.ReklamAyari
import com.example.model.VefatBildirim
import com.example.model.VefatKaydi
import com.example.util.TurkishUtils
import com.example.util.friendlyTurkishFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class VefatFiltre(val label: String) {
    BUGUN("Bugün"),
    SON_3_GUN("Son 3 Gün"),
    TUMU("Tümü"),
    TAZIYE_EVINE_GORE("Taziye Evine Göre")
}

data class VefatUiState(
    val yukleniyor: Boolean = true,
    val yenileniyor: Boolean = false,
    val tumKayitlar: List<VefatKaydi> = emptyList(),
    val filtrelenmisKayitlar: List<VefatKaydi> = emptyList(),
    val taziyeEvineGoreGruplar: Map<String, List<VefatKaydi>> = emptyMap(),
    val aramaMetni: String = "",
    val seciliFiltre: VefatFiltre = VefatFiltre.TUMU,
    val reklam: ReklamAyari? = ReklamAyari.DEFAULT_SAMPLE,
    val bildirimler: List<VefatBildirim> = emptyList(),
    val sonGelenBildirim: VefatBildirim? = null,
    val tumKullanicilar: List<KullaniciKaydi> = emptyList(),
    val girisLoglari: List<GirisLogu> = emptyList(),
    val belediyeUyarisi: String? = null,
    val cevrimdisi: Boolean = false,
    val hataMesaji: String? = null,
    val sonGuncellemeMs: Long = 0L,
    val isAdmin: Boolean = false,
    val yoneticiMesaji: String? = null,
    val yoneticiHata: String? = null,
    val yoneticiIslemSuruyor: Boolean = false
) {
    val manuelKayitlar: List<VefatKaydi>
        get() = tumKayitlar.filter { it.kaynak == com.example.model.KayitKaynagi.MANUEL }
}

class VefatViewModel(
    private val repository: VefatRepository,
    private val localCache: LocalSnapshotCache,
    private val currentUserId: String,
    private val currentUserEmail: String?,
    private val currentUserDisplayName: String? = null,
    private val isFreshGoogleSignIn: Boolean = false
) : ViewModel() {

    private val _aramaMetni = MutableStateFlow("")
    private val _seciliFiltre = MutableStateFlow(VefatFiltre.TUMU)
    private val _yerelBelediyeFallback = MutableStateFlow<List<VefatKaydi>>(emptyList())
    private val _yerelReklamFallback = MutableStateFlow<ReklamAyari?>(ReklamAyari.DEFAULT_SAMPLE)
    private val _sonGelenBildirim = MutableStateFlow<VefatBildirim?>(null)
    private val _systemNotificationEvent = MutableStateFlow<VefatBildirim?>(null)
    val systemNotificationEvent: StateFlow<VefatBildirim?> = _systemNotificationEvent.asStateFlow()

    private val _durumMesajlari = MutableStateFlow(StatusFlags())

    private data class StatusFlags(
        val yukleniyor: Boolean = true,
        val yenileniyor: Boolean = false,
        val belediyeUyarisi: String? = null,
        val cevrimdisi: Boolean = false,
        val hataMesaji: String? = null,
        val yoneticiMesaji: String? = null,
        val yoneticiHata: String? = null,
        val yoneticiIslemSuruyor: Boolean = false
    )

    private val isAdminUser: Boolean =
        currentUserEmail != null && currentUserEmail.lowercase() in VefatRepository.ADMIN_EMAILS

    private val manuelFlow = repository.observeManuelVefatlar()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing manuel_vefat", root)
            _durumMesajlari.update { it.copy(hataMesaji = friendlyTurkishFirestoreError(root)) }
            emit(emptyList())
        }

    private val belediyeCacheFlow = repository.observeBelediyeCache()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing cache/belediye", root)
            emit(BelediyeCacheSnapshot())
        }

    private val reklamFlow = repository.observeReklam()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing ayarlar/reklam", root)
            emit(null)
        }

    private val bildirimlerFlow = repository.observeBildirimler()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing bildirimler", root)
            emit(emptyList())
        }

    private val adminKullanicilarFlow = if (isAdminUser) {
        repository.observeTumKullanicilar()
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing kullanicilar", firestoreError ?: error)
                emit(emptyList())
            }
    } else {
        flowOf(emptyList())
    }

    private val adminGirisLoglariFlow = if (isAdminUser) {
        repository.observeGirisLoglari(100L)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing giris_loglari", firestoreError ?: error)
                emit(emptyList())
            }
    } else {
        flowOf(emptyList())
    }

    private val filtreFlow = combine(_aramaMetni, _seciliFiltre) { q, f -> q to f }
    private val bildirimStateFlow = combine(bildirimlerFlow, _sonGelenBildirim) { list, latest ->
        list to latest
    }
    private val adminAnalyticsFlow = combine(adminKullanicilarFlow, adminGirisLoglariFlow) { users, logs ->
        users to logs
    }

    val uiState: StateFlow<VefatUiState> = combine(
        manuelFlow,
        belediyeCacheFlow,
        reklamFlow,
        filtreFlow,
        _durumMesajlari,
        bildirimStateFlow,
        adminAnalyticsFlow
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val manuelList = args[0] as List<VefatKaydi>
        val belediyeCache = args[1] as BelediyeCacheSnapshot
        val remoteReklam = args[2] as ReklamAyari?
        @Suppress("UNCHECKED_CAST")
        val filtrePair = args[3] as Pair<String, VefatFiltre>
        val query = filtrePair.first
        val filtre = filtrePair.second
        val flags = args[4] as StatusFlags
        @Suppress("UNCHECKED_CAST")
        val bildirimPair = args[5] as Pair<List<VefatBildirim>, VefatBildirim?>
        val bildirimList = bildirimPair.first
        val sonBildirim = bildirimPair.second
        @Suppress("UNCHECKED_CAST")
        val analyticsPair = args[6] as Pair<List<KullaniciKaydi>, List<GirisLogu>>
        val kullaniciList = if (isAdminUser) analyticsPair.first else emptyList()
        val girisLogList = if (isAdminUser) analyticsPair.second else emptyList()

        val effectiveBelediye = if (belediyeCache.records.isNotEmpty()) {
            viewModelScope.launch {
                localCache.saveBelediyeRecords(belediyeCache.records, belediyeCache.updatedAtMs)
            }
            belediyeCache.records
        } else {
            _yerelBelediyeFallback.value
        }

        val combined = mergeAndSortRecords(manuelList, effectiveBelediye)
        val filtered = applyFilters(combined, query, filtre)
        val grouped = if (filtre == VefatFiltre.TAZIYE_EVINE_GORE) {
            filtered.groupBy { record ->
                record.taziyeYeri.trim().ifBlank { "Taziye Evi Belirtilmemiş" }
            }
        } else {
            emptyMap()
        }

        val activeReklam = remoteReklam ?: _yerelReklamFallback.value
        if (remoteReklam != null) {
            viewModelScope.launch { localCache.saveReklam(remoteReklam) }
        }

        VefatUiState(
            yukleniyor = flags.yukleniyor && combined.isEmpty(),
            yenileniyor = flags.yenileniyor,
            tumKayitlar = combined,
            filtrelenmisKayitlar = filtered,
            taziyeEvineGoreGruplar = grouped,
            aramaMetni = query,
            seciliFiltre = filtre,
            reklam = activeReklam,
            bildirimler = bildirimList,
            sonGelenBildirim = sonBildirim,
            tumKullanicilar = kullaniciList,
            girisLoglari = girisLogList,
            belediyeUyarisi = flags.belediyeUyarisi,
            cevrimdisi = flags.cevrimdisi,
            hataMesaji = flags.hataMesaji,
            sonGuncellemeMs = belediyeCache.updatedAtMs,
            isAdmin = isAdminUser,
            yoneticiMesaji = flags.yoneticiMesaji,
            yoneticiHata = flags.yoneticiHata,
            yoneticiIslemSuruyor = flags.yoneticiIslemSuruyor
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = VefatUiState(isAdmin = isAdminUser)
    )

    init {
        viewModelScope.launch {
            repository.recordUserSessionAndLoginLog(
                email = currentUserEmail,
                adSoyad = currentUserDisplayName,
                cihaz = VefatRepository.defaultDeviceLabel(),
                olay = if (isFreshGoogleSignIn) {
                    VefatRepository.OLAY_GOOGLE_GIRIS
                } else {
                    VefatRepository.OLAY_OTURUM_ACILDI
                }
            )
        }

        viewModelScope.launch {
            val (cachedRecords, _) = localCache.loadBelediyeRecords()
            if (cachedRecords.isNotEmpty()) {
                _yerelBelediyeFallback.value = cachedRecords
            }
            val cachedReklam = localCache.loadReklam()
            if (cachedReklam != null) {
                _yerelReklamFallback.value = cachedReklam
            }
            refreshBelediye(force = false)
        }

        viewModelScope.launch {
            bildirimlerFlow.collect { list ->
                if (list.isEmpty()) return@collect
                val seenIds = localCache.getSeenBildirimIds()
                val now = System.currentTimeMillis()
                val freshUnseen = list.filter { item ->
                    item.id.isNotBlank() &&
                        item.id !in seenIds &&
                        (now - item.olusturmaMs) in 0L..FIFTEEN_MINUTES_MS
                }
                if (freshUnseen.isNotEmpty()) {
                    val newest = freshUnseen.first()
                    _sonGelenBildirim.value = newest
                    _systemNotificationEvent.value = newest
                }
                localCache.markBildirimlerSeen(list.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } })
            }
        }
    }

    fun setPresence(aktif: Boolean) {
        viewModelScope.launch {
            repository.updateUserPresence(aktif)
        }
    }

    fun markOfflineAndSignOut(onProceedSignOut: () -> Unit) {
        viewModelScope.launch {
            try {
                withTimeoutOrNull(1500L) {
                    repository.updateUserPresence(false)
                }
            } finally {
                onProceedSignOut()
            }
        }
    }

    fun consumeSystemNotificationEvent() {
        _systemNotificationEvent.value = null
    }

    fun dismissIncomingBildirim() {
        _sonGelenBildirim.value = null
    }

    fun setAramaMetni(query: String) {
        _aramaMetni.value = query
    }

    fun setSeciliFiltre(filtre: VefatFiltre) {
        _seciliFiltre.value = filtre
    }

    fun setOfflineStatus(isOffline: Boolean) {
        _durumMesajlari.update { it.copy(cevrimdisi = isOffline) }
    }

    fun refreshBelediye(force: Boolean = true) {
        viewModelScope.launch {
            _durumMesajlari.update { it.copy(yenileniyor = true, hataMesaji = null) }
            try {
                val outcome = repository.refreshBelediyeIfNeeded(force = force)
                val warning = when (outcome) {
                    BelediyeRefreshOutcome.SITE_UNREACHABLE_USING_CACHE ->
                        "Belediye sitesine şu an ulaşılamıyor, son kaydedilen liste gösteriliyor."
                    else -> null
                }
                _durumMesajlari.update {
                    it.copy(
                        yukleniyor = false,
                        yenileniyor = false,
                        belediyeUyarisi = warning
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _durumMesajlari.update {
                    it.copy(
                        yukleniyor = false,
                        yenileniyor = false,
                        belediyeUyarisi = "Belediye sitesine şu an ulaşılamıyor, son kaydedilen liste gösteriliyor.",
                        hataMesaji = friendlyTurkishFirestoreError(e)
                    )
                }
            }
        }
    }

    fun saveManuelVefat(
        kayit: VefatKaydi,
        sendNotification: Boolean = true,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _durumMesajlari.update {
                it.copy(yoneticiIslemSuruyor = true, yoneticiHata = null, yoneticiMesaji = null)
            }
            val isCreate = kayit.id.isBlank()
            val res = if (isCreate) {
                repository.createManuelVefat(kayit)
            } else {
                repository.updateManuelVefat(kayit).map { kayit.id }
            }
            res.fold(
                onSuccess = { savedId ->
                    var notifNotice = ""
                    if (sendNotification) {
                        val message = buildVefatNotificationBody(kayit)
                        val notifRes = repository.sendBildirim(
                            baslik = "Vefat İlanı: ${kayit.adSoyad}",
                            mesaj = message,
                            adSoyad = kayit.adSoyad,
                            vefatKayitId = savedId
                        )
                        notifNotice = if (notifRes.isSuccess) {
                            " ve tüm kullanıcılara bildirim gönderildi."
                        } else {
                            " (kayıt eklendi, bildirim gönderilemedi)."
                        }
                    } else {
                        notifNotice = "."
                    }
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiMesaji = "Vefat kaydı başarıyla kaydedildi$notifNotice"
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiHata = friendlyTurkishFirestoreError(err)
                        )
                    }
                }
            )
        }
    }

    fun sendCustomBildirim(baslik: String, mesaj: String, adSoyad: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _durumMesajlari.update {
                it.copy(yoneticiIslemSuruyor = true, yoneticiHata = null, yoneticiMesaji = null)
            }
            repository.sendBildirim(
                baslik = baslik,
                mesaj = mesaj,
                adSoyad = adSoyad
            ).fold(
                onSuccess = {
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiMesaji = "Bildirim tüm kullanıcılara gönderildi."
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiHata = friendlyTurkishFirestoreError(err)
                        )
                    }
                }
            )
        }
    }

    fun deleteBildirim(bildirimId: String) {
        viewModelScope.launch {
            repository.deleteBildirim(bildirimId).onFailure { err ->
                _durumMesajlari.update {
                    it.copy(yoneticiHata = friendlyTurkishFirestoreError(err))
                }
            }
        }
    }

    fun deleteManuelVefat(recordId: String) {
        viewModelScope.launch {
            _durumMesajlari.update {
                it.copy(yoneticiIslemSuruyor = true, yoneticiHata = null, yoneticiMesaji = null)
            }
            repository.deleteManuelVefat(recordId).fold(
                onSuccess = {
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiMesaji = "Kayıt silindi."
                        )
                    }
                },
                onFailure = { err ->
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiHata = friendlyTurkishFirestoreError(err)
                        )
                    }
                }
            )
        }
    }

    fun saveReklam(reklam: ReklamAyari) {
        viewModelScope.launch {
            _durumMesajlari.update {
                it.copy(yoneticiIslemSuruyor = true, yoneticiHata = null, yoneticiMesaji = null)
            }
            repository.saveReklam(reklam).fold(
                onSuccess = {
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiMesaji = if (reklam.aktif) "Reklam yayınlandı." else "Reklam kapatıldı."
                        )
                    }
                },
                onFailure = { err ->
                    _durumMesajlari.update {
                        it.copy(
                            yoneticiIslemSuruyor = false,
                            yoneticiHata = friendlyTurkishFirestoreError(err)
                        )
                    }
                }
            )
        }
    }

    fun clearYoneticiStatus() {
        _durumMesajlari.update { it.copy(yoneticiHata = null, yoneticiMesaji = null) }
    }

    private fun buildVefatNotificationBody(kayit: VefatKaydi): String {
        val parts = mutableListOf("${kayit.adSoyad} vefat etti.")
        if (kayit.definYeri.isNotBlank()) parts.add("Defin: ${kayit.definYeri}")
        if (kayit.taziyeYeri.isNotBlank()) parts.add("Taziye: ${kayit.taziyeYeri}")
        return parts.joinToString(" ")
    }

    private fun mergeAndSortRecords(
        manuelList: List<VefatKaydi>,
        belediyeList: List<VefatKaydi>
    ): List<VefatKaydi> {
        val dedup = LinkedHashMap<String, VefatKaydi>()
        for (item in manuelList + belediyeList) {
            val key = "${TurkishUtils.normalizeForSearch(item.adSoyad)}|${item.dogumTarihi.trim()}|${item.vefatTarihi.trim()}"
            if (!dedup.containsKey(key)) {
                dedup[key] = item
            }
        }
        return dedup.values.sortedWith(
            compareByDescending<VefatKaydi> { it.vefatTarihiIso }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
        )
    }

    private fun applyFilters(
        records: List<VefatKaydi>,
        query: String,
        filtre: VefatFiltre
    ): List<VefatKaydi> {
        val normalizedQuery = TurkishUtils.normalizeForSearch(query)
        return records.filter { item ->
            val matchesTime = when (filtre) {
                VefatFiltre.BUGUN -> TurkishUtils.isWithinDays(item.vefatTarihiIso, 0)
                VefatFiltre.SON_3_GUN -> TurkishUtils.isWithinDays(item.vefatTarihiIso, 2)
                VefatFiltre.TUMU, VefatFiltre.TAZIYE_EVINE_GORE -> true
            }
            if (!matchesTime) return@filter false

            if (normalizedQuery.isBlank()) return@filter true

            val searchable = listOf(
                item.adSoyad,
                item.taziyeYeri,
                item.dogumYeri,
                item.definYeri,
                item.cenazeYakini
            ).joinToString(" ") { TurkishUtils.normalizeForSearch(it) }
            searchable.contains(normalizedQuery)
        }
    }

    private companion object {
        const val TAG = "VefatViewModel"
        const val FIFTEEN_MINUTES_MS = 15 * 60 * 1000L
    }
}
