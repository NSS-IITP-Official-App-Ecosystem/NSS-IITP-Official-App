import sys
import json
try:
    from firebase_admin import credentials, firestore, initialize_app
except ImportError:
    sys.exit(1)

cred = credentials.Certificate('../serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

archive_col = "NSS_Events_Attendence_2024_2025"
backup_file = "backups/20260813_003646_events.json"

print(f"Restoring events from {backup_file} into {archive_col}...")

with open(backup_file, 'r', encoding='utf-8') as f:
    events_data = json.load(f)

batch = db.batch()
total_events = 0
total_attendance = 0
batch_count = 0

for event in events_data:
    event_id = event.pop("_doc_id")
    attendance_data = event.pop("_attendance_subcollection", [])
    
    # 1. Restore Event Document
    dest_event_ref = db.collection(archive_col).document(event_id)
    batch.set(dest_event_ref, event)
    total_events += 1
    batch_count += 1
    
    # 2. Restore Attendance Subcollection
    for att in attendance_data:
        att_id = att.pop("_doc_id")
        dest_att_ref = dest_event_ref.collection("attendance").document(att_id)
        batch.set(dest_att_ref, att)
        total_attendance += 1
        batch_count += 1
        
        if batch_count >= 400:
            batch.commit()
            batch = db.batch()
            batch_count = 0

if batch_count > 0:
    batch.commit()

print(f"SUCCESS: Restored {total_events} events and {total_attendance} attendance records.")
