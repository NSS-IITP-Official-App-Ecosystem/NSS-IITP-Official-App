#!/usr/bin/env python3
"""
Script to export volunteer assignments from Firebase generatedSchedules collection.

Usage:
    python export_schedule.py <path_to_google_services_json>

Output:
    A sorted list of volunteer assignments showing:
    - Name
    - Assigned Slot (Time, School/Section)
    - Assigned Subject
"""

import sys
import json
from collections import defaultdict

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("Error: firebase-admin package not installed.")
    print("Install it with: pip install firebase-admin")
    sys.exit(1)


def get_project_id_from_google_services(json_path: str) -> str:
    """Extract the project ID from google-services.json file."""
    with open(json_path, 'r') as f:
        data = json.load(f)
    
    # google-services.json structure has project_info.project_id
    if 'project_info' in data and 'project_id' in data['project_info']:
        return data['project_info']['project_id']
    
    # Fallback for service account JSON
    if 'project_id' in data:
        return data['project_id']
    
    raise ValueError("Could not find project_id in the provided JSON file")


def initialize_firebase(json_path: str):
    """Initialize Firebase with the provided credentials."""
    try:
        # Check if it's a google-services.json or a service account key
        with open(json_path, 'r') as f:
            data = json.load(f)
        
        # If it's google-services.json, we need a service account key instead
        if 'project_info' in data:
            print("Error: You provided google-services.json which is for Android apps.")
            print("For server-side Python access, you need a Service Account Key JSON file.")
            print("\nTo get one:")
            print("1. Go to Firebase Console > Project Settings > Service Accounts")
            print("2. Click 'Generate new private key'")
            print("3. Save the downloaded JSON file and provide its path")
            sys.exit(1)
        
        # Initialize with service account credentials
        cred = credentials.Certificate(json_path)
        firebase_admin.initialize_app(cred)
        
        return firestore.client()
    
    except firebase_admin.exceptions.FirebaseError as e:
        print(f"Firebase initialization error: {e}")
        sys.exit(1)
    except FileNotFoundError:
        print(f"Error: File not found: {json_path}")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"Error: Invalid JSON file: {json_path}")
        sys.exit(1)


def fetch_schedules(db) -> list:
    """Fetch all documents from generatedSchedules collection."""
    schedules = []
    
    try:
        docs = db.collection('generatedSchedules').stream()
        
        for doc in docs:
            data = doc.to_dict()
            data['_id'] = doc.id
            schedules.append(data)
        
        return schedules
    
    except Exception as e:
        print(f"Error fetching schedules: {e}")
        sys.exit(1)


def extract_assignments(schedules: list) -> list:
    """Extract all assignments from the schedules."""
    assignments = []
    
    for schedule in schedules:
        schedule_name = schedule.get('name', schedule.get('_id', 'Unknown'))
        
        # Get reference data for day/slot names
        reference_data = schedule.get('referenceData', {})
        day_names = reference_data.get('dayNames', [])
        time_slot_names = reference_data.get('timeSlotNames', [])
        
        # Get optimized assignments (primary) or fallback to assignments
        optimized_assignments = schedule.get('optimizedAssignments', [])
        if not optimized_assignments:
            optimized_assignments = schedule.get('assignments', [])
        
        for assignment in optimized_assignments:
            volunteer_name = assignment.get('volunteerName', '')
            volunteer_roll_no = assignment.get('volunteerRollNo', '')
            volunteer_group = assignment.get('volunteerGroup', '')
            day_index = assignment.get('dayIndex', 0)
            slot_index = assignment.get('slotIndex', 0)
            assigned_subject = assignment.get('assignedSubject', '') or assignment.get('subjectCode', '')
            
            # Skip if no volunteer name or no subject
            if not volunteer_name:
                continue
            
            # Get day and slot names
            day_name = day_names[day_index] if day_index < len(day_names) else f"Day {day_index + 1}"
            time_slot = time_slot_names[slot_index] if slot_index < len(time_slot_names) else f"Slot {slot_index + 1}"
            
            assignments.append({
                'name': volunteer_name,
                'roll_no': volunteer_roll_no,
                'group': volunteer_group,
                'time': time_slot,
                'day': day_name,
                'school_section': schedule_name,
                'subject': assigned_subject if assigned_subject else 'N/A'
            })
    
    return assignments


def format_output(assignments: list):
    """Format and print the assignments in a table format."""
    if not assignments:
        print("No assignments found.")
        return
    
    # Sort by name (primary), then by school (secondary), then by day
    day_order = {'Mon': 0, 'Tue': 1, 'Wed': 2, 'Thu': 3, 'Fri': 4, 'Sat': 5, 'Sun': 6}
    assignments.sort(key=lambda x: (
        x['name'].strip().lower(),
        x['school_section'],
        day_order.get(x['day'], 99),
        x['time']
    ))
    
    # Calculate column widths (stripping whitespace to ensure accurate width)
    name_width = max(len(a['name'].strip()) for a in assignments)
    name_width = max(name_width, len("Name"))
    
    # Shortened header to reduce visual gap if data is short
    slot_header = "Assigned Slot"
    slot_width = max(len(f"{a['time']}, {a['day']}, {a['school_section']}") for a in assignments)
    slot_width = max(slot_width, len(slot_header))
    
    subject_width = max(len(a['subject'].strip()) for a in assignments)
    subject_width = max(subject_width, len("Subject"))
    
    # Print header with 1 space before and after |
    # Using RIGHT ALIGNMENT (>) for Name so it sits closer to the pipe
    header = f"{'Name':>{name_width}} | {slot_header:<{slot_width}} | {'Subject':<{subject_width}}"
    separator = f"{'-' * name_width}-+-{'-' * slot_width}-+-{'-' * subject_width}"
    
    print("\n" + "=" * len(header))
    print("VOLUNTEER ASSIGNMENT REPORT")
    print("=" * len(header))
    print(f"\nTotal assignments: {len(assignments)}")
    print(f"Unique volunteers: {len(set(a['name'] for a in assignments))}\n")
    print(header)
    print(separator)
    
    # Print assignments
    for assignment in assignments:
        name = assignment['name'].strip()
        slot_info = f"{assignment['time']}, {assignment['day']}, {assignment['school_section']}"
        subject = assignment['subject'].strip()
        # Right align name so it is close to the pipe
        print(f"{name:>{name_width}} | {slot_info:<{slot_width}} | {subject:<{subject_width}}")
    
    print(separator)
    print(f"Total: {len(assignments)} assignments\n")


def main():
    if len(sys.argv) != 2:
        print("Usage: python export_schedule.py <path_to_service_account_key.json>")
        print("\nNote: You need a Firebase Service Account Key JSON file, not google-services.json")
        print("To get one:")
        print("  1. Go to Firebase Console > Project Settings > Service Accounts")
        print("  2. Click 'Generate new private key'")
        print("  3. Save the downloaded JSON file and provide its path")
        sys.exit(1)
    
    json_path = sys.argv[1]
    
    print(f"Initializing Firebase with: {json_path}")
    db = initialize_firebase(json_path)
    
    print("Fetching schedules from generatedSchedules collection...")
    schedules = fetch_schedules(db)
    print(f"Found {len(schedules)} schedule(s)")
    
    print("Extracting assignments...")
    assignments = extract_assignments(schedules)
    
    format_output(assignments)


if __name__ == "__main__":
    main()
