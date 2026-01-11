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

def init_firebase():
    """Locates service account key and initializes Firebase."""
    
    # Common locations to check
    possible_paths = [
        "C:/Users/itses/CodeVault/Projects/Keys/IITP App/service-account.json",
        "serviceAccountKey.json",
        "scripts/serviceAccountKey.json",
        "../serviceAccountKey.json",
        "../../serviceAccountKey.json"
    ]
    
    key_path = None
    for path in possible_paths:
        if os.path.exists(path):
            key_path = os.path.abspath(path)
            print(f"Found service account key at: {key_path}")
            break
            
    if not key_path:
        print("Service account key not found in common locations.")
        while True:
            user_input = input("Enter the absolute path to your serviceAccountKey.json: ").strip()
            # Handle drag-and-drop quotes
            user_input = user_input.strip("'\"")
            
            if os.path.exists(user_input):
                key_path = user_input
                break
            print("Invalid path. Please try again.")

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(key_path)
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

def get_event_rolls(db):
    # Try both probable collection names
    col_name = "NSS_Events_Attendence" # As seen in other scripts
    
    events_ref = db.collection(col_name)
    # Check if we get any docs, if not try the other spelling
    if not list(events_ref.limit(1).stream()):
        col_name = "NSS_Events_Attendance"
        events_ref = db.collection(col_name)

    print(f"\nFetching events from '{col_name}'...")
    
    try:
        # Stream all events (ID and Name if possible)
        # Using stream() to get all
        events = list(events_ref.stream())
        
        if not events:
            print("No events found.")
            return

        # Sort events by ID or Timestamp for better UX? ID seems event date prefixed usually
        events.sort(key=lambda x: x.id, reverse=True) # Assuming date prefix like "22_Dec_...", newest first roughly

        print("\nAvailable Events:")
        for idx, doc in enumerate(events):
             # Try to get a readable name
            data = doc.to_dict()
            name = data.get('eventName') or data.get('title') or ""
            print(f"{idx + 1}. {doc.id} {f'({name})' if name else ''}")

        while True:
            try:
                selection = input("\nSelect an event number: ").strip()
                if not selection: continue
                
                sel_idx = int(selection) - 1
                if 0 <= sel_idx < len(events):
                    selected_doc = events[sel_idx]
                    break
                else:
                    print("Invalid number.")
            except ValueError:
                print("Please enter a number.")

        print(f"\nFetching attendees for: {selected_doc.id}")
        
        doc_snapshot = selected_doc.reference.get()
        event_data = doc_snapshot.to_dict()
        
        attendees = event_data.get('attendees', [])
        
        if not attendees:
            print("No attendees found for this event.")
            return

        roll_numbers = []
        for att in attendees:
            # Handle camelCase vs snake_case
            roll = att.get('rollNumber') or att.get('roll_number')
            if roll:
                roll_numbers.append(roll)
        
        # Sort for neatness
        roll_numbers.sort()
        
        print(f"\nFound {len(roll_numbers)} attendees.")
        print("Space separated list:")
        print("-" * 20)
        print(" ".join(roll_numbers))
        print("-" * 20)
        
        # Copy suggestion
        print("(You can copy the line above)")

    except Exception as e:
        print(f"Error fetching data: {e}")

if __name__ == "__main__":
    db = init_firebase()
    print("Successfully connected to Firestore.")
    get_event_rolls(db)
