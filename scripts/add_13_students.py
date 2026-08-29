import firebase_admin
from firebase_admin import credentials, firestore, auth

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

PASSWORD = "Nss@2026Password"
RDW = "Rural Development Wing"
PRERNA = "Prerna Wing"

# 13 Students from spreadsheet
STUDENTS = [
    {"roll": "2403ES05", "name": "Gujjala Gagana Sadrush", "email": "gujjala_2403es05@iitp.ac.in", "wing": RDW},
    {"roll": "2401EC37", "name": "Natte Ravi Shankar", "email": "natte_2401ec37@iitp.ac.in", "wing": PRERNA},
    {"roll": "2401MC18", "name": "Gandham Suhas", "email": "gandham_2401mc18@iitp.ac.in", "wing": RDW},
    {"roll": "2402GT06", "name": "Vempada Narendra Reddy", "email": "vempada_2402gt06@iitp.ac.in", "wing": PRERNA},
    {"roll": "2403CE03", "name": "Podicheti Sriram", "email": "podicheti_2403ce03@iitp.ac.in", "wing": RDW},
    {"roll": "2401PH13", "name": "Apoorva Singh", "email": "apoorva_2401ph13@iitp.ac.in", "wing": PRERNA},
    {"roll": "2401CS79", "name": "J Sneha Pratap Singh", "email": "sneha_2401cs79@iitp.ac.in", "wing": RDW},
    {"roll": "2401CB67", "name": "Jyoti", "email": "jyoti_2401cb67@iitp.ac.in", "wing": PRERNA},
    {"roll": "2401PH05", "name": "Sanskar Kulshrestha", "email": "sanskar_2401ph05@iitp.ac.in", "wing": RDW},
    {"roll": "2401CB33", "name": "Nittin Kumar", "email": "nittin_2401cb33@iitp.ac.in", "wing": PRERNA},
    {"roll": "2401EE41", "name": "Purushotam Kumar", "email": "purushotam_2401ee41@iitp.ac.in", "wing": RDW},
    {"roll": "2401AI09", "name": "Soumabho Pal", "email": "soumabho_2401ai09@iitp.ac.in", "wing": PRERNA},
    {"roll": "2401ME61", "name": "Aditya Raj", "email": "aditya_2401me61@iitp.ac.in", "wing": RDW},
]

print("=" * 70)
print(f"Adding {len(STUDENTS)} students to Firebase Auth & Firestore")
print("=" * 70)

success_count = 0
for s in STUDENTS:
    roll = s["roll"].strip().upper()
    name = s["name"].strip()
    email = s["email"].strip().lower()
    wing = s["wing"]

    # 1. Auth
    auth_uid = None
    try:
        user = auth.get_user_by_email(email)
        auth_uid = user.uid
        print(f"[AUTH] Existing user: {email} (UID: {auth_uid})")
    except auth.UserNotFoundError:
        user = auth.create_user(
            email=email,
            password=PASSWORD,
            display_name=name,
            email_verified=True
        )
        auth_uid = user.uid
        print(f"[AUTH] Created new user: {email} (UID: {auth_uid})")
    except Exception as e:
        print(f"[AUTH ERROR] {email}: {e}")
        continue

    # 2. Firestore
    user_doc = {
        "name": name,
        "rollNumber": roll,
        "instituteOutlookId": email,
        "uid": auth_uid,
        "userType": "student",
        "wings": [wing],
        "hours": 0.0,
        "sem1Hours": 0.0,
        "sem2Hours": 0.0,
        "eventsAttended": 0,
        "eventsList": [],
        "unreadCount": 0,
    }

    try:
        db.collection("users").document(roll).set(user_doc, merge=True)
        print(f"[FIRESTORE] Saved {roll} -> {name} | Wing: {wing}\n")
        success_count += 1
    except Exception as e:
        print(f"[FIRESTORE ERROR] {roll}: {e}\n")

print("=" * 70)
print(f"Finished: {success_count}/{len(STUDENTS)} students successfully added!")
print("=" * 70)
