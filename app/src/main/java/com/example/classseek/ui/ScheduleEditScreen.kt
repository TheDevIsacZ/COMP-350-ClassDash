package com.example.classseek.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classseek.models.ClassInfo
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.calendar.TimePickerDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    userProfile: UserProfile,
    onSave: (List<ClassInfo>, String) -> Unit,
    onBack: () -> Unit
) {
    var semester by remember { mutableStateOf(userProfile.semester.ifEmpty { "Fall" }) }
    val classes = remember {
        mutableStateListOf<ClassInfo>().apply {
            if (userProfile.classes.isNotEmpty()) addAll(userProfile.classes) else add(ClassInfo())
        }
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    }
                    if (content != null) {
                        val parsedClasses = parseIcs(content)
                        if (parsedClasses.isNotEmpty()) {
                            classes.clear()
                            classes.addAll(parsedClasses)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScheduleEditScreen", "Error reading ICS file", e)
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF3F4F6),
        topBar = {
            TopAppBar(
                title = { Text("Edit Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import ICS")
                    }
                    TextButton(onClick = { onSave(classes.toList(), semester) }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { classes.add(ClassInfo()) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Class") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text("Semester Selection", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("Fall", "Spring", "Winter").forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = semester == s, onClick = { semester = s })
                        Text(s)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            classes.forEachIndexed { index, classInfo ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Class ${index + 1}", fontWeight = FontWeight.Bold)
                            
                            val isClassEmpty = classInfo.className.isBlank() && 
                                             classInfo.building.isBlank() && 
                                             classInfo.roomNumber.isBlank() && 
                                             classInfo.dayOfWeek.isBlank() && 
                                             classInfo.startTime.isBlank() && 
                                             classInfo.endTime.isBlank()
                            
                            val isLastAndEmpty = classes.size == 1 && isClassEmpty

                            IconButton(
                                onClick = {
                                    if (classes.size > 1) {
                                        classes.removeAt(index)
                                    } else {
                                        classes[index] = ClassInfo()
                                    }
                                },
                                enabled = !isLastAndEmpty
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Remove or Clear Class",
                                    tint = if (isLastAndEmpty) Color.Gray else Color.Red
                                )
                            }
                        }
                        
                        OutlinedTextField(
                            value = classInfo.className,
                            onValueChange = { classes[index] = classInfo.copy(className = it) },
                            label = { Text("Class Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = classInfo.building,
                                onValueChange = { classes[index] = classInfo.copy(building = it) },
                                label = { Text("Building") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = classInfo.roomNumber,
                                onValueChange = { classes[index] = classInfo.copy(roomNumber = it) },
                                label = { Text("Room #") },
                                modifier = Modifier.weight(0.5f),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val daysList = listOf("M", "T", "W", "TH", "F", "SA", "SU")
                        val selectedDays = classInfo.dayOfWeek.split(",").filter { it.isNotBlank() }.toMutableList()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            daysList.forEach { day ->
                                val isSelected = selectedDays.contains(day)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedDays.remove(day) else selectedDays.add(day)
                                        classes[index] = classInfo.copy(dayOfWeek = selectedDays.joinToString(","))
                                    },
                                    label = { Text(day, fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        var showStartTimePicker by remember { mutableStateOf(false) }
                        var showEndTimePicker by remember { mutableStateOf(false) }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = classInfo.startTime,
                                onValueChange = { },
                                label = { Text("Start Time") },
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.any { it.pressed }) {
                                                    showStartTimePicker = true
                                                }
                                            }
                                        }
                                    },
                                readOnly = true,
                                enabled = true
                            )
                            OutlinedTextField(
                                value = classInfo.endTime,
                                onValueChange = { },
                                label = { Text("End Time") },
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.any { it.pressed }) {
                                                    showEndTimePicker = true
                                                }
                                            }
                                        }
                                    },
                                readOnly = true,
                                enabled = true
                            )
                        }

                        if (showStartTimePicker) {
                            TimePickerDialog(
                                initialTime = if (classInfo.startTime.isEmpty()) "09:00 AM" else classInfo.startTime,
                                onTimeSelected = {
                                    classes[index] = classInfo.copy(startTime = it)
                                    showStartTimePicker = false
                                },
                                onDismiss = { showStartTimePicker = false }
                            )
                        }

                        if (showEndTimePicker) {
                            TimePickerDialog(
                                initialTime = if (classInfo.endTime.isEmpty()) "10:00 AM" else classInfo.endTime,
                                onTimeSelected = {
                                    classes[index] = classInfo.copy(endTime = it)
                                    showEndTimePicker = false
                                },
                                onDismiss = { showEndTimePicker = false }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }
}

// Helper functions for ICS parsing
private fun parseIcs(icsContent: String): List<ClassInfo> {
    val events = mutableListOf<ClassInfo>()
    // Basic unfolding: remove newline followed by space/tab
    val unfoldedContent = icsContent.replace(Regex("\r?\n[ \t]"), "")
    val lines = unfoldedContent.lines()
    var currentEvent: MutableMap<String, String>? = null

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "BEGIN:VEVENT") {
            currentEvent = mutableMapOf()
        } else if (trimmed == "END:VEVENT") {
            currentEvent?.let {
                val summary = (it["SUMMARY"] ?: "").replace("\\,", ",")
                val description = (it["DESCRIPTION"] ?: "").replace("\\,", ",")
                
                // Use Description as primary name if available, fallback to Summary (course code)
                val classNameToUse = if (description.isNotBlank()) description else summary
                
                if (classNameToUse.isNotBlank() && !summary.startsWith("Exam -", ignoreCase = true)) {
                    val location = it["LOCATION"] ?: ""
                    val (building, room) = parseLocation(location)
                    
                    val dtStart = it["DTSTART"] ?: ""
                    val startTime = parseTime(dtStart)
                    val endTime = parseTime(it["DTEND"] ?: "")
                    
                    var days = parseDaysFromRRule(it["RRULE"] ?: "")
                    // Only fall back to the start date's day if it's NOT an online class
                    // This fixes the issue where online classes incorrectly pick up "Saturday"
                    if (days.isBlank() && !location.equals("Online", ignoreCase = true)) {
                        days = getDayFromDate(dtStart)
                    }
                    
                    events.add(ClassInfo(
                        className = classNameToUse,
                        building = building,
                        roomNumber = room,
                        dayOfWeek = days,
                        startTime = startTime,
                        endTime = endTime
                    ))
                }
            }
            currentEvent = null
        } else if (currentEvent != null && trimmed.contains(":")) {
            val keyPart = trimmed.substringBefore(":")
            val value = trimmed.substringAfter(":")
            // Extract the base key name (e.g., DTSTART from DTSTART;TZID=PST)
            val key = keyPart.substringBefore(";").uppercase()
            currentEvent[key] = value
        }
    }
    return events
}

