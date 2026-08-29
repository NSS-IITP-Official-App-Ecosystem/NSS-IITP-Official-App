import sys
import json
try:
    from firebase_admin import credentials, firestore, initialize_app
except:
    sys.exit(1)
cred = credentials.Certificate('../serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

users_docs = list(db.collection('users').stream())

has_chetna_or_prayatna = 0
has_prerna = 0

print("--- Spot Check: 5 Comma-Mashed Edge Cases ---")
comma_mashed_rolls = ['2501CE18', '2501ME22', '2501ME35', '2501ME43', '2502GT08'] # Wait, rolls might not match perfectly if name was duplicate, let's use names
comma_mashed_names = ['Dhruv nagar', 'Aditya Raj', 'MANAS DUBEY', 'T.Rohith reddy', 'ESLAVATH ANIL']

print("\n--- Spot Check: 6 Both-Wings Edge Cases ---")
both_wings_names = ['Ankesh Kumar', 'Eshan Bhaskar', 'Dinker Anand', 'Mahipal M', 'Aditya Gupta', 'Aditya Onam']

for doc in users_docs:
    d = doc.to_dict() or {}
    wings = d.get('wings', [])
    name = d.get('name')
    
    if any('chetna' in w.lower() or 'prayatna' in w.lower() for w in wings):
        has_chetna_or_prayatna += 1
        
    if any('prerna' in w.lower() for w in wings):
        has_prerna += 1
        
    if name in comma_mashed_names:
        print(f"Name: {name}")
        print(f"  Wings: {wings}")
        
    if name in both_wings_names:
        print(f"Name: {name}")
        print(f"  Wings: {wings}")

print("\n--- Summary Statistics ---")
print(f"Users with Chetna/Prayatna: {has_chetna_or_prayatna}")
print(f"Users with Prerna Wing: {has_prerna}")
