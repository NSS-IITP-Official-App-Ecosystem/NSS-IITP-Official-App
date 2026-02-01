import os
import sys
import collections
import firebase_admin
from firebase_admin import credentials, firestore

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
        # Check command line argument
        if len(sys.argv) > 1:
            key_path = sys.argv[1]
            if not os.path.exists(key_path):
                 print(f"Error: provided key path does not exist: {key_path}")
                 sys.exit(1)
        else:
            print("Service account key not found in common locations.")
            print("Usage: python list_unassigned_volunteers.py [path_to_service_account.json]")
            sys.exit(1)

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(key_path)
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

def list_unassigned(db):
    print("Fetching master list of volunteers from 'ttwStudents'...")
    
    volunteers_ref = db.collection('ttwStudents')
    volunteers_docs = volunteers_ref.stream()
    
    # Map: Roll Number -> Name
    # We use Roll Number as the unique ID
    all_volunteers = {}
    
    for doc in volunteers_docs:
        data = doc.to_dict()
        roll = data.get('rollNumber')
        name = data.get('name', 'Unknown')
        
        # Check if the volunteer is expected to have classes
        if data.get('classesPerWeek') == 0:
            continue

        if roll:
            all_volunteers[roll] = name
            
    print(f"Found {len(all_volunteers)} total volunteers in 'ttwStudents'.")
    
    print("Fetching schedules from 'generatedSchedules'...")
    schedules_ref = db.collection('generatedSchedules')
    schedules_docs = schedules_ref.stream()
    
    assigned_rolls = set()
    schedule_count = 0
    
    for doc in schedules_docs:
        schedule_count += 1
        data = doc.to_dict()
        
        # Check optimizedAssignments first, then assignments
        assignments = data.get('optimizedAssignments', [])
        if not assignments:
             assignments = data.get('assignments', [])
             
        for asn in assignments:
            roll = asn.get('volunteerRollNo')
            # Fallback to matching name if roll is missing (less reliable but useful)
            if not roll:
                # Try finding roll by name from our master list
                v_name = asn.get('volunteerName')
                if v_name:
                    # Inefficient reverse lookup but dataset is small
                    for r, n in all_volunteers.items():
                        if n == v_name:
                            roll = r
                            break
            
            if roll:
                assigned_rolls.add(roll)
                
    print(f"Scanned {schedule_count} schedules.")
    print(f"Found {len(assigned_rolls)} unique volunteers with assignments.")
    
    # Find unassigned
    unassigned = []
    for roll, name in all_volunteers.items():
        if roll not in assigned_rolls:
            unassigned.append({'name': name, 'roll': roll})
            
    # Sort by name
    unassigned.sort(key=lambda x: x['name'])
    
    print(f"\nFound {len(unassigned)} unassigned volunteers.")
    
    if unassigned:
        print("\n" + "="*50)
        print(f"{'Name':<30} | {'Roll Number'}")
        print("="*50)
        for v in unassigned:
            print(f"{v['name']:<30} | {v['roll']}")
        print("="*50)
        print(f"Total: {len(unassigned)}\n")

if __name__ == "__main__":
    db = init_firebase()
    list_unassigned(db)
