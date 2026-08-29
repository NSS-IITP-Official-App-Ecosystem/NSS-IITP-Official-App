import csv
import re
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

with open("scripts/teaching_preference_responses.csv", "r", encoding="utf-8") as f:
    rows = list(csv.reader(f))

header = rows[0]
data_rows = [r for r in rows[1:] if len(r) > 2 and r[2].strip()]

print(f"Total response rows: {len(data_rows)}")

responses_by_roll = {}
for r in data_rows:
    timestamp_str = r[0].strip()
    name = r[1].strip()
    roll = r[2].strip().upper()
    email = r[3].strip().lower()
    contact = r[4].strip()
    acad_group = r[5].strip()
    
    pref1 = r[6].strip()
    pref2 = r[7].strip()
    pref3 = r[8].strip()
    pref4 = r[13].strip() if len(r) > 13 else ''
    pref5 = r[14].strip() if len(r) > 14 else ''
    pref6 = r[15].strip() if len(r) > 15 else ''
    sanskrit = r[21].strip() if len(r) > 21 else 'No'
    
    prefs = [p for p in [pref1, pref2, pref3, pref4, pref5, pref6] if p]
    if sanskrit.lower() == 'yes' and 'Sanskrit' not in prefs:
        prefs.append('Sanskrit')
    
    responses_by_roll[roll] = {
        "timestamp": timestamp_str,
        "name": name,
        "roll": roll,
        "email": email,
        "contact": contact,
        "academicGroup": acad_group,
        "preferences": prefs,
        "sanskrit": sanskrit
    }

print(f"Unique student roll numbers in response sheet: {len(responses_by_roll)}")

users_db = {u.id: u.to_dict() for u in db.collection('users').stream()}
ttw_students_db = {d.id: d.to_dict() for d in db.collection('ttwStudents').stream()}

in_ttw_wing_count = 0
not_in_ttw_wing = []
in_ttw_collection = 0
not_in_ttw_collection = []

for roll, data in responses_by_roll.items():
    u = users_db.get(roll)
    wings = u.get('wings', []) if u else []
    
    if "Teaching and Technical Wing" in wings:
        in_ttw_wing_count += 1
    else:
        not_in_ttw_wing.append((roll, data['name'], wings))
        
    if roll in ttw_students_db:
        in_ttw_collection += 1
    else:
        not_in_ttw_collection.append((roll, data['name'], data['preferences']))

print(f"\n--- Analysis vs Firestore Users ---")
print(f"Responses assigned to TTW Wing: {in_ttw_wing_count}")
print(f"Responses NOT in TTW Wing: {len(not_in_ttw_wing)}")
for r, n, w in not_in_ttw_wing:
    print(f"  - {r}: {n} (Current Wings in DB: {w})")

print(f"\n--- Analysis vs 'ttwStudents' Collection ---")
print(f"Already present in 'ttwStudents': {in_ttw_collection}")
print(f"NOT in 'ttwStudents' (New to import): {len(not_in_ttw_collection)}")
for r, n, p in not_in_ttw_collection:
    print(f"  - {r}: {n} -> Prefs: {p}")
