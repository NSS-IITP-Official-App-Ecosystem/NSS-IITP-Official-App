import sys
try:
    from firebase_admin import credentials, firestore, initialize_app
except:
    sys.exit(1)
cred = credentials.Certificate('../serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

events = list(db.collection('NSS_Events_Attendence_2024_2025').stream())
chetna_count = 0
prayatna_count = 0

for doc in events:
    d = doc.to_dict() or {}
    wings = d.get('wings', [])
    if not isinstance(wings, list): continue
    
    has_chetna = any('chetna' in w.lower() for w in wings)
    has_prayatna = any('prayatna' in w.lower() for w in wings)
    
    if has_chetna: chetna_count += 1
    if has_prayatna: prayatna_count += 1
        
print(f"Historic Events with Chetna Wing: {chetna_count}")
print(f"Historic Events with Prayatna Wing: {prayatna_count}")
