
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os
import sys

def initialize_firebase():
    """Initializes Firebase with the service account key."""
    # Default location
    key_path = "serviceAccountKey.json"
    
    # Check current directory
    if not os.path.exists(key_path):
        # Check parent directories
        possible_paths = [
            "serviceAccountKey.json",
            "../serviceAccountKey.json",
            "scripts/serviceAccountKey.json"
        ]
        
        for path in possible_paths:
            if os.path.exists(path):
                key_path = path
                break
    
    if not os.path.exists(key_path):
         print("Could not find serviceAccountKey.json in common locations.")
         try:
             key_path = input("Enter the absolute path to your serviceAccountKey.json: ").strip().replace('"', '')
         except EOFError:
             print("Error: No input provided.")
             sys.exit(1)
            
    try:
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
        print(f"Successfully connected to Firebase using {key_path}")
        return firestore.client()
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

def main():
    db = initialize_firebase()
    
    print("\n--- Fetching Events ---\n")
    
    events_ref = db.collection('NSS_Events_Attendence')
    # Use get() instead of stream() to be able to index them easily
    all_events = list(events_ref.stream())
    
    # Prepare list of events for display
    event_list = []
    for doc in all_events:
        data = doc.to_dict()
        event_name = data.get('eventName')
        if not event_name and data.get('name'):
            event_name = data.get('name')
        if not event_name:
            event_name = doc.id
            
        attendees_count = len(data.get('attendees', []))
        event_list.append({
            'doc': doc,
            'name': event_name,
            'count': attendees_count
        })
    
    # Sort by name for easier reading
    event_list.sort(key=lambda x: x['name'])
    
    # Display events
    for i, event in enumerate(event_list):
        print(f"{i+1}. {event['name']} ({event['count']} attendees)")
        
    if not event_list:
        print("No events found.")
        return

    print("\n")
    try:
        selection = input("Enter the number of the event to process: ")
        idx = int(selection) - 1
        if idx < 0 or idx >= len(event_list):
            print("Invalid selection.")
            return
    except ValueError:
        print("Invalid input.")
        return
        
    selected_event = event_list[idx]
    print(f"\nProcessing Event: {selected_event['name']}\n")
    
    attendees = selected_event['doc'].to_dict().get('attendees', [])
    
    manual_attendees = []
    ios_users = []
    
    for attendee in attendees:
        name = attendee.get('name', 'Unknown')
        roll = attendee.get('rollNumber', 'Unknown')
        if roll == 'Unknown' and attendee.get('roll_number'):
            roll = attendee.get('roll_number')
            
        device_id = attendee.get('deviceId', '')
        if not device_id and attendee.get('device_id'):
            device_id = attendee.get('device_id')
            
        device_id_str = str(device_id).strip()
        
        # Categorize
        if device_id_str == "":
            manual_attendees.append({'name': name, 'roll': roll})
        elif device_id_str.lower().startswith('web'):
            ios_users.append({'name': name, 'roll': roll})
            
    # Output results
    
    if manual_attendees:
        print("--- Manual Attendance ---")
        for p in manual_attendees:
            print(f"Name: {p['name']}, Roll: {p['roll']}")
        print(f"Total: {len(manual_attendees)}\n")
        
    if ios_users:
        print("--- IOS Users ---")
        for p in ios_users:
            print(f"Name: {p['name']}, Roll: {p['roll']}")
        print(f"Total: {len(ios_users)}\n")
        
    if not manual_attendees and not ios_users:
        print("No Manual or IOS (Web) attendees found for this event.")

if __name__ == "__main__":
    main()
