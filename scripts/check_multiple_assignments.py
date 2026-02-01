import os
import sys
import collections
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

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
        print("Service account key not found.")
        sys.exit(1)

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(key_path)
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

def check_multiple_assignments(db):
    print("Fetching schedules from 'generatedSchedules'...")
    
    collection_ref = db.collection('generatedSchedules')
    schedules = list(collection_ref.stream())
    
    print(f"Found {len(schedules)} schedules.")
    
    # Dictionary to store assignments per volunteer
    # Key: Roll Number (or Name if roll missing)
    # Value: List of assignment details {slot, subject, schedule}
    volunteer_assignments = collections.defaultdict(list)
    
    # Map roll number to name for display
    roll_to_name = {}
    
    for doc in schedules:
        data = doc.to_dict()
        schedule_name = doc.id
        
        # Get reference data for day/slot mapping
        ref_data = data.get('referenceData', {})
        day_names = ref_data.get('dayNames', ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"])
        slot_names = ref_data.get('timeSlotNames', [])
        
        # Check both assignments and optimizedAssignments
        assignments = data.get('optimizedAssignments', [])
        if not assignments:
             assignments = data.get('assignments', [])
             
        for asn in assignments:
            name = asn.get('volunteerName')
            roll = asn.get('volunteerRollNo')
            subject = asn.get('assignedSubject') or asn.get('subjectCode')
            
            if not name or not subject:
                continue
                
            # Use roll number as primary key if available, else name
            key = roll if roll else name
            if roll:
                roll_to_name[roll] = name
            else:
                roll_to_name[name] = name
            
            # Resolve Slot
            day_idx = asn.get('dayIndex', 0)
            slot_idx = asn.get('slotIndex', 0)
            
            standard_days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
            day_str = standard_days[day_idx] if 0 <= day_idx < 7 else f"Day {day_idx + 1}"
            slot_str = slot_names[slot_idx] if slot_idx < len(slot_names) else f"Slot {slot_idx}"
            
            assignment_info = {
                "schedule": schedule_name,
                "day": day_str,
                "time": slot_str,
                "subject": subject
            }
            
            volunteer_assignments[key].append(assignment_info)

    # Filter for >= 2 assignments
    multi_assigned = []
    
    for key, assignments in volunteer_assignments.items():
        if len(assignments) >= 2:
            name = roll_to_name.get(key, key)
            multi_assigned.append({
                "name": name,
                "key": key,
                "count": len(assignments),
                "assignments": assignments
            })
            
    # Sort by count descending
    multi_assigned.sort(key=lambda x: x['count'], reverse=True)
    
    print(f"\nFound {len(multi_assigned)} volunteers with 2 or more assignments.")
    
    if not multi_assigned:
        return

    print("\n" + "="*80)
    print(f"{'Volunteer Name':<25} | {'Count':<5} | {'Assignments (Slot - Subject)'}")
    print("="*80)
    
    for v in multi_assigned:
        print(f"{v['name']:<25} | {v['count']:<5} |")
        for idx, a in enumerate(v['assignments']):
            # Format: [Schedule] Day Time - Subject
            detail = f"   {idx+1}. [{a['schedule']}] {a['day']} {a['time']} - {a['subject']}"
            print(detail)
        print("-" * 80)

if __name__ == "__main__":
    db = init_firebase()
    check_multiple_assignments(db)
