import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

wing_name = "Rural Development Wing"
rolls = ["2501AI21", "2501AI42", "2501CE39", "2503ME05"]

for r in rolls:
    ref = db.collection("users").document(r)
    doc = ref.get()
    if doc.exists:
        data = doc.to_dict()
        current_wings = data.get("wings", [])
        if not isinstance(current_wings, list):
            current_wings = [current_wings] if current_wings else []
        new_wings = list(set(current_wings + [wing_name]))
        ref.update({"wings": new_wings, "userType": "student"})
        print(f"Updated {r} ({data.get('name')}) -> wings: {new_wings}")
    else:
        print(f"User {r} not found.")
