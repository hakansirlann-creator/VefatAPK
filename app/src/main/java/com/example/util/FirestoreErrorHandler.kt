package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyTurkishFirestoreError(exception: Throwable): String {
    val firestoreException = generateSequence(exception) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreException?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Bu işlem için yönetici yetkisi gerekiyor veya oturumunuz doğrulanamadı."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "İnternet bağlantısı kurulamadı. Lütfen bağlantınızı kontrol edin."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Günlük ücretsiz veritabanı kullanım sınırına ulaşıldı."
        else -> {
            val msg = exception.localizedMessage.orEmpty()
            if (msg.contains("network", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true)) {
                "İnternet bağlantısı yok. Çevrimdışı veriler gösteriliyor."
            } else {
                "İşlem sırasında bir hata oluştu. Lütfen tekrar deneyin."
            }
        }
    }
}
