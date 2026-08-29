import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

docs = list(db.collection('ttwStudents').stream())
batch = db.batch()
count = 0

for d in docs:
    ref = db.collection('ttwStudents').document(d.id)
    batch.update(ref, {"classesPerWeek": 1})
    count += 1

batch.commit()
print(f"Set classesPerWeek = 1 for all {count} documents in ttwStudents.")
