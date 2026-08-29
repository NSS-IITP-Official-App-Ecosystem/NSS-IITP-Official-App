import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

roll = "2601CE55"
wing_name = "Rural Development Wing"

doc_ref = db.collection("users").document(roll)
doc = doc_ref.get()

if doc.exists:
    data = doc.to_dict()
    current_wings = data.get("wings", [])
    if not isinstance(current_wings, list):
        current_wings = [current_wings] if current_wings else []
    
    if wing_name not in current_wings:
        new_wings = list(set(current_wings + [wing_name]))
        doc_ref.update({"wings": new_wings})
        print(f"Updated {roll} ({data.get('name')}) -> wings: {new_wings}")
    else:
        print(f"{roll} ({data.get('name')}) already has '{wing_name}'. Current wings: {current_wings}")
else:
    print(f"User {roll} not found in Firestore.")
