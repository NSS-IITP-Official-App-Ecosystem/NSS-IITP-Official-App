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

WING_NAME = "Prerna Wing"

PRERNA_VOLUNTEERS = [
    # Page 1
    {"name": "Nagella Shrivatsa Prasad", "roll": "2603AI01"},
    {"name": "Maloth Parimala", "roll": "2601ES05"},
    {"name": "Jiya Ganeshsingh Chauhan", "roll": "2602PC06"},
    {"name": "Yeruva Praharshini", "roll": "2601AI03"},
    {"name": "Ananya Kumari", "roll": "2601CT11"},
    {"name": "Aditya Kaushal", "roll": "2602GT03"},
    {"name": "Pranjal Narendra Chaudhari", "roll": "2602ST06"},
    {"name": "Devangana Aneesh", "roll": "2601MC15"},
    {"name": "Kalluru Deekshitha Reddy", "roll": "2601CS22"},
    {"name": "Muniza Parvin", "roll": "2602CS07"},
    {"name": "Modadugu Venkata Sai Siva", "roll": "2601AI22"},
    {"name": "Anurag Nallani", "roll": "2601ME69"},
    {"name": "Abhishek V Singh", "roll": "2601ES17"},
    {"name": "Tamalika Sau", "roll": "2601MM39"},
    {"name": "Apurva Pranay", "roll": "2601MM10"},
    {"name": "Ramavath Nithin Kumar Naik", "roll": "2601ME73"},
    {"name": "K Naveen Nayak", "roll": "2602CM09"},
    {"name": "Navanitha J", "roll": "2601ME29"},
    {"name": "Mudavath Divya", "roll": "2601CB56"},
    {"name": "Rehan Riazahmad Mulla", "roll": "2601MC12"},
    {"name": "Addala Suhani", "roll": "2601MC39"},
    # Page 2
    {"name": "Parlapalli Surya Srikar Reddy", "roll": "2601AI43"},
    {"name": "Mohammad Bohra", "roll": "2602MC08"},
    {"name": "Dharavath Deepanvitha", "roll": "2601CT31"},
    {"name": "Kashvi Verma", "roll": "2601CT14"},
    {"name": "Manya Dhirawat", "roll": "2601CT10"},
    {"name": "Dasu Jayasree", "roll": "2601CE23"},
    {"name": "Hazare Sanvi", "roll": "2601ME24"},
    {"name": "Shivjeet Kumar", "roll": "2601ME71"},
    {"name": "Mahak Shakya", "roll": "2603PH03"},
    {"name": "Vemu Manjula", "roll": "2601CS57"},
    {"name": "Vivek Kumar Singh", "roll": "2603CE02"},
    {"name": "Arya Deshmukh", "roll": "2601CE18"},
    {"name": "Rachit Agarwal", "roll": "2603ES04"},
    {"name": "Mudokulam Jaikishan Naik", "roll": "2601CS30"},
    {"name": "Sarvjeet Kumar", "roll": "2601CE36"},
    {"name": "Dara Vinoothna", "roll": "2601ME48"},
    {"name": "Gudala Venkata Pranav Bhanu", "roll": "2601MM36"},
    {"name": "Pradyumna", "roll": "2601ME10"},
    {"name": "Ankit Mishra", "roll": "2601CS21"},
    {"name": "Wagmare Ajay", "roll": "2601MM35"},
    {"name": "Natasha Sen", "roll": "2602ST03"},
    # Page 3
    {"name": "Rahul kumar Reddy", "roll": "2601CS32"},
    {"name": "S LITHESH CHETAN VADAVELLI", "roll": "2601ME44"},
    {"name": "Krish Kumar", "roll": "2601ME46"},
    {"name": "Abhigyan Singh", "roll": "2601EE27"},
    {"name": "Dhanjit Das", "roll": "2601MM06"},
    {"name": "Mandhani Netal Radheshyam", "roll": "2601CT06"},
    {"name": "Kaushik Kumar", "roll": "2603EC02"},
    {"name": "Sivani Anamika R", "roll": "2601PH24"},
    {"name": "Chandrachur Mondal", "roll": "2601MC04"},
    {"name": "Amit Dhakad", "roll": "2601PH02"},
    {"name": "Mukiri Saatwik", "roll": "2601CS81"},
    {"name": "Chintala Rishitha", "roll": "2601MM34"},
    {"name": "Vankunavath Shashank Indra Tej", "roll": "2601CS10"},
    {"name": "Paridhi Agrawal", "roll": "2601EE32"},
    {"name": "Saptarshi Patra", "roll": "2602ST02"},
    {"name": "Trisha Sharma", "roll": "2601MM40"},
    {"name": "Jakkampudi Tanuj", "roll": "2601CS05"},
    {"name": "Ritu Kumari", "roll": "2601ME81"},
    {"name": "Utkarsh Raj", "roll": "2601CS74"},
    {"name": "Pradyuman Singh Shekhawat", "roll": "2601ES22"},
    {"name": "Tejas Babhale", "roll": "2601MC28"},
    # Page 4
    {"name": "Yelaka Midhun Sai", "roll": "2601ME53"},
    {"name": "Karwar Vedant Dattatray", "roll": "2603ME02"},
    {"name": "Ambati Keerthi Pranavi", "roll": "2601CS67"},
    {"name": "Aryan Singh", "roll": "2602VL03"},
    {"name": "Sriejan Das", "roll": "2603CB02"},
    {"name": "Champa Yeshey", "roll": "2603CS02"},
    {"name": "Chandrachur Mondal", "roll": "2601MC04"},
    {"name": "Mulkala Vashista", "roll": "2601AI15"},
    {"name": "Annamanani Ashwadh", "roll": "2601AI02"},
    {"name": "Sourabh Kumar", "roll": "2601MC33"},
    {"name": "Wriddhi Adhya", "roll": "2601CE39"},
    {"name": "Ambati Keerthi Pranavi", "roll": "2601CS67"},
    {"name": "Saksham Kori", "roll": "2501CT36"},
    {"name": "Chaitnya Singh Chauhan", "roll": "2501MC11"},
    {"name": "Lakshmi Revanth Sai Paladugu", "roll": "2603EE03"},
    {"name": "Yelaka Midhun Sai", "roll": "2601ME53"},
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
    print("  NSS IITP -- Update Wing to Prerna Wing")
    print(f"  Total records in list: {len(PRERNA_VOLUNTEERS)}")
    print("=" * 60)

    unique_rolls = {}
    for entry in PRERNA_VOLUNTEERS:
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
    print(f"  - Already had Prerna Wing: {already_set_count}")
    print(f"  - Not found in DB: {not_found_count}")
    if not_found_list:
        print("\n  Students NOT found in Firestore users collection:")
        for r, n in not_found_list:
            print(f"    - {r}: {n}")
    print("=" * 60)

if __name__ == "__main__":
    main()
