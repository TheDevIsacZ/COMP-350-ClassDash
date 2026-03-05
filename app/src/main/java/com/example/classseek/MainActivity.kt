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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.classseek.ui.theme.ClassSeekTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- Added code to print SHA-1 to Logcat ---
        try {
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA1")
                val digest = md.digest(signature.toByteArray())
                val sha1 = digest.joinToString(":") { "%02X".format(it) }
                Log.d("MY_SHA1_CERT", "Your SHA-1 Fingerprint is: $sha1")
            }
        } catch (e: Exception) {
            Log.e("MY_SHA1_CERT", "Error getting signature", e)
        }
        // -------------------------------------------

        enableEdgeToEdge()
        setContent {
            ClassSeekTheme {
                ClassSeekApp(onSignInSuccess = { account ->
                    // Trigger calendar fetch when sign in is successful
                })
            }
        }
    }

    // for creating the calendar service: Instantiate the Calendar service and fetch events
    suspend fun getCalendarEvents(account: GoogleSignInAccount) {
        withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@MainActivity, listOf("https://www.googleapis.com/auth/calendar")
                )
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()

                // Fetch calendar events
                val events = service.events().list("primary")
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
                val items = events.items
                
                Log.d("CALENDAR_API", "Fetched ${items?.size ?: 0} events")
                items?.forEach { event ->
                    Log.d("CALENDAR_API", "Event: ${event.summary} at ${event.start}")
                }
            } catch (e: Exception) {
                Log.e("CALENDAR_API", "Error fetching events", e)
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ClassSeekApp(onSignInSuccess: (GoogleSignInAccount) -> Unit = {}) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context as? MainActivity }
    
    // For runtime permission requests: Android 6.0+ logic
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // For runtime permission requests: Launcher to request multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCalendarPermission = permissions[Manifest.permission.READ_CALENDAR] == true &&
                permissions[Manifest.permission.WRITE_CALENDAR] == true
    }

    // for initiating sign-in flow: Configure Google Sign-In Options
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // for initiating sign-in flow: Launcher for Google Sign-In result
    // for handling sign-in result: retrieves the user's Google account information
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            // for handling sign-in result: Retrieve the account object
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account

            // for handling sign-in result: Use the account's token to make API calls
            // The account object contains the necessary credentials to access the API.
            Log.d("GOOGLE_SIGN_IN", "Sign-in successful: ${account?.email}")
            
            account?.let { 
                onSignInSuccess(it)
                // for creating the calendar service: trigger the fetch
                scope.launch {
                    activity?.getCalendarEvents(it)
                }
            }
        } catch (e: ApiException) {
            // for handling sign-in result: Sign in failed
            Log.e("GOOGLE_SIGN_IN", "signInResult:failed code=" + e.statusCode)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            // For runtime permission requests: UI logic to show permission request button
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                if (!hasCalendarPermission) {
                    Text(text = "Calendar access is required to sync your schedule.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }) {
                        Text("Grant Calendar Permissions")
                    }
                } else {
                    Text(text = "Calendar access granted!")
                    Spacer(modifier = Modifier.height(16.dp))

                    // for initiating sign-in flow: UI logic to show sign-in button
                    if (signedInAccount == null) {
                        Button(onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            signInLauncher.launch(signInIntent)
                        }) {
                            Text("Sign in with Google")
                        }
                    } else {
                        Text(text = "Signed in as: ${signedInAccount?.email}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Greeting(
                            name = signedInAccount?.displayName ?: "User",
                            modifier = Modifier
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                signedInAccount?.let { activity?.getCalendarEvents(it) }
                            }
                        }) {
                            Text("Refresh Calendar Events")
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ClassSeekTheme {
        Greeting("Android")
    }
}
