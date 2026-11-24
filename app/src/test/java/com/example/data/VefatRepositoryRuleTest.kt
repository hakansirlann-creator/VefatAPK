package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.KayitKaynagi
import com.example.model.ReklamAyari
import com.example.model.VefatKaydi
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VefatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun observeManuelVefatlar_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = VefatRepository(firestore, auth)

        val ex = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeManuelVefatlar().first()
            }
            null
        } catch (e: Exception) {
            e
        }
        val firestoreEx = checkNotNull(ex?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $ex"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun nonAdminUser_cannotCreateManuelVefatOrUpdateReklam_butCanRead() = runBlocking {
        signInTestUser(READER_EMAIL, verifyEmail = true)
        val repo = VefatRepository(firestore, auth)

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createManuelVefat(
                VefatKaydi(
                    id = "",
                    adSoyad = "Yetkisiz Deneme",
                    vefatTarihi = "15.11.2026",
                    vefatTarihiIso = "2026-11-15",
                    kaynak = KayitKaynagi.MANUEL
                )
            )
        }
        assertTrue(createRes.isFailure)
        val createEx = createRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, createEx?.code)

        val adRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveReklam(ReklamAyari.DEFAULT_SAMPLE)
        }
        assertTrue(adRes.isFailure)
        val adEx = adRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, adEx?.code)

        val notifRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendBildirim(
                baslik = "Yetkisiz Bildirim",
                mesaj = "Deneme",
                adSoyad = "Test"
            )
        }
        assertTrue(notifRes.isFailure)
        val notifEx = notifRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, notifEx?.code)

        val list = withTimeout(FLOW_TIMEOUT_MS) { repo.observeManuelVefatlar().first() }
        assertTrue(list.size >= 0)

        val bildirimler = withTimeout(FLOW_TIMEOUT_MS) { repo.observeBildirimler().first() }
        assertTrue(bildirimler.size >= 0)

        // Non-admin CAN record own session/login log & update own presence
        val sessionRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordUserSessionAndLoginLog(
                email = READER_EMAIL,
                adSoyad = "Okuyucu Test",
                cihaz = "Test Phone",
                olay = VefatRepository.OLAY_GOOGLE_GIRIS
            )
        }
        assertTrue("Expected reader session log to succeed: ${sessionRes.exceptionOrNull()}", sessionRes.isSuccess)

        val presenceRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.updateUserPresence(false) }
        assertTrue("Expected reader presence update to succeed: ${presenceRes.exceptionOrNull()}", presenceRes.isSuccess)

        // Non-admin CANNOT list users or read login logs (hidden for admin only)
        val usersListEx = try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeTumKullanicilar().first() }
            null
        } catch (e: Exception) {
            e
        }
        val usersFirestoreEx = checkNotNull(usersListEx?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED when non-admin lists users, got $usersListEx"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, usersFirestoreEx.code)

        val logsListEx = try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeGirisLoglari().first() }
            null
        } catch (e: Exception) {
            e
        }
        val logsFirestoreEx = checkNotNull(logsListEx?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED when non-admin reads login logs, got $logsListEx"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, logsFirestoreEx.code)
    }

    @Test
    fun verifiedAdmin_canCreateUpdateObserveAndDeleteManuelVefat_andSaveReklam_andSendBildirim_andViewUsersLogs() = runBlocking {
        signInTestUser(READER_EMAIL, verifyEmail = true)
        val readerRepo = VefatRepository(firestore, auth)
        val readerSessionRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            readerRepo.recordUserSessionAndLoginLog(
                email = READER_EMAIL,
                adSoyad = "Okuyucu Kullanıcı",
                cihaz = "Pixel 9",
                olay = VefatRepository.OLAY_GOOGLE_GIRIS
            )
        }
        assertTrue("Expected readerSessionRes to succeed: ${readerSessionRes.exceptionOrNull()}", readerSessionRes.isSuccess)

        signInTestUser(ADMIN_EMAIL, verifyEmail = true)
        val repo = VefatRepository(firestore, auth)

        val allUsers = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeTumKullanicilar().first { list -> list.any { it.email == READER_EMAIL } }
        }
        val readerUser = allUsers.first { it.email == READER_EMAIL }
        assertTrue(readerUser.aktif)
        assertTrue(readerUser.girisSayisi >= 1L)

        val loginLogs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeGirisLoglari().first { logs ->
                logs.any { it.email == READER_EMAIL && it.olay == VefatRepository.OLAY_GOOGLE_GIRIS }
            }
        }
        assertTrue(loginLogs.isNotEmpty())

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createManuelVefat(
                VefatKaydi(
                    id = "",
                    adSoyad = "Hasan YILDIZ",
                    dogumTarihi = "1945",
                    dogumYeri = "Batman",
                    vefatTarihi = "15.11.2026",
                    vefatTarihiIso = "2026-11-15",
                    definYeri = "Asri Mezarlık",
                    definTarihi = "15.11.2026",
                    cenazeYakini = "Kemal YILDIZ",
                    yakinTelefonu = "05321112233",
                    taziyeYeri = "Kültür Taziye Evi",
                    taziyeTarihi = "15-17.11.2026",
                    kaynak = KayitKaynagi.MANUEL
                )
            )
        }
        assertTrue("Expected admin create to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val createdId = createRes.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeManuelVefatlar().first { items -> items.any { it.id == createdId } }
        }
        val createdItem = observed.first { it.id == createdId }
        assertEquals("Hasan YILDIZ", createdItem.adSoyad)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateManuelVefat(createdItem.copy(taziyeYeri = "Güncel Taziye Evi"))
        }
        assertTrue("Expected admin update to succeed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

        val adRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveReklam(
                ReklamAyari(
                    aktif = true,
                    baslik = "Taziye Yemeği Hizmeti",
                    metin = "Sıcak taziye yemeği ve ikram hizmeti",
                    arkaPlanRengi = "#1B4332",
                    telefon = "04882120000"
                )
            )
        }
        assertTrue("Expected admin reklam save to succeed: ${adRes.exceptionOrNull()}", adRes.isSuccess)

        val bildirimRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendBildirim(
                baslik = "Vefat İlanı: Hasan YILDIZ",
                mesaj = "Hasan YILDIZ vefat etti.",
                adSoyad = "Hasan YILDIZ",
                vefatKayitId = createdId
            )
        }
        assertTrue("Expected admin bildirim send to succeed: ${bildirimRes.exceptionOrNull()}", bildirimRes.isSuccess)
        val bildirimId = bildirimRes.getOrThrow()

        val observedBildirimler = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeBildirimler().first { items -> items.any { it.id == bildirimId } }
        }
        assertTrue(observedBildirimler.any { it.id == bildirimId && it.adSoyad == "Hasan YILDIZ" })

        val delBildirimRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteBildirim(bildirimId)
        }
        assertTrue("Expected admin bildirim delete to succeed: ${delBildirimRes.exceptionOrNull()}", delBildirimRes.isSuccess)

        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteManuelVefat(createdId)
        }
        assertTrue("Expected admin delete to succeed: ${deleteRes.exceptionOrNull()}", deleteRes.isSuccess)
    }

    private companion object {
        const val ADMIN_EMAIL = "hakansirlann@gmail.com"
        const val READER_EMAIL = "okuyucu@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
