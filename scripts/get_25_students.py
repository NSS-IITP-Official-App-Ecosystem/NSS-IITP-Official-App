import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
res = []

for u in users:
    d = u.to_dict()
    r = u.id.strip().upper()
    user_type = (d.get('userType') or 'student').lower().strip()
    if r.startswith('25') and user_type == 'student':
        res.append({
            'roll': r,
            'name': d.get('name', ''),
            'email': d.get('instituteOutlookId', ''),
            'wings': d.get('wings', []),
            'hours': d.get('hours', 0.0)
        })

res.sort(key=lambda x: x['roll'])
print(f"Total students with roll starting with 25: {len(res)}\n")
for item in res:
    print(f"Roll: {item['roll']:<10} | Name: {item['name']:<28} | Wings: {item['wings']} | Hours: {item['hours']}")
