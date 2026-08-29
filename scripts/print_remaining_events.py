import sys
try:
    from firebase_admin import credentials, firestore, initialize_app
except:
    sys.exit(1)
cred = credentials.Certificate('../serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

events = list(db.collection('NSS_Events_Attendence').stream())
print("Remaining Events in NSS_Events_Attendence:")
for doc in events:
    d = doc.to_dict()
    print(f"ID: {doc.id}")
    print(f"Name: {d.get('eventName', d.get('name', 'N/A'))}")
    print(f"Date: {d.get('eventDate', 'N/A')}")
    print(f"Created/Added by: {d.get('addedBy', 'N/A')}")
    print("---")
