package com.noorzzz.nhspectrum.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.noorzzz.nhspectrum.ui.theme.*

private data class LanguageOption(val tag: String, val label: String)
private data class AccentOption(val name: String, val color: Color)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nhspectrum_prefs", 0) }
    val languages = remember {
        listOf(
            LanguageOption("en", "English"),
            LanguageOption("es", "Español"),
            LanguageOption("hi", "हिन्दी"),
            LanguageOption("ml", "മലയാളം"),
            LanguageOption("ar", "العربية"),
            LanguageOption("fr", "Français"),
            LanguageOption("de", "Deutsch"),
            LanguageOption("pt", "Português")
        )
    }
    val accents = remember {
        listOf(
            AccentOption("Teal", Color(0xFF20D6B2)),
            AccentOption("Cyber Blue", Color(0xFF35A7FF)),
            AccentOption("Violet", Color(0xFFB28BFF)),
            AccentOption("Amber", Color(0xFFFFB454)),
            AccentOption("Crimson", Color(0xFFFF667A)),
            AccentOption("Lime", Color(0xFF9BD95A))
        )
    }
    var language by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }
    var accentName by remember { mutableStateOf(prefs.getString("accent_name", "Teal") ?: "Teal") }
    var languageMenu by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var locatorSound by remember { mutableStateOf(prefs.getBoolean("locator_sound", true)) }

    LaunchedEffect(Unit) {
        accents.firstOrNull { it.name == accentName }?.let { setAccentColor(it.color) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.settings_title), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(stringResource(R.string.settings_intro), color = TextMuted, fontSize = 11.sp)

        SettingsSection(stringResource(R.string.settings_language)) {
            Box {
                SettingsRow(stringResource(R.string.settings_app_language), languages.firstOrNull { it.tag == language }?.label ?: "English",
                    Modifier.clickable { languageMenu = true })
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    languages.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                language = option.tag
                                prefs.edit().putString("language", option.tag).apply()
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
                                languageMenu = false
                            }
                        )
                    }
                }
            }
            Text("Language changes use Android's per-app locale support. Some technical labels remain English until their translations are added.", color = TextMuted, fontSize = 10.sp)
        }

        SettingsSection(stringResource(R.string.settings_accent)) {
            Text(stringResource(R.string.settings_accent_desc), color = TextMuted, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                accents.forEach { option ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.clickable {
                            accentName = option.name
                            setAccentColor(option.color)
                            prefs.edit().putString("accent_name", option.name).apply()
                        }.padding(3.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(option.color)
                            .border(if (accentName == option.name) 3.dp else 1.dp,
                                if (accentName == option.name) TextPrimary else Border, CircleShape))
                        Text(option.name, color = if (accentName == option.name) TextPrimary else TextMuted, fontSize = 8.sp)
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_locator)) {
            SettingsRow(stringResource(R.string.settings_locator_sound), if (locatorSound) "ON" else "MUTED",
                Modifier.clickable {
                    locatorSound = !locatorSound
                    prefs.edit().putBoolean("locator_sound", locatorSound).apply()
                })
            Text("Controls the optional sound preference for the device locator. Locator tone playback will only be active when the locator feature is available.", color = TextMuted, fontSize = 10.sp)
        }

        SettingsSection(stringResource(R.string.settings_privacy)) {
            SettingsRow(stringResource(R.string.settings_disclaimer), "VIEW", Modifier.clickable { showDisclaimer = true })
            Text("Location and wireless observations are sensitive. Keep session exports private and record only in places where you are authorized.", color = TextMuted, fontSize = 10.sp)
        }

        SettingsSection(stringResource(R.string.settings_about)) {
            SettingsRow(stringResource(R.string.settings_developer), "Noorzzz")
            SettingsRow("Application", "NHSpectrum")
            SettingsRow(stringResource(R.string.settings_platform), "Android 10+ · capability-dependent")
            SettingsRow(stringResource(R.string.settings_about_app), "VIEW", Modifier.clickable { showAbout = true })
        }
        Text("NHSpectrum · Built for responsible wireless field analysis", color = TextMuted, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            containerColor = Panel,
            title = { Text(stringResource(R.string.settings_disclaimer_title), color = Accent, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "NHSpectrum is intended for lawful network inventory, RF observation, diagnostics and authorized security work only. Scan only networks and devices that you own or have permission to assess. Do not use the application to disrupt communications, intercept private content, access networks without authorization, or track people or devices without a lawful basis and appropriate consent.\n\nWireless identifiers and location trails can be sensitive personal data. Store and share exports carefully.\n\nDetection is best-effort: Android permissions, hardware, firmware, RF conditions and vendor restrictions affect what can be observed. Missing fields are not proof that a device or signal is absent. GPS and signal-strength estimates are not guaranteed to be exact.",
                    color = TextDim, fontSize = 11.sp
                )
            },
            confirmButton = { TextButton(onClick = { showDisclaimer = false }) { Text(stringResource(R.string.settings_disclaimer_confirm), color = Accent) } }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = Panel,
            title = { Text("NHSpectrum", color = Accent, fontWeight = FontWeight.Bold) },
            text = { Text("Developed by Noorzzz. A mobile RF observation and wardriving toolkit. Features are capability-detected and depend on the Android device, permissions and connected hardware.", color = TextDim, fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.close), color = Accent) } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, sectionContent: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Text(title, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            sectionContent(this)
        }
    )
}

@Composable
private fun SettingsRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().then(modifier), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 11.sp)
        Text(value, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
