package com.example.classseek.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.classseek.models.UserProfile
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCreationScreen(
    initialProfile: UserProfile?,
    initialName: String,
    initialEmail: String,
    onSaveProfile: (UserProfile) -> Unit,
    onBack: (() -> Unit)? = null // Optional back button for edit mode
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: initialName) }
    var major by remember { mutableStateOf(initialProfile?.major ?: "") }
    var bio by remember { mutableStateOf(initialProfile?.bio ?: "") }
    var location by remember { mutableStateOf(initialProfile?.location ?: "") }
    var githubUrl by remember { mutableStateOf(initialProfile?.githubUrl ?: "") }
    var profilePictureUrl by remember { mutableStateOf(initialProfile?.profilePictureUrl ?: "") }
    var bannerUrl by remember { mutableStateOf(initialProfile?.bannerUrl ?: "") }

    var selectedProfileImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBannerImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val profileImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedProfileImageUri = uri
    }

    val bannerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedBannerImageUri = uri
    }

    val context = LocalContext.current

    suspend fun uploadImage(uri: Uri, folder: String): String {
        try {
            val storageRef = FirebaseStorage.getInstance().reference
            val fileName = UUID.randomUUID().toString()
            val imageRef = storageRef.child("$folder/$fileName")
            
            // Start the upload process to Firebase Storage
            imageRef.putFile(uri).await()
            
            // Retrieve the public download URL after a successful upload
            return imageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("PROFILE_DEBUG", "Failed to upload $folder", e)
            throw e
        }
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Edit Profile") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Banner Selection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.LightGray)
                    .clickable { bannerImageLauncher.launch("image/*") }
            ) {
                val bannerToDisplay = selectedBannerImageUri ?: if (bannerUrl.isNotEmpty()) Uri.parse(bannerUrl) else null
                if (bannerToDisplay != null) {
                    AsyncImage(
                        model = bannerToDisplay,
                        contentDescription = "Banner Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFF9E00), Color(0xFFD00000))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White)
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Banner",
                        modifier = Modifier.padding(8.dp),
                        tint = Color.White
                    )
                }
            }

            // Profile Image Selection (overlapping)
            Box(
                modifier = Modifier
                    .offset(y = (-50).dp)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
                    .clickable { profileImageLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val profileToDisplay = selectedProfileImageUri ?: if (profilePictureUrl.isNotEmpty()) Uri.parse(profilePictureUrl) else null
                if (profileToDisplay != null) {
                    AsyncImage(
                        model = profileToDisplay,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-30).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (initialProfile == null) "Create Your Profile" else "Edit Your Profile",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("Major") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (e.g. Broome Library)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = githubUrl,
                    onValueChange = { githubUrl = it },
                    label = { Text("GitHub / Website URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isUploading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Uploading images...")
                }

                Button(
                    onClick = {
                        isUploading = true
                        scope.launch {
                            try {
                                val finalProfileUrl = selectedProfileImageUri?.let { uploadImage(it, "profile_pics") } ?: profilePictureUrl
                                val finalBannerUrl = selectedBannerImageUri?.let { uploadImage(it, "banners") } ?: bannerUrl

                                val joinDate = initialProfile?.joinDate ?: run {
                                    "Joined " + SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())
                                }

                                //edited name and email for easier lookup of profile and email for the database lookups

                                val normalizedEmail = initialEmail.trim().lowercase()
                                val trimmedName = name.trim()

                                // Trigger the save callback with the gathered profile data
                            onSaveProfile(
                                UserProfile(
                                    uid = initialProfile?.uid ?: "",
                                    name = trimmedName,
                                    email = normalizedEmail,
                                    major = major,
                                    bio = bio,
                                    location = location,
                                    githubUrl = githubUrl,
                                    profilePictureUrl = finalProfileUrl,
                                    bannerUrl = finalBannerUrl,
                                    joinDate = joinDate,
                                    followersCount = initialProfile?.followersCount ?: "0",
                                    followingCount = initialProfile?.followingCount ?: "0"
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("PROFILE_DEBUG", "Error saving profile", e)
                            Toast.makeText(context, "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isUploading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                // Validation: Prevent saving if required fields are missing or an upload is in progress
                enabled = name.isNotBlank() && major.isNotBlank() && !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Profile")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
