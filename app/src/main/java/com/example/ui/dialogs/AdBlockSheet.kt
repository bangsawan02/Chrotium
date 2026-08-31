package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AdBlockStats
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EnergyGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSheet(
    stats: AdBlockStats,
    currentUrl: String,
    tabBlockedCount: Int,
    onToggleEnabled: () -> Unit,
    onToggleCosmetic: () -> Unit,
    onToggleTrackers: () -> Unit,
    onTogglePopups: () -> Unit = {},
    onToggleFingerprinting: () -> Unit = {},
    onToggleCurrentSiteWhitelist: () -> Unit,
    onRemoveWhitelistedDomain: (String) -> Unit,
    onResetStats: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentHost = try {
        if (currentUrl.isNotBlank() && !currentUrl.startsWith("about:")) {
            URI(currentUrl).host ?: currentUrl
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }

    val isCurrentHostWhitelisted = currentHost.isNotBlank() && stats.whitelistedDomains.any {
        currentHost == it || currentHost.endsWith(".$it")
    }

    val savedDataFormatted = if (stats.savedDataKbEstimate >= 1024) {
        String.format("%.1f MB", stats.savedDataKbEstimate / 1024.0)
    } else {
        "${stats.savedDataKbEstimate} KB"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        modifier = modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = if (stats.isEnabled) EnergyGreen.copy(alpha = 0.15f) else TextTertiary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (stats.isEnabled) EnergyGreen else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Layanan Pemblokir Iklan & Pelacak",
                            color = TextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (stats.isEnabled) "Perlindungan Aktif & Hemat Baterai" else "Pemblokiran Nonaktif",
                            color = if (stats.isEnabled) EnergyGreen else TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp).testTag("close_adblock_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Real-time Stats Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Iklan Diblokir",
                            value = "${stats.totalBlockedCount}",
                            subtext = if (tabBlockedCount > 0) "$tabBlockedCount di tab ini" else "Total keseluruhan",
                            icon = Icons.Default.Security,
                            accentColor = EnergyGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Hemat Data",
                            value = savedDataFormatted,
                            subtext = "Efisiensi kuota web",
                            icon = Icons.Default.DataUsage,
                            accentColor = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = EnergyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Performa Halaman Maksimal",
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Mencegah eksekusi skrip pelacak pihak ketiga sehingga rendering halaman web menjadi jauh lebih cepat.",
                                    color = TextTertiary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Section 2: Current Site Whitelist Toggle
                if (currentHost.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentHostWhitelisted) DarkSurfaceVariant else DarkSurface
                            ),
                            border = BorderStroke(1.dp, if (isCurrentHostWhitelisted) ElectricCyan else BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (isCurrentHostWhitelisted) ElectricCyan else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = currentHost,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isCurrentHostWhitelisted) "Iklan diizinkan di situs ini (Whitelist)" else "Pemblokiran iklan aktif di situs ini",
                                            color = if (isCurrentHostWhitelisted) ElectricCyan else EnergyGreen,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Switch(
                                    checked = !isCurrentHostWhitelisted,
                                    onCheckedChange = { onToggleCurrentSiteWhitelist() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AmoledBlack,
                                        checkedTrackColor = EnergyGreen,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = DarkSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("toggle_site_adblock_switch")
                                )
                            }
                        }
                    }
                }

                // Section 3: Feature Toggles
                item {
                    Text(
                        text = "Konfigurasi Filter",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Master Toggle
                            SettingRow(
                                title = "Aktifkan Pemblokir Iklan",
                                subtitle = "Filter permintaan jaringan terhadap daftar blokir",
                                icon = Icons.Default.Shield,
                                iconTint = EnergyGreen,
                                isChecked = stats.isEnabled,
                                onCheckedChange = { onToggleEnabled() },
                                testTag = "toggle_adblock_master"
                            )

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                            // Cosmetic Filter Toggle
                            SettingRow(
                                title = "Penyembunyian Elemen Kosmetik",
                                subtitle = "Runtuhkan ruang kosong bekas iklan (Cosmetic Hiding)",
                                icon = Icons.Default.VisibilityOff,
                                iconTint = ElectricCyan,
                                isChecked = stats.isCosmeticFilteringEnabled && stats.isEnabled,
                                enabled = stats.isEnabled,
                                onCheckedChange = { onToggleCosmetic() },
                                testTag = "toggle_cosmetic_filter"
                            )

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                            // Tracker Blocking Toggle
                            SettingRow(
                                title = "Blokir Pelacak & Telemetri",
                                subtitle = "Cegah pelacakan sidik jari, analitik & penambang kripto",
                                icon = Icons.Default.Speed,
                                iconTint = ElectricCyan,
                                isChecked = stats.isTrackerBlockingEnabled && stats.isEnabled,
                                enabled = stats.isEnabled,
                                onCheckedChange = { onToggleTrackers() },
                                testTag = "toggle_tracker_filter"
                            )

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                            // Popup Protection Toggle
                            SettingRow(
                                title = "Cegah Pop-up & Pengalihan Paksa",
                                subtitle = "Blokir tab/jendela pengalihan otomatis iklan liar",
                                icon = Icons.Default.Security,
                                iconTint = EnergyGreen,
                                isChecked = stats.isPopupBlockingEnabled && stats.isEnabled,
                                enabled = stats.isEnabled,
                                onCheckedChange = { onTogglePopups() },
                                testTag = "toggle_popup_filter"
                            )

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                            // Anti-Fingerprinting Toggle
                            SettingRow(
                                title = "Perlindungan Sidik Jari Canvas",
                                subtitle = "Samarkan Canvas API untuk mencegah identifikasi perangkat",
                                icon = Icons.Default.Shield,
                                iconTint = ElectricCyan,
                                isChecked = stats.isAntiFingerprintingEnabled && stats.isEnabled,
                                enabled = stats.isEnabled,
                                onCheckedChange = { onToggleFingerprinting() },
                                testTag = "toggle_fingerprint_filter"
                            )
                        }
                    }
                }

                // Section 4: Whitelisted Domains List
                if (stats.whitelistedDomains.isNotEmpty()) {
                    item {
                        Text(
                            text = "Situs Dikecualikan (${stats.whitelistedDomains.size})",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(stats.whitelistedDomains.toList()) { domain ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = domain,
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onRemoveWhitelistedDomain(domain) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus dari Whitelist",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Reset Stats Button
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onResetStats,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTertiary),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("reset_adblock_stats_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Statistik Pemblokiran", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                color = TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else TextTertiary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = if (enabled) TextPrimary else TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = EnergyGreen,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = DarkSurfaceVariant
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
