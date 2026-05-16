package com.example.classseek.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.classseek.models.ClassSchedule
import com.google.api.services.calendar.model.Event
import java.util.*
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    initialDateMillis: Long? = null,
    editingEvent: Event? = null,
    onBackClick: () -> Unit,
    onSaveClick: (ClassSchedule, String?) -> Unit,
    onDeleteClick: (String) -> Unit = {},
    onSetReminder: (Event?) -> Unit = {}
) {
    var eventName by remember { mutableStateOf(editingEvent?.summary ?: "") }
    var location by remember { mutableStateOf(editingEvent?.location ?: "") }

    val initialStartTime = editingEvent?.start?.dateTime?.let { formatTimeToPicker(it.value) } ?: "09:00 AM"
    val initialEndTime = editingEvent?.end?.dateTime?.let { formatTimeToPicker(it.value) } ?: "10:00 AM"

    var startTime by remember { mutableStateOf(initialStartTime) }
    var endTime by remember { mutableStateOf(initialEndTime) }
    
    val initialDays = editingEvent?.recurrence?.firstOrNull()?.let { parseRruleDays(it) } ?: emptySet()
    var selectedDays by remember { mutableStateOf(initialDays) }

    BackHandler {
        onBackClick()
    }
    
    val initialStartDate = editingEvent?.start?.dateTime?.value ?: editingEvent?.start?.date?.value ?: initialDateMillis ?: System.currentTimeMillis()
    val initialEndDate = editingEvent?.recurrence?.firstOrNull()?.let { parseRruleUntil(it) } ?: (initialStartDate + 1000L * 60 * 60 * 24 * 7)

    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val dayValues = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingEvent != null) "Edit Event" else "New Event") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSetReminder(editingEvent) }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Set Reminder")
                    }
                    if (editingEvent != null) {
                        IconButton(onClick = { onDeleteClick(editingEvent.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Event")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Event Name") },
                placeholder = { Text("e.g. Meeting, Gym, Study") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repeats weekly on:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    days.forEachIndexed { index, day ->
                        val dayValue = dayValues[index]
                        val isSelected = selectedDays.contains(dayValue)
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        selectedDays - dayValue
                                    } else {
                                        selectedDays + dayValue
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Time & Date", style = MaterialTheme.typography.titleSmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTime,
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
                    value = endTime,
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

            OutlinedCard(
                onClick = { showStartDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(formatDate(startDate), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (selectedDays.isNotEmpty()) {
                OutlinedCard(
                    onClick = { showEndDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Repeat Until", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Text(formatDate(endDate), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val finalDays = if (selectedDays.isEmpty()) {
                        val cal = Calendar.getInstance().apply { timeInMillis = startDate }
                        listOf(cal.get(Calendar.DAY_OF_WEEK))
                    } else {
                        selectedDays.toList()
                    }

                    onSaveClick(
                        ClassSchedule(
                            className = eventName,
                            daysOfWeek = finalDays,
                            startTime = startTime,
                            endTime = endTime,
                            location = location,
                            startDate = startDate,
                            endDate = if (selectedDays.isEmpty()) startDate else endDate
                        ),
                        editingEvent?.id
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = eventName.isNotBlank()
            ) {
                Text(if (editingEvent != null) "Update Event" else "Save to Calendar", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDate = it }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            val colors = DatePickerDefaults.colors(
                dayContentColor = Color.Black,
                weekdayContentColor = Color.Black,
                todayContentColor = Color(0xFF4CAF50), // Standard Green
                todayDateBorderColor = Color(0xFF4CAF50)
            )
            DatePicker(
                state = datePickerState,
                colors = colors,
                showModeToggle = false,
                title = null,
                headline = null
            )
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { endDate = it }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            val colors = DatePickerDefaults.colors(
                dayContentColor = Color.Black,
                weekdayContentColor = Color.Black,
                todayContentColor = Color(0xFF4CAF50),
                todayDateBorderColor = Color(0xFF4CAF50)
            )
            DatePicker(
                state = datePickerState,
                colors = colors,
                showModeToggle = false,
                title = null,
                headline = null
            )
        }
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onTimeSelected = {
                startTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onTimeSelected = {
                endTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val date = try { sdf.parse(initialTime) } catch (e: Exception) { null }
    val cal = Calendar.getInstance().apply {
        if (date != null) time = date
    }

    val is24Hour = false
    
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = is24Hour
    )

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = "Select time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TimePicker(state = timePickerState)
                Row(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            val resultCal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                            }
                            onTimeSelected(sdf.format(resultCal.time))
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimeToPicker(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun parseRruleDays(rrule: String): Set<Int> {
    val daysMap = mapOf(
        "MO" to Calendar.MONDAY,
        "TU" to Calendar.TUESDAY,
        "WE" to Calendar.WEDNESDAY,
        "TH" to Calendar.THURSDAY,
        "FR" to Calendar.FRIDAY,
        "SA" to Calendar.SATURDAY,
        "SU" to Calendar.SUNDAY
    )
    val byDayPart = rrule.split(";").find { it.startsWith("BYDAY=") } ?: return emptySet()
    val days = byDayPart.removePrefix("BYDAY=").split(",")
    return days.mapNotNull { daysMap[it] }.toSet()
}

private fun parseRruleUntil(rrule: String): Long {
    val untilPart = rrule.split(";").find { it.startsWith("UNTIL=") } ?: return System.currentTimeMillis()
    val dateStr = untilPart.removePrefix("UNTIL=")
    val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return try { sdf.parse(dateStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
}
