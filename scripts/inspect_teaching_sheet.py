import csv
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

with open("scripts/teaching_preference_responses.csv", "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    rows = list(reader)

print(f"Total rows in sheet: {len(rows)}")
if rows:
    print(f"Header: {rows[0]}\n")
    for i, r in enumerate(rows[1:10], 1):
        print(f"Row {i}: {r}")

# Check current ttwStudents in Firestore
ttw_docs = {d.id: d.to_dict() for d in db.collection('ttwStudents').stream()}
print(f"\nCurrent documents in Firestore 'ttwStudents': {len(ttw_docs)}")
