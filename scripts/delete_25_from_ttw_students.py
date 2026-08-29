import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

docs = list(db.collection('ttwStudents').stream())
rolls_25 = [d.id for d in docs if d.id.startswith('25')]

print("=" * 65)
print(f" Deleting 25-series documents from 'ttwStudents'")
print(f" Total to delete: {len(rolls_25)}")
print("=" * 65)

# Firestore batch supports up to 500 operations per batch
batch = db.batch()
count = 0

for roll in rolls_25:
    doc_ref = db.collection('ttwStudents').document(roll)
    batch.delete(doc_ref)
    count += 1
    print(f"[{count}/{len(rolls_25)}] Deleting: {roll}")

batch.commit()
print("=" * 65)
print(f"✅ Successfully deleted all {count} 25-series records from 'ttwStudents'.")
print("=" * 65)
