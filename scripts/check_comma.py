import sys
try:
    from firebase_admin import credentials, firestore, initialize_app
except:
    sys.exit(1)
cred = credentials.Certificate('serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
for u in users:
    d = u.to_dict() or {}
    wings = d.get('wings')
    
    if isinstance(wings, list):
        for w in wings:
            if 'Chetna' in w and 'Rural' in w:
                print(f"{d.get('name')}: {repr(w)}")
                for c in w:
                    if not c.isalnum() and not c.isspace():
                        print(f"  char: {c} (ord: {ord(c)})")
