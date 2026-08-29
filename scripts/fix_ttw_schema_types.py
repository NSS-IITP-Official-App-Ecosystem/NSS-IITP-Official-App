import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# Clean schema for ttwStudents
ttw_docs = list(db.collection('ttwStudents').stream())
print(f"Fixing schema for {len(ttw_docs)} documents in ttwStudents...")

batch = db.batch()
count = 0

for doc in ttw_docs:
    data = doc.to_dict()
    roll = doc.id.upper().strip()
    
    name = str(data.get('name', '')).strip()
    roll_num = roll
    email = str(data.get('email', '')).strip()
    acad_group = str(data.get('academicGroup', '')).strip()
    if not acad_group and 'group' in data:
        acad_group = str(data['group']).strip()
        
    prefs = data.get('subjectPreferences', [])
    classes = data.get('classesPerWeek', 1)
    modes = data.get('teachingModes', [])
    
    clean_doc = {
        "name": name,
        "rollNumber": roll_num,
        "email": email,
        "academicGroup": acad_group,
        "classesPerWeek": classes,
        "subjectPreferences": prefs
    }
    if modes:
        clean_doc["teachingModes"] = modes
        
    ref = db.collection('ttwStudents').document(roll)
    batch.set(ref, clean_doc) # Replaces document cleanly without int 'group' or 'classCount' fields
    count += 1

batch.commit()
print(f"Successfully cleaned all {count} documents in ttwStudents.")
