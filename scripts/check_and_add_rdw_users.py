import os
import sys
import firebase_admin
from firebase_admin import credentials, auth, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

WING_NAME = "Rural Development Wing"

TARGET_USERS = [
    {
        "name": "Daksh Joshi",
        "roll": "2601CS03",
        "email": "daksh_2601cs03@iitp.ac.in"
    },
    {
        "name": "Dulapalli Leela Krishna",
        "roll": "2601ME23",
        "email": "dulapalli_2601me23@iitp.ac.in"
    },
    {
        "name": "Drishti",
        "roll": "2601MM11",
        "email": "drishti_2601mm11@iitp.ac.in"
    },
    {
        "name": "Arka Das",
        "roll": "2602PH02",
        "email": "arka_2602ph02@iitp.ac.in"
    }
]

print("=" * 65)
print(f" Checking & Updating Users -> {WING_NAME}")
print("=" * 65)

for u in TARGET_USERS:
    roll = u["roll"].strip().upper()
    name = u["name"].strip()
    email = u["email"].strip().lower()

    doc_ref = db.collection("users").document(roll)
    doc = doc_ref.get()

    if doc.exists:
        data = doc.to_dict()
        current_wings = data.get("wings", [])
        if not isinstance(current_wings, list):
            current_wings = [current_wings] if current_wings else []

        new_wings = list(set(current_wings + [WING_NAME]))
        update_payload = {
            "wings": new_wings,
            "userType": "student"
        }
        doc_ref.update(update_payload)
        print(f"[EXISTING UPDATED] {roll} ({data.get('name', name)}) -> wings: {new_wings}")
    else:
        # Create Auth user if doesn't exist
        auth_uid = None
        try:
            user_record = auth.get_user_by_email(email)
            auth_uid = user_record.uid
            print(f"  Auth user found for {email} (UID: {auth_uid})")
        except auth.UserNotFoundError:
            user_record = auth.create_user(
                email=email,
                password="Temp@1234",
                display_name=name
            )
            auth_uid = user_record.uid
            print(f"  Created new Auth user for {email} (UID: {auth_uid})")
        except Exception as e:
            print(f"  Auth error for {email}: {e}")

        # Create document in Firestore
        new_user_data = {
            "name": name,
            "instituteOutlookId": email,
            "rollNumber": roll,
            "uid": auth_uid,
            "userType": "student",
            "wings": [WING_NAME],
            "eventsAttended": 0,
            "hours": 0.0,
            "sem1Hours": 0.0,
            "sem2Hours": 0.0,
            "unreadCount": 0
        }
        doc_ref.set(new_user_data)
        print(f"[CREATED NEW USER] {roll} ({name}) -> wings: ['{WING_NAME}'] (UID: {auth_uid})")

print("=" * 65)
