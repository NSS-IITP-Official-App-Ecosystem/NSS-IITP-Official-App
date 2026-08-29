import firebase_admin
from firebase_admin import credentials, firestore, auth

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users_to_check = [
    ("2403ES05", "GUJJALA GAGANA SADRUSH", "gujjala_2403es05@iitp.ac.in"),
    ("2401EC37", "NATTE RAVI SHANKAR", "natte_2401ec37@iitp.ac.in"),
    ("2401MC18", "GANDHAM SUHAS", "gandham_2401mc18@iitp.ac.in"),
    ("2402GT06", "Vempada Narendra Reddy", "vempada_2402gt06@iitp.ac.in"),
    ("2403CE03", "Podicheti Sriram", "podicheti_2403ce03@iitp.ac.in"),
    ("2401PH13", "APOORVA SINGH", "apoorva_2401ph13@iitp.ac.in"),
    ("2401CS79", "J SNEHA PRATAP SINGH", "sneha_2401cs79@iitp.ac.in"),
    ("2401CB67", "JYOTI .", "jyoti_2401cb67@iitp.ac.in"),
    ("2401PH05", "SANSKAR KULSHRESTHA", "sanskar_2401ph05@iitp.ac.in"),
    ("2401CB33", "NITTIN KUMAR", "nittin_2401cb33@iitp.ac.in"),
    ("2401EE41", "PURUSHOTAM KUMAR", "purushotam_2401ee41@iitp.ac.in"),
    ("2401AI09", "SOUMABHO PAL", "soumabho_2401ai09@iitp.ac.in"),
    ("2401ME61", "ADITYA RAJ", "aditya_2401me61@iitp.ac.in")
]

for roll, name, email in users_to_check:
    doc = db.collection("users").document(roll).get()
    exists = doc.exists
    doc_data = doc.to_dict() if exists else None
    
    auth_user = None
    try:
        auth_user = auth.get_user_by_email(email)
        auth_exists = True
    except auth.UserNotFoundError:
        auth_exists = False
    
    print(f"Roll: {roll} | Name: {name} | Firestore Exists: {exists} | Auth Exists: {auth_exists}")
    if exists:
        print(f"   -> Firestore data: {doc_data}")
