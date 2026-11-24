# Batman Vefat

Geliştirici: **Hakan Sırlan**  
Platform: Android (Kotlin & Jetpack Compose + Firebase Firestore & Authentication)

## Özellikler
- **Otomatik Belediye Verisi ve 5 Dakikalık Önbellek:** Batman Belediyesi (`batman.bel.tr`) vefat ve taziye sayfasındaki son 100 kayıt ve modal detayları ayrıştırılır; 5 dakikadan yeni ise Firestore `cache/belediye` önbelleğinden okunur.
- **Anlık Senkronizasyon ve Bildirimler:** Yönetici tarafından eklenen kayıtlar (`manuel_vefat`), reklam alanı (`ayarlar/reklam`) ve anlık vefat bildirimleri (`bildirimler`) `onSnapshot` dinleyicileriyle uygulamayı açık tutan kullanıcılara anında iletilir ve sistem bildirimi olarak gösterilir. *(Uygulama tamamen kapalıyken uzak push bildirimleri için Firebase Console'da FCM ve Cloud Functions yapılandırılması gerekir.)*
- **Özel Yönetici Paneli:** Alt gezinme çubuğundan ve üst başlıktan doğrudan erişilebilen 5 sekmeli yönetici paneli (*Vefat Ekle*, *Eklediklerim*, *Bildirim Gönder*, *Reklam*, *Kullanıcılar*).
- **Yöneticiye Özel Kurulum, Anlık Aktiflik ve Giriş Logları:** Toplam kayıtlı Google hesabı sayısı, anlık çevrimiçi kullanıcı listesi ve Google giriş log kayıtları (`kullanicilar`, `giris_loglari`) yalnızca yönetici hesabına özel sekmede görüntülenir.
- **Türkçe Karakter Duyarlı Arama ve Filtreler:** `İ/ı`, `Ş`, `Ğ`, `Ü`, `Ö`, `Ç` farklarını yok sayarak arama; *Bugün*, *Son 3 Gün*, *Tümü* ve *Taziye Evine Göre* görünümleri.
- **Yol Tarifi, Arama ve WhatsApp:** API anahtarı gerektirmeyen Google Haritalar bağlantıları, maskelenmiş telefon numarası koruması, düzenlenebilir 3 farklı taziye mesajı şablonu ve paylaşım desteği.
- **Çevrimdışı Dayanıklılık:** Son görülen liste yerel DataStore önbelleğinde saklanır; çevrimdışıyken veya belediye sitesine ulaşılamadığında uyarı şeridiyle gösterilir.

## 1. Firebase Kurulumu
1. Bu projede Firebase Firestore ve Google ile Giriş (`CredentialManager`) yapılandırması `app/google-services.json` ve `app/src/main/res/values/firebase_applet_config.xml` üzerinden bağlıdır.
2. Güvenlik kuralları proje kökündeki `firestore.rules` dosyasında tanımlıdır:
   - Tüm kimliği doğrulanmış kullanıcılar vefat ilanlarını (`yayinda == true`), bildirimleri (`bildirimler`), önbelleği (`cache/belediye`) ve reklamı (`ayarlar/reklam`) okuyabilir.
   - `manuel_vefat`, `bildirimler` ve `ayarlar/reklam` yazma/silme işlemleri yalnızca doğrulanmış yönetici hesabına (`hakansirlann@gmail.com`) açıktır.
   - `kullanicilar` listelemesi ve `giris_loglari` okuması yalnızca yönetici hesabına açıktır; kullanıcılar yalnızca kendi oturum/aktiflik kayıtlarını güncelleyebilir.
3. Kuralları yerel emülatörde doğrulamak için:
   - `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
   - `gradle :app:testDebugUnitTest --tests '*Rule*'`

## 2. Derleme ve Dağıtım (Deploy)
- **Debug APK oluşturma:** `gradle assembleDebug`
- **Release APK / AAB oluşturma:** AI Studio menüsünden APK/AAB dışa aktarımını veya `gradle bundleRelease` komutunu kullanabilirsiniz.
- **Firestore kurallarını yayınlama:** `firebase deploy --only firestore:rules`

## 3. Ana Ekrana Ekleme (Android)
1. Uygulamayı cihazınıza yükledikten sonra uygulama çekmecesindeki **Batman Vefat** (yeşil zemin üzerinde beyaz güvercin) simgesine basılı tutun.
2. **Ana ekrana ekle** veya simgeyi sürükleyerek telefonunuzun ana ekranına bırakın.
3. İlk açılışta yer alan **"Bildirimlere ve konuma izin ver"** düğmesiyle hızlıca izinleri onaylayabilir veya izin vermeden doğrudan Google Haritalar yönlendirmesiyle kullanmaya devam edebilirsiniz.

---
*Kaynak: Batman Belediyesi (batman.bel.tr). Bu uygulama resmî bir belediye uygulaması değildir.*
