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

def check_group_availability(db):
    print("Scanning 'teachingSlotPresets' collection...")
    
    collection_ref = db.collection('teachingSlotPresets')
    docs = collection_ref.stream()
    
    # Store group counts
    group_counts = collections.Counter()
    
    total_slots_scanned = 0
    
    for doc in docs:
        data = doc.to_dict()
        slot_name = doc.id
        availability = data.get('availability', {})
        
        if not availability:
            continue
            
        # Iterate over days (Mon, Tue, etc.)
        for day, times in availability.items():
            # times is a map of "0": "1,2,3...", "1": "..."
            if isinstance(times, dict):
                for index, groups_str in times.items():
                    total_slots_scanned += 1
                    
                    if not groups_str:
                        continue
                        
                    # Handle if it's a string (expected) or list
                    if isinstance(groups_str, str):
                        # Split by comma
                        groups = [g.strip() for g in groups_str.split(',') if g.strip()]
                    elif isinstance(groups_str, list):
                        groups = [str(g).strip() for g in groups_str]
                    else:
                        continue
                        
                    group_counts.update(groups)
    
    print(f"\nScanned {total_slots_scanned} individual time slots across all presets.")
    print("\nGroup Availability Frequency (Group ID: Count):")
    print("---------------------------------------------")
    
    # Sort by Group ID (assuming numeric)
    # Filter potential non-numeric group IDs to bottom
    
    numeric_groups = []
    other_groups = []
    
    for g, count in group_counts.items():
        if g.isdigit():
            numeric_groups.append((int(g), count))
        else:
            other_groups.append((g, count))
            
    numeric_groups.sort(key=lambda x: x[0])
    other_groups.sort(key=lambda x: x[0])
    
    # Print Logic
    all_sorted = numeric_groups + other_groups
    
    for group_id, count in all_sorted:
        print(f"Group {group_id}: {count}")

if __name__ == "__main__":
    db = init_firebase()
    check_group_availability(db)
