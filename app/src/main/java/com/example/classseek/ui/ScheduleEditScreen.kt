package com.example.classseek.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classseek.models.ClassInfo
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.calendar.TimePickerDialog

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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Class ${index + 1}", fontWeight = FontWeight.Bold)
                            if (classes.size > 1) {
                                IconButton(onClick = { classes.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                }
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

