import os
import sys
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import json

def init_firebase():
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

def inspect_schedule(db, schedule_name):
    doc_ref = db.collection('generatedSchedules').document(schedule_name)
    doc = doc_ref.get()
    
    if not doc.exists:
        print(f"Schedule '{schedule_name}' not found.")
        return

    data = doc.to_dict()
    
    # 1. Print Reference Data
    ref_data = data.get('referenceData', {})
    print(f"\n--- Reference Data for {schedule_name} ---")
    print(json.dumps(ref_data, indent=2))
    
    # 2. Find Assignments for "Kajal Batra"
    print(f"\n--- Assignments for Kajal Batra in {schedule_name} ---")
    
    assignments = data.get('optimizedAssignments', []) or data.get('assignments', [])
    
    for asn in assignments:
        if "Kajal" in asn.get('volunteerName', ''):
            print(json.dumps(asn, indent=2))

if __name__ == "__main__":
    db = init_firebase()
    # Check 'RP 9G' as per the image
    inspect_schedule(db, "RP 9G")
