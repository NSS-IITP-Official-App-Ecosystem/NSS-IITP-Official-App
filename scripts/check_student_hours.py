import firebase_admin
from firebase_admin import credentials, firestore

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

print("Checking student hours in the 'users' collection...\n")
users = db.collection("users").get()

non_zero_users = 0
total_users = 0

for doc in users:
    total_users += 1
    data = doc.to_dict()
    sem1 = data.get("sem1Hours", 0)
    sem2 = data.get("sem2Hours", 0)
    events_att = data.get("eventsAttended", 0)
    
    # Check if they have anything other than 0
    if sem1 != 0 or sem2 != 0 or events_att != 0:
        non_zero_users += 1
        if non_zero_users <= 10:
            print(f"[{doc.id}] {data.get('name', 'Unknown')}: sem1={sem1}, sem2={sem2}, eventsAttended={events_att}")

print("-" * 40)
print(f"Total Users: {total_users}")
print(f"Users with non-zero hours: {non_zero_users}")
