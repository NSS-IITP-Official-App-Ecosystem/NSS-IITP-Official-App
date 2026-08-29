import sys
import pandas as pd
import firebase_admin
from firebase_admin import credentials, firestore, auth
import concurrent.futures

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

default_password = "Nss@2026Password"

def process_student(row):
    email = str(row['Email']).strip()
    roll = str(row['Roll No.']).strip().upper()
    name = str(row['Name']).strip()
    
    if not email or not roll or str(email).lower() == 'nan':
        return None
        
    uid = None
    try:
        user = auth.create_user(email=email, password=default_password, email_verified=True)
        uid = user.uid
    except auth.EmailAlreadyExistsError:
        try:
            user = auth.get_user_by_email(email)
            uid = user.uid
        except Exception:
            return None
    except Exception:
        return None
        
    return {
        "name": name,
        "rollNumber": roll,
        "instituteOutlookId": email,
        "uid": uid,
        "unreadCount": 0,
        "userType": "student",
        "wings": [],
        "hours": 0.0,
        "sem1Hours": 0.0,
        "sem2Hours": 0.0,
        "eventsAttended": 0,
        "eventsList": []
    }

def main():
    excel_path = "students data/NSS_Allocation_2026-27.xlsx"
    print(f"Reading students from {excel_path}...")
    df = pd.read_excel(excel_path)
    
    print(f"Starting parallel import of {len(df)} students...")
    success = 0
    batch = db.batch()
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        results = list(executor.map(process_student, [row for _, row in df.iterrows()]))
        
    for doc in results:
        if doc:
            doc_ref = db.collection("users").document(doc["rollNumber"])
            batch.set(doc_ref, doc, merge=True)
            success += 1
            if success % 100 == 0:
                batch.commit()
                print(f"Committed {success} users to Firestore...")
                batch = db.batch()
                
    if success % 100 != 0:
        batch.commit()
        
    print(f"\\n✅ SUCCESSFULLY IMPORTED: {success} students!")

if __name__ == "__main__":
    main()
