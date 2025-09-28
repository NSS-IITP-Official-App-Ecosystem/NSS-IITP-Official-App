# Schedule Generation Implementation Plan

## Overview

This document outlines the implementation plan for the "Generate Schedule" feature in the scheduling module. The feature will allow users to select volunteer presets and availability presets, visualize volunteer availability in a grid format, calculate TFV (Total Free Volunteers) for each slot, and automatically assign volunteers to slots based on optimized criteria.

## Implementation Steps

### 1. Data Fetching & Preparation

- Fetch data from "teachingSlotPreset" (SP) collection for selected presets (e.g., AM-1, RP-1)
  - Extract day, slot, and columnNames information
  - Create grid structure based on this data for each selected preset
- Fetch data from "volunteerAvailability" (VA) collection
  - Determine which groups are free in which slots
  - Map this data to the corresponding grid cells
- Fetch data from "volunteerPreset" (VP) collection
  - Extract volunteer information, including group assignments and counts

### 2. Grid UI Implementation

- Create a grid component for each selected availability preset
  - Rows represent days (from SP)
  - Columns represent time slots (from SP)
  - Each cell displays:
    - Cell identifier (day/slot)
    - Groups that are free in that slot (from VA)
    - Calculated TFV value

### 3. TFV Calculation Logic

- For each cell in the grid:
  - Identify which groups are free in that cell (from VA)
  - Look up volunteer count for each free group (from VP)
  - Calculate TFV by summing the volunteer counts
  - Example: If groups 1, 2, 5 are free and have 7, 8, 13 volunteers respectively, TFV = 28
  - Display this value prominently in each cell

### 4. Volunteer Assignment Logic

- Implement an "Assign" button with the following functionality:
  - When clicked, scan all grids to find the cell with the lowest TFV
  - Search VP to find volunteers whose group is in the set of free groups for that cell
  - Create assignments mapping volunteers to the selected cell
  - Update the UI to reflect these assignments
  - Mark assigned volunteers as unavailable for subsequent assignments
  - Recalculate TFV values after each assignment

### 5. Persistence Implementation

- Add a "Save" button that:
  - Collects all volunteer-to-slot assignments
  - Formats the data in the appropriate structure
  - Saves the complete schedule to the Firebase "generatedSchedules" collection
  - Provides feedback on successful save or error

### 6. UI Enhancements

- Add visual indicators for:
  - Cells that have been assigned volunteers
  - The currently selected cell for assignment
  - Cells with critically low TFV values
- Implement a summary view showing:
  - Total volunteers assigned
  - Slots that still need assignments
  - Distribution of volunteers across groups

### 7. Error Handling & Edge Cases

- Handle scenarios where:
  - No cell has any available volunteers (all TFVs are zero)
  - Multiple cells tie for lowest TFV
  - A volunteer is already assigned to another slot at the same time
  - Network issues during data fetching or saving

### 8. Testing Plan

- Test with various combinations of volunteer and availability presets
- Verify TFV calculations with known test data
- Test the assignment algorithm with edge cases
- Ensure saved data appears correctly when loaded again

### 9. Performance Considerations

- Optimize data fetching to minimize Firebase reads
- Consider caching strategies for volunteer and availability data
- Implement pagination or virtualization if dealing with large volunteer sets
- Batch Firebase operations when saving assignments
