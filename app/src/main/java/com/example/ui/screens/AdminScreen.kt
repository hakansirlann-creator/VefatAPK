package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.AuthHelper
import com.example.model.GirisLogu
import com.example.model.KayitKaynagi
import com.example.model.KullaniciKaydi
import com.example.model.ReklamAyari
import com.example.model.VefatBildirim
import com.example.model.VefatKaydi
import com.example.ui.VefatUiState
import com.example.util.TurkishUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    uiState: VefatUiState,
    currentUserEmail: String?,
    onSaveManuel: (VefatKaydi, Boolean, () -> Unit) -> Unit,
    onDeleteManuel: (String) -> Unit,
    onSendBildirim: (String, String, String, () -> Unit) -> Unit,
    onDeleteBildirim: (String) -> Unit,
    onSaveReklam: (ReklamAyari) -> Unit,
    onClearStatus: () -> Unit,
    onPrepareSignOut: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingRecord by remember { mutableStateOf<VefatKaydi?>(null) }
    var pendingDeleteRecord by remember { mutableStateOf<VefatKaydi?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Yönetici Paneli", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = currentUserEmail ?: "Oturum açık",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        onPrepareSignOut {
                            AuthHelper.signOut(credentialManager, scope)
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("admin_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Çıkış Yap"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            if (!uiState.isAdmin) {
                AdminUnauthorizedView(
                    currentUserEmail = currentUserEmail,
                    onSwitchToAdminAccount = {
                        onPrepareSignOut {
                            AuthHelper.signOut(credentialManager, scope) {
                                AuthHelper.onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {},
                                    onAuthError = {},
                                    scope = scope
                                )
                            }
                        }
                    },
                    onSignOut = {
                        onPrepareSignOut {
                            AuthHelper.signOut(credentialManager, scope)
                        }
                    }
                )
            } else {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 12.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            onClearStatus()
                            selectedTab = 0
                        },
                        text = { Text(if (editingRecord != null) "Düzenle" else "Vefat Ekle") },
                        modifier = Modifier.testTag("admin_tab_add")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            onClearStatus()
                            selectedTab = 1
                        },
                        text = { Text("Eklediklerim (${uiState.manuelKayitlar.size})") },
                        modifier = Modifier.testTag("admin_tab_list")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            onClearStatus()
                            selectedTab = 2
                        },
                        text = { Text("Bildirim Gönder") },
                        modifier = Modifier.testTag("admin_tab_notify")
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = {
                            onClearStatus()
                            selectedTab = 3
                        },
                        text = { Text("Reklam") },
                        modifier = Modifier.testTag("admin_tab_ad")
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = {
                            onClearStatus()
                            selectedTab = 4
                        },
                        text = { Text("Kullanıcılar (${uiState.tumKullanicilar.size})") },
                        modifier = Modifier.testTag("admin_tab_users")
                    )
                }

                if (uiState.yoneticiMesaji != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = uiState.yoneticiMesaji,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (uiState.yoneticiHata != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = uiState.yoneticiHata,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                when (selectedTab) {
                    0 -> VefatFormTab(
                        initialRecord = editingRecord,
                        isBusy = uiState.yoneticiIslemSuruyor,
                        onCancelEdit = { editingRecord = null },
                        onSave = { kayit, sendNotif ->
                            onSaveManuel(kayit, sendNotif) {
                                editingRecord = null
                            }
                        }
                    )
                    1 -> ManuelRecordsTab(
                        records = uiState.manuelKayitlar,
                        onEdit = { record ->
                            editingRecord = record
                            selectedTab = 0
                        },
                        onDeleteRequest = { record ->
                            pendingDeleteRecord = record
                        }
                    )
                    2 -> BildirimGonderTab(
                        bildirimler = uiState.bildirimler,
                        isBusy = uiState.yoneticiIslemSuruyor,
                        onSend = onSendBildirim,
                        onDelete = onDeleteBildirim
                    )
                    3 -> ReklamEditorTab(
                        currentReklam = uiState.reklam ?: ReklamAyari.DEFAULT_SAMPLE,
                        isBusy = uiState.yoneticiIslemSuruyor,
                        onPublish = onSaveReklam
                    )
                    4 -> KullaniciVeLogTab(
                        kullanicilar = uiState.tumKullanicilar,
                        girisLoglari = uiState.girisLoglari
                    )
                }
            }
        }
    }

    val recordToDelete = pendingDeleteRecord
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteRecord = null },
            title = { Text("Kaydı Sil") },
            text = {
                Text("\"${recordToDelete.adSoyad}\" kaydını kalıcı olarak silmek istediğinize emin misiniz? Silindiğinde tüm kullanıcıların listesinden kaldırılır.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteManuel(recordToDelete.id)
                        pendingDeleteRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Evet, Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRecord = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
private fun AdminUnauthorizedView(
    currentUserEmail: String?,
    onSwitchToAdminAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.AdminPanelSettings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = "Yönetici Yetkisi Gerekli",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Şu anki oturumunuz (${currentUserEmail ?: "Bilinmeyen"}) yönetici yetkisine sahip değil. Vefat kaydı eklemek, bildirim göndermek ve reklam alanını yönetmek için yetkili yönetici Google hesabıyla giriş yapmalısınız.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onSwitchToAdminAccount,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("switch_admin_account_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Yönetici Google Hesabıyla Giriş Yap")
        }
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Oturumu Kapat")
        }
        Text(
            text = stringResource(R.string.disclaimer_source),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VefatFormTab(
    initialRecord: VefatKaydi?,
    isBusy: Boolean,
    onCancelEdit: () -> Unit,
    onSave: (VefatKaydi, Boolean) -> Unit
) {
    var adSoyad by remember(initialRecord) { mutableStateOf(initialRecord?.adSoyad.orEmpty()) }
    var dogumTarihi by remember(initialRecord) { mutableStateOf(initialRecord?.dogumTarihi.orEmpty()) }
    var dogumYeri by remember(initialRecord) { mutableStateOf(initialRecord?.dogumYeri.orEmpty()) }
    var vefatTarihi by remember(initialRecord) {
        mutableStateOf(initialRecord?.vefatTarihi?.ifBlank { TurkishUtils.todayDdMmYyyy() } ?: TurkishUtils.todayDdMmYyyy())
    }
    var definYeri by remember(initialRecord) { mutableStateOf(initialRecord?.definYeri.orEmpty()) }
    var definTarihi by remember(initialRecord) { mutableStateOf(initialRecord?.definTarihi.orEmpty()) }
    var cenazeYakini by remember(initialRecord) { mutableStateOf(initialRecord?.cenazeYakini.orEmpty()) }
    var yakinTelefonu by remember(initialRecord) { mutableStateOf(initialRecord?.yakinTelefonu.orEmpty()) }
    var taziyeYeri by remember(initialRecord) { mutableStateOf(initialRecord?.taziyeYeri.orEmpty()) }
    var taziyeTarihi by remember(initialRecord) { mutableStateOf(initialRecord?.taziyeTarihi.orEmpty()) }
    var sendNotification by remember(initialRecord) { mutableStateOf(initialRecord == null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (initialRecord != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kaydı Düzenle",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onCancelEdit) {
                    Text("Yeni Kayda Geç")
                }
            }
        }

        OutlinedTextField(
            value = adSoyad,
            onValueChange = { adSoyad = it },
            label = { Text("Ad Soyad *") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_ad_soyad_input"),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = dogumTarihi,
                onValueChange = { dogumTarihi = it },
                label = { Text("Doğum Tarihi") },
                placeholder = { Text("GG.AA.YYYY") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = dogumYeri,
                onValueChange = { dogumYeri = it },
                label = { Text("Doğum Yeri") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = vefatTarihi,
                onValueChange = { vefatTarihi = it },
                label = { Text("Vefat Tarihi") },
                placeholder = { Text("GG.AA.YYYY") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = definTarihi,
                onValueChange = { definTarihi = it },
                label = { Text("Defin Tarihi") },
                placeholder = { Text("GG.AA.YYYY") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = definYeri,
            onValueChange = { definYeri = it },
            label = { Text("Defin Yeri (Mezarlık)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = cenazeYakini,
                onValueChange = { cenazeYakini = it },
                label = { Text("Cenaze Yakını") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = yakinTelefonu,
                onValueChange = { yakinTelefonu = it },
                label = { Text("Yakın Telefonu") },
                placeholder = { Text("05XX...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = taziyeYeri,
            onValueChange = { taziyeYeri = it },
            label = { Text("Taziye Yeri (Taziye Evi Adı)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = taziyeTarihi,
            onValueChange = { taziyeTarihi = it },
            label = { Text("Taziye Tarihi") },
            placeholder = { Text("15-17.11.2026") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tüm kullanıcılara bildirim gönder",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (adSoyad.isNotBlank()) {
                            "\"${adSoyad.trim()} vefat etti.\" duyurusu anında iletilir."
                        } else {
                            "Kaydedildiğinde \"Falanca kişi vefat etti\" bildirimi gönderilir."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = sendNotification,
                    onCheckedChange = { sendNotification = it },
                    modifier = Modifier.testTag("admin_auto_notify_switch")
                )
            }
        }

        if (validationError != null) {
            Text(
                text = validationError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                if (adSoyad.trim().length < 2) {
                    validationError = "Lütfen en az 2 karakterlik Ad Soyad girin."
                    return@Button
                }
                validationError = null
                val formattedVefat = TurkishUtils.formatDateDdMmYyyy(vefatTarihi)
                    .ifBlank { TurkishUtils.todayDdMmYyyy() }
                onSave(
                    VefatKaydi(
                        id = initialRecord?.id.orEmpty(),
                        adSoyad = adSoyad.trim(),
                        dogumTarihi = dogumTarihi.trim(),
                        dogumYeri = dogumYeri.trim(),
                        vefatTarihi = formattedVefat,
                        vefatTarihiIso = TurkishUtils.toIsoDate(formattedVefat),
                        definYeri = definYeri.trim(),
                        definTarihi = definTarihi.trim(),
                        cenazeYakini = cenazeYakini.trim(),
                        yakinTelefonu = yakinTelefonu.trim(),
                        taziyeYeri = taziyeYeri.trim(),
                        taziyeTarihi = taziyeTarihi.trim(),
                        kaynak = KayitKaynagi.MANUEL
                    ),
                    sendNotification
                )
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("admin_save_vefat_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (initialRecord != null) "Değişiklikleri Kaydet" else "Kaydet ve Yayınla")
            }
        }
    }
}

@Composable
private fun ManuelRecordsTab(
    records: List<VefatKaydi>,
    onEdit: (VefatKaydi) -> Unit,
    onDeleteRequest: (VefatKaydi) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (records.isEmpty()) {
            Text(
                text = "Henüz yönetici tarafından eklenmiş bir vefat kaydı yok.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            records.forEach { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.adSoyad,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${record.vefatTarihi} • ${record.taziyeYeri.ifBlank { record.definYeri }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onEdit(record) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Düzenle", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onDeleteRequest(record) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BildirimGonderTab(
    bildirimler: List<VefatBildirim>,
    isBusy: Boolean,
    onSend: (String, String, String, () -> Unit) -> Unit,
    onDelete: (String) -> Unit
) {
    var adSoyad by remember { mutableStateOf("") }
    var baslik by remember { mutableStateOf("Yeni Vefat İlanı") }
    var mesaj by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Anlık Bildirim Gönder",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Uygulamayı açık veya arka planda aktif tutan tüm kullanıcılara anında bildirim gönderir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = adSoyad,
            onValueChange = {
                adSoyad = it
                if (mesaj.isBlank() || mesaj.endsWith("vefat etti.")) {
                    mesaj = if (it.isNotBlank()) "${it.trim()} vefat etti." else ""
                }
            },
            label = { Text("Vefat Eden Adı Soyadı") },
            placeholder = { Text("Örn: İsmail UÇAR") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notify_ad_soyad_input"),
            singleLine = true
        )

        OutlinedTextField(
            value = baslik,
            onValueChange = { baslik = it },
            label = { Text("Bildirim Başlığı *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = mesaj,
            onValueChange = { mesaj = it },
            label = { Text("Bildirim Mesajı *") },
            placeholder = { Text("Örn: Ahmet Yılmaz vefat etti. Taziye: Kültür Taziye Evi") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notify_message_input"),
            minLines = 2,
            maxLines = 4
        )

        if (validationError != null) {
            Text(
                text = validationError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                if (baslik.isBlank() || mesaj.isBlank()) {
                    validationError = "Lütfen bildirim başlığı ve mesajını girin."
                    return@Button
                }
                validationError = null
                onSend(baslik.trim(), mesaj.trim(), adSoyad.trim()) {
                    adSoyad = ""
                    mesaj = ""
                }
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("admin_send_notification_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tüm Kullanıcılara Bildirim Gönder")
        }

        if (bildirimler.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Text(
                text = "Gönderilen Son Bildirimler",
                style = MaterialTheme.typography.titleMedium
            )
            bildirimler.take(10).forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.baslik, style = MaterialTheme.typography.titleMedium)
                            Text(
                                item.mesaj,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Bildirimi sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReklamEditorTab(
    currentReklam: ReklamAyari,
    isBusy: Boolean,
    onPublish: (ReklamAyari) -> Unit
) {
    var baslik by remember { mutableStateOf(currentReklam.baslik) }
    var metin by remember { mutableStateOf(currentReklam.metin) }
    var gorselUrl by remember { mutableStateOf(currentReklam.gorselUrl) }
    var telefon by remember { mutableStateOf(currentReklam.telefon) }
    var linkUrl by remember { mutableStateOf(currentReklam.linkUrl) }
    var arkaPlanRengi by remember { mutableStateOf(currentReklam.arkaPlanRengi.ifBlank { "#1B4332" }) }
    var aktif by remember { mutableStateOf(currentReklam.aktif) }

    LaunchedEffect(currentReklam) {
        baslik = currentReklam.baslik
        metin = currentReklam.metin
        gorselUrl = currentReklam.gorselUrl
        telefon = currentReklam.telefon
        linkUrl = currentReklam.linkUrl
        arkaPlanRengi = currentReklam.arkaPlanRengi.ifBlank { "#1B4332" }
        aktif = currentReklam.aktif
    }

    val colorPresets = listOf("#1B4332", "#2D6A4F", "#1F2937", "#3F2E21", "#2B3A42")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reklam Alanı Görünür", style = MaterialTheme.typography.titleMedium)
            Switch(checked = aktif, onCheckedChange = { aktif = it })
        }

        OutlinedTextField(
            value = baslik,
            onValueChange = { baslik = it },
            label = { Text("Reklam Başlığı") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = metin,
            onValueChange = { metin = it },
            label = { Text("Reklam Metni") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        OutlinedTextField(
            value = gorselUrl,
            onValueChange = { gorselUrl = it },
            label = { Text("Görsel URL (İsteğe Bağlı)") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = telefon,
                onValueChange = { telefon = it },
                label = { Text("Telefon (İsteğe Bağlı)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = arkaPlanRengi,
                onValueChange = { arkaPlanRengi = it },
                label = { Text("Arka Plan Rengi") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hazır renkler:", style = MaterialTheme.typography.bodyMedium)
            colorPresets.forEach { hex ->
                val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF1B4332))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickable { arkaPlanRengi = hex }
                )
            }
        }

        OutlinedTextField(
            value = linkUrl,
            onValueChange = { linkUrl = it },
            label = { Text("Detay Link URL (İsteğe Bağlı)") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    onPublish(
                        ReklamAyari(
                            aktif = true,
                            baslik = baslik.trim(),
                            metin = metin.trim(),
                            gorselUrl = gorselUrl.trim(),
                            telefon = telefon.trim(),
                            linkUrl = linkUrl.trim(),
                            arkaPlanRengi = arkaPlanRengi.trim().ifBlank { "#1B4332" }
                        )
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("admin_publish_ad_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Yayınla")
            }

            OutlinedButton(
                onClick = {
                    onPublish(
                        currentReklam.copy(
                            aktif = false,
                            baslik = baslik.trim(),
                            metin = metin.trim(),
                            gorselUrl = gorselUrl.trim(),
                            telefon = telefon.trim(),
                            linkUrl = linkUrl.trim(),
                            arkaPlanRengi = arkaPlanRengi.trim().ifBlank { "#1B4332" }
                        )
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("admin_close_ad_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reklamı Kapat")
            }
        }
    }
}

@Composable
private fun KullaniciVeLogTab(
    kullanicilar: List<KullaniciKaydi>,
    girisLoglari: List<GirisLogu>
) {
    val nowMs = System.currentTimeMillis()
    val aktifKullanicilar = remember(kullanicilar, nowMs) {
        kullanicilar.filter { it.isCurrentlyActive(nowMs) }
    }
    var selectedSection by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Yükleme, Aktiflik ve Google Giriş Kayıtları",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Bu alan yalnızca yönetici hesabına özeldir. Normal kullanıcılar diğer kullanıcıları ve giriş kayıtlarını göremez.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminStatCard(
                title = "Toplam Yükleyen",
                value = "${kullanicilar.size}",
                subtitle = "Google hesabı",
                highlight = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_total_users_card")
            )
            AdminStatCard(
                title = "Şu An Aktif",
                value = "${aktifKullanicilar.size}",
                subtitle = "Anlık çevrimiçi",
                highlight = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_active_users_card")
            )
            AdminStatCard(
                title = "Giriş Logu",
                value = "${girisLoglari.size}",
                subtitle = "Son kayıtlar",
                highlight = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_login_logs_card")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sectionLabels = listOf(
                "Aktif (${aktifKullanicilar.size})",
                "Yükleyenler (${kullanicilar.size})",
                "Giriş Logları (${girisLoglari.size})"
            )
            sectionLabels.forEachIndexed { idx, label ->
                val isSelected = selectedSection == idx
                if (isSelected) {
                    Button(
                        onClick = { selectedSection = idx },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_users_section_$idx"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedSection = idx },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_users_section_$idx"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> {
                if (aktifKullanicilar.isEmpty()) {
                    EmptyAdminAnalyticsCard(
                        icon = Icons.Rounded.Group,
                        message = "Şu an aktif görünen başka kullanıcı bulunmuyor."
                    )
                } else {
                    aktifKullanicilar.forEach { user ->
                        KullaniciBilgiCard(
                            user = user,
                            isOnline = true,
                            nowMs = nowMs
                        )
                    }
                }
            }
            1 -> {
                if (kullanicilar.isEmpty()) {
                    EmptyAdminAnalyticsCard(
                        icon = Icons.Rounded.Group,
                        message = "Henüz kayıtlı kullanıcı verisi yok."
                    )
                } else {
                    kullanicilar.forEach { user ->
                        KullaniciBilgiCard(
                            user = user,
                            isOnline = user.isCurrentlyActive(nowMs),
                            nowMs = nowMs
                        )
                    }
                }
            }
            else -> {
                if (girisLoglari.isEmpty()) {
                    EmptyAdminAnalyticsCard(
                        icon = Icons.Rounded.History,
                        message = "Henüz Google giriş log kaydı bulunmuyor."
                    )
                } else {
                    girisLoglari.forEach { log ->
                        GirisLogCard(log = log)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    subtitle: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (highlight) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A))
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KullaniciBilgiCard(
    user: KullaniciKaydi,
    isOnline: Boolean,
    nowMs: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        text = user.adSoyad.ifBlank { "Google Kullanıcısı" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = if (isOnline) {
                        Color(0xFF16A34A).copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isOnline) "Aktif" else "Çevrimdışı",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOnline) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (user.email.isNotBlank()) {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = user.cihaz.ifBlank { "Android Cihaz" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${user.girisSayisi} giriş",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "İlk kurulum: ${formatEpochDateTime(user.ilkGirisMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Son aktif: ${formatRelativeTime(user.sonAktifMs, nowMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GirisLogCard(log: GirisLogu) {
    val isExplicitGoogleSignIn = log.olay == "GOOGLE_GIRIS"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isExplicitGoogleSignIn) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isExplicitGoogleSignIn) "Google ile Giriş" else "Oturum Açıldı",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isExplicitGoogleSignIn) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = formatEpochDateTime(log.zamanMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.adSoyad.ifBlank { "Google Kullanıcısı" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (log.email.isNotBlank()) {
                Text(
                    text = log.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (log.cihaz.isNotBlank()) {
                Text(
                    text = "Cihaz: ${log.cihaz}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyAdminAnalyticsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatEpochDateTime(epochMs: Long): String {
    if (epochMs <= 0L) return "-"
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
    return sdf.format(Date(epochMs))
}

private fun formatRelativeTime(epochMs: Long, nowMs: Long): String {
    if (epochMs <= 0L) return "-"
    val diffSec = ((nowMs - epochMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Şimdi"
        diffSec < 3600L -> "${diffSec / 60L} dk önce"
        diffSec < 86_400L -> "${diffSec / 3600L} sa önce"
        else -> formatEpochDateTime(epochMs)
    }
}

