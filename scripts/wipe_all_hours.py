import firebase_admin
from firebase_admin import credentials, firestore
import sys

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

print("Starting complete hours wipe for all users...\n")
users = db.collection("users").get()

batch = db.batch()
count = 0
total_users = 0

for doc in users:
    total_users += 1
    
    # We enforce a hard reset of these exact fields for EVERY user
    reset_data = {
        "hours": 0,
        "sem1Hours": 0,
        "sem2Hours": 0,
        "eventsAttended": 0,
        "eventsList": []
    }
    
    batch.update(doc.reference, reset_data)
    count += 1
    
    # Firestore batches are limited to 500 writes
    if count >= 450:
        batch.commit()
        batch = db.batch()
        count = 0

if count > 0:
    batch.commit()

print(f"✅ Successfully wiped hours and event lists for all {total_users} users (including Admins).")
print("The database is completely fresh for the new session.")