private fun parseLocation(location: String): Pair<String, String> {
    if (location.equals("Online", ignoreCase = true)) return "Online" to ""
    val parts = location.split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return "" to ""
    if (parts.size == 1) return parts[0] to ""
    
    // Assume last part is the room number
    val room = parts.last()
    val building = parts.dropLast(1).joinToString(" ")
    return building to room
}

private fun parseTime(timeStr: String): String {
    // Expected format: 20260124T090000 or similar
    val timePart = timeStr.substringAfter("T", "")
    if (timePart.length < 4) return ""
    
    val hour = try { timePart.substring(0, 2).toInt() } catch (e: Exception) { return "" }
    val minute = timePart.substring(2, 4)
    
    val amPm = if (hour >= 12) "PM" else "AM"
    val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    
    return String.format(Locale.US, "%02d:%s %s", hour12, minute, amPm)
}

private fun parseDaysFromRRule(rrule: String): String {
    // Expected part: BYDAY=MO,WE
    val byDayPart = rrule.split(";").find { it.startsWith("BYDAY=") }?.substringAfter("=") ?: ""
    val daysMap = mapOf(
        "MO" to "M",
        "TU" to "T",
        "WE" to "W",
        "TH" to "TH",
        "FR" to "F",
        "SA" to "SA",
        "SU" to "SU"
    )
    return byDayPart.split(",")
        .map { it.filter { char -> char.isLetter() } } // Remove numbers like 2TH
        .mapNotNull { daysMap[it] }
        .joinToString(",")
}

private fun getDayFromDate(dateStr: String): String {
    if (dateStr.length < 8) return ""
    val pureDate = dateStr.take(8)
    return try {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        val date = sdf.parse(pureDate)
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "M"
            java.util.Calendar.TUESDAY -> "T"
            java.util.Calendar.WEDNESDAY -> "W"
            java.util.Calendar.THURSDAY -> "TH"
            java.util.Calendar.FRIDAY -> "F"
            java.util.Calendar.SATURDAY -> "SA"
            java.util.Calendar.SUNDAY -> "SU"
            else -> ""
        }
    } catch (e: Exception) {
        ""
    }
}
