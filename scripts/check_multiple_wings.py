import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
multi_wing_students = []
multi_wing_admins = []

for u in users:
    d = u.to_dict()
    wings = d.get('wings', [])
    if isinstance(wings, list) and len(wings) > 1:
        item = {
            'roll': u.id,
            'name': d.get('name', 'Unknown'),
            'userType': d.get('userType', 'student'),
            'wings': wings,
            'email': d.get('instituteOutlookId', '')
        }
        if item['userType'] == 'admin':
            multi_wing_admins.append(item)
        else:
            multi_wing_students.append(item)

print(f"Total users in DB: {len(users)}")
print(f"Total users with multiple wings: {len(multi_wing_students) + len(multi_wing_admins)}")
print(f"  - Students with multiple wings: {len(multi_wing_students)}")
print(f"  - Admins with multiple wings: {len(multi_wing_admins)}\n")

print("=" * 70)
print(" STUDENTS WITH MULTIPLE WINGS:")
print("=" * 70)
for u in sorted(multi_wing_students, key=lambda x: x['roll']):
    print(f"Roll: {u['roll']:<10} | Name: {u['name']:<30} | Wings: {', '.join(u['wings'])}")

print("\n" + "=" * 70)
print(" ADMINS WITH MULTIPLE WINGS:")
print("=" * 70)
for u in sorted(multi_wing_admins, key=lambda x: x['roll']):
    print(f"Roll: {u['roll']:<10} | Name: {u['name']:<30} | Wings: {', '.join(u['wings'])}")
