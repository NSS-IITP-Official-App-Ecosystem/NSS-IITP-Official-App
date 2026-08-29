import csv
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

SUBJECT_MAP = {
    "mathematics": "Maths",
    "maths": "Maths",
    "physics": "Physics",
    "chemistry": "Chemistry",
    "biology": "Biology",
    "social science": "SST",
    "sst": "SST",
    "english": "English",
    "sanskrit": "Sanskrit",
    "science": "Science",
    "gk": "GK",
    "general knowledge": "GK",
    "hindi": "Hindi"
}

ALL_SUBJECTS_DEFAULT_ORDER = [
    "Maths", "Physics", "Chemistry", "Biology", "SST", "English", "Sanskrit", "Science", "GK", "Hindi"
]

with open("scripts/teaching_preference_responses.csv", "r", encoding="utf-8") as f:
    rows = list(csv.reader(f))

data_rows = [r for r in rows[1:] if len(r) > 2 and r[2].strip()]

# Deduplicate responses keeping the latest submission
responses_by_roll = {}
for r in data_rows:
    timestamp_str = r[0].strip()
    name = r[1].strip()
    roll = r[2].strip().upper()
    email = r[3].strip().lower()
    contact = r[4].strip()
    acad_group = r[5].strip().upper()
    
    pref1 = r[6].strip()
    pref2 = r[7].strip()
    pref3 = r[8].strip()
    pref4 = r[13].strip() if len(r) > 13 else ''
    pref5 = r[14].strip() if len(r) > 14 else ''
    pref6 = r[15].strip() if len(r) > 15 else ''
    
    mode1 = r[17].strip() if len(r) > 17 else ''
    mode2 = r[18].strip() if len(r) > 18 else ''
    mode3 = r[19].strip() if len(r) > 19 else ''
    mode4 = r[20].strip() if len(r) > 20 else ''
    
    sanskrit = r[21].strip() if len(r) > 21 else 'No'
    
    # Map subjects in order
    ordered_prefs = []
    for raw_p in [pref1, pref2, pref3, pref4, pref5, pref6]:
        clean_p = raw_p.strip().lower()
        if clean_p in SUBJECT_MAP:
            norm = SUBJECT_MAP[clean_p]
            if norm not in ordered_prefs:
                ordered_prefs.append(norm)
    
    if sanskrit.lower() == 'yes' and 'Sanskrit' not in ordered_prefs:
        ordered_prefs.append('Sanskrit')
        
    # Append any remaining subjects to complete the preference list
    for s in ALL_SUBJECTS_DEFAULT_ORDER:
        if s not in ordered_prefs:
            ordered_prefs.append(s)
            
    teaching_modes = [m for m in [mode1, mode2, mode3, mode4] if m]
    
    responses_by_roll[roll] = {
        "timestamp": timestamp_str,
        "name": name,
        "roll": roll,
        "email": email,
        "contact": contact,
        "academicGroup": acad_group,
        "preferences": ordered_prefs,
        "teachingModes": teaching_modes,
        "sanskrit": sanskrit
    }

print(f"Total unique respondents to import: {len(responses_by_roll)}")

batch = db.batch()
count = 0
total_batches = 0

for roll, data in responses_by_roll.items():
    # 1. Update ttwStudents
    ttw_ref = db.collection("ttwStudents").document(roll)
    ttw_payload = {
        "classesPerWeek": 1,
        "academicGroup": data["academicGroup"],
        "subjectPreferences": data["preferences"],
    }
    if data["teachingModes"]:
        ttw_payload["teachingModes"] = data["teachingModes"]
        
    batch.set(ttw_ref, ttw_payload, merge=True)
    
    # 2. Update users document with academicGroup if present
    if data["academicGroup"]:
        user_ref = db.collection("users").document(roll)
        batch.set(user_ref, {"academicGroup": data["academicGroup"]}, merge=True)
        
    count += 1
    if count % 200 == 0:
        batch.commit()
        total_batches += 1
        batch = db.batch()

if count % 200 != 0:
    batch.commit()
    total_batches += 1

print(f"Successfully imported teaching preferences for all {count} volunteers into 'ttwStudents' and 'users' collection.")
