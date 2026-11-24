package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.ReklamAyari
import com.example.ui.theme.ForestGreen

@Composable
fun ReklamBannerCard(
    reklam: ReklamAyari?,
    modifier: Modifier = Modifier
) {
    if (reklam == null || !reklam.aktif) return
    val context = LocalContext.current

    val bgColor = remember(reklam.arkaPlanRengi) {
        parseHexColorOrDefault(reklam.arkaPlanRengi, ForestGreen)
    }
    val contentColor = remember(bgColor) {
        if (bgColor.luminance() < 0.4f) Color.White else Color(0xFF181C1A)
    }
    val secondaryTextColor = contentColor.copy(alpha = 0.86f)
    var imageLoadFailed by remember(reklam.gorselUrl) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 128.dp)
            .testTag("reklam_banner_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (reklam.gorselUrl.isNotBlank() && !imageLoadFailed) {
                AsyncImage(
                    model = reklam.gorselUrl,
                    contentDescription = reklam.baslik.ifBlank { "Reklam görseli" },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                    onError = { imageLoadFailed = true }
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reklam.baslik.ifBlank { "Sponsorlu İlan" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = contentColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Reklam",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor
                        )
                    }
                }

                if (reklam.metin.isNotBlank()) {
                    Text(
                        text = reklam.metin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (reklam.telefon.isNotBlank() || reklam.linkUrl.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (reklam.telefon.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val cleanTel = reklam.telefon.filter { it.isDigit() || it == '+' }
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanTel"))
                                    runCatching { context.startActivity(intent) }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ara", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (reklam.linkUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val normalizedUrl = if (reklam.linkUrl.startsWith("http")) {
                                        reklam.linkUrl
                                    } else {
                                        "https://${reklam.linkUrl}"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
                                    runCatching { context.startActivity(intent) }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Detay", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseHexColorOrDefault(hex: String, fallback: Color): Color {
    return try {
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(normalized))
    } catch (_: Exception) {
        fallback
    }
}
