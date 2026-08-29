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

WING_NAME = "Teaching and Technical Wing"

# List of all 114 TTW Volunteers from the official list
TTW_VOLUNTEERS = [
    {"serial": 1, "name": "Kumar Shankar", "roll": "2601ME02"},
    {"serial": 2, "name": "Chinmay Aggarwal", "roll": "2601ME04"},
    {"serial": 3, "name": "Pratyusha Maji", "roll": "2601EE23"},
    {"serial": 4, "name": "Vikash Vaibhav", "roll": "2603ME04"},
    {"serial": 5, "name": "Utkarsh Yadav", "roll": "2601CB12"},
    {"serial": 6, "name": "Purnim Raj", "roll": "2601EE05"},
    {"serial": 7, "name": "Ayush Narayan", "roll": "2601EE07"},
    {"serial": 8, "name": "Tanushka Sharma", "roll": "2601CB25"},
    {"serial": 9, "name": "Narottam Singh Sikarwar", "roll": "2601CS15"},
    {"serial": 10, "name": "Alankrit Patel", "roll": "2603CS01"},
    {"serial": 11, "name": "Arnav Agrawal", "roll": "2601CT23"},
    {"serial": 12, "name": "Deepak Kumar", "roll": "2601MC17"},
    {"serial": 13, "name": "Brajendra Kumar", "roll": "2601EE10"},
    {"serial": 14, "name": "Ayansh Pathak", "roll": "2601MM42"},
    {"serial": 15, "name": "Ryan Mathew", "roll": "2601EE33"},
    {"serial": 16, "name": "Bhushan Vijay Barde", "roll": "2601MM32"},
    {"serial": 17, "name": "Abhishek Kumar", "roll": "2602MC03"},
    {"serial": 18, "name": "Shreyansh Ravi", "roll": "2601ME08"},
    {"serial": 19, "name": "Anshul Tyagi", "roll": "2601CB06"},
    {"serial": 20, "name": "Ratan Kumar Yadav", "roll": "2601ME55"},
    {"serial": 21, "name": "Lapshetwar Shivam Pradip", "roll": "2601CS49"},
    {"serial": 22, "name": "Ali Ali", "roll": "2601ME06"},
    {"serial": 23, "name": "Rizwanur Rahman", "roll": "2601MM31"},
    {"serial": 24, "name": "Vivek Goyal", "roll": "2601CE17"},
    {"serial": 25, "name": "Swarit Srivastava", "roll": "2601CB26"},
    {"serial": 26, "name": "Chaitanaya Nathalia", "roll": "2601CS85"},
    {"serial": 27, "name": "Harsh Pratap Singh", "roll": "2601CE11"},
    {"serial": 28, "name": "Krishna Kumar", "roll": "2601CS42"},
    {"serial": 29, "name": "Ayansh Utkarsh Maurya", "roll": "2602CM05"},
    {"serial": 30, "name": "Nitya Bansal", "roll": "2601CT09"},
    {"serial": 31, "name": "Mothukupally Nihal Reddy", "roll": "2601MC44"},
    {"serial": 32, "name": "Krishnam", "roll": "2601ME17"},
    {"serial": 33, "name": "Akhil Saini", "roll": "2601PH23"},
    {"serial": 34, "name": "Samaksh Vishnoi", "roll": "2601MM05"},
    {"serial": 35, "name": "Prashant Meena", "roll": "2601CE29"},
    {"serial": 36, "name": "Aarna Niti Pushkar", "roll": "2601PH04"},
    {"serial": 37, "name": "Brijesh Nishad", "roll": "2601CT19"},
    {"serial": 38, "name": "Bonuga Koushik Chandra Reddy", "roll": "2603CE04"},
    {"serial": 39, "name": "Shivam Krishnan", "roll": "2601MM25"},
    {"serial": 40, "name": "Mohammad Ibrahim", "roll": "2601CB30"},
    {"serial": 41, "name": "Jeetesh Kumar Sahu", "roll": "2601CB11"},
    {"serial": 42, "name": "Rounak Mandal", "roll": "2601MM38"},
    {"serial": 43, "name": "Yusuf Imtiyaz", "roll": "2601ME50"},
    {"serial": 44, "name": "Swapnil Chowdhury", "roll": "2601ME43"},
    {"serial": 45, "name": "Samarth Bajpai", "roll": "2601MM21"},
    {"serial": 46, "name": "Harshit Singh", "roll": "2601EC11"},
    {"serial": 47, "name": "Ujjwal Priyedarshi", "roll": "2601PH14"},
    {"serial": 48, "name": "Shobhit Airan", "roll": "2601CB46"},
    {"serial": 49, "name": "Shaswat Gangopadhyay", "roll": "2601PH13"},
    {"serial": 50, "name": "Harshit Bajaj", "roll": "2602VL06"},
    {"serial": 51, "name": "Chetan Singh", "roll": "2601CE13"},
    {"serial": 52, "name": "Yash Purushottam Sawsakade", "roll": "2601MC20"},
    {"serial": 53, "name": "Silimkar Tejas Mangesh", "roll": "2603ME13"},
    {"serial": 54, "name": "Rohit Dulariya", "roll": "2601CE63"},
    {"serial": 55, "name": "Amey Mittal", "roll": "2601CT08"},
    {"serial": 56, "name": "Anirudh G", "roll": "2601EC29"},
    {"serial": 57, "name": "Dipu Kumar Kewat", "roll": "2603CT03"},
    {"serial": 58, "name": "Rakesh Jangid", "roll": "2601CE30"},
    {"serial": 59, "name": "Ayush Raj", "roll": "2601EE08"},
    {"serial": 60, "name": "Nikhil", "roll": "2601CB53"},
    {"serial": 61, "name": "Bishu Bhaskar", "roll": "2603CB03"},
    {"serial": 62, "name": "Prince Sharma", "roll": "2601EC13"},
    {"serial": 63, "name": "Ponnakanti Vivek Ripunjay", "roll": "2601PH30"},
    {"serial": 64, "name": "Harshawardhan Navnath Gaikwad", "roll": "2601MM19"},
    {"serial": 65, "name": "Gauransh Sharma", "roll": "2601ME80"},
    {"serial": 66, "name": "Pritam Kumar Siddhant", "roll": "2601CE40"},
    {"serial": 67, "name": "Shah Vraj Ketul", "roll": "2601EC45"},
    {"serial": 68, "name": "Ishan Mittal", "roll": "2601MC38"},
    {"serial": 69, "name": "Saksham Mittal", "roll": "2601MC06"},
    {"serial": 70, "name": "Kamran Kamran", "roll": "2601CE43"},
    {"serial": 71, "name": "Pramod Yadav", "roll": "2601CS47"},
    {"serial": 72, "name": "Kumar Naman", "roll": "2601MC29"},
    {"serial": 73, "name": "Lakshmi Patni", "roll": "2601MC50"},
    {"serial": 74, "name": "Jaiveen Kaur", "roll": "2602CM04"},
    {"serial": 75, "name": "Aditya Kumar Bhagat", "roll": "2603EC01"},
    {"serial": 76, "name": "Asif Uddaulah", "roll": "2601EC03"},
    {"serial": 77, "name": "Bodapati Veera Venkata Ravi", "roll": "2602CS02"},
    {"serial": 78, "name": "Mahi Khera", "roll": "2601CE54"},
    {"serial": 79, "name": "Anurag Singh", "roll": "2601ME21"},
    {"serial": 80, "name": "Dharamveer Pingoliya", "roll": "2601CE22"},
    {"serial": 81, "name": "Dev Asati", "roll": "2602CS05"},
    {"serial": 82, "name": "Anaghmoy Chatterjee", "roll": "2601ME64"},
    {"serial": 83, "name": "Umesh Kumar Saini", "roll": "2601CS82"},
    {"serial": 84, "name": "Rudra Mahawat", "roll": "2601ME60"},
    {"serial": 85, "name": "Satyam Singh", "roll": "2601ME11"},
    {"serial": 86, "name": "Anushka", "roll": "2601MM04"},
    {"serial": 87, "name": "Garima", "roll": "2601EC14"},
    {"serial": 88, "name": "Vaibhav Anand", "roll": "2601CE35"},
    {"serial": 89, "name": "Pranil Nikhil Dube", "roll": "2603ES03"},
    {"serial": 90, "name": "Husain Husain", "roll": "2601CS12"},
    {"serial": 91, "name": "Harish Attri", "roll": "2601MC02"},
    {"serial": 92, "name": "Snigdh Arindam", "roll": "2601CE15"},
    {"serial": 93, "name": "Krishna Sharma", "roll": "2601EC43"},
    {"serial": 94, "name": "Aaranya Ganotra", "roll": "2601MM24"},
    {"serial": 95, "name": "Aditya Maurya", "roll": "2601ME36"},
    {"serial": 96, "name": "Chirag Kumar", "roll": "2601MC13"},
    {"serial": 97, "name": "Satvik Deorah", "roll": "2601MC16"},
    {"serial": 98, "name": "Abhayanand Kumar", "roll": "2601EE28"},
    {"serial": 99, "name": "Abhishek Kumar", "roll": "2601CS50"},
    {"serial": 100, "name": "Aditi Kumari", "roll": "2601CB02"},
    {"serial": 101, "name": "Somya Tikwani", "roll": "2602CS06"},
    {"serial": 102, "name": "Aanya Verma", "roll": "2601CE37"},
    {"serial": 103, "name": "Vaibhav", "roll": "2601CS87"},
    {"serial": 104, "name": "Bhosale Saksham Sanjaykumar", "roll": "2601EE40"},
    {"serial": 105, "name": "S K Tharun", "roll": "2602MT10"},
    {"serial": 106, "name": "Parumandla Jashwanth", "roll": "2601ME82"},
    {"serial": 107, "name": "Poornima Tiwari", "roll": "2601ME59"},
    {"serial": 108, "name": "Ayush Gupta", "roll": "2601CB24"},
    {"serial": 109, "name": "Noor Jamali", "roll": "2601CS64"},
    {"serial": 110, "name": "Kada Darshan", "roll": "2601AI48"},
    {"serial": 111, "name": "Pranjal Prashant Nerkar", "roll": "2603ME01"},
    {"serial": 112, "name": "Ritesh Kumar", "roll": "2601CT21"},
    {"serial": 113, "name": "Aadya Singh", "roll": "2601ME66"},
    {"serial": 114, "name": "Anurag Kumar", "roll": "2602CS08"},
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
    print("  NSS IITP -- Update Wing to Teaching and Technical Wing")
    print(f"  Total records in list: {len(TTW_VOLUNTEERS)}")
    print("=" * 60)

    # Deduplicate rolls if any
    unique_rolls = {}
    for entry in TTW_VOLUNTEERS:
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

    updated_count = 0
    not_found_count = 0
    already_set_count = 0
    not_found_list = []

    # Batch operations or individual document updates
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

        # Ensure current_wings is a list
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
    print(f"  - Already had TTW wing: {already_set_count}")
    print(f"  - Not found in DB: {not_found_count}")
    if not_found_list:
        print("\n  Students NOT found in Firestore users collection:")
        for r, n in not_found_list:
            print(f"    - {r}: {n}")
    print("=" * 60)

if __name__ == "__main__":
    main()
