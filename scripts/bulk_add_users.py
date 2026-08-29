import os
import sys

# Try importing firebase_admin
try:
    import firebase_admin
    from firebase_admin import auth
    from firebase_admin import credentials
    from firebase_admin import firestore
except ImportError:
    print("Error: 'firebase-admin' package is missing.")
    print("Please run: pip install firebase-admin")
    sys.exit(1)

# ─────────────────────────────────────────────────────────────────────────────
# USER DATA — Deduplicated from Google Form responses (25/08/2026)
# Issues fixed:
#   - Duplicates removed (kept latest submission per roll number)
#   - Ramavath email had a space -> fixed: "ramavath_2601me73@iitp.ac.in"
#   - ARKA DAS: roll=2602PH02 but form email said 2603ph02 -> corrected to match roll
#     WARNING: Verify ARKA DAS email before running!
# ─────────────────────────────────────────────────────────────────────────────
USERS = [
    # (roll_number,        name,                        email)
    ("2602VL07",     "Neeraj Guguloth",            "neeraj_2602vl07@iitp.ac.in"),
    ("2601MM04",     "Anushka Ghosh",              "anushka_2601mm04@iitp.ac.in"),
    ("2601ME59",     "Poornima Tiwari",            "poornima_2601me59@iitp.ac.in"),
    ("2601CS58",     "Aamana Khatoon",             "aamana_2601cs58@iitp.ac.in"),
    ("2601EE46",     "SHAIK NOUSHEER",             "shaik_2601ee46@iitp.ac.in"),
    ("2601ME30",     "Mahika Paliwal",             "mahika_2601me30@iitp.ac.in"),
    ("2601ME73",     "Ramavath Nithin kumar naik", "ramavath_2601me73@iitp.ac.in"),  # space removed from email
    ("UA2603CDH108", "Sanaulla Khan",              "sanaulla_ua2603cdh108@iitp.ac.in"),
    ("2601CS64",     "Noor Jamali",                "noor_2601cs64@iitp.ac.in"),
    ("2601CS63",     "Navneet Kumar Nitin",        "navneet_2601cs63@iitp.ac.in"),
    ("2602PH02",     "ARKA DAS",                   "arka_2602ph02@iitp.ac.in"),      # WARNING: form had 2603, corrected to 2602
]

PASSWORD = "Nss@2026Password"
USER_TYPE = "student"


def init_firebase(key_path):
    if not firebase_admin._apps:
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Successfully connected to Firestore.\n")
    return db


def add_user(db, roll_number, name, email):
    print(f"--- Processing: {roll_number} | {name} | {email}")

    # 1. Firebase Auth
    auth_uid = None
    try:
        user = auth.get_user_by_email(email)
        print(f"    Auth: User already exists -> UID: {user.uid}  (resetting password)")
        auth.update_user(user.uid, password=PASSWORD, display_name=name)
        auth_uid = user.uid
    except auth.UserNotFoundError:
        user = auth.create_user(email=email, password=PASSWORD, display_name=name)
        print(f"    Auth: Created new user    -> UID: {user.uid}")
        auth_uid = user.uid
    except Exception as e:
        print(f"    ERROR (Auth): {e}")
        return False

    # 2. Firestore
    user_data = {
        "name": name,
        "instituteOutlookId": email,
        "rollNumber": roll_number,
        "uid": auth_uid,
        "userType": USER_TYPE,
        "wings": [],
        "eventsAttended": 0,
        "hours": 0,
        "sem1Hours": 0,
        "sem2Hours": 0,
        "unreadCount": 0,
    }

    try:
        doc_ref = db.collection("users").document(roll_number)
        doc = doc_ref.get()
        if doc.exists:
            existing = doc.to_dict()
            print(f"    Firestore: Document exists (name={existing.get('name')}) -- merging.")
        doc_ref.set(user_data, merge=True)
        print(f"    SUCCESS -- Password: {PASSWORD}\n")
        return True
    except Exception as e:
        print(f"    ERROR (Firestore): {e}\n")
        return False


def main():
    print("=" * 60)
    print("  NSS IITP -- Bulk Student Import")
    print(f"  {len(USERS)} unique users to process")
    print("=" * 60)

    # Resolve service account key
    possible_paths = [
        "../serviceAccountKey.json",
        "serviceAccountKey.json",
        "C:/Users/itses/CodeVault/Projects/Keys/IITP App/service-account.json",
    ]
    key_path = None
    for p in possible_paths:
        if os.path.exists(p):
            key_path = os.path.abspath(p)
            print(f"Found service account key: {key_path}\n")
            break

    while not key_path:
        raw = input("Enter path to serviceAccountKey.json: ").strip().strip("'\"")
        if os.path.isfile(raw) and raw.endswith(".json"):
            key_path = os.path.abspath(raw)
        else:
            print("Not a valid JSON file. Try again.")

    try:
        db = init_firebase(key_path)
    except Exception as e:
        print(f"Failed to connect to Firebase: {e}")
        sys.exit(1)

    # Warn about known data issues
    print("WARNING: DATA ISSUES DETECTED -- please verify before proceeding:")
    print("   1. ARKA DAS (2602PH02): form email was 'arka_2603ph02@iitp.ac.in'")
    print("      Script uses 'arka_2602ph02@iitp.ac.in' to match roll number.")
    print("   2. Ramavath email had a leading space -- fixed automatically.")
    confirm = input("\nProceed with import? (y/n): ").strip().lower()
    if confirm != "y":
        print("Aborted.")
        sys.exit(0)

    print()
    success, failed = 0, 0
    for roll, name, email in USERS:
        ok = add_user(db, roll, name, email)
        if ok:
            success += 1
        else:
            failed += 1

    print("=" * 60)
    print(f"  Import complete: {success} succeeded, {failed} failed")
    print("=" * 60)


if __name__ == "__main__":
    main()
