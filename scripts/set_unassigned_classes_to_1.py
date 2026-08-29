import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

docs = list(db.collection('ttwStudents').stream())
not_set_rolls = []

for d in docs:
    val = d.to_dict().get('classesPerWeek')
    if val is None or val == 'Not Set':
        not_set_rolls.append(d.id)

print(f"Found {len(not_set_rolls)} students with classesPerWeek Not Set: {not_set_rolls}")

batch = db.batch()
for roll in not_set_rolls:
    ref = db.collection('ttwStudents').document(roll)
    batch.set(ref, {"classesPerWeek": 1}, merge=True)
    print(f"Setting classesPerWeek = 1 for {roll}")

batch.commit()
print("Successfully updated all of them to classesPerWeek = 1.")
