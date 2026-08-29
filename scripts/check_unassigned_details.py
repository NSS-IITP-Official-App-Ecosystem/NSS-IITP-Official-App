import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
unassigned_students = []
unassigned_admins = []

for u in users:
    d = u.to_dict()
    wings = d.get('wings', [])
    if not isinstance(wings, list):
        wings = [wings] if wings else []
    
    if not wings:
        user_type = (d.get('userType') or 'student').lower().strip()
        item = {
            'roll': u.id,
            'name': d.get('name', 'Unknown'),
            'email': d.get('instituteOutlookId', ''),
            'userType': user_type,
            'hours': d.get('hours', 0.0)
        }
        if user_type == 'admin':
            unassigned_admins.append(item)
        else:
            unassigned_students.append(item)

print(f"Total unassigned students: {len(unassigned_students)}")
for s in unassigned_students:
    print(f"Roll: {s['roll']} | Name: {s['name']} | Email: {s['email']} | Hours: {s['hours']}")

if unassigned_admins:
    print(f"\nTotal unassigned admins: {len(unassigned_admins)}")
    for a in unassigned_admins:
        print(f"Roll: {a['roll']} | Name: {a['name']} | Email: {a['email']}")
