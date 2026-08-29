import re
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# Fetch all users from Firestore
users_db = {u.id.upper().strip(): u.to_dict() for u in db.collection('users').stream()}
ttw_docs = list(db.collection('ttwStudents').stream())

print(f"Total documents in ttwStudents to enrich: {len(ttw_docs)}")

batch = db.batch()
count = 0

for doc in ttw_docs:
    roll = doc.id.upper().strip()
    data = doc.to_dict()
    
    user_data = users_db.get(roll, {})
    name = user_data.get('name', 'Unknown')
    email = user_data.get('instituteOutlookId', '')
    
    # Extract numeric part from academicGroup (e.g. "G19" -> "19", "G2" -> "2")
    raw_group = str(data.get('academicGroup') or user_data.get('academicGroup') or '')
    match = re.search(r'\d+', raw_group)
    group_num_str = match.group(0) if match else "0"
    group_int = int(group_num_str)
    
    classes = data.get('classesPerWeek', 1)
    if classes is None:
        classes = 1
        
    enrich_payload = {
        "name": name,
        "rollNumber": roll,
        "email": email,
        "academicGroup": group_num_str,
        "group": group_int,
        "classCount": classes,
        "classesPerWeek": classes
    }
    
    ref = db.collection('ttwStudents').document(roll)
    batch.set(ref, enrich_payload, merge=True)
    count += 1

batch.commit()
print(f"Successfully enriched all {count} documents in 'ttwStudents' with name, rollNumber, email, and numeric group!")
