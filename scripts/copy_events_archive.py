import sys
try:
    from firebase_admin import credentials, firestore, initialize_app
except:
    sys.exit(1)

cred = credentials.Certificate('../serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

source_col = "NSS_Events_Attendence"
dest_col = "NSS_Events_Attendence_2024_2025"

print(f"Deep copying {source_col} to {dest_col}...")

events = list(db.collection(source_col).stream())
batch = db.batch()
total_events = 0
total_attendance = 0
batch_count = 0

for event_doc in events:
    # 1. Copy the Parent Event Document
    dest_event_ref = db.collection(dest_col).document(event_doc.id)
    batch.set(dest_event_ref, event_doc.to_dict())
    total_events += 1
    batch_count += 1
    
    # 2. Copy the 'attendance' subcollection for this event
    attendance_docs = list(event_doc.reference.collection("attendance").stream())
    for att_doc in attendance_docs:
        dest_att_ref = dest_event_ref.collection("attendance").document(att_doc.id)
        batch.set(dest_att_ref, att_doc.to_dict())
        total_attendance += 1
        batch_count += 1
        
        # Firestore batch limit is 500
        if batch_count >= 400:
            batch.commit()
            batch = db.batch()
            batch_count = 0

if batch_count > 0:
    batch.commit()

print(f"SUCCESS: Copied {total_events} events and {total_attendance} attendance records.")
