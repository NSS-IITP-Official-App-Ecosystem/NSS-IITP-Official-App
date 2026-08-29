import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# Check if any admin exists in ttwStudents
users_db = {u.id.upper(): u.to_dict() for u in db.collection('users').stream()}
ttw_docs = list(db.collection('ttwStudents').stream())

deleted_admins = []
for d in ttw_docs:
    roll = d.id.upper()
    u = users_db.get(roll, {})
    if u.get('userType') == 'admin':
        db.collection('ttwStudents').document(d.id).delete()
        deleted_admins.append((roll, u.get('name')))

print(f"Deleted {len(deleted_admins)} admins from ttwStudents: {deleted_admins}")
print(f"Remaining pure student documents in ttwStudents: {len(ttw_docs) - len(deleted_admins)}")
