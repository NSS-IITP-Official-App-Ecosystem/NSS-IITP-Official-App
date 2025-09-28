# Optimized Schedule Data Storage

## Overview

The Firebase Firestore data structure for storing generated schedules uses an optimized document-based preset organization. This structure provides better organization, reduced data redundancy, and easier access to schedule data by creating one document per teaching slot preset with index-based storage.

## Optimized Data Structure Features

### Key Optimizations

1. **Removed Volunteer IDs**: Volunteer IDs are no longer stored in the saved data structure
2. **Index-Based Storage**: Uses day indices (0-6) and time slot indices (0-n) instead of redundant string data
3. **Centralized Reference Data**: Day names and time slot names are stored once per preset as reference arrays
4. **Separate Group Availability**: Group availability data is stored separately from volunteer assignments
5. **Reduced Redundancy**: Eliminates repeated storage of day names, time labels, and other reference data

## New Data Structure

### Collection Structure

All schedule data is stored in the `generatedSchedules` collection, with each teaching slot preset represented as a separate document.

**Collection:** `generatedSchedules/`

### Document Structure

Each document is named exactly after the teaching slot preset name and contains ALL assignment data for that preset using optimized index-based storage.

**Document Naming Examples:**

- `AM 10B` (document containing all assignments for AM 10B preset)
- `CS 9A` (document containing all assignments for CS 9A preset)
- `UB 12G` (document containing all assignments for UB 12G preset)

### Document Content

Each preset document contains optimized assignment data using index-based storage to eliminate redundancy:

```json
{
  "name": "AM 10B",
  "totalVolunteers": 5,

  "referenceData": {
    "dayNames": ["Mon", "Tue", "Wed", "Thu", "Fri"],
    "timeSlotNames": ["8:00", "9:00", "10:00", "11:00"],
    "totalDays": 5,
    "totalSlots": 4
  },

  "groupAvailability": [
    {
      "dayIndex": 0,
      "slotIndex": 0,
      "availableGroups": ["1", "2", "3"]
    },
    {
      "dayIndex": 0,
      "slotIndex": 1,
      "availableGroups": ["2", "3", "4"]
    }
  ],

  "optimizedSlotAssignments": [
    {
      "dayIndex": 0,
      "slotIndex": 0,
      "volunteerCount": 2,
      "assignments": [
        {
          "volunteerName": "John Doe",
          "volunteerRollNumber": "2301CS01",
          "volunteerGroup": "1"
        },
        {
          "volunteerName": "Jane Smith",
          "volunteerRollNumber": "2301CS02",
          "volunteerGroup": "2"
        }
      ]
    }
  ],

  "optimizedAssignments": [
    {
      "volunteerName": "John Doe",
      "volunteerRollNumber": "2301CS01",
      "volunteerGroup": "1",
      "dayIndex": 0,
      "slotIndex": 0
    },
    {
      "volunteerName": "Jane Smith",
      "volunteerRollNumber": "2301CS02",
      "volunteerGroup": "2",
      "dayIndex": 0,
      "slotIndex": 0
    }
  ]
}
```

### Key Optimizations

1. **Removed Volunteer IDs**: No longer stored in assignment data
2. **Index-Based References**: Uses dayIndex (0-6) and slotIndex (0-n) instead of string names
3. **Centralized Reference Data**: Day names and time slot names stored once per preset
4. **Separate Group Availability**: Group availability data separated from volunteer assignments
5. **Reduced Data Size**: Eliminates redundant string storage across multiple assignments

### Benefits of Optimized Structure

1. **Reduced Storage Size**: Eliminates redundant day/time string data
2. **Faster Queries**: Index-based lookups are more efficient
3. **Better Organization**: Clear separation of reference data and assignments
4. **Easier Maintenance**: Centralized reference data management
5. **Scalability**: More efficient for large numbers of assignments

## Implementation Details

### Key Changes in ScheduleGenerationViewModel

1. **`saveSchedule()` function** uses optimized index-based storage
2. **`createPresetDocumentData()` function** creates optimized data structure
3. **`extractReferenceDataFromSlots()` function** extracts day/time reference data
4. **`extractGroupAvailabilityData()` function** separates group availability
5. **`convertOptimizedDataToReadable()` utility** for data conversion when needed
6. **Integrated ScheduleConstants** for centralized day/time reference management

