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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.classseek.models.ClassSchedule
import com.example.classseek.ui.theme.AppPrimary
import com.example.classseek.ui.theme.AppQuaternary
import com.example.classseek.ui.theme.Grey
import java.util.*
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    initialDateMillis: Long? = null,
    existingEvent: com.google.api.services.calendar.model.Event? = null,
    initialReminders: List<Int> = emptyList(),
    initialReminderUnits: Map<Int, String> = emptyMap(),
    onBackClick: () -> Unit,
    onSaveClick: (ClassSchedule) -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    var eventName by remember { mutableStateOf(existingEvent?.summary ?: "") }
    var location by remember { mutableStateOf(existingEvent?.location ?: "") }
    
    val initialStartTime = existingEvent?.start?.dateTime?.let { 
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it.value))
    } ?: "09:00 AM"
    
    val initialEndTime = existingEvent?.end?.dateTime?.let { 
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it.value))
    } ?: "10:00 AM"

    var startTime by remember { mutableStateOf(initialStartTime) }
    var endTime by remember { mutableStateOf(initialEndTime) }
    
    val initialDays = existingEvent?.recurrence?.firstOrNull { it.startsWith("RRULE:") }?.let { rrule ->
        val byDay = rrule.split(";").firstOrNull { it.startsWith("BYDAY=") }?.substringAfter("BYDAY=")
        val daysMap = mapOf(
            "MO" to Calendar.MONDAY,
            "TU" to Calendar.TUESDAY,
            "WE" to Calendar.WEDNESDAY,
            "TH" to Calendar.THURSDAY,
            "FR" to Calendar.FRIDAY,
            "SA" to Calendar.SATURDAY,
            "SU" to Calendar.SUNDAY
        )
        byDay?.split(",")?.mapNotNull { daysMap[it] }?.toSet()
    } ?: emptySet()

    var selectedDays by remember { mutableStateOf(initialDays) }

    BackHandler {
        onBackClick()
    }
    
    val initialStartMillis = remember(existingEvent, initialDateMillis) {
        val raw = existingEvent?.start?.dateTime?.value 
            ?: existingEvent?.start?.date?.value 
            ?: initialDateMillis 
            ?: System.currentTimeMillis()
            
        if (existingEvent?.start?.dateTime == null) {
            val cal = if (existingEvent?.start?.date != null) {
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = raw }
            } else {
                Calendar.getInstance().apply { timeInMillis = raw }
            }
            Calendar.getInstance().apply {
                clear()
                set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        } else {
            raw
        }
    }
        
    val initialEndMillis = remember(existingEvent, initialStartMillis) {
        existingEvent?.recurrence?.firstOrNull { it.startsWith("RRULE:") }?.let { rrule ->
            val until = rrule.split(";").firstOrNull { it.startsWith("UNTIL=") }?.substringAfter("UNTIL=")
            if (until != null) {
                try {
                    SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(until)?.let { date ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
                        Calendar.getInstance().apply {
                            clear()
                            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis
                    }
                } catch (e: Exception) {
                    null
                }
            } else null
        } ?: (initialStartMillis + 1000L * 60 * 60 * 24 * 7)
    }

    var startDate by remember(initialStartMillis) { mutableStateOf(initialStartMillis) }
    var endDate by remember(initialEndMillis) { mutableStateOf(initialEndMillis) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var showCustomReminderDialog by remember { mutableStateOf(false) }

    // Use a key to re-initialize when initialReminders change (important for new events after they get an ID)
    var selectedReminders by remember(initialReminders) { 
        mutableStateOf(initialReminders)
    }

    var selectedReminderUnits by remember(initialReminderUnits) {
        mutableStateOf(initialReminderUnits)
    }

    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val dayValues = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingEvent != null) "Edit Event" else "New Event") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
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
                                    endDate = if (selectedDays.isEmpty()) startDate else endDate,
                                    reminders = selectedReminders,
                                    reminderUnits = selectedReminderUnits
                                )
                            )
                        },
                        enabled = eventName.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)

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
                label = { Text("Event name") },
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

            Text("Time & Date", style = MaterialTheme.typography.titleSmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { },
                    label = { Text("Start time") },
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
                    label = { Text("end Time") },
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
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = AppPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Date", style = MaterialTheme.typography.labelMedium, color = AppPrimary)
                        Text(formatDate(startDate), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

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
                                .background(if (isSelected) AppPrimary else MaterialTheme.colorScheme.surfaceVariant)
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

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReminderMenu = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = AppPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Add notification",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showReminderMenu) {
                    BasicAlertDialog(
                        onDismissRequest = { showReminderMenu = false }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Grey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .padding(vertical = 8.dp)
                            ) {
                                val options = listOf(
                                    0 to "At time of event",
                                    5 to "5 minutes before",
                                    10 to "10 minutes before",
                                    15 to "15 minutes before",
                                    30 to "30 minutes before",
                                    60 to "1 hour before",
                                    -1 to "Custom..."
                                )

                                val filteredOptions = options.filter { (minutes, _) ->
                                    minutes == -1 || !selectedReminders.contains(minutes)
                                }

                                filteredOptions.forEach { (minutes, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (minutes == -1) {
                                                    showCustomReminderDialog = true
                                                } else {
                                                    if (!selectedReminders.contains(minutes)) {
                                                        selectedReminders = (selectedReminders + minutes).sortedDescending()
                                                    }
                                                }
                                                showReminderMenu = false
                                            }
                                            .padding(vertical = 14.dp, horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .border(
                                                    width = 2.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showCustomReminderDialog) {
                    CustomReminderDialog(
                        onDismiss = { showCustomReminderDialog = false },
                        onConfirm = { minutes, unit ->
                            if (!selectedReminders.contains(minutes)) {
                                selectedReminders = (selectedReminders + minutes).sortedDescending()
                                selectedReminderUnits = selectedReminderUnits + (minutes to unit)
                            }
                            showCustomReminderDialog = false
                        }
                    )
                }

                selectedReminders.forEach { minutes ->
                    val savedUnit = selectedReminderUnits[minutes]
                    val label = when {
                        minutes == 0 -> "At time of event"
                        savedUnit != null -> {
                            val amount = when (savedUnit) {
                                "Hours" -> minutes / 60
                                "Days" -> minutes / 1440
                                else -> minutes
                            }
                            val unitLabel = when {
                                savedUnit == "Hours" && amount == 1 -> "hour"
                                savedUnit == "Hours" -> "hours"
                                savedUnit == "Days" && amount == 1 -> "day"
                                savedUnit == "Days" -> "days"
                                amount == 1 -> "minute"
                                else -> "minutes"
                            }
                            "$amount $unitLabel before"
                        }
                        minutes == 60 -> "1 hour before"
                        minutes % 1440 == 0 -> "${minutes / 1440} days before"
                        minutes % 60 == 0 -> "${minutes / 60} hours before"
                        else -> "$minutes minutes before"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { selectedReminders = selectedReminders - minutes },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove reminder", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (existingEvent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDeleteClick?.invoke() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember(startDate) {
                val cal = Calendar.getInstance().apply { timeInMillis = startDate }
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                }.timeInMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                        startDate = Calendar.getInstance().apply {
                            clear()
                            set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Grey
            )
        ) {
            val colors = DatePickerDefaults.colors(
                containerColor = Grey,
                titleContentColor = Color.White,
                headlineContentColor = Color.White,
                subheadContentColor = Color.White,
                navigationContentColor = Color.White,
                dayContentColor = Color.White,
                weekdayContentColor = Color.White,
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
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember(endDate) {
                val cal = Calendar.getInstance().apply { timeInMillis = endDate }
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                }.timeInMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                        endDate = Calendar.getInstance().apply {
                            clear()
                            set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Grey
            )
        ) {
            val colors = DatePickerDefaults.colors(
                containerColor = Grey,
                titleContentColor = Color.White,
                headlineContentColor = Color.White,
                subheadContentColor = Color.White,
                navigationContentColor = Color.White,
                dayContentColor = Color.White,
                weekdayContentColor = Color.White,
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
            onTimeSelected = { newStartTime ->
                startTime = newStartTime
                
                // Calculate end time as 1 hour after start time
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                try {
                    val date = sdf.parse(newStartTime)
                    if (date != null) {
                        val cal = Calendar.getInstance().apply {
                            time = date
                        }
                        
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        if (hour == 23) {
                            endTime = "11:59 PM"
                        } else {
                            cal.add(Calendar.HOUR_OF_DAY, 1)
                            endTime = sdf.format(cal.time)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback or ignore if parsing fails
                }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var amountText by remember { mutableStateOf("10") }
    var selectedUnit by remember { mutableStateOf("Minutes before") }
    val units = listOf("Minutes before", "Hours", "Days")
    val purpleColor = AppPrimary

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Grey,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Custom notification",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = purpleColor,
                        unfocusedBorderColor = Color.White,
                        cursorColor = purpleColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                units.forEach { unit ->
                    val isSelected = selectedUnit == unit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedUnit = unit }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) purpleColor else Color.White,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(purpleColor)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = purpleColor, style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(
                        onClick = {
                            val amount = amountText.toIntOrNull() ?: 10
                            val minutes = when (selectedUnit) {
                                "Minutes before" -> amount
                                "Hours" -> amount * 60
                                "Days" -> amount * 60 * 24
                                else -> amount
                            }
                            onConfirm(minutes, selectedUnit)
                        }
                    ) {
                        Text("OK", color = purpleColor, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
