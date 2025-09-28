# Calendar Module

## Overview
The Calendar module provides scheduling and event management functionality integrated into the ChatApp.

## Features
- View and manage calendar events
- Separate tabs for teaching events and general events
- Different permissions for admin and regular users
- Adding and viewing event details

## Integration
The module is integrated with the main app through the navigation bar's calendar icon.

## Using in MainActivity
```kotlin
// Import the CalendarFragment
import com.phad.chatapp.features.calendar.ui.CalendarFragment

// In your bottom navigation handler:
R.id.navigation_calendar -> {
    supportFragmentManager.beginTransaction()
        .replace(R.id.content_frame, CalendarFragment.newInstance())
        .commit()
}
```

## Module Structure
- `ui/` - UI components (fragments, adapters)
- `models/` - Data models
- `repository/` - Data access layer
- `viewmodel/` - ViewModels for UI state management

## Notes
- The module uses a shared ViewModel at the activity level to ensure data consistency across tabs
- The Calendar feature works independently from the rest of the app for better separation of concerns 