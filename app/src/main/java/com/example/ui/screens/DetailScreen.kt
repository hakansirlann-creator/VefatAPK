package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.KayitKaynagi
import com.example.model.VefatKaydi
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmAmberBg
import com.example.ui.theme.WarmAmberBgDark
import com.example.util.TurkishUtils
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    kayit: VefatKaydi,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showWhatsAppDialog by remember { mutableStateOf(false) }

    val taziyeHedef = kayit.taziyeYeri.trim()
    val definHedef = kayit.definYeri.trim()
    val dialablePhone = TurkishUtils.formatDialablePhone(kayit.yakinTelefonu)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Vefat Ayrıntısı",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = kayit.adSoyad,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (kayit.kaynak == KayitKaynagi.MANUEL) {
                                Surface(
                                    color = if (isDark) WarmAmberBgDark else WarmAmberBg,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Yakın Bildirimi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) MaterialTheme.colorScheme.onSurface else WarmAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        DetailRow("Vefat Tarihi", kayit.vefatTarihi)
                        DetailRow("Doğum Tarihi", kayit.dogumTarihi)
                        DetailRow("Doğum Yeri", kayit.dogumYeri)
                        DetailRow("Defin Yeri", kayit.definYeri)
                        DetailRow("Defin Tarihi", kayit.definTarihi)
                        DetailRow("Cenaze Yakını", kayit.cenazeYakini)
                        DetailRow("Yakın Telefonu", kayit.yakinTelefonu)
                        DetailRow("Taziye Yeri", kayit.taziyeYeri)
                        DetailRow("Taziye Tarihi", kayit.taziyeTarihi)
                    }
                }

                // Taziye Evi Harita & Yol Tarifi
                if (taziyeHedef.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val dest = URLEncoder.encode("$taziyeHedef Batman", "UTF-8")
                                openExternalUrl(
                                    context,
                                    "https://www.google.com/maps/dir/?api=1&destination=$dest&travelmode=driving"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("taziye_directions_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Taziye Evine Yol Tarifi", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = {
                                val q = URLEncoder.encode("$taziyeHedef Batman", "UTF-8")
                                openExternalUrl(
                                    context,
                                    "https://www.google.com/maps/search/?api=1&query=$q"
                                )
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("taziye_map_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Haritada Göster", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Mezarlık Yol Tarifi
                if (definHedef.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val dest = URLEncoder.encode("$definHedef Batman", "UTF-8")
                            openExternalUrl(
                                context,
                                "https://www.google.com/maps/dir/?api=1&destination=$dest&travelmode=driving"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cemetery_directions_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mezarlığa Yol Tarifi", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Cenaze Yakınını Ara
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            if (dialablePhone != null) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialablePhone"))
                                runCatching { context.startActivity(intent) }
                            }
                        },
                        enabled = !kayit.isTelefonMaskeli && dialablePhone != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("call_relative_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cenaze Yakınını Ara", style = MaterialTheme.typography.labelLarge)
                    }
                    if (kayit.isTelefonMaskeli) {
                        Text(
                            text = buildString {
                                append(stringResource(R.string.phone_masked_notice))
                                if (kayit.yakinTelefonu.isNotBlank()) {
                                    append(" (${kayit.yakinTelefonu})")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // WhatsApp ile Taziye Mesajı Gönder
                Button(
                    onClick = { showWhatsAppDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("whatsapp_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WhatsApp ile Taziye Mesajı Gönder", style = MaterialTheme.typography.labelLarge)
                }

                // Paylaş
                OutlinedButton(
                    onClick = { shareRecord(context, kayit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Paylaş", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.disclaimer_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showWhatsAppDialog) {
        WhatsAppCondolenceDialog(
            kayit = kayit,
            onDismiss = { showWhatsAppDialog = false },
            onSend = { messageText ->
                showWhatsAppDialog = false
                val encodedText = URLEncoder.encode(messageText, "UTF-8")
                val waPhone = TurkishUtils.formatWhatsAppPhone(kayit.yakinTelefonu)
                val url = if (waPhone != null) {
                    "https://wa.me/$waPhone?text=$encodedText"
                } else {
                    "https://wa.me/?text=$encodedText"
                }
                openExternalUrl(context, url)
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun WhatsAppCondolenceDialog(
    kayit: VefatKaydi,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val templates = remember(kayit.adSoyad) {
        listOf(
            "Başsağlığı (Klasik)" to "${kayit.adSoyad} vefatını üzüntüyle öğrendim. Merhuma Allah'tan rahmet, ailesine ve yakınlarına başsağlığı ve sabır dilerim.",
            "Dua ile" to "İnnâ lillâhi ve innâ ileyhi râciûn. ${kayit.adSoyad} için Allah'tan rahmet, kederli ailesine sabırlar dilerim. Mekanı cennet olsun.",
            "Kısa Mesaj" to "Merhum ${kayit.adSoyad} için başsağlığı dileriz. Allah rahmet eylesin, başınız sağ olsun."
        )
    }
    var selectedTemplateIdx by remember { mutableIntStateOf(0) }
    var messageText by remember { mutableStateOf(templates[0].second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "WhatsApp Taziye Mesajı",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (TurkishUtils.formatWhatsAppPhone(kayit.yakinTelefonu) != null) {
                        "Mesaj doğrudan cenaze yakınının WhatsApp numarasına açılacaktır."
                    } else {
                        "Telefon numarası gizlendiği için WhatsApp açıldığında kişiyi kendiniz seçebilirsiniz."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    templates.forEachIndexed { idx, (label, text) ->
                        FilterChip(
                            selected = selectedTemplateIdx == idx,
                            onClick = {
                                selectedTemplateIdx = idx
                                messageText = text
                            },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_message_input"),
                    label = { Text("Taziye mesajınız") },
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(messageText) },
                enabled = messageText.isNotBlank(),
                modifier = Modifier.testTag("whatsapp_send_confirm")
            ) {
                Text("WhatsApp'ta Aç")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Bağlantı açılamadı.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareRecord(context: Context, kayit: VefatKaydi) {
    val shareText = buildString {
        appendLine("Batman Vefat İlanı")
        appendLine("Vefat Eden: ${kayit.adSoyad}")
        if (kayit.vefatTarihi.isNotBlank()) appendLine("Vefat Tarihi: ${kayit.vefatTarihi}")
        if (kayit.definYeri.isNotBlank()) appendLine("Defin Yeri: ${kayit.definYeri}")
        if (kayit.definTarihi.isNotBlank()) appendLine("Defin Tarihi: ${kayit.definTarihi}")
        if (kayit.taziyeYeri.isNotBlank()) appendLine("Taziye Yeri: ${kayit.taziyeYeri}")
        if (kayit.taziyeTarihi.isNotBlank()) appendLine("Taziye Tarihi: ${kayit.taziyeTarihi}")
    }.trim()

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Batman Vefat: ${kayit.adSoyad}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "İlanı Paylaş"))
    } catch (_: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Batman Vefat", shareText))
        Toast.makeText(context, "Bilgiler panoya kopyalandı.", Toast.LENGTH_SHORT).show()
    }
}
