import csv
import firebase_admin
from firebase_admin import credentials, firestore

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

print("Reading admins_2026.csv and granting admin access...\n")

admin_rolls = []
with open("scripts/admins_2026.csv", "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    header = next(reader) # skip header line
    for row in reader:
        # Check if row is not empty and has the roll number column
        if len(row) >= 3 and row[2].strip():
            roll = row[2].strip().upper()
            admin_rolls.append(roll)

print(f"Parsed {len(admin_rolls)} roll numbers from the Google Sheet.")

batch = db.batch()
count = 0
updated = 0

for roll in admin_rolls:
    doc_ref = db.collection("users").document(roll)
    # Using merge=True safely updates them to admin without overwriting their existing profile
    batch.set(doc_ref, {"userType": "admin"}, merge=True)
    
    count += 1
    updated += 1
    
    # Firestore batches allow max 500 operations
    if count >= 450:
        batch.commit()
        batch = db.batch()
        count = 0

if count > 0:
    batch.commit()

print(f"✅ Successfully promoted {updated} students to Admin!")
