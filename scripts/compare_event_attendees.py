import os
import sys

# Try importing firebase_admin
try:
    import firebase_admin
    from firebase_admin import credentials
    from firebase_admin import firestore
except ImportError:
    print("Error: 'firebase-admin' package is missing.")
    print("Please run: pip install firebase-admin")
    sys.exit(1)

def get_attendees(db, collection_name, event_id):
    """
    Fetches the event document and returns a dictionary {roll_number: name}
    """
    doc_ref = db.collection(collection_name).document(event_id)
    doc = doc_ref.get()
    
    if not doc.exists:
        print(f"Error: Event '{event_id}' not found in collection '{collection_name}'.")
        return None

    data = doc.to_dict()
    attendees_list = data.get('attendees', [])
    
    if not attendees_list:
        print(f"Warning: Event '{event_id}' has no attendees.")
        return {}

    # Parse attendees array
    # Expected format: [{'roll_number': '...', 'name': '...'}, ...]
    attendees_map = {}
    for entry in attendees_list:
        # Check for snake_case (app.js) or camelCase logic just in case
        roll = entry.get('roll_number') or entry.get('rollNumber')
        name = entry.get('name') or "Unknown"
        
        if roll:
            attendees_map[roll.upper()] = name
            
    return attendees_map

def main():
    print("--- Compare Event Attendees ---")
    
    # 1. Get Service Account Key Path
    while True:
        key_path = input("Enter the absolute path to your serviceAccountKey.json: ").strip()
        key_path = key_path.strip("'\"")
        
        if not key_path:
            print("Path cannot be empty.")
            continue

        if os.path.exists(key_path):
            if os.path.isfile(key_path) and key_path.endswith('.json'):
                break
            elif os.path.isdir(key_path):
                json_files = [f for f in os.listdir(key_path) if f.endswith('.json')]
                if 'serviceAccountKey.json' in json_files:
                    key_path = os.path.join(key_path, 'serviceAccountKey.json')
                    print(f"Using: {key_path}")
                    break
                elif len(json_files) == 1:
                    key_path = os.path.join(key_path, json_files[0])
                    print(f"Using: {key_path}")
                    break
        print(f"Error: Valid JSON key file not found.")

    # 2. Initialize Firebase
    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(key_path)
            firebase_admin.initialize_app(cred)
        else:
            print("Firebase already initialized.")
            
        db = firestore.client()
        print("Successfully connected to Firestore.")
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

    # 3. Determine Collection Name
    # The codebase uses 'NSS_Events_Attendence' (Typo), but UI might show 'Attendance'.
    # We'll check both.
    col_name = "NSS_Events_Attendence" 
    
    # Simple check to see if the collection has docs
    test_docs = list(db.collection(col_name).limit(1).stream())
    if not test_docs:
        print(f"Note: '{col_name}' seems empty. Trying 'NSS_Events_Attendance'...")
        col_name = "NSS_Events_Attendance"

    print(f"\nUsing Collection: {col_name}")
    print("Listing recent events to help you choose...")
    
    # List recent events to help user copy-paste IDs
    try:
        events_ref = db.collection(col_name)
        # Limit to 20 to avoid spamming
        all_events = events_ref.limit(20).stream() 
        print("\nAvailable Events (ID):")
        for ev in all_events:
            print(f" - {ev.id}")
            
    except Exception as e:
        print(f"Error listing events: {e}")

    # 4. Get Event IDs
    print("\n--- Enter Event IDs to Compare ---")
    event1_id = input("Event 1 ID: ").strip()
    event2_id = input("Event 2 ID: ").strip()

    if not event1_id or not event2_id:
        print("Error: Both Event IDs are required.")
        return

    # 5. Fetch Data
    print(f"\nFetching attendees for '{event1_id}'...")
    atts1 = get_attendees(db, col_name, event1_id)
    
    print(f"Fetching attendees for '{event2_id}'...")
    atts2 = get_attendees(db, col_name, event2_id)

    if atts1 is None or atts2 is None:
        print("Aborting due to missing event(s).")
        return

    # 6. Compare
    rolls1 = set(atts1.keys())
    rolls2 = set(atts2.keys())

    only_in_1 = sorted(list(rolls1 - rolls2))
    only_in_2 = sorted(list(rolls2 - rolls1))

    # 7. Output Results
    print("\n" + "="*60)
    print(f"PRESENT IN '{event1_id}' BUT NOT IN '{event2_id}' ({len(only_in_1)})")
    print("="*60)
    if only_in_1:
        for r in only_in_1:
            print(f"{r} - {atts1[r]}")
    else:
        print("None.")

    print("\n" + "="*60)
    print(f"PRESENT IN '{event2_id}' BUT NOT IN '{event1_id}' ({len(only_in_2)})")
    print("="*60)
    if only_in_2:
        for r in only_in_2:
            print(f"{r} - {atts2[r]}")
    else:
        print("None.")

    print("\n" + "="*60)
    print("Comparison Complete.")

if __name__ == "__main__":
    main()
