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

WING_NAME = "Rural Development Wing"

RDW_VOLUNTEERS = [
    # Page 1
    {"name": "Abhinav Anand", "roll": "2603EE05"},
    {"name": "Pradip Kumar", "roll": "2601CE45"},
    {"name": "Rohan Kumar Singh", "roll": "2601PH05"},
    {"name": "Varahi Rohit Pardeshi", "roll": "2601CB55"},
    {"name": "Paridhi Agrawal", "roll": "2601EE32"},
    {"name": "Sumit Sarkar", "roll": "2601EE15"},
    {"name": "Yuvraj Singh", "roll": "2601EC26"},
    {"name": "Jayant Raj", "roll": "2601MM17"},
    {"name": "Bakuri Nava Deep Raju", "roll": "2601ME79"},
    {"name": "Shreya Kumari", "roll": "2601ME61"},
    {"name": "Aayush Paikaray", "roll": "2601MC41"},
    {"name": "Daidipya Dadhich", "roll": "2602GT04"},
    {"name": "Srirup Saha", "roll": "2601EC04"},
    {"name": "Nasir Raza", "roll": "2601CB47"},
    {"name": "Navneet Kumar Nitin", "roll": "2601CS63"},
    {"name": "Aayush Mishra", "roll": "2602GT02"},
    {"name": "Ranveer Singh", "roll": "2601EE29"},
    {"name": "Shyam Sundar Ghorui", "roll": "2601MM30"},
    {"name": "Pintu Mondal", "roll": "2601CB54"},
    {"name": "Kevlani Yash Manishbhai", "roll": "2601CB67"},
    {"name": "Gangapatnam Shyam Abhishek", "roll": "2601CB65"},
    {"name": "Aryan", "roll": "2601ME07"},
    {"name": "Natte Charan Sai Teja", "roll": "2601EC17"},
    {"name": "Nihal Shoju", "roll": "2601CS73"},
    {"name": "Krish", "roll": "2601CS84"},
    {"name": "Shlok Tanmaya", "roll": "2601PH07"},
    {"name": "Neeraj Guguloth", "roll": "2602VL07"},
    {"name": "Raj Shekhar", "roll": "2601EE22"},
    # Page 2
    {"name": "Lingam Rohit", "roll": "2601EC08"},
    {"name": "R Syam Sundar Reddy", "roll": "2602PC01"},
    {"name": "Raikwar Shruti Vinod", "roll": "2602MC04"},
    {"name": "Didde Ramya", "roll": "2601ES14"},
    {"name": "Mankhush", "roll": "2601EE03"},
    {"name": "Gavva Abhiram Reddy", "roll": "2601EC42"},
    {"name": "Bhagat Anushka Satyaprakash", "roll": "2601EE04"},
    {"name": "Gulshan Kumar", "roll": "2601ME12"},
    {"name": "Gorle Gnanadeep", "roll": "2601ME22"},
    {"name": "Harshit Kumar", "roll": "2601AI14"},
    {"name": "Aman Kumar", "roll": "2601CT25"},
    {"name": "Viraj Singh", "roll": "2601AI24"},
    {"name": "Arka Das", "roll": "2603PH02"},
    {"name": "Kuluri Venkata Sai Kaushik", "roll": "2601CS34"},
    {"name": "Aryan Aggrawal", "roll": "2603PH01"},
    {"name": "Dhruv", "roll": "2601CS43"},
    {"name": "Suraj Singh", "roll": "2601PH29"},
    {"name": "Kondaka Yasaswy", "roll": "2602PC04"},
    {"name": "Prince", "roll": "2601CT04"},
    {"name": "Biswajit Sahoo", "roll": "2601CS16"},
    {"name": "Yenuga Peddireddy Gari Krishna", "roll": "2601PH26"},
    {"name": "Atharv Joshi", "roll": "2601ME77"},
    {"name": "Tanav", "roll": "2601CT32"},
    {"name": "Lakavath Anirvinya", "roll": "2601EE44"},
    {"name": "Potnuru Sartak", "roll": "2602MC07"},
    {"name": "Unhone Pushpak Yogesh", "roll": "2602MC06"},
    {"name": "Shaik Arbaaz Ahmed", "roll": "2601CE50"},
    # Page 3
    {"name": "Talasani Rithwik Reddy", "roll": "2601CS27"},
    {"name": "Tadi Bhargava Siva Durga", "roll": "2601MC45"},
    {"name": "Chintala Rishitha", "roll": "2601MM34"},
    {"name": "Siriki Hemanth", "roll": "2601CB05"},
    {"name": "Lakavath Santhosh", "roll": "2601AI33"},
    {"name": "Banothu Sai Charan", "roll": "2601PH31"},
    {"name": "Yash Prasad", "roll": "2601MC40"},
    {"name": "Shivank Goyal", "roll": "2601MM18"},
    {"name": "Mahika paliwal", "roll": "2601ME30"},
    {"name": "Kirti Vitthal Fasate", "roll": "2601MM33"},
    {"name": "Bondugula Likith Sai", "roll": "2601EC07"},
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
    print("  NSS IITP -- Update Wing to Rural Development Wing")
    print(f"  Total records in list: {len(RDW_VOLUNTEERS)}")
    print("=" * 60)

    unique_rolls = {}
    for entry in RDW_VOLUNTEERS:
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
    print(f"  - Already had Rural Development Wing: {already_set_count}")
    print(f"  - Not found in DB: {not_found_count}")
    if not_found_list:
        print("\n  Students NOT found in Firestore users collection:")
        for r, n in not_found_list:
            print(f"    - {r}: {n}")
    print("=" * 60)

if __name__ == "__main__":
    main()
