# Subject Assignment Feature Implementation

## Overview

This document describes the implementation of the Subject Assignment feature for the TW-APP scheduling module. The feature allows users to create subject presets and pair them with schedule presets.

## Features Implemented

### 1. Subject Preset Creation

- **Location**: `SubjectAssignmentScreen.kt` and `CreateSubjectPresetDialog.kt`
- **Functionality**:
  - Create presets with 10 predefined subjects: MA, SC, EN, HN, SA, SS, BI, CH, PY, CM
  - Use +/- buttons to set the number of classes for each subject
  - Default value of 0 (subject not included if 0 classes)
  - Save presets with custom names to Firebase with proper error handling
  - **Enhanced**: Improved save operation with success/failure feedback via snackbars
  - **Fixed**: Increment/decrement buttons now properly update count display in real-time

### 2. Subject Preset Management

- **Location**: `SubjectAssignmentScreen.kt`
- **Functionality**:
  - **Delete Presets**: Red circular delete button on each preset card (following UI.md specifications)
  - **Confirmation Dialog**: "Delete Preset" dialog with preset name confirmation
  - **Firebase Integration**: Proper deletion from "subjectPresets" collection
  - **User Feedback**: Success/error messages via snackbars
  - **Loading States**: Visual feedback during delete operations
  - **Error Handling**: Network failure and permission error handling

### 3. Schedule and Subject Preset Selection

- **Location**: `SubjectPresetSelectionScreen.kt`
- **Functionality**:
  - Load existing schedule presets from "generatedSchedules" Firebase collection
  - Load existing subject presets from "subjectPresets" Firebase collection
  - Allow selection of multiple schedule presets
  - For each selected schedule preset, allow selection of one subject preset
  - Clear pairing visualization (Schedule Preset → Subject Preset)

### 3. Data Models

- **Location**: `models/SubjectPreset.kt`
- **Components**:
  - `SubjectPreset` data class for storing preset information
  - `SubjectConstants` object with all subject codes and names
  - `ScheduleSubjectPairing` data class for pairing information

### 4. Firebase Integration

- **Collection**: `subjectPresets` (added to `FirestoreCollection.kt`)
- **Operations**: Create, Read, Delete subject presets
- **Document Organization**: Uses preset name as document ID for better readability
- **Enhanced Features**:
  - Proper error handling for all database operations
  - Success/failure feedback via snackbars
  - Loading states during operations
  - Automatic timestamp generation on creation
  - Duplicate name validation
  - Invalid character validation for document IDs
- **Data Structure**:

  ```kotlin
  // In Firebase (document ID = preset name):
  {
    "name": "string",
    "subjects": {"MA": 2, "SC": 3, ...},
    "createdAt": 1234567890
    // Note: 'id' field is NOT stored in document
  }

  // In app memory:
  SubjectPreset(
      id: String, // Same as name for local state management
      name: String,
      createdAt: Long,
      subjects: Map<String, Int>
  )
  ```

## UI Design Principles

### Minimalistic Design

- Clean, functional interface focusing on usability over visual polish
- Consistent with existing app styling patterns
- Uses established UI components (StandardButton, Cards, etc.)

### UI Updates (v3)

- **Header Changes**: Replaced "New Preset" button with "Pair" button for direct access to schedule-subject pairing
- **Simplified Layout**: Removed "Assign Subjects to Schedules" card from main content
- **Streamlined Actions**: "Create Preset" button moved directly below header (outside card containers)
- **Icon Cleanup**: Removed "+" icons from buttons for cleaner text-only design
- **Delete Functionality**: Added red circular delete buttons to preset cards (following UI.md specifications)
- **User Feedback**: Implemented snackbar notifications for all operations (create, delete, errors)
- **Confirmation Dialogs**: Added delete confirmation with preset name display
- **Bug Fix**: Fixed increment/decrement functionality - count display now updates immediately

### Left-Right Layout Pattern (Dialog)

- Subject list on the left side
- Increment/decrement controls on the right side
- Clear visual separation and intuitive interaction

### Color Scheme

- Follows existing app theme colors
- DarkBackground (#121212) for screens
- NeutralCardSurface (#1F1F1F) for cards
- YellowAccent (#FFD600) for action buttons
- White text with gray descriptions

## Navigation Flow

1. **Main Menu** → "Assign Subject" button
2. **Subject Assignment Screen** → Two main actions:
   - "Create Preset" button (below header) → Opens creation dialog
   - "Pair" button (in header) → Navigate to selection screen
3. **Subject Preset Selection Screen** → Pair schedules with subject presets

## Files Modified/Created

### New Files

- `models/SubjectPreset.kt` - Data models and constants
- `schedule/SubjectAssignmentScreen.kt` - Main subject assignment screen
- `schedule/CreateSubjectPresetDialog.kt` - Subject preset creation dialog
- `schedule/SubjectPresetSelectionScreen.kt` - Schedule-subject pairing screen

### Modified Files

- `firebase/FirestoreCollection.kt` - Added SUBJECT_PRESETS constant
- `SchedulingApp.kt` - Added navigation routes and imports
- `schedule/ScheduleMakerScreen.kt` - Updated "Assign Subject" button navigation

## Technical Implementation Details

### Subject Management

- 10 predefined subjects with codes and full names
- Increment/decrement buttons with visual feedback
- Disabled decrement when count is 0
- Only subjects with count > 0 are saved to preset

### Validation Features

- **Preset Name Validation**: Prevents blank names
- **Duplicate Name Prevention**: Checks for existing presets with same name
- **Character Validation**: Prevents invalid characters (/, \, .) in preset names
- **Subject Count Validation**: Requires at least one subject with count > 0
- **Real-time Error Display**: Shows validation errors immediately in dialog

### Firebase Operations

- Asynchronous operations using Kotlin coroutines
- Comprehensive error handling for network failures
- **Enhanced**: Uses preset name as document ID (no random IDs)
- **Enhanced**: Excludes 'id' field from stored document data
- **Enhanced**: Duplicate name validation before saving
- **Enhanced**: Invalid character validation for document IDs
- Real-time data loading with loading states
- **Enhanced**: Delete operations with proper error handling
- **Enhanced**: Success/failure feedback via SnackbarHost

### State Management

- Compose state management with `remember` and `mutableStateOf`
- Loading states for async operations (create, delete, load)
- Error message handling and display
- **Enhanced**: Delete confirmation dialog state management
- **Enhanced**: Snackbar state management for user feedback

## Future Enhancements (Not Implemented)

- Edit existing subject presets
- Save schedule-subject pairings to Firebase
- Assignment logic integration with existing scheduling features
- Validation for schedule-subject compatibility
- Bulk operations (delete multiple presets)
- Import/export preset functionality

## Testing

- Compilation successful with `./gradlew :scheduling:compileDebugKotlin`
- All new screens integrate with existing navigation
- Firebase operations follow established patterns
- UI components follow existing design system

## Usage Instructions

1. Navigate to Schedule Maker
2. Click "Assign Subject" button
3. **Create Presets**: Use "Create Preset" button to specify class counts for each subject
4. **Delete Presets**: Click the red delete button on any preset card, confirm in the dialog
5. **Pair with Schedules**: Use "Pair" button in header to pair presets with existing schedules
6. **Feedback**: Success/error messages appear as snackbars at the bottom of the screen
7. Subject presets are automatically saved to Firebase with timestamps

This enhanced implementation provides a complete subject assignment system with full CRUD operations, proper error handling, and user feedback while maintaining consistency with the existing codebase architecture and design patterns.
