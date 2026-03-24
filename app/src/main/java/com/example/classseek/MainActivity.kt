package com.example.classseek

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.classseek.ui.CalendarScreen
import com.example.classseek.ui.theme.ClassSeekTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.e("CALENDAR_DEBUG", "MainActivity: APP STARTED")
        logSha1()

        enableEdgeToEdge()
        setContent {
            ClassSeekTheme {
                ClassSeekApp()
            }
        }
    }

    private fun logSha1() {
        try {
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }
            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA1")
                val digest = md.digest(signature.toByteArray())
                val sha1 = digest.joinToString(":") { "%02X".format(it) }
                Log.e("CALENDAR_DEBUG", "SHA-1 Fingerprint: $sha1")
            }
        } catch (e: Exception) {
            Log.e("CALENDAR_DEBUG", "Error getting signature", e)
        }
    }

    suspend fun getCalendarEvents(account: GoogleSignInAccount): List<Event>? {
        return withContext(Dispatchers.IO) {
            try {
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: Starting for ${account.email}")
                
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                
                // Detailed check for permission
                val hasPermission = GoogleSignIn.hasPermissions(account, Scope(calendarScope))
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: Has calendar permission? $hasPermission")

                val credential = GoogleAccountCredential.usingOAuth2(
                    this@MainActivity, listOf(calendarScope)
                )
                
                credential.selectedAccountName = account.email
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: Credential setup with email: ${account.email}")

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()
                
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: Calling Google Calendar API...")
                val eventsResult = service.events().list("primary")
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(50)
                    .execute()
                
                val items = eventsResult.items
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: SUCCESS! Found ${items?.size ?: 0} events")
                items ?: emptyList()
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: ERROR fetching events", e)
                e.printStackTrace()
                null
            }
        }
    }
}

@Composable
fun ClassSeekApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context as? MainActivity }
    
    var calendarEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.e("CALENDAR_DEBUG", "signInLauncher: Launcher returned. ResultCode: ${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            Log.e("CALENDAR_DEBUG", "signInLauncher: Sign-in successful for: ${account?.email}")
            
            account?.let { 
                scope.launch {
                    Log.e("CALENDAR_DEBUG", "signInLauncher: Launching event fetch coroutine")
                    val events = activity?.getCalendarEvents(it)
                    if (events != null) {
                        calendarEvents = events
                        Log.e("CALENDAR_DEBUG", "signInLauncher: State updated with ${events.size} events")
                    } else {
                        Log.e("CALENDAR_DEBUG", "signInLauncher: getCalendarEvents returned null")
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("CALENDAR_DEBUG", "signInLauncher: Sign-in failed. Code: ${e.statusCode}, Msg: ${e.message}")
        } catch (e: Exception) {
            Log.e("CALENDAR_DEBUG", "signInLauncher: Unexpected error", e)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> CalendarScreen(
                        signedInAccount = signedInAccount,
                        calendarEvents = calendarEvents,
                        onSignInClick = { intent -> 
                            Log.e("CALENDAR_DEBUG", "onSignInClick: Launching Picker")
                            signInLauncher.launch(intent) 
                        },
                        onSignOutClick = {
                            Log.e("CALENDAR_DEBUG", "onSignOutClick: Clearing state")
                            signedInAccount = null
                            calendarEvents = emptyList()
                        },
                        onRefreshClick = { account ->
                            Log.e("CALENDAR_DEBUG", "onRefreshClick: Manual refresh for ${account.email}")
                            val events = activity?.getCalendarEvents(account)
                            if (events != null) calendarEvents = events
                        }
                    )
                    AppDestinations.FAVORITES -> Text("Favorites Screen", modifier = Modifier.padding(16.dp))
                    AppDestinations.PROFILE -> Text("Profile Screen", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}
