# Scheduling Module

This module provides scheduling functionality for ChatApp_Standalone. It integrates The Phad Project's scheduling features as a library module.

## Integration Overview

The scheduling module has been integrated with the following components:

1. **Models**: 
   - TeachingSlotPreset.kt
   - VolunteerPreset.kt 
   - User.kt

2. **Firebase Integration**:
   - FirestoreManager.kt - Uses the existing Firebase instance from the main app
   - FirestoreCollection.kt - Constants for Firestore collection names

3. **UI Components**:
   - SchedulingFragment.kt - Main entry point integrated with MainActivity's navigation
   - SchedulingApp.kt - Main composable with navigation setup
   - Theme components (Color.kt, Type.kt, Theme.kt)

4. **Screens**:
   - ScheduleMakerScreen.kt - Main screen with navigation options
   - AvailabilityOptionsScreen.kt - Screen for managing volunteer availability
   - More screens to be integrated

## Usage

The scheduling module is accessed through the "Scheduling" tab in the bottom navigation of ChatApp_Standalone. When the user clicks on this tab, the SchedulingFragment is loaded, which displays the SchedulingApp composable.

## Testing

For independent testing, a TestActivity is included in the module. This activity can be used to test the scheduling module without the main app.

## Firebase Collections

The module uses the following Firestore collections:
- `users` - User data
- `teachingSlotPresets` - Teaching slot presets
- `volunteerPresets` - Volunteer availability presets
- `schedules` - Generated teaching schedules

## Dependencies

The module uses the following dependencies:
- Firebase Firestore - For data storage
- Jetpack Compose - For UI components
- Navigation Compose - For navigation between screens

## Next Steps

1. Complete the implementation of the remaining screens from The Phad Project
2. Update all imports and package names in migrated files
3. Add more tests to ensure everything works correctly
4. Document any additional features or changes 