### Data Organization

**referenceData Object:** Contains day names, time slot names, and metadata (stored once per preset)
**groupAvailability Array:** Maps day/slot indices to available group numbers
**optimizedAssignments Array:** Flat list using indices for compatibility and easy access

### Benefits

1. **Simplified Structure**: One document per preset instead of multiple collections
2. **Easier Queries**: Direct document access by preset name
3. **Better Performance**: Fewer Firestore operations and simpler data retrieval
4. **Cleaner Organization**: All preset data contained in a single document
5. **Backward Compatibility**: Maintains flat assignment structure for existing code

## Usage Examples

### Accessing Schedule Data

**To get reference data and assignments:**

```kotlin
val presetDoc = db.collection("generatedSchedules").document("AM 10B").get().await()

// Get reference data
val referenceData = presetDoc.get("referenceData") as? Map<String, Any>
val dayNames = referenceData?.get("dayNames") as? List<String> ?: emptyList()
val timeSlotNames = referenceData?.get("timeSlotNames") as? List<String> ?: emptyList()

// Get optimized assignments
val optimizedAssignments = presetDoc.get("optimizedAssignments") as? List<Map<String, Any>>
```

**To get assignments for a specific time slot using indices:**

```kotlin
val optimizedAssignments = presetDoc.get("optimizedAssignments") as? List<Map<String, Any>>
val mondaySlot1Assignments = optimizedAssignments?.filter {
    it["dayIndex"] == 0 && it["slotIndex"] == 0  // Monday (0), First slot (0)
}
```

**To convert optimized data to readable format:**

```kotlin
// Using the utility function in ViewModel
val readableData = viewModel.convertOptimizedDataToReadable(
    optimizedAssignments = optimizedAssignmentsList,
    referenceData = referenceDataObject
)
```

**To get group availability for a specific slot:**

```kotlin
val groupAvailability = presetDoc.get("groupAvailability") as? List<Map<String, Any>>
val mondaySlot1Groups = groupAvailability?.find {
    it["dayIndex"] == 0 && it["slotIndex"] == 0
}?.get("availableGroups") as? List<String>
```

### Testing the New Structure

A test function `testDocumentBasedPresetStorageStructure()` has been added to the ViewModel to demonstrate the expected structure with sample data.

## Example Firestore Structure

After generating a schedule with presets "AM 10B", "CS 9A", and "UB 12G":

```
generatedSchedules/ (collection)
├── AM 10B (document with all AM 10B assignments)
├── CS 9A (document with all CS 9A assignments)
└── UB 12G (document with all UB 12G assignments)
```

## Student Assignment Data Location

**Primary Location:** Within each preset document in the `optimizedAssignments` array (flat structure)
**Reference Data:** Within each preset document in the `referenceData` object
**Group Data:** Within each preset document in the `groupAvailability` array

Each assignment contains:

- Volunteer name, roll number, and group (NO volunteer ID)
- Day index and slot index (instead of string names)
- Reference to day/time names stored separately
- Group availability stored separately by indices

## Testing the Implementation

To test the optimized data structure:

1. **Call the test function** in ScheduleGenerationViewModel:

   ```kotlin
   viewModel.testOptimizedDataStructure()
   ```

2. **Generate a real schedule** and observe the Firestore console to see the optimized document structure

3. **Check the logs** for detailed information about the storage process and data optimizations

## Migration Notes

- This replaces the previous collection-per-preset approach
- Each teaching slot preset gets exactly one document
- Document names match teaching slot preset names exactly
- All assignment data for a preset is contained within its document
- Maintains compatibility with existing assignment data formats

## Key Differences from Previous Implementation

**Before (Collection-per-preset):**

```
AM 10B/ (collection)
├── mon slot 1 (document)
├── mon slot 2 (document)
└── tue slot 1 (document)
```

**Now (Document-per-preset):**

```
generatedSchedules/ (collection)
├── AM 10B (document with all slots)
├── CS 9A (document with all slots)
└── UB 12G (document with all slots)
```
