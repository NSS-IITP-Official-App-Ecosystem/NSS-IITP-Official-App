import firebase_admin
from firebase_admin import credentials, firestore
import json

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

print("Fetching sample records for event '15_Aug_4'...\n")
att_docs = db.collection("NSS_Events_Attendence").document("15_Aug_4").collection("attendance").limit(5).get()

if not att_docs:
    print("No records found or event does not exist.")
else:
    for doc in att_docs:
        data = doc.to_dict()
        
        # Convert timestamps/datetimes for JSON printing
        for key, value in data.items():
            if hasattr(value, 'isoformat'):
                data[key] = value.isoformat()
            # If it's a Firestore Timestamp, it might not have isoformat but we can cast to string
            elif hasattr(value, 'strftime'):
                data[key] = value.strftime('%Y-%m-%dT%H:%M:%SZ')
            else:
                data[key] = str(value)
                
        print(f"Document ID (Roll Number): {doc.id}")
        print(json.dumps(data, indent=2))
        print("-" * 40)
