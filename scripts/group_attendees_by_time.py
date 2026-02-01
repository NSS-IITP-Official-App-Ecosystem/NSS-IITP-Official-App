import os
import sys
from datetime import datetime, timedelta, timezone
from collections import defaultdict

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

def group_attendees_by_time(db):
    # Try both probable collection names
    col_name = "NSS_Events_Attendence"
    
    events_ref = db.collection(col_name)
    if not list(events_ref.limit(1).stream()):
        col_name = "NSS_Events_Attendance"
        events_ref = db.collection(col_name)

    print(f"\nFetching events from '{col_name}'...")
    
    try:
        # Stream all events
        events = list(events_ref.stream())
        
        if not events:
            print("No events found.")
            return

        # Sort events by ID (usually date prefixed)
        events.sort(key=lambda x: x.id, reverse=True)

        print("\nAvailable Events:")
        for idx, doc in enumerate(events):
            data = doc.to_dict()
            name = data.get('eventName') or data.get('title') or ""
            attendee_count = len(data.get('attendees', []))
            print(f"{idx + 1}. {doc.id} {f'({name})' if name else ''} - {attendee_count} attendees")

        # Select event
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

        print(f"\nSelected Event: {selected_doc.id}")
        
        # Get grouping preference
        print("\nGroup attendees by:")
        print("1. Month")
        print("2. Day")
        print("3. Hour")
        
        while True:
            try:
                group_choice = input("Enter choice (1/2/3): ").strip()
                if group_choice in ['1', '2', '3']:
                    break
                print("Invalid choice.")
            except:
                print("Invalid input.")
        
        grouping = ['month', 'day', 'hour'][int(group_choice) - 1]
        
        # Fetch attendees
        doc_snapshot = selected_doc.reference.get()
        event_data = doc_snapshot.to_dict()
        
        attendees = event_data.get('attendees', [])
        
        if not attendees:
            print("No attendees found for this event.")
            return

        # Group attendees
        grouped = defaultdict(list)
        
        for att in attendees:
            # Get roll number
            roll = att.get('rollNumber') or att.get('roll_number') or 'Unknown'
            
            # Get timestamp (handle both camelCase and snake_case)
            timestamp = att.get('scanTimestamp') or att.get('scan_timestamp')
            
            if not timestamp:
                grouped['No Timestamp'].append(roll)
                continue
            
            # Convert Firestore timestamp to datetime (IST Hardcoded)
            try:
                ist = timezone(timedelta(hours=5, minutes=30))

                if hasattr(timestamp, 'seconds'):
                    # Firestore Timestamp -> UTC -> IST
                    dt = datetime.fromtimestamp(timestamp.seconds, timezone.utc).astimezone(ist)
                elif isinstance(timestamp, datetime):
                    if timestamp.tzinfo is None:
                        dt = timestamp.replace(tzinfo=timezone.utc).astimezone(ist)
                    else:
                        dt = timestamp.astimezone(ist)
                else:
                    # Try parsing as string if needed
                    grouped['Invalid Timestamp'].append(roll)
                    continue
                    
                # Create grouping key
                if grouping == 'month':
                    key = dt.strftime("%B %Y")  # e.g., "July 2025"
                elif grouping == 'day':
                    key = dt.strftime("%d %B")  # e.g., "24 July"
                else:  # hour
                    key = dt.strftime("%d %B, %I %p")  # e.g., "24 July, 02 PM"
                
                grouped[key].append(roll)
                
            except Exception as e:
                grouped['Error Processing'].append(roll)
        
        # Sort groups and display
        print(f"\n{'='*60}")
        print(f"Attendees grouped by {grouping.upper()}")
        print(f"{'='*60}\n")
        
        sorted_keys = sorted(grouped.keys())
        
        for key in sorted_keys:
            rolls = sorted(grouped[key])
            print(f"{key} = {' '.join(rolls)}")
            print(f"  (Total: {len(rolls)})\n")
        
        print(f"{'='*60}")
        print(f"Total Attendees: {len(attendees)}")

    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    db = init_firebase()
    print("Successfully connected to Firestore.")
    group_attendees_by_time(db)
