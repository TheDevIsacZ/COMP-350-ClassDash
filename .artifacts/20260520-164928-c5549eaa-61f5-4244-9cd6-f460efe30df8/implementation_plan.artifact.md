# Sort Events by Time and Update Event Styling

This plan addresses the issue where upcoming events are grouped by source instead of being sorted by time. It also improves visual separation between days and introduces specific colors for different event types.

## Proposed Changes

### Calendar Component

#### [CalendarScreen.kt](file:///C:/Users/adrie/AndroidStudioProjects/COMP-350-ClassDash/app/src/main/java/com/example/classseek/ui/calendar/CalendarScreen.kt)

- **Global Sorting**: Sort `filteredEvents` and `eventsForSelectedDate` by start time to interleave events from all sources.
- **Visual Separation**: Add a `Spacer` (16.dp) after each day's list of events in the `LazyColumn`.
- **Event Styling in `AgendaItem`**:
    - Update `AgendaItem` to accept `isSchoolEvent` and `isClassEvent` parameters.
    - Set the vertical line and background tint colors based on the event type:
        - **School Event**: `0xFFCB132A` (Red)
        - **Class Schedule Event**: `0xFF6650a4` (Purple)
        - **Custom Event**: `0xFF4285F4` (Blue)
    - Update calls to `AgendaItem` in `CalendarScreen` to pass the correct flags.

```kotlin
// Updated AgendaItem logic
val eventColor = when {
    isSchoolEvent -> Color(0xFFCB132A)
    isClassEvent -> Color(0xFF6650a4)
    else -> Color(0xFF4285F4)
}
```

## Verification Plan

### Manual Verification
- **Test Case 1: Sorting**
    - Verify that custom, school, and class events are sorted strictly by start time within a day.
- **Test Case 2: Spacing**
    - Ensure a 16.dp gap exists between the last event of one day and the header of the next.
- **Test Case 3: Event Colors**
    - Verify that Imported School events have a Red vertical line.
    - Verify that Class Schedule events have a Purple vertical line.
    - Verify that Custom events have a Blue vertical line.
- **Test Case 4: Bottom Sheet**
    - Check that sorting and colors are correct in the date-specific bottom sheet.
