# Manage Volunteers Screen - UI Specifications

## 1. Top Header Bar
The top navigation bar containing the Back button, Title, and Search action.

### Container Implementation
*   **Component Type:** Custom `Row` layout (NOT `TopAppBar`)
    *   *Reason:* Allows precise control over padding and element alignment
*   **Structure:** Row containing IconButton, Text with weight(1f), and optional action button
*   **Vertical Padding:**
    *   **Top:** `16.dp`
    *   **Bottom:** `8.dp`
*   **Horizontal Padding:**
    *   **Start:** `4.dp`
    *   **End:** `20.dp`
*   **Alignment:** Vertically Centered (`Alignment.CenterVertically`)
*   **Width:** `fillMaxWidth()`

### Components

#### A. Back Button
*   **Component:** `IconButton`
*   **Touch Target Size:** `48.dp`
*   **Icon:** `Icons.AutoMirrored.Filled.ArrowBack`
*   **Icon Size:** `24.dp`
*   **Icon Tint:** `Color.White`

#### B. Screen Title
*   **Text:** "Choose Volunteers" (or "Manage Volunteers")
*   **Typography:** `MaterialTheme.typography.titleLarge`
    *   **Font Size:** `22.sp`
    *   **Font Weight:** `FontWeight.Bold`
*   **Color:** `Color.White`
*   **Padding:** `start = 8.dp`
*   **Layout Weight:** `1f`

#### C. Search / Action Button (Circular)
*   **Container Padding:** `top = 2.dp`, `end = 4.dp` (Visual adjustment)
*   **Container Size:** `40.dp` x `40.dp`
*   **Shape:** `CircleShape`
*   **Background Color:** `YellowAccent`
*   **Icon Size:** `24.dp`
*   **Icon Tint:** `Color.Black`
*   **Icon Tint:** `Color.Black`
*   **Alignment:** Center of the Box
*   **Visibility:** HIDDEN during `Loading State`.

---

## 2. Floating Action Button (Save)
The primary action button for saving results.

### Position (Critical)
*   **Strategy:** **Manual Box Positioning** (Do NOT use `Scaffold`'s `floatingActionButton` slot).
    *   *Reason:* Ensures pixel-perfect alignment across screens with different Scaffold behaviors.
*   **Parent:** Main Content `Box` (Bottom Layer).
*   **Alignment:** `Alignment.BottomEnd`
*   **Padding/Margin:**
    *   **End (Right):** `24.dp`
    *   **Bottom:** `48.dp` (Elevated position)

### Styling
*   **Component:** `FloatingActionButton`
*   **Container Color:** `Color(0xFF4CAF50)` (Green)
*   **Content Color:** `Color.White`
*   **Icon:** `Icons.Default.Save`
*   **Icon Size:** `24.dp`

---

## 3. List & Scroll Behavior

### Content Padding
*   **Bottom Padding:** `100.dp`
    *   *Purpose:* Ensures the last list items are visible above the floating UI elements (FAB/Snackbar) and navigation bar.

### Custom Vertical Scrollbar
A custom-drawn scrollbar indicating list position.

*   **Position:** `Alignment.CenterEnd`
*   **Container Width:** `6.dp`
*   **Right Margin:** `2.dp` (Padding from edge)
*   **Thumb Style:**
    *   **Width:** `4.dp`
    *   **Color:** `YellowAccent` with `alpha = 0.5f`
    *   **Corner Radius:** `2.dp`
*   **Behavior:** Scale thumb height based on visible/total items ratio (min 10% height).

---

## 4. Loading State
Consistent loading pattern across the TTW interface.

*   **Component:** `CircularProgressIndicator` only.
*   **Color:** `YellowAccent`.
*   **Text:** NO accompanying text (e.g., "Loading...", "Please wait").
*   **Alignment:** Center of the screen.

---

## 5. Empty Data State
UI displayed when no results or data are found.

*   **Content:** Text message (e.g., "No users found").
*   **Alignment:**
    *   **Vertical:** Vertically centered within its container.
    *   **Horizontal:** **Horizontally centered** within the screen width.
*   **Typography:** `MaterialTheme.typography.bodyLarge`.
*   **Typography:** `MaterialTheme.typography.bodyLarge`.
*   **Color:** `Color.White`.

---

## 6. Group Information Chip
Standard chip for displaying component/group counts (used in Volunteer Presets and Teaching Slots).

*   **Size:**
    *   **Width:** `60.dp`
    *   **Height:** `28.dp`
*   **Shape:** `RoundedCornerShape(8.dp)`
*   **Background:** `YellowAccent`
*   **Text:**
    *   **Alignment:** Centered (`textAlign = TextAlign.Center`, `contentAlignment = Alignment.Center`)
    *   **Style:** `MaterialTheme.typography.bodySmall`
    *   **Font Weight:** `FontWeight.Medium`
    *   **Color:** `Color.Black`
    *   **Max Lines:** `1` with `TextOverflow.Ellipsis`

