# ViewAssignmentsScreen UI Documentation

This document outlines the UI components and styling conventions used in the `ViewAssignmentsScreen`.

## 1. Top App Bar

The screen uses a custom top app bar within a `Scaffold`.

- **Container**: `Column`
  - `modifier`: `fillMaxWidth()`, `background(DarkBackground)`, `padding(vertical = 8.dp)`
- **Main Row**: Contains the back button, title, and search icon.
  - `verticalAlignment`: `Alignment.CenterVertically`
- **Back Button**:
  - `IconButton`: `modifier = Modifier.size(42.dp)`
  - `Icon`: `tint = Color.White`
- **Screen Title**:
  - `Text`: "View Assignments"
  - `style`: `MaterialTheme.typography.titleLarge`
  - `color`: `Color.White`
  - `fontWeight`: `FontWeight.Bold`
- **Search Bar**: `OutlinedTextField`
  - `shape`: `RoundedCornerShape(12.dp)`
  - `colors`: `focusedBorderColor = YellowAccent`, `unfocusedBorderColor = YellowAccent`

## 2. Section Selector

A scrollable row for filtering by school and section.

- **Container**: `LazyRow`
  - `contentPadding`: `PaddingValues(horizontal = 20.dp)`
  - `horizontalArrangement`: `Arrangement.spacedBy(8.dp)`
- **School Name**:
  - `Text`: e.g., "AM -"
  - `color`: `YellowAccent`
  - `fontWeight`: `FontWeight.Bold`
  - `style`: `MaterialTheme.typography.bodyLarge`
- **Section Buttons**:
  - `Button`:
    - `shape`: `RoundedCornerShape(4.dp)`
    - `contentPadding`: `PaddingValues(horizontal = 12.dp, vertical = 6.dp)`
    - `modifier`: `Modifier.height(32.dp)`
  - **Colors**:
    - Selected: `containerColor = YellowAccent`, `contentColor = Color.Black`
    - Unselected: `containerColor = Color(0xFF2A2A2A)`, `contentColor = Color.White`
  - **Text**:
    - `style`: `MaterialTheme.typography.bodyMedium`

## 3. Main Content List

- **Container**: `LazyColumn`
  - `verticalArrangement`: `Arrangement.spacedBy(24.dp)`
  - `contentPadding`: `PaddingValues(top = 16.dp, bottom = 100.dp)`

## 4. Assignment Grid

A table-like grid displaying assignments.

- **Grid Container**: `Surface`
  - `modifier`: `border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))`
  - `background`: `Color(0xFF181818)`
  - `shape`: `RoundedCornerShape(12.dp)`
- **Grid Header Row**:
  - `background`: `Color(0xFF232323)` with rounded top corners.
  - `padding`: `vertical = 10.dp`, `horizontal = 8.dp`
- **Header Text** (`Day/Time` and slot names):
  - `color`: `YellowAccent`
  - `fontWeight`: `FontWeight.Bold`
  - `style`: `MaterialTheme.typography.labelMedium`
- **Day Rows**:
  - `background`: Alternating `Color(0xFF181818)` and `Color(0xFF222222)`
- **Day Name Cell**: `Box`
  - `modifier`: `width(56.dp)`
- **Day Name Text**:
  - `color`: `Color.White`
  - `fontWeight`: `FontWeight.Bold`
  - `style`: `MaterialTheme.typography.labelMedium`

## 5. Assignment Cell

Represents a single slot in the grid.

- **Container**: `Card`
  - `modifier`: `width(90.dp)`, `height(64.dp)`, `border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))`
  - `shape`: `RoundedCornerShape(8.dp)`
  - `elevation`: `defaultElevation = 2.dp` (if not empty)
- **Colors**:
  - Empty: `containerColor = Color(0xFF232323)`
  - Assigned: `containerColor = YellowAccent`
- **Content Text** (if assigned):
  - `color`: `Color.Black` or `Color.DarkGray`
  - `style`: `MaterialTheme.typography.labelSmall`

## 6. Assignment Detail Dialog

A dialog showing detailed information for a selected assignment.

- **Dialog Container**: `Column`
  - `modifier`: `padding(16.dp)`
  - `background`: `Color(0xFF222222)`
  - `shape`: `RoundedCornerShape(12.dp)`
- **Info Card**: `Box`
  - `modifier`: `fillMaxWidth()`, `padding(16.dp)`
  - `background`: `Color(0xFF2E7D32)`
  - `shape`: `RoundedCornerShape(8.dp)`
- **Text Styles**:
  - Volunteer Name: `titleLarge`, `FontWeight.Bold`, `Color.White`
  - Roll Number: `titleMedium`, `Color.White`
- **Subject Pill**: `Box`
  - `background`: `YellowAccent`, `shape: RoundedCornerShape(16.dp)`
  - `padding`: `horizontal = 16.dp, vertical = 4.dp)`
  - Text: `bodyMedium`, `FontWeight.Bold`, `Color.Black`
- **Slot Details**: `Text`
  - `style`: `MaterialTheme.typography.bodyMedium`
  - `color`: `Color(0xFFB0B0B0)`
- **Section Headers**: `Text` (e.g., "Subject Preferences")
  - `style`: `MaterialTheme.typography.bodyMedium`
  - `color`: `YellowAccent`
  - `fontWeight`: `FontWeight.Bold`
- **Info Pills**: `Row` containing multiple `Box` pills.
  - `shape`: `RoundedCornerShape(16.dp)`
  - `padding`: `horizontal = 12.dp, vertical = 6.dp`
  - **Primary Pill**:
    - `background`: `YellowAccent`
    - `contentColor`: `Color.Black`
  - **Default Pill**:
    - `background`: `Color(0xFF3A3A3A)`
    - `contentColor`: `Color.White`
- **Close Button**: Standard `Button`
  - `colors`: `containerColor = YellowAccent`, `contentColor = Color.Black`
