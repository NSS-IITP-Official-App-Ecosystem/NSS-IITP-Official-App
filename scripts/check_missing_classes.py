#!/usr/bin/env python3
"""
Script to check the ttwStudents collection and list all volunteers 
where 'classesPerWeek' field is missing or null.

Usage:
    python check_missing_classes.py <path_to_service_account_key.json>

Output:
    A sorted list (alphabetical by Name) of volunteers:
    - Name
    - Roll Number
"""

import sys
import json

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("Error: firebase-admin package not installed.")
    print("Install it with: pip install firebase-admin")
    sys.exit(1)


def initialize_firebase(json_path: str):
    """Initialize Firebase with the provided credentials."""
    try:
        # Check if it's a valid JSON first
        with open(json_path, 'r') as f:
            data = json.load(f)
        
        if 'project_info' in data:
            print("Error: You provided google-services.json which is for Android apps.")
            print("For server-side Python access, you need a Service Account Key JSON file.")
            sys.exit(1)
        
        cred = credentials.Certificate(json_path)
        firebase_admin.initialize_app(cred)
        return firestore.client()
    
    except Exception as e:
        print(f"Firebase initialization error: {e}")
        sys.exit(1)


def check_missing_classes(db):
    """Fetch students with missing classesPerWeek."""
    missing_data_volunteers = []
    
    try:
        print("Fetching ttwStudents collection...")
        docs = db.collection('ttwStudents').stream()
        
        count = 0
        for doc in docs:
            count += 1
            data = doc.to_dict()
            
            # Check if field is missing or None
            # User requirement: "number of classes null (field not present)"
            if 'classesPerWeek' not in data or data['classesPerWeek'] is None:
                # Try to get Name (case insensitive search for key if needed, but assuming 'name' or 'Name')
                name = data.get('name', data.get('Name', 'Unknown'))
                roll_no = doc.id  # Document ID is usually the roll number
                
                missing_data_volunteers.append({
                    'name': name,
                    'roll_no': roll_no
                })
        
        print(f"Scanned {count} documents.")
        return missing_data_volunteers
    
    except Exception as e:
        print(f"Error fetching data: {e}")
        sys.exit(1)


def format_output(volunteers: list):
    """Format and print the sorted list."""
    if not volunteers:
        print("\nGood news! No volunteers found with missing 'classesPerWeek'.")
        return
    
    # Sort alphabetically by Name
    volunteers.sort(key=lambda x: x['name'].strip().lower())
    
    print("\n" + "=" * 50)
    print(f"Volunteers with Missing 'classesPerWeek' ({len(volunteers)})")
    print("=" * 50)
    
    # Calculate widths for alignment
    name_width = max(len(v['name'].strip()) for v in volunteers)
    name_width = max(name_width, len("Name"))
    
    # Header with numeric column
    header = f"{'#':<3} | {'Name':<{name_width}} | Roll Number"
    separator = "-" * len(header)
    
    print(f"\n{header}")
    print(separator)
    
    for i, v in enumerate(volunteers, 1):
        print(f"{i:<3} | {v['name'].strip():<{name_width}} | {v['roll_no']}")
    
    print(separator)
    print(f"Total Count: {len(volunteers)}\n")


def main():
    if len(sys.argv) != 2:
        print("Usage: python check_missing_classes.py <path_to_service_account_key.json>")
        sys.exit(1)
    
    json_path = sys.argv[1]
    
    print(f"Initializing Firebase with: {json_path}")
    db = initialize_firebase(json_path)
    
    print("Checking for missing class data...")
    missing_volunteers = check_missing_classes(db)
    
    format_output(missing_volunteers)


if __name__ == "__main__":
    main()
