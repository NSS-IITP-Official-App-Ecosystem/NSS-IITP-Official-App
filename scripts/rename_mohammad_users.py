import firebase_admin
from firebase_admin import credentials, auth, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

NAME_UPDATES = {
    "2601ME06": "Mohammad Ali",
    "2601CS12": "Mohammad Husain",
    "2601CB30": "Mohammad Ibrahim",
    "2601CE43": "Mohammad Kamran"
}

print("=" * 60)
print(" Updating Names in Firestore and Firebase Auth")
print("=" * 60)

for roll, new_name in NAME_UPDATES.items():
    doc_ref = db.collection("users").document(roll)
    doc = doc_ref.get()
    
    if doc.exists:
        data = doc.to_dict()
        old_name = data.get("name")
        uid = data.get("uid")
        
        # 1. Update Firestore
        doc_ref.update({"name": new_name})
        print(f"Firestore: [{roll}] '{old_name}' -> '{new_name}'")
        
        # 2. Update Auth display_name if UID exists
        if uid:
            try:
                auth.update_user(uid, display_name=new_name)
                print(f"  Auth: Display name updated to '{new_name}' (UID: {uid})")
            except Exception as e:
                print(f"  Auth Error: {e}")
    else:
        print(f"Not found: {roll}")

print("=" * 60)
