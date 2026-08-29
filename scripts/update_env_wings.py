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

WING_NAME = "Environmental Wing"

ENV_VOLUNTEERS = [
    {"serial": 1, "name": "Mallika Lokesh", "roll": "2601EC31"},
    {"serial": 2, "name": "Kanak Agrawal", "roll": "2601AI04"},
    {"serial": 3, "name": "Aditi Verma", "roll": "2601AI16"},
    {"serial": 4, "name": "Yogyta Verma", "roll": "2601CS11"},
    {"serial": 5, "name": "Rashmita Yadav", "roll": "2603MC03"},
    {"serial": 6, "name": "Aarya Pankaj Patil", "roll": "2601CT05"},
    {"serial": 7, "name": "Anupam Jha", "roll": "2601CE10"},
    {"serial": 8, "name": "Suyash Verma", "roll": "2601EC41"},
    {"serial": 9, "name": "Swati", "roll": "2602GT06"},
    {"serial": 10, "name": "Satyam Sinha", "roll": "2602GT01"},
    {"serial": 11, "name": "Illa Ramya", "roll": "2601CB59"},
    {"serial": 12, "name": "Anushka Gupta", "roll": "2601AI45"},
    {"serial": 13, "name": "Allu Sai Jagan", "roll": "2601EC15"},
    {"serial": 14, "name": "Yashraj Jeph", "roll": "2602GT05"},
    {"serial": 15, "name": "Parikshit Bedi", "roll": "2603ME09"},
    {"serial": 16, "name": "Sarda Dhruv", "roll": "2603ME05"},
    {"serial": 17, "name": "Ambala Athrij", "roll": "2601EE24"},
    {"serial": 18, "name": "Patel Lokesh", "roll": "2601AI28"},
    {"serial": 19, "name": "Jajimogga Teja", "roll": "2601ME37"},
    {"serial": 20, "name": "Ramchandra Tholiya", "roll": "2603ES01"},
    {"serial": 21, "name": "Sachin Kumar", "roll": "2603CB04"},
    {"serial": 22, "name": "Mayank Jangid", "roll": "2601CT17"},
    {"serial": 23, "name": "Ritojoy Mandal", "roll": "2603MM02"},
    {"serial": 24, "name": "Yashaswi Sudhanv Balanagu", "roll": "2601ME32"},
    {"serial": 25, "name": "Taneti Venkata Rama Kanth", "roll": "2601EC35"},
    {"serial": 26, "name": "Threenender Bhukya", "roll": "2601EC28"},
    {"serial": 27, "name": "Gaurav Agnihotri", "roll": "2601CB29"},
    {"serial": 28, "name": "Pratheeksha B G", "roll": "2603CB01"},
    {"serial": 29, "name": "Shivansh Kumar", "roll": "2601EC44"},
    {"serial": 30, "name": "Deepanshu Jangir", "roll": "2601EC20"},
    {"serial": 31, "name": "Biroju Rushikesh", "roll": "2603ME07"},
    {"serial": 32, "name": "Adya Agarwal", "roll": "2603MM04"},
    {"serial": 33, "name": "Ramavath Vinay", "roll": "2601CS88"},
    {"serial": 34, "name": "Vadthyavath Bhuvanendra Naik", "roll": "2601CS01"},
    {"serial": 35, "name": "Varun", "roll": "2601MC32"},
    {"serial": 36, "name": "Jadhav Prajyot", "roll": "2601CS24"},
    {"serial": 37, "name": "Gaurav Kumar", "roll": "2601CE60"},
    {"serial": 38, "name": "Namala Yeshwanth Kumar", "roll": "2601ME38"},
    {"serial": 39, "name": "Gugulothu Ram Charan", "roll": "2601EE25"},
    {"serial": 40, "name": "Kodavath Harshavardhan", "roll": "2601EE37"},
    {"serial": 41, "name": "Kurupudi Sai Sathvika", "roll": "2601CS39"},
    {"serial": 42, "name": "Adrita Bej", "roll": "2601ME16"},
    {"serial": 43, "name": "Satya Kumar Shubham", "roll": "2601EE01"},
    {"serial": 44, "name": "Putluru Sai Varshith Reddy", "roll": "2603EE04"},
    {"serial": 45, "name": "Anmol Kumar Sah", "roll": "2601CS70"},
    {"serial": 46, "name": "Panthangi Abhishek", "roll": "2601EC32"},
    {"serial": 47, "name": "Gajibelli Pushpa Vamsi", "roll": "2601CS19"},
    {"serial": 48, "name": "Muttamsetti Jayasri Durga", "roll": "2601ME56"},
    {"serial": 49, "name": "Ashish Jacob", "roll": "2601ME35"},
    {"serial": 50, "name": "Boda Ram Charan", "roll": "2601EE30"},
    {"serial": 51, "name": "Ramavath Praveen Kumar", "roll": "2601MM44"},
    {"serial": 52, "name": "Rohit Kumar Behera", "roll": "2602PC03"},
    {"serial": 53, "name": "Varanasi Sai Rishin", "roll": "2601ME14"},
    {"serial": 54, "name": "Akula Anjana Sowmya", "roll": "2601CB51"},
    {"serial": 55, "name": "Mandalapu Sai Sahasra", "roll": "2602PC02"},
    {"serial": 56, "name": "Ankit Meena", "roll": "2602CS09"},
    {"serial": 57, "name": "Shrishant Kumar", "roll": "2601CS78"},
    {"serial": 58, "name": "Telugu Shashank", "roll": "2603MM05"},
    {"serial": 59, "name": "Ishita Singh", "roll": "2601CB18"},
    {"serial": 60, "name": "Madhavaram Saharsh", "roll": "2601MC24"},
    {"serial": 61, "name": "Kottisa Haripreeth", "roll": "2601ME52"},
    {"serial": 62, "name": "Utkarsha Ravindra Kakulate", "roll": "2601CS62"},
    {"serial": 63, "name": "V Varunkumar", "roll": "2601CS41"},
    {"serial": 64, "name": "Nukala Venkata Chandrahas", "roll": "2601AI01"},
    {"serial": 65, "name": "Singh Aryaman Harendra", "roll": "2601ME34"},
    {"serial": 66, "name": "Gumpu Puneeth Sasank", "roll": "2601AI41"},
    {"serial": 67, "name": "Siddatapu Ajay Kumar", "roll": "2601CT35"},
    {"serial": 68, "name": "Murukuti Sridhar Reddy", "roll": "2601ME78"},
    {"serial": 69, "name": "Samir Kumar", "roll": "2601EE26"},
    {"serial": 70, "name": "Rishu Kumar", "roll": "2602CS11"},
    {"serial": 71, "name": "Sreeman Buram", "roll": "2601EE17"},
    {"serial": 72, "name": "Bhukya Mohan", "roll": "2601ME74"},
    {"serial": 73, "name": "Peddaram Sahasra Vardhini", "roll": "2601CB64"},
    {"serial": 74, "name": "SHAIK NOUSHEER", "roll": "2601EE46"},
    {"serial": 75, "name": "G Yozan Babu", "roll": "2601AI17"},
    {"serial": 76, "name": "Gangapatnam shyam abhishek", "roll": "2601CB65"},
    {"serial": 77, "name": "Lingam Rohit", "roll": "2601EC08"},
    {"serial": 78, "name": "Aamana Khatoon", "roll": "2601CS58"},
    {"serial": 79, "name": "Aayush Mishra", "roll": "2602GT02"},
    {"serial": 80, "name": "Raj shekhar", "roll": "2601EE22"},
]

