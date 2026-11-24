package com.example.data

import android.content.Context
import com.example.R
import com.example.model.GirisLogu
import com.example.model.KayitKaynagi
import com.example.model.KullaniciKaydi
import com.example.model.ManuelVefatDto
import com.example.model.ReklamAyari
import com.example.model.VefatBildirim
import com.example.model.VefatKaydi
import com.google.firebase.firestore.Query
import com.example.util.OperationType
import com.example.util.TurkishUtils
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class BelediyeCacheSnapshot(
    val records: List<VefatKaydi> = emptyList(),
    val updatedAtMs: Long = 0L
)

enum class BelediyeRefreshOutcome {
    FRESH_FROM_SITE,
    CACHED_WITHIN_FIVE_MINUTES,
    SITE_UNREACHABLE_USING_CACHE
}

class VefatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val scraper: BelediyeScraper = BelediyeScraper()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Firestore erişimi için Google hesabı ile giriş yapılmalıdır.")
    }

    fun observeManuelVefatlar(): Flow<List<VefatKaydi>> {
        val path = COLLECTION_MANUEL
        return db.collection(COLLECTION_MANUEL)
            .whereEqualTo("yayinda", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ManuelVefatDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomain() }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser != null) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeBelediyeCache(): Flow<BelediyeCacheSnapshot> {
        val docRef = db.collection(COLLECTION_CACHE).document(DOC_BELEDIYE)
        return docRef.snapshots()
            .map { snapshot -> parseBelediyeCacheSnapshot(snapshot) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser != null) {
                    handleFirestoreError(toLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    fun observeReklam(): Flow<ReklamAyari?> {
        val docRef = db.collection(COLLECTION_AYARLAR).document(DOC_REKLAM)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    ReklamAyari::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser != null) {
                    handleFirestoreError(toLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun refreshBelediyeIfNeeded(force: Boolean = false): BelediyeRefreshOutcome {
        val uid = requireUserId()
        val cacheDocRef = db.collection(COLLECTION_CACHE).document(DOC_BELEDIYE)
        val nowMs = System.currentTimeMillis()

        val existingCache = try {
            val snap = cacheDocRef.get().await()
            parseBelediyeCacheSnapshot(snap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, cacheDocRef.path)
            BelediyeCacheSnapshot()
        }

        val isFresh = existingCache.records.isNotEmpty() &&
            (nowMs - existingCache.updatedAtMs) in 0 until FIVE_MINUTES_MS
        if (!force && isFresh) {
            return BelediyeRefreshOutcome.CACHED_WITHIN_FIVE_MINUTES
        }

        val scrapedResult = scraper.fetchLatestRecords()
        return if (scrapedResult.isSuccess) {
            val records = scrapedResult.getOrDefault(emptyList()).take(100)
            val payloadMaps = records.map { item ->
                mapOf(
                    "id" to item.id,
                    "adSoyad" to item.adSoyad.take(120),
                    "dogumTarihi" to item.dogumTarihi.take(30),
                    "dogumYeri" to item.dogumYeri.take(80),
                    "vefatTarihi" to item.vefatTarihi.take(20),
                    "vefatTarihiIso" to item.vefatTarihiIso.take(20),
                    "definYeri" to item.definYeri.take(120),
                    "definTarihi" to item.definTarihi.take(40),
                    "cenazeYakini" to item.cenazeYakini.take(100),
                    "yakinTelefonu" to item.yakinTelefonu.take(30),
                    "taziyeYeri" to item.taziyeYeri.take(120),
                    "taziyeTarihi" to item.taziyeTarihi.take(60)
                )
            }
            val cachePayload = mapOf(
                "kayitlar" to payloadMaps,
                "guncellenmeZamaniMs" to nowMs,
                "guncelleyenUid" to uid,
                "kaynak" to "batman.bel.tr",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                cacheDocRef.set(cachePayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, cacheDocRef.path)
            }
            BelediyeRefreshOutcome.FRESH_FROM_SITE
        } else {
            BelediyeRefreshOutcome.SITE_UNREACHABLE_USING_CACHE
        }
    }

    suspend fun createManuelVefat(input: VefatKaydi): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection(COLLECTION_MANUEL).document()
        val formattedVefat = TurkishUtils.formatDateDdMmYyyy(input.vefatTarihi)
            .ifBlank { TurkishUtils.todayDdMmYyyy() }
        val payload = mapOf(
            "adSoyad" to TurkishUtils.sanitizeText(input.adSoyad).take(120),
            "dogumTarihi" to TurkishUtils.sanitizeText(input.dogumTarihi).take(30),
            "dogumYeri" to TurkishUtils.sanitizeText(input.dogumYeri).take(80),
            "vefatTarihi" to formattedVefat.take(20),
            "vefatTarihiIso" to TurkishUtils.toIsoDate(formattedVefat).take(20),
            "definYeri" to TurkishUtils.sanitizeText(input.definYeri).take(120),
            "definTarihi" to TurkishUtils.formatDateDdMmYyyy(input.definTarihi).take(40),
            "cenazeYakini" to TurkishUtils.sanitizeText(input.cenazeYakini).take(100),
            "yakinTelefonu" to TurkishUtils.sanitizeText(input.yakinTelefonu).take(30),
            "taziyeYeri" to TurkishUtils.sanitizeText(input.taziyeYeri).take(120),
            "taziyeTarihi" to TurkishUtils.sanitizeText(input.taziyeTarihi).take(60),
            "yayinda" to true,
            "ekleyenUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateManuelVefat(existing: VefatKaydi): Result<Unit> {
        requireUserId()
        require(existing.id.isNotBlank()) { "Güncellenecek kayıt kimliği boş olamaz." }
        val docRef = db.collection(COLLECTION_MANUEL).document(existing.id)
        val formattedVefat = TurkishUtils.formatDateDdMmYyyy(existing.vefatTarihi)
            .ifBlank { TurkishUtils.todayDdMmYyyy() }
        val updates = mapOf(
            "adSoyad" to TurkishUtils.sanitizeText(existing.adSoyad).take(120),
            "dogumTarihi" to TurkishUtils.sanitizeText(existing.dogumTarihi).take(30),
            "dogumYeri" to TurkishUtils.sanitizeText(existing.dogumYeri).take(80),
            "vefatTarihi" to formattedVefat.take(20),
            "vefatTarihiIso" to TurkishUtils.toIsoDate(formattedVefat).take(20),
            "definYeri" to TurkishUtils.sanitizeText(existing.definYeri).take(120),
            "definTarihi" to TurkishUtils.formatDateDdMmYyyy(existing.definTarihi).take(40),
            "cenazeYakini" to TurkishUtils.sanitizeText(existing.cenazeYakini).take(100),
            "yakinTelefonu" to TurkishUtils.sanitizeText(existing.yakinTelefonu).take(30),
            "taziyeYeri" to TurkishUtils.sanitizeText(existing.taziyeYeri).take(120),
            "taziyeTarihi" to TurkishUtils.sanitizeText(existing.taziyeTarihi).take(60),
            "yayinda" to existing.yayinda,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteManuelVefat(recordId: String): Result<Unit> {
        requireUserId()
        require(recordId.isNotBlank()) { "Silinecek kayıt kimliği boş olamaz." }
        val docRef = db.collection(COLLECTION_MANUEL).document(recordId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveReklam(reklam: ReklamAyari): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection(COLLECTION_AYARLAR).document(DOC_REKLAM)
        val payload = mapOf(
            "aktif" to reklam.aktif,
            "baslik" to TurkishUtils.sanitizeText(reklam.baslik).take(100),
            "metin" to TurkishUtils.sanitizeText(reklam.metin).take(240),
            "gorselUrl" to reklam.gorselUrl.trim().take(500),
            "telefon" to TurkishUtils.sanitizeText(reklam.telefon).take(30),
            "linkUrl" to reklam.linkUrl.trim().take(500),
            "arkaPlanRengi" to reklam.arkaPlanRengi.trim().ifBlank { "#1B4332" }.take(20),
            "guncelleyenUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeBildirimler(): Flow<List<VefatBildirim>> {
        val path = COLLECTION_BILDIRIMLER
        return db.collection(COLLECTION_BILDIRIMLER)
            .whereEqualTo("yayinda", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VefatBildirim::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.olusturmaMs }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser != null) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun sendBildirim(
        baslik: String,
        mesaj: String,
        adSoyad: String,
        vefatKayitId: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection(COLLECTION_BILDIRIMLER).document()
        val payload = buildMap<String, Any> {
            put("baslik", TurkishUtils.sanitizeText(baslik).ifBlank { "Yeni Vefat İlanı" }.take(120))
            put("mesaj", TurkishUtils.sanitizeText(mesaj).take(300))
            put("adSoyad", TurkishUtils.sanitizeText(adSoyad).take(120))
            if (vefatKayitId.isNotBlank()) {
                put("vefatKayitId", vefatKayitId.take(128))
            }
            put("yayinda", true)
            put("olusturmaMs", System.currentTimeMillis())
            put("gonderenUid", uid)
            put("createdAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBildirim(bildirimId: String): Result<Unit> {
        requireUserId()
        require(bildirimId.isNotBlank()) { "Silinecek bildirim kimliği boş olamaz." }
        val docRef = db.collection(COLLECTION_BILDIRIMLER).document(bildirimId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordUserSessionAndLoginLog(
        email: String?,
        adSoyad: String?,
        cihaz: String,
        olay: String = OLAY_OTURUM_ACILDI
    ): Result<Unit> {
        val uid = requireUserId()
        val nowMs = System.currentTimeMillis()
        val safeEmail = TurkishUtils.sanitizeText(email.orEmpty()).take(160)
        val safeName = TurkishUtils.sanitizeText(adSoyad.orEmpty()).take(120)
        val safeDevice = TurkishUtils.sanitizeText(cihaz).ifBlank { "Android Cihaz" }.take(80)
        val safeEvent = TurkishUtils.sanitizeText(olay).ifBlank { OLAY_OTURUM_ACILDI }.take(40)

        val userDocRef = db.collection(COLLECTION_KULLANICILAR).document(uid)
        val logDocRef = db.collection(COLLECTION_GIRIS_LOGLARI).document()

        return try {
            val existing = userDocRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "uid" to uid,
                    "email" to safeEmail,
                    "adSoyad" to safeName,
                    "cihaz" to safeDevice,
                    "aktif" to true,
                    "ilkGirisMs" to nowMs,
                    "sonGirisMs" to nowMs,
                    "sonAktifMs" to nowMs,
                    "girisSayisi" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "email" to safeEmail,
                    "adSoyad" to safeName,
                    "cihaz" to safeDevice,
                    "aktif" to true,
                    "sonGirisMs" to nowMs,
                    "sonAktifMs" to nowMs,
                    "girisSayisi" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.update(updatePayload).await()
            }

            val logPayload = mapOf(
                "uid" to uid,
                "email" to safeEmail,
                "adSoyad" to safeName,
                "cihaz" to safeDevice,
                "olay" to safeEvent,
                "zamanMs" to nowMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
            logDocRef.set(logPayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDocRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPresence(aktif: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val userDocRef = db.collection(COLLECTION_KULLANICILAR).document(uid)
        val nowMs = System.currentTimeMillis()
        return try {
            val existing = userDocRef.get().await()
            if (!existing.exists()) {
                val user = auth.currentUser
                val createPayload = mapOf(
                    "uid" to uid,
                    "email" to TurkishUtils.sanitizeText(user?.email.orEmpty()).take(160),
                    "adSoyad" to TurkishUtils.sanitizeText(user?.displayName.orEmpty()).take(120),
                    "cihaz" to defaultDeviceLabel(),
                    "aktif" to aktif,
                    "ilkGirisMs" to nowMs,
                    "sonGirisMs" to nowMs,
                    "sonAktifMs" to nowMs,
                    "girisSayisi" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(createPayload).await()
            } else {
                val updates = mapOf(
                    "aktif" to aktif,
                    "sonAktifMs" to nowMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDocRef.path)
            Result.failure(e)
        }
    }

    fun observeTumKullanicilar(): Flow<List<KullaniciKaydi>> {
        val path = COLLECTION_KULLANICILAR
        return db.collection(COLLECTION_KULLANICILAR)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    KullaniciKaydi::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.sonAktifMs }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser != null) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeGirisLoglari(limitCount: Long = 100L): Flow<List<GirisLogu>> {
        val path = COLLECTION_GIRIS_LOGLARI
        return db.collection(COLLECTION_GIRIS_LOGLARI)
            .orderBy("zamanMs", Query.Direction.DESCENDING)
            .limit(limitCount)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GirisLogu::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser != null) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    private fun parseBelediyeCacheSnapshot(snapshot: DocumentSnapshot): BelediyeCacheSnapshot {
        if (!snapshot.exists()) return BelediyeCacheSnapshot()
        val updatedAtMs = snapshot.getLong("guncellenmeZamaniMs") ?: 0L
        @Suppress("UNCHECKED_CAST")
        val rawList = snapshot.get("kayitlar") as? List<Map<String, Any?>> ?: emptyList()
        val records = rawList.mapNotNull { row ->
            val adSoyad = (row["adSoyad"] as? String).orEmpty().trim()
            if (adSoyad.isBlank()) return@mapNotNull null
            val vefatTarihi = TurkishUtils.formatDateDdMmYyyy((row["vefatTarihi"] as? String).orEmpty())
            VefatKaydi(
                id = (row["id"] as? String).orEmpty().ifBlank { "bel_${adSoyad.hashCode()}" },
                adSoyad = adSoyad,
                dogumTarihi = (row["dogumTarihi"] as? String).orEmpty(),
                dogumYeri = (row["dogumYeri"] as? String).orEmpty(),
                vefatTarihi = vefatTarihi,
                vefatTarihiIso = (row["vefatTarihiIso"] as? String).orEmpty()
                    .ifBlank { TurkishUtils.toIsoDate(vefatTarihi) },
                definYeri = (row["definYeri"] as? String).orEmpty(),
                definTarihi = TurkishUtils.formatDateDdMmYyyy((row["definTarihi"] as? String).orEmpty()),
                cenazeYakini = (row["cenazeYakini"] as? String).orEmpty(),
                yakinTelefonu = (row["yakinTelefonu"] as? String).orEmpty(),
                taziyeYeri = (row["taziyeYeri"] as? String).orEmpty(),
                taziyeTarihi = (row["taziyeTarihi"] as? String).orEmpty(),
                kaynak = KayitKaynagi.BELEDIYE
            )
        }
        return BelediyeCacheSnapshot(records = records, updatedAtMs = updatedAtMs)
    }

    companion object {
        const val COLLECTION_MANUEL = "manuel_vefat"
        const val COLLECTION_CACHE = "cache"
        const val DOC_BELEDIYE = "belediye"
        const val COLLECTION_AYARLAR = "ayarlar"
        const val DOC_REKLAM = "reklam"
        const val COLLECTION_BILDIRIMLER = "bildirimler"
        const val COLLECTION_KULLANICILAR = "kullanicilar"
        const val COLLECTION_GIRIS_LOGLARI = "giris_loglari"
        const val OLAY_GOOGLE_GIRIS = "GOOGLE_GIRIS"
        const val OLAY_OTURUM_ACILDI = "OTURUM_ACILDI"
        const val FIVE_MINUTES_MS = 5 * 60 * 1000L
        val ADMIN_EMAILS = setOf("hakansirlann@gmail.com", "hakan@batmanvefat.app")

        fun defaultDeviceLabel(): String {
            val manu = android.os.Build.MANUFACTURER.orEmpty().trim()
            val model = android.os.Build.MODEL.orEmpty().trim()
            val combined = when {
                manu.isBlank() && model.isBlank() -> "Android Cihaz"
                model.startsWith(manu, ignoreCase = true) -> model
                else -> "$manu $model".trim()
            }
            return combined.take(80)
        }
    }
}
