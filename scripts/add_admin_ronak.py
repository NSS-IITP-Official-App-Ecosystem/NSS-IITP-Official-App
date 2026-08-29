import firebase_admin
from firebase_admin import credentials, auth, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

roll = "2501PH10"
name = "Ronak Jakhar"
email = "rounak_2501ph10@iitp.ac.in"
wing = "Rural Development Wing"
user_type = "admin"

print("=" * 60)
print(f"Adding/Updating Admin: {roll} ({name})")
print("=" * 60)

auth_uid = None
try:
    user_record = auth.get_user_by_email(email)
    auth_uid = user_record.uid
    print(f"Found existing Auth user: UID={auth_uid}")
except auth.UserNotFoundError:
    user_record = auth.create_user(
        email=email,
        password="Temp@1234",
        display_name=name
    )
    auth_uid = user_record.uid
    print(f"Created new Auth user: UID={auth_uid}")
except Exception as e:
    print(f"Auth error: {e}")

doc_ref = db.collection("users").document(roll)
doc = doc_ref.get()

if doc.exists:
    data = doc.to_dict()
    current_wings = data.get("wings", [])
    if not isinstance(current_wings, list):
        current_wings = [current_wings] if current_wings else []
    
    new_wings = list(set(current_wings + [wing]))
    update_payload = {
        "name": name,
        "instituteOutlookId": email,
        "userType": user_type,
        "wings": new_wings
    }
    if auth_uid:
        update_payload["uid"] = auth_uid
        
    doc_ref.update(update_payload)
    print(f"Updated existing Firestore user: {roll} -> userType={user_type}, wings={new_wings}")
else:
    new_data = {
        "name": name,
        "instituteOutlookId": email,
        "rollNumber": roll,
        "uid": auth_uid,
        "userType": user_type,
        "wings": [wing],
        "eventsAttended": 0,
        "hours": 0.0,
        "sem1Hours": 0.0,
        "sem2Hours": 0.0,
        "unreadCount": 0
    }
    doc_ref.set(new_data)
    print(f"Created new Firestore user: {roll} -> userType={user_type}, wings=['{wing}']")

print("=" * 60)
print("SUCCESS: User added as admin.")
print("=" * 60)