def init_firebase(key_path):
    if not firebase_admin._apps:
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Successfully connected to Firestore.\n")
    return db

def main():
    print("=" * 60)
    print("  NSS IITP -- Update Wing to Environmental Wing")
    print(f"  Total records in list: {len(ENV_VOLUNTEERS)}")
    print("=" * 60)

    unique_rolls = {}
    for entry in ENV_VOLUNTEERS:
        roll = entry["roll"].strip().upper()
        unique_rolls[roll] = entry["name"]
    
    print(f"  Unique roll numbers: {len(unique_rolls)}")

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
    not_found_list = []

    sys.stdout.reconfigure(encoding='utf-8')
    for roll, name in unique_rolls.items():
        doc_ref = db.collection("users").document(roll)
        doc = doc_ref.get()

        if not doc.exists:
            print(f"[NOT FOUND] User not found in Firestore: {roll} ({name})")
            not_found_count += 1
            not_found_list.append((roll, name))
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
            print(f"[UPDATED] {roll} ({name}) -> wings: {new_wings}")
            updated_count += 1

    print("\n" + "=" * 60)
    print(f"  Summary:")
    print(f"  - Total unique students: {len(unique_rolls)}")
    print(f"  - Successfully updated: {updated_count}")
    print(f"  - Already had Environmental Wing: {already_set_count}")
    print(f"  - Not found in DB: {not_found_count}")
    if not_found_list:
        print("\n  Students NOT found in Firestore users collection:")
        for r, n in not_found_list:
            print(f"    - {r}: {n}")
    print("=" * 60)

if __name__ == "__main__":
    main()
