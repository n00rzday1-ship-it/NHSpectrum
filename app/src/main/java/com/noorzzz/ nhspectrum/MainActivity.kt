package com.noorzzz.nhspectrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import android.app.NotificationManager
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.graphics.Color
import com.noorzzz.nhspectrum.ui.theme.setAccentColor
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.noorzzz.nhspectrum.ui.screens.BootScreen
import com.noorzzz.nhspectrum.ui.screens.MainShell
import com.noorzzz.nhspectrum.ui.theme.NHSpectrumTheme
import com.noorzzz.nhspectrum.viewmodel.ScannerViewModel

class MainActivity : AppCompatActivity() {

    private var usbAttachGeneration by mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            android.util.Log.w("NHSpectrum", "Optional capabilities unavailable; permissions denied: $denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogHandler()
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) usbAttachGeneration = 1
        enableEdgeToEdge()
        loadUserPreferences()
        requestNeededPermissions()

        setContent {
            NHSpectrumTheme(darkTheme = true) {
                val navController = rememberNavController()
                val viewModel: ScannerViewModel = viewModel()
                val openedFromUsb = remember { intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED }
                LaunchedEffect(usbAttachGeneration) {
                    if (usbAttachGeneration > 0) {
                        viewModel.connectEsp32()
                        usbAttachGeneration = 0
                    }
                }
                val clipboard = LocalClipboardManager.current
                val crashFile = remember { File(filesDir, "nhspectrum-last-crash.txt") }
                var crashReport by remember {
                    mutableStateOf(
                        runCatching {
                            if (crashFile.isFile) crashFile.readText().take(12_000) else null
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "boot",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("boot") {
                        BootScreen {
                            navController.navigate("main") {
                                popUpTo("boot") { inclusive = true }
                            }
                        }
                    }
                    composable("main") {
                        MainShell(viewModel = viewModel, initialTab = if (openedFromUsb) 5 else 0)
                    }
                }

                // Keep the last fatal exception visible after restart instead of losing
                // the evidence when Android terminates the process.
                if (!crashReport.isNullOrBlank()) {
                    AlertDialog(
                        onDismissRequest = {
                            crashFile.delete()
                            crashReport = null
                        },
                        title = { Text("NHSpectrum stopped unexpectedly") },
                        text = {
                            Text(
                                crashReport.orEmpty(),
                                maxLines = 16,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(crashReport.orEmpty()))
                            }) {
                                Text("Copy crash report")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                crashFile.delete()
                                crashReport = null
                            }) {
                                Text("Continue")
                            }
                        }
                    )
                }
            }
        }
    }



    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) usbAttachGeneration++
    }

    private fun installCrashLogHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val details = StringWriter().also { writer ->
                    error.printStackTrace(PrintWriter(writer))
                }.toString()
                File(filesDir, "nhspectrum-last-crash.txt").writeText(
                    "NHSpectrum crash report\n" +
                        "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                        "Thread: ${thread.name}\n\n" + details
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun loadUserPreferences() {
        val prefs = getSharedPreferences("nhspectrum_prefs", MODE_PRIVATE)
        val accent = when (prefs.getString("accent_name", "Teal")) {
            "Cyber Blue" -> Color(0xFF35A7FF)
            "Violet" -> Color(0xFFB28BFF)
            "Amber" -> Color(0xFFFFB454)
            "Crimson" -> Color(0xFFFF667A)
            "Lime" -> Color(0xFF9BD95A)
            else -> Color(0xFF20D6B2)
        }
        setAccentColor(accent)
        val language = prefs.getString("language", "en") ?: "en"
        val requested = LocaleListCompat.forLanguageTags(language)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != requested.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(requested)
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
