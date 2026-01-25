# Scheduling Algorithm Logic

This document details the algorithm used for assigning volunteers to teaching slots.

## 1. Core Principle: "Hardest First" (TFV)

The algorithm prioritization relies on **TFV (Total Free Volunteers)**.

*   **Definition**: TFV represents the total number of volunteers currently available to take a specific slot.
*   **Calculation**:
    1.  For a given slot, we look at the **Available Groups** (e.g., "Groups 1-3, 5").
    2.  We expand these ranges into individual groups (1, 2, 3, 5).
    3.  We sum the current count of *unassigned* (or partially assigned) volunteers in each of these groups along with the groups available.
    4.  **TFV = Sum of available volunteers in eligible groups.**
    *   *Correction/Clarification*: A volunteer contributes 1 to the TFV count if they are available, regardless of how many classes they still need to teach. This is a "Head Count", not a "Class Count".
*   **Strategy**: The algorithm always attempts to fill the slot with the **lowest TFV** first.
    *   *Why?* A slot with a low TFV (e.g., only 2 volunteers available) is critical. If we use those volunteers elsewhere, this slot might become impossible to fill.

## 2. Volunteer Selection Process

When attempting to fill a slot (the one with the lowest TFV), the system follows this hierarchy:

### Step A: Identify Target Subject
The slot may require multiple subjects (e.g., "Maths" needs 2 classes, "Physics" needs 1).
1.  It looks at the `subjectPriorities` list for that slot.
2.  It picks the subject with the **highest priority** (lowest priority number) that still has a `classCount > 0`.

### Step B: Filter Eligible Volunteers
It searches the pool of `unassignedVolunteers` for candidates who match **ALL** these criteria:
1.  **Availability**: Their group is listed in the slot's "Available Groups".
2.  **Capacity**: They still have classes to teach (`volunteer.classCount > 0`).
3.  **Adjacency Constraint & Daily Limit**:
    *   **Max 2 Classes Per Day**: A volunteer cannot be assigned more than 2 classes in a single day.
    *   **Same School & Adjacency**:
        *   If the volunteer has *no* other assignments on this day -> **Eligible**.
        *   If they *do* have assignments (currently 1, max allowed is 2):
            1.  **Same School**: The new slot must be at the **same school** (First 2 characters of school name must match).
            2.  **Strictly Adjacent**: The gap between the two slots must be **between 0 and 20 minutes** (inclusive).
                *   *Example*: 11:40-12:20 is adjacent to 12:20-13:00 (0 gap).
                *   *Example*: 11:00-11:40 is adjacent to 12:00-12:40 (20 min gap).
4.  **Subject Preference**: They must have requested the "Target Subject" (from Step A).

4.  **Subject Constraint (Same-Day Protocol)**:
    *   **Rule**: A specific subject (e.g., "Maths") cannot be taught more than once per day for the same class (school preset).
    *   **Check**: Before assigning a subject to a slot, the system checks if "Maths" has already been assigned to *any* other slot on the same day for this school.
    *   **Effect on Algorithm**:
        *   We select the **Highest Priority Subject** that still needs classes.
        *   If this subject is blocked by the Same-Day rule, we **DO NOT** fallback to the next priority subject.
        *   Instead, we **SKIP this slot entirely** for the current pass. We wait for a different slot (on a different day) to open up for this high-priority subject.
        *   *Why?* Falling back to a lower priority subject might fill up the slot, leaving no room for the high-priority subject later, which is unacceptable.

### Step C: Ranking (Interview Score)
Eligible volunteers are sorted by their **Interview Score** in descending order. Higher-scoring volunteers get priority for their preferred subjects.

### Step D: Round-Robin Preferences
To ensure fairness, the auto-assignment runs in "Rounds":
1.  **Round 1 (1st Preference)**:
    *   Iterate through **ALL** unassigned slots, starting from the **Lowest TFV** (Hardest to fill).
    *   Try to fill each slot using only volunteers who have the target subject as their **1st Preference**.
    *   If a slot matches, assign it, **recalculate TFVs**, and restart the scan from the (new) hardest slot.
    *   If a slot *fails* to find a 1st-preference match, **SKIP** it and try the *next* hardest slot.
    *   Continue until *no* unassigned slot can be filled with a 1st-preference volunteer.
2.  **Round 2 (2nd Preference)**:
    *   Repeat the same process (Hardest -> Easiest) but now checking for **2nd Preference** matches.
3.  **Round 3+**: Continue for subsequent preferences.

*Note*: This ensures we maximize 1st-preference matches across the board before resorting to 2nd choices, even for "easier" slots.

## 3. Assignment Execution (The "Feedback Loop")

When a volunteer is successfully assigned to a slot, several state updates occur immediately, which influence the next iteration of the algorithm:

1.  **Slot Update**:
    *   The volunteer's name, ID, and the **Assigned Subject** are stamped on the slot.
    *   The `classCount` for that specific subject is decremented for **ALL** slots belonging to the same school (e.g., if "Maths" need was 2, it becomes 1 for the entire school's schedule).

2.  **Volunteer Update**:
    *   The volunteer's personal `classCount` is decremented (e.g., they need 2 classes, now they need 1).
    *   If their `classCount` drops to 0, they are marked as fully `isAssigned` and removed from the active pool for future slots.

3.  **Adjacency Tracking**:
    *   The system records this assignment (Day + Time + School) for this volunteer. This is referenced in the "Adjacency Constraint" check (Step B.3) for any future assignments on the same day.

4.  **Global Counter Update**:
    *   The global count of available volunteers for that specific **Group** is decremented by 1.

5.  **TFV Recalculation (Critical)**:
    *   Because the "Global Group Count" changed (Step 4), the TFV for *all other slots* that rely on that group will decrease.
    *   *Example*: If Group 5 had 10 volunteers and we assigned one, now it has 9. Every future slot accepting Group 5 now has a slightly lower TFV.
    *   This might change which slot is considered the "Hardest" (Lowest TFV) for the next iteration.

## Summary Checklist

- [ ] **TFV**: Calculate available heads per slot. Pick lowest.
- [ ] **Subject**: Pick highest priority subject need.
- [ ] **Filter**: Group avail + Class capacity + Adjacency rules + Subject Preference match.
- [ ] **Rank**: Sort by Interview Score.
- [ ] **Update**: Reduce volunteer capacity, reduce subject need, reduce group count.
- [ ] **Recalculate**: Update TFV for the whole grid based on new group counts.
