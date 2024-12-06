package com.plcoding.biometricauth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.plcoding.biometricauth.BiometricPromptManager.*
import com.plcoding.biometricauth.ui.theme.BiometricAuthTheme

class MainActivity : AppCompatActivity() {

    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Utworzenie bezpiecznego EncryptedSharedPreferences
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secure_notes",
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        setContent {
            BiometricAuthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val biometricResult by promptManager.promptResults.collectAsState(
                        initial = null
                    )
                    val enrollLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult(),
                        onResult = {
                            println("Activity result: $it")
                        }
                    )
                    LaunchedEffect(biometricResult) {
                        if (biometricResult is BiometricResult.AuthenticationNotSet) {
                            if (Build.VERSION.SDK_INT >= 30) {
                                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                    putExtra(
                                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                                    )
                                }
                                enrollLauncher.launch(enrollIntent)
                            }
                        }
                    }
                    NoteScreen(sharedPreferences, promptManager, biometricResult)
                }
            }
        }
    }
}

@Composable
fun NoteScreen(
    sharedPreferences: android.content.SharedPreferences,
    promptManager: BiometricPromptManager,
    biometricResult: BiometricResult?
) {
    var note by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }

    // Po udanym uwierzytelnieniu pobieramy notatkę z SharedPreferences
    LaunchedEffect(biometricResult) {
        if (biometricResult is BiometricResult.AuthenticationSuccess) {
            isAuthenticated = true
            // Odczytujemy zapisane dane po uwierzytelnieniu
            note = sharedPreferences.getString("note", "") ?: ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isAuthenticated) {
            Button(onClick = {
                promptManager.showBiometricPrompt(
                    title = "Authenticate to access your note",
                    description = "Provide biometric credentials to proceed"
                )
            }) {
                Text("Authenticate")
            }
        } else {
            TextField(
                value = note,
                onValueChange = { note = it },  // Aktualizacja stanu notatki
                label = { Text("Your Note") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            Button(onClick = {
                // Zapisanie notatki w zaszyfrowanym SharedPreferences
                sharedPreferences.edit().putString("note", note).apply()
            }) {
                Text("Save Note")
            }

            // Przycisk wylogowywania
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // Resetowanie danych po wylogowaniu
                sharedPreferences.edit().remove("note").apply() // Usuwanie zapisanej notatki
                isAuthenticated = false // Resetowanie stanu autentykacji
                note = "" // Resetowanie notatki
            }) {
                Text("Log Out")
            }
        }

        biometricResult?.let { result ->
            when (result) {
                is BiometricResult.AuthenticationSuccess -> {
                    // Jeżeli uwierzytelnienie się powiodło, użytkownik może edytować notatkę
                    isAuthenticated = true
                }
                is BiometricResult.AuthenticationError -> {
                    Text("Authentication error: ${result.error}")
                }
                BiometricResult.AuthenticationFailed -> {
                    Text("Authentication failed")
                }
                else -> {
                    // Inne stany
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NoteScreenPreview() {
    val context = LocalContext.current
    val activity = AppCompatActivity()

    val promptManager = BiometricPromptManager(activity)

    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    val sharedPreferences = EncryptedSharedPreferences.create(
        "secure_notes",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    NoteScreen(sharedPreferences, promptManager, biometricResult = null)
}
