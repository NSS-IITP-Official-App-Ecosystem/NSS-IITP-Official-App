import json
import re
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# 31 students added today
TODAY_ADDED_ROLLS = {
    '2601MM25', '2601CT08', '2601CB02', '2601CS85', '2601MM32', '2601ME64', 
    '2601CB30', '2601EC03', '2601EC43', '2601MC16', '2601ME59', '2601CE40', 
    '2603ME13', '2601MM19', '2601MM05', '2601PH23', '2601PH13', '2601CB46', 
    '2601CE37', '2601CE22', '2601CT09', '2601ME82', '2601PH30', '2602CS06', 
    '2603CS01', '2601CE15', '2602CS02', '2601CB12', '2601EE33', '2601ME36', 
    '2501CE41'
}

# Recover original classesPerWeek from previous inspection
# Let's extract from the log file
log_path = r'C:\Users\sm273\.gemini\antigravity-ide\brain\8006f607-7f91-4ebe-be78-17a52f90cfb8\.system_generated\logs\transcript_full.jsonl'
full_output = None
with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        if 'Vivek Goyal' in line and '2601CE17' in line:
            data = json.loads(line)
            full_output = data.get('content', '')
            break

original_classes = {}
if full_output:
    for line in full_output.splitlines():
        parts = [p.strip() for p in line.split('|')]
        if len(parts) >= 4:
            roll = parts[0]
            classes_str = parts[3]
            if re.match(r'^[0-9A-Z]{8}$', roll):
                if classes_str.isdigit():
                    original_classes[roll] = int(classes_str)
                else:
                    original_classes[roll] = None # Was N/A

print(f"Recovered {len(original_classes)} previous student records.")

batch = db.batch()
count_restored = 0
count_kept_1 = 0

for roll, original_val in original_classes.items():
    ref = db.collection('ttwStudents').document(roll)
    if original_val is not None:
        batch.update(ref, {"classesPerWeek": original_val})
    else:
        batch.update(ref, {"classesPerWeek": firestore.DELETE_FIELD})
    count_restored += 1

for roll in TODAY_ADDED_ROLLS:
    ref = db.collection('ttwStudents').document(roll)
    batch.set(ref, {"classesPerWeek": 1}, merge=True)
    count_kept_1 += 1

batch.commit()
print(f"Successfully restored previous classesPerWeek for {count_restored} original students.")
print(f"Set classesPerWeek = 1 for {count_kept_1} newly added students.")
