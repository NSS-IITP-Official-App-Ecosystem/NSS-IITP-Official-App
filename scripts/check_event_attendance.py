import os
import sys
from datetime import datetime, timedelta, timezone
from fpdf import FPDF

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

def check_event_attendance(db):
    print("\n--- Event Attendance Check ---")
    
    # Try both probable collection names
    col_name = "NSS_Events_Attendence" 
    events_ref = db.collection(col_name)
    
    # Check if collection exists/has data
    if not list(events_ref.limit(1).stream()):
        col_name = "NSS_Events_Attendance"
        events_ref = db.collection(col_name)

    print(f"Fetching events from '{col_name}'...")
    
    try:
        events = list(events_ref.stream())
        if not events:
            print("No events found.")
            return

        event_list = []
        for event_doc in events:
            data = event_doc.to_dict()
            event_id = event_doc.id
            event_name = get_event_name_from_id(event_id)
            event_date = data.get('eventDate') or event_id
            created_at = data.get('createdAt')
            sort_date = parse_date_for_sort(data.get('eventDate'), created_at)
            
            event_list.append({
                'id': event_id,
                'name': event_name,
                'date_str': event_date,
                'sort_date': sort_date,
                'data': data
            })

        # Sort by date descending (newest first)
        event_list.sort(key=lambda x: x['sort_date'], reverse=True)

        print(f"\n{'='*80}")
        print(f"{'S.No':<5} | {'Event Name':<40} | {'Date':<20}")
        print(f"{'='*80}")

        for i, event in enumerate(event_list, 1):
            name = event['name']
            if len(name) > 38:
                name = name[:35] + "..."
            print(f"{i:<5} | {name:<40} | {event['date_str']:<20}")
        
        print(f"{'='*80}")

        while True:
            choice = input("\nEnter the serial number of the event to view attendance (or 'q' to quit): ").strip()
            if choice.lower() == 'q':
                return
            
            try:
                idx = int(choice) - 1
                if 0 <= idx < len(event_list):
                    selected_event = event_list[idx]
                    break
                else:
                    print("Invalid serial number. Please try again.")
            except ValueError:
                print("Invalid input. Please enter a number.")

        print(f"\nFetching attendance for: {selected_event['name']} ({selected_event['date_str']})")
        
        attendees = selected_event['data'].get('attendees', [])
        
        if not attendees:
            print("No attendees found for this event.")
            return

        records = []
        for att in attendees:
            roll_number = att.get('rollNumber') or att.get('roll_number') or "Unknown"
            student_name = att.get('name') or att.get('studentName') or "Unknown"
            
            # Marking details
            marked_by = "Unknown"
            scanned_from = att.get('scannedFrom')
            
            if isinstance(scanned_from, dict):
                marked_by = scanned_from.get('adminName') or scanned_from.get('adminRollNumber') or "Unknown"
            elif att.get('markedBy'): # Fallback for potential older data/schema
                marked_by = att.get('markedBy')
            
            # Handle 'manualEntry' vs 'isManual' (fallback)
            is_manual = att.get('manualEntry', False)
            if 'isManual' in att: # Fallback
                 is_manual = att.get('isManual')
            
            method = "Manual" if is_manual else "QR Scan"
            
            # Timestamp
            timestamp = att.get('scanTimestamp') or att.get('scan_timestamp')
            time_str = get_ist_time(timestamp) if timestamp else "No Time Recorded"

            records.append({
                "roll_number": roll_number,
                "student_name": student_name,
                "method": method,
                "time_str": time_str,
                "marked_by": marked_by,
                # Keep the timestamp for sorting
                "sort_time": timestamp if timestamp else None
            })

        # Sort attendees by time marked (ascending arrival time)
        def get_sort_time(rec):
            ts = rec['sort_time']
            if ts and hasattr(ts, 'seconds'):
                return ts.seconds
            return 0
            
        records.sort(key=get_sort_time)

        print(f"\nAttendance Records ({len(records)} present)")
        print(f"{'='*120}")
        print(f"{'Roll Number':<15} | {'Student Name':<25} | {'Method':<10} | {'Attendance Time':<25} | {'Marked By'}")
        print(f"{'='*120}")

        for rec in records:
            name = rec['student_name']
            if len(name) > 23:
                name = name[:20] + "..."
            print(f"{rec['roll_number']:<15} | {name:<25} | {rec['method']:<10} | {rec['time_str']:<25} | {rec['marked_by']}")

        print(f"{'='*120}")

        save_pdf = input("\nDo you want to save these attendance records as a PDF? (y/n): ").strip().lower()
        if save_pdf == 'y':
            try:
                pdf = FPDF(orientation="landscape")
                pdf.add_page()
                pdf.set_font("helvetica", size=12)

                # Title
                pdf.cell(0, 10, text=f"Attendance Records: {selected_event['name']} ({selected_event['date_str']})", new_x="LMARGIN", new_y="NEXT", align='C')
                pdf.cell(0, 10, text=f"Total Present: {len(records)}", new_x="LMARGIN", new_y="NEXT", align='C')
                pdf.ln(5)

                # Table Header
                pdf.set_font("helvetica", style='B', size=10)
                col_widths = [35, 65, 30, 60, 65]
                headers = ['Roll Number', 'Student Name', 'Method', 'Attendance Time', 'Marked By']
                
                for i in range(5):
                    pdf.cell(col_widths[i], 10, text=headers[i], border=1, align='C')
                pdf.ln()

                # Table Rows
                pdf.set_font("helvetica", size=10)
                for rec in records:
                    pdf.cell(col_widths[0], 10, text=str(rec['roll_number']), border=1)
                    
                    # Truncate student name if very long to fit column
                    name = str(rec['student_name'])
                    if len(name) > 30:
                        name = name[:27] + "..."
                    # Replace characters that might cause unicode errors in basic helvetica font
                    name = name.encode('latin-1', 'replace').decode('latin-1')
                    pdf.cell(col_widths[1], 10, text=name, border=1)
                    
                    pdf.cell(col_widths[2], 10, text=str(rec['method']), border=1, align='C')
                    pdf.cell(col_widths[3], 10, text=str(rec['time_str']), border=1, align='C')
                    
                    marked_by = str(rec['marked_by'])
                    if len(marked_by) > 30:
                        marked_by = marked_by[:27] + "..."
                    marked_by = marked_by.encode('latin-1', 'replace').decode('latin-1')
                    pdf.cell(col_widths[4], 10, text=marked_by, border=1)
                    
                    pdf.ln()

                # Sanitize event name for filename
                clean_name = "".join([c if c.isalnum() or c.isspace() else "_" for c in str(selected_event['name'])]).strip().replace(" ", "_")
                clean_date = str(selected_event['date_str']).replace(" ", "_").replace(":", "-")
                filename = f"Attendance_{clean_name}_{clean_date}.pdf"
                
                pdf.output(filename)
                print(f"Successfully generated PDF: {filename}")
                
            except Exception as e:
                print(f"Error generating PDF: {e}")

    except Exception as e:
        print(f"Error extracting data: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    db = init_firebase()
    print("Successfully connected to Firestore.")
    check_event_attendance(db)
