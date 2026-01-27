import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os

def main():
    default_path = r"C:\Users\itses\CodeVault\Projects\Keys\IITP App\service-account.json"
    print(f"Please enter the path to your Firebase service account JSON file (Press Enter for default):")
    service_account_path = input().strip()

    if not service_account_path:
        service_account_path = default_path

    # Remove quotes if the user added them (common when copying paths in Windows)
    if service_account_path.startswith('"') and service_account_path.endswith('"'):
        service_account_path = service_account_path[1:-1]
    elif service_account_path.startswith("'") and service_account_path.endswith("'"):
        service_account_path = service_account_path[1:-1]
    
    if not os.path.exists(service_account_path):
        print(f"Error: File not found at {service_account_path}")
        return

    try:
        # Avoid re-initializing if the script is run in an environment where the app is already initialized
        if not firebase_admin._apps:
            cred = credentials.Certificate(service_account_path)
            firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("Successfully connected to Firestore.")
    except Exception as e:
        print(f"Failed to connect to Firestore: {e}")
        return

    collection_name = "ttwStudents"
    # Target preference order based on the provided image
    target_preferences = [
        "Sanskrit",   # 0
        "Biology",    # 1
        "SST",        # 2
        "English",    # 3
        "Chemistry",  # 4
        "Physics",    # 5
        "Maths"       # 6
    ]

    print(f"\nChecking collection '{collection_name}' for students with the following subject preference order:")
    for i, subject in enumerate(target_preferences):
        print(f"{i}: {subject}")
    print("-" * 50)

    try:
        # Stream documents to handle large collections efficiently
        docs = db.collection(collection_name).stream()
        
        match_count = 0
        matched_students = []
        
        for doc in docs:
            data = doc.to_dict()
            student_prefs = data.get("subjectPreferences")
            
            # Check if preferences match exactly
            if isinstance(student_prefs, list) and student_prefs == target_preferences:
                name = data.get("name", "Unknown Name")
                # Using 'rollNumber' as seen in the screenshot
                roll = data.get("rollNumber", "Unknown Roll")
                # Store tuple of (name, roll) for sorting later
                matched_students.append((name, roll))
                match_count += 1
        
        # Sort by name
        matched_students.sort(key=lambda x: x[0])

        for name, roll in matched_students:
            print(f"{name} | Roll: {roll}")
                
        print("-" * 50)
        print(f"Total students with exact preference match: {match_count}")

    except Exception as e:
        print(f"An error occurred while querying the database: {e}")

if __name__ == "__main__":
    main()
