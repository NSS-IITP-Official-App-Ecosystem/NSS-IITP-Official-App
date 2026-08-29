import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

ttw_docs = list(db.collection('ttwStudents').stream())
print(f"Total documents in ttwStudents: {len(ttw_docs)}\n")

user_map = {}
for u in db.collection('users').stream():
    d = u.to_dict()
    user_map[u.id] = (d.get('name', ''), d.get('wings', []))

entries = []
for doc in ttw_docs:
    data = doc.to_dict()
    roll = doc.id
    name, wings = user_map.get(roll, ('Unknown', []))
    classes = data.get('classesPerWeek', 'N/A')
    prefs = data.get('subjectPreferences', [])
    entries.append((roll, name, wings, classes, prefs))

entries.sort(key=lambda x: x[0])

print("Roll Number | Name | Wing | Classes/Wk | Subject Preferences")
print("-" * 80)
for r, n, w, c, p in entries:
    wings_str = ", ".join(w) if w else "None"
    prefs_str = ", ".join(p[:3]) if p else "None"
    print(f"{r:<10} | {n:<24} | {wings_str:<28} | {c:<10} | {prefs_str}...")
