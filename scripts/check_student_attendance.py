import os
import sys
from datetime import datetime, timedelta, timezone

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

def get_ist_time(timestamp):
    """Converts a Firestore timestamp or datetime object to a readable IST string."""
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
            return "Invalid Timestamp"

        return dt.strftime("%d-%b-%Y %I:%M:%S %p")
    except Exception:
        return "Error Parsing Time"

def get_event_name_from_id(doc_id):
    """
    Parses event name from document ID.
    Format: day_month_event_name_with_underscores
    """
    parts = doc_id.split('_')
    if len(parts) >= 3:
        # Join all parts after day and month (index 2 onwards)
        raw_name = " ".join(parts[2:])
        # Replace remaining underscores if any (though split consumes them, the original logic implies this)
        # Actually split('_') removes separator. We just rejoin with space.
        return raw_name
    return "Unknown Event"

def parse_date_for_sort(date_str, created_at=None):
    """Parses date string or uses created_at timestamp for sorting."""
    if date_str:
        clean_date = date_str.strip()
        formats = [
            "%d %b %Y", # 05 Feb 2026
            "%d %B %Y", # 05 February 2026
            "%Y-%m-%d", # 2026-02-05
        ]
        for fmt in formats:
            try:
                return datetime.strptime(clean_date, fmt)
            except ValueError:
                continue
    
    # Fallback to created_at if available
    if created_at:
        try:
             if hasattr(created_at, 'seconds'):
                return datetime.fromtimestamp(created_at.seconds)
        except:
            pass

    return datetime.min

def check_student_attendance(db):
    print("\n--- Student Attendance History Check ---")
    roll_number = input("Enter Student Roll Number: ").strip() # Case insensitive match later?
    
    if not roll_number:
        print("Roll number cannot be empty.")
        return

    # Try both probable collection names
    col_name = "NSS_Events_Attendence" 
    events_ref = db.collection(col_name)
    
    # Check if collection exists/has data
    if not list(events_ref.limit(1).stream()):
        col_name = "NSS_Events_Attendance"
        events_ref = db.collection(col_name)

    print(f"Scanning events in '{col_name}'...")
    
    total_events_present = 0
    records = []

    try:
        events = list(events_ref.stream())
        
        for event_doc in events:
            data = event_doc.to_dict()
            attendees = data.get('attendees', [])
            
            # Find the student in the attendees list
            student_record = None
            for att in attendees:
                # Check for roll number (handle potential case sensitivity/whitespace)
                att_roll = att.get('rollNumber') or att.get('roll_number')
                if att_roll and str(att_roll).strip().lower() == roll_number.lower():
                    student_record = att
                    break
            
            if student_record:
                total_events_present += 1
                
                # Extract details
                event_id = event_doc.id
                
                # Try getting name from data first (e.g. description), else parse ID
                event_name = get_event_name_from_id(event_id)
                
                # Truncate event name if too long for table
                if len(event_name) > 28:
                    event_name = event_name[:25] + "..."
                
                event_date = data.get('eventDate') or event_id
                
                # Determine sort date
                created_at = data.get('createdAt')
                sort_date = parse_date_for_sort(data.get('eventDate'), created_at)

                # Marking details
                marked_by = "Unknown"
                scanned_from = student_record.get('scannedFrom')
                
                if isinstance(scanned_from, dict):
                    marked_by = scanned_from.get('adminName') or scanned_from.get('adminRollNumber') or "Unknown"
                elif student_record.get('markedBy'): # Fallback for potential older data/schema
                    marked_by = student_record.get('markedBy')
                
                # Handle 'manualEntry' vs 'isManual' (fallback)
                is_manual = student_record.get('manualEntry', False)
                if 'isManual' in student_record: # Fallback
                     is_manual = student_record.get('isManual')
                
                method = "Manual" if is_manual else "QR Scan"
                
                # Timestamp
                timestamp = student_record.get('scanTimestamp') or student_record.get('scan_timestamp')
                time_str = get_ist_time(timestamp) if timestamp else "No Time Recorded"

                records.append({
                    "name": event_name,
                    "date_str": event_date,
                    "sort_date": sort_date,
                    "method": method,
                    "time_str": time_str,
                    "marked_by": marked_by
                })

        # Sort by date descending (newest first)
        records.sort(key=lambda x: x['sort_date'], reverse=True)

        print(f"\nResults for Roll Number: {roll_number}")
        print(f"{'='*150}")
        print(f"{'Event Name':<30} | {'Date':<15} | {'Method':<10} | {'Attendance Marked Time':<25} | {'Marked By'}")
        print(f"{'='*150}")

        for rec in records:
             print(f"{rec['name']:<30} | {rec['date_str']:<15} | {rec['method']:<10} | {rec['time_str']:<25} | {rec['marked_by']}")

        print(f"{'='*150}")
        print(f"Total Events Present: {total_events_present}")

    except Exception as e:
        print(f"Error extracting data: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    db = init_firebase()
    print("Successfully connected to Firestore.")
    check_student_attendance(db)
