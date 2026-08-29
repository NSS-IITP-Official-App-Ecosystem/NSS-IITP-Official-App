import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
same_name_users = []

for u in users:
    d = u.to_dict()
    name = (d.get('name') or '').strip()
    parts = [p.strip() for p in name.split() if p.strip()]
    if len(parts) >= 2:
        first = parts[0].lower()
        last = parts[-1].lower()
        if first == last:
            same_name_users.append({
                'roll': u.id,
                'name': name,
                'email': d.get('instituteOutlookId', ''),
                'userType': d.get('userType', ''),
                'wings': d.get('wings', [])
            })

same_name_users.sort(key=lambda x: x['roll'])

print(f"Found {len(same_name_users)} users where first and last name are the same:\n")
for u in same_name_users:
    wings_str = ", ".join(u['wings']) if u['wings'] else "Unassigned"
    print(f"Roll: {u['roll']:<10} | Name: {u['name']:<22} | Email: {u['email']:<32} | Type: {u['userType']:<8} | Wing: {wings_str}")
