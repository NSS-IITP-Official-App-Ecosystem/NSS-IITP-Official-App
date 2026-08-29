import firebase_admin
from firebase_admin import credentials, firestore

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()
events = db.collection("NSS_Events_Attendence").get()
print(f"Total events currently in database: {len(events)}")
print("-" * 50)

for evt in events:
    data = evt.to_dict()
    name = data.get("eventName", evt.id)
    date = data.get("eventDate", "Unknown")
    
    # get attendance subcollection count
    att_docs = evt.reference.collection("attendance").get()
    print(f"- {name} (Date: {date}) | Attendance count: {len(att_docs)}")
