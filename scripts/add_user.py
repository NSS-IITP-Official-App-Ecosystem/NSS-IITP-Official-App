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

def main():
    print("--- Add New NSS User (General) ---")
    
    # 1. Get Service Account Key Path
    # Common locations to check
    possible_paths = [
        "C:/Users/itses/CodeVault/Projects/Keys/IITP App/service-account.json",
        "serviceAccountKey.json",
        "scripts/serviceAccountKey.json",
    ]
    
    key_path = None
    for path in possible_paths:
        if os.path.exists(path):
            key_path = os.path.abspath(path)
            print(f"Found service account key at: {key_path}")
            break
            
    while not key_path:
        key_path = input("Enter the absolute path to your serviceAccountKey.json: ").strip()
        key_path = key_path.strip("'\"")
        
        if not key_path:
            print("Path cannot be empty.")
            continue

        if os.path.exists(key_path):
            if os.path.isfile(key_path) and key_path.endswith('.json'):
                break
            elif os.path.isdir(key_path):
                # Check for json files inside
                json_files = [f for f in os.listdir(key_path) if f.endswith('.json')]
                if 'serviceAccountKey.json' in json_files:
                    key_path = os.path.join(key_path, 'serviceAccountKey.json')
                    print(f"Found and using: {key_path}")
                    break
                elif len(json_files) == 1:
                    key_path = os.path.join(key_path, json_files[0])
                    print(f"Found and using: {key_path}")
                    break
        
        print(f"Error: Valid JSON key file not found at {key_path}. Please try again.")
        key_path = None # Reset if validation failed

    # 2. Initialize Firebase
    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(key_path)
            firebase_admin.initialize_app(cred)
        else:
            print("Firebase already initialized.")
            
        db = firestore.client()
        print("Successfully connected to Firestore.")
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

    # 3. Collect User Details
    print("\n--- Enter User Details ---")
    
    while True:
        roll_number = input("Roll Number (e.g. 2501CS01): ").strip().upper()
        if roll_number: break
        print("Roll Number is required.")

    while True:
        name = input("Name: ").strip()
        if name: break
        print("Name is required.")

    while True:
        email = input("Institute Outlook ID (Email): ").strip()
        if email: break
        print("Email is required.")
        
    user_type = input("User Type [student]: ").strip().lower()
    if not user_type:
        user_type = "student"

    password = "Temp@1234"

    # 4. Create Auth User
    print(f"\nSetting up Authentication for {email}...")
    auth_uid = None
    
    try:
        try:
            # Check if user exists
            user = auth.get_user_by_email(email)
            print(f"Auth user already exists. UID: {user.uid}")
            auth_uid = user.uid
        except auth.UserNotFoundError:
            # Create new user
            user = auth.create_user(
                email=email,
                password=password,
                display_name=name
            )
            print(f"Created new Auth user. UID: {user.uid}")
            auth_uid = user.uid
    except Exception as e:
        print(f"Error with Authentication: {e}")
        return

    if not auth_uid:
        print("Error: Could not retrieve Auth UID.")
        return

    # 5. Write to Firestore
    user_data = {
        "name": name,
        "instituteOutlookId": email,
        "rollNumber": roll_number,
        "uid": auth_uid,
        "userType": user_type,
        "wings": [],
        "eventsAttended": 0,
        "hours": 0,
        "sem1Hours": 0,
        "sem2Hours": 0,
        "unreadCount": 0
    }

    try:
        doc_ref = db.collection('users').document(roll_number)
        
        # Check if exists
        doc = doc_ref.get()
        if doc.exists:
            print(f"Warning: Firestore document for '{roll_number}' already exists.")
            existing = doc.to_dict()
            print(f"Existing Name: {existing.get('name')}")
            
            confirm = input("Overwrite this user data? (y/n): ").lower()
            if confirm != 'y':
                print("Skipping Firestore update.")
                return

        doc_ref.set(user_data, merge=True)
        print(f"\nSUCCESS: User '{roll_number}' has been configured.")
        print(f"  - Name: {name}")
        print(f"  - Email: {email}")
        print(f"  - Password: {password}")
        print(f"  - Auth UID: {auth_uid}")
            
    except Exception as e:
        print(f"Error updating Firestore: {e}")

if __name__ == "__main__":
    main()
