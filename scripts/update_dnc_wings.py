import os
import sys

try:
    import firebase_admin
    from firebase_admin import credentials
    from firebase_admin import firestore
except ImportError:
    print("Error: 'firebase-admin' package is missing.")
    print("Please run: pip install firebase-admin")
    sys.exit(1)

WING_NAME = "Design and Curation Wing"

DNC_VOLUNTEERS = [
    # Poster Making Team
    {"name": "Arsia", "roll": "2601MM23", "team": "Poster Making Team"},
    {"name": "Rahul Prasad P", "roll": "2603AI03", "team": "Poster Making Team"},
    {"name": "Kandibanda Nehal", "roll": "2601AI32", "team": "Poster Making Team"},
    {"name": "Ameya Pravin Kamat", "roll": "2601CE26", "team": "Poster Making Team"},
    {"name": "Anirudh Makkapati", "roll": "2602ST05", "team": "Poster Making Team"},
    {"name": "Shaurya Gupta", "roll": "2601CT01", "team": "Poster Making Team"},
    # Video Editing Team
    {"name": "Neha", "roll": "2601PH20", "team": "Video Editing Team"},
    {"name": "Jayshri Agarwal", "roll": "2601ES20", "team": "Video Editing Team"},
    {"name": "Abhishek", "roll": "2601MM15", "team": "Video Editing Team"},
    {"name": "Nishad Baviskar", "roll": "2603AI02", "team": "Video Editing Team"},
    {"name": "Banoth Sidharth", "roll": "2601MC37", "team": "Video Editing Team"},
    {"name": "Srinaina Gowru", "roll": "2601EC27", "team": "Video Editing Team"},
    # Photography Team
    {"name": "Dhande Hemani Vinod", "roll": "2602VL08", "team": "Photography Team"},
    {"name": "Ahon Pansa", "roll": "2601CE51", "team": "Photography Team"},
    {"name": "Anubhu Das", "roll": "2601MC09", "team": "Photography Team"},
    {"name": "Lakshya Malkhede", "roll": "2601MC05", "team": "Photography Team"},
    {"name": "Kale Sneha Zelaji", "roll": "2601EE42", "team": "Photography Team"},
    {"name": "Ayush Singh", "roll": "2603CE06", "team": "Photography Team"},
    {"name": "Priyamgaurvi", "roll": "2601CE06", "team": "Photography Team"},
    {"name": "Tokalwad Parth Anand", "roll": "2601AI44", "team": "Photography Team"},
]

def init_firebase(key_path):
    if not firebase_admin._apps:
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Successfully connected to Firestore.\n")
    return db

def main():
    print("=" * 65)
    print(f"  NSS IITP -- Update Wing to {WING_NAME}")
    print(f"  Total records in list: {len(DNC_VOLUNTEERS)}")
    print("=" * 65)

    possible_paths = [
        "../serviceAccountKey.json",
        "serviceAccountKey.json",
        "C:/NSS IITP APP/NSS-IITP-Official-App/serviceAccountKey.json",
    ]
    key_path = None
    for p in possible_paths:
        if os.path.exists(p):
            key_path = os.path.abspath(p)
            print(f"Found service account key: {key_path}\n")
            break

    if not key_path:
        print("serviceAccountKey.json not found.")
        sys.exit(1)

    try:
        db = init_firebase(key_path)
    except Exception as e:
        print(f"Failed to connect to Firebase: {e}")
        sys.exit(1)

    updated_count = 0
    not_found_count = 0
    already_set_count = 0

    sys.stdout.reconfigure(encoding='utf-8')
    for entry in DNC_VOLUNTEERS:
        roll = entry["roll"].strip().upper()
        name = entry["name"]
        team = entry["team"]

        doc_ref = db.collection("users").document(roll)
        doc = doc_ref.get()

        if not doc.exists:
            print(f"[NOT FOUND] User not found in Firestore: {roll} ({name})")
            not_found_count += 1
            continue

        data = doc.to_dict() or {}
        current_wings = data.get("wings", [])

        if not isinstance(current_wings, list):
            current_wings = [current_wings] if current_wings else []

        if WING_NAME in current_wings:
            print(f"[ALREADY SET] {roll} ({name}) already has '{WING_NAME}'")
            already_set_count += 1
        else:
            new_wings = list(set(current_wings + [WING_NAME]))
            doc_ref.update({"wings": new_wings})
            print(f"[UPDATED] {roll} ({name}) [{team}] -> wings: {new_wings}")
            updated_count += 1

    print("\n" + "=" * 65)
    print("  Summary:")
    print(f"  - Total unique students: {len(DNC_VOLUNTEERS)}")
    print(f"  - Successfully updated: {updated_count}")
    print(f"  - Already had {WING_NAME}: {already_set_count}")
    print(f"  - Not found in DB: {not_found_count}")
    print("=" * 65)

if __name__ == "__main__":
    main()
