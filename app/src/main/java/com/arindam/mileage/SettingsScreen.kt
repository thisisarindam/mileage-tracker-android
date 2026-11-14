package com.arindam.mileage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(data: ScreenData, busy: Boolean, currency: (String) -> Unit, theme: (Boolean) -> Unit, open: (String) -> Unit, export: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCurrency by rememberSaveable(data.preferences.currency) { mutableStateOf(data.preferences.currency) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("preferences") {
            GlassCard {
                Text("PREFERENCES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField("Metric (Kilometres, Litres)", {}, label = { Text("Unit System") }, readOnly = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!busy) expanded = it }) {
                    val code = selectedCurrency
                    OutlinedTextField("${currencySymbol(code)} ($code)", {}, label = { Text("Currency") }, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("INR", "USD", "EUR", "GBP").forEach { code -> DropdownMenuItem(text = { Text("${currencySymbol(code)} ($code)") }, onClick = { selectedCurrency = code; expanded = false }) }
                    }
                }
                Text("Currency changes the label only; stored amounts are never converted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("APPEARANCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DarkMode, null, Modifier.padding(end = 12.dp))
                    Text("Dark theme", Modifier.weight(1f))
                    Switch(checked = data.preferences.darkTheme, onCheckedChange = theme, enabled = !busy)
                }
                Button(onClick = { currency(selectedCurrency) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save Settings") }
            }
        }
        item("import-title") { SectionHeading("Import & Backup", "Your histories. Always on your device.") }
        item("csv-import") {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.FileUpload, FuelBlue)
                    Column(Modifier.weight(1f)) {
                        Text("Import existing records", style = MaterialTheme.typography.titleMedium)
                        Text("Original CSVs, no editing required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TransferButton("Import Fuel CSV", Icons.Outlined.LocalGasStation, !busy) { open("fuel") }
                TransferButton("Import Maintenance CSV", Icons.Outlined.Build, !busy) { open("maintenance") }
                Text("Preview new, duplicate and invalid rows before saving. Existing local edits are preserved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("json-backup") {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.Backup, MileageGreen)
                    Column(Modifier.weight(1f)) {
                        Text("JSON · full backup", style = MaterialTheme.typography.titleMedium)
                        Text("Fuel, services, preferences & import IDs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = { export("json") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save full JSON backup") }
                TransferButton("Restore full JSON backup", Icons.Outlined.Restore, !busy) { open("json") }
                Text("Restore previews the file and asks before replacing all records and preferences in one transaction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("csv-export") {
            GlassCard {
                Text("CSV · per-history exports", style = MaterialTheme.typography.titleMedium)
                Text("For spreadsheets, not a full backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TransferButton("Export fuel history CSV", Icons.Outlined.Description, !busy) { export("fuel") }
                TransferButton("Export maintenance history CSV", Icons.Outlined.Description, !busy) { export("maintenance") }
            }
        }
        item("offline-info") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.OfflineBolt, null, tint = MileageGreen, modifier = Modifier.size(20.dp))
                Text("Fully offline. No account or automatic cloud backup. Choose files saved locally for airplane mode. Maximum opened file: 20 MiB. Keep a JSON backup before uninstalling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("version") { Text("MILEAGE TRACKER 1.1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
private fun TransferButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    }
}
