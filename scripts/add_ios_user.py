import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os
import sys

def main():
    print("--- Add iOS User to Allowlist ---")
    
    # 1. Get Service Account Key Path
    while True:
        key_path = input("Enter the absolute path to your serviceAccountKey.json: ").strip()
        # Remove quotes if user dragged and dropped file
        key_path = key_path.strip("'\"")
        
        if os.path.isdir(key_path):
            # User provided a directory, check for json files inside
            json_files = [f for f in os.listdir(key_path) if f.endswith('.json')]
            if not json_files:
                print(f"Error: No .json files found in directory '{key_path}'. Please provide the full path to the file.")
                continue
            
            if 'serviceAccountKey.json' in json_files:
                target_file = os.path.join(key_path, 'serviceAccountKey.json')
                print(f"Found 'serviceAccountKey.json' in directory. Using: {target_file}")
                key_path = target_file
                break
            elif len(json_files) == 1:
                target_file = os.path.join(key_path, json_files[0])
                print(f"Found json file. Using: {target_file}")
                key_path = target_file
                break
            else:
                print(f"Directory contains multiple JSON files: {json_files}")
                print("Please append the correct filename to your path.")
                continue

        if os.path.exists(key_path):
            break
        else:
            print(f"Error: File not found at {key_path}. Please try again.")

    # 2. Initialize Firebase
    try:
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("Successfully connected to Firestore.")
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

    # 3. Get Roll Number
    while True:
        roll_number = input("Enter the Roll Number to authorized (e.g., 2301CS01): ").strip().upper()
        if roll_number:
            confirm = input(f"Are you sure you want to allow '{roll_number}'? (y/n): ").lower()
            if confirm == 'y':
                break
        else:
            print("Roll number cannot be empty.")

    # 4. Add to Firestore
    try:
        doc_ref = db.collection('allowed_ios_users').document(roll_number)
        
        # Check if already exists
        doc = doc_ref.get()
        if doc.exists:
            print(f"Warning: User '{roll_number}' is already in the allowlist.")
            choice = input("Do you want to update/overwrite it? (y/n): ").lower()
            if choice != 'y':
                print("Operation cancelled.")
                return

        # Set the data
        data = {
            'added_at': firestore.SERVER_TIMESTAMP,
            'active': True
        }
        
        doc_ref.set(data)
        print(f"\nSUCCESS: User '{roll_number}' has been added to the 'allowed_ios_users' collection.")
        
    except Exception as e:
        print(f"Error adding user: {e}")

if __name__ == "__main__":
    try:
        import firebase_admin
    except ImportError:
        print("Error: 'firebase-admin' package is missing.")
        print("Please run: pip install firebase-admin")
        sys.exit(1)
        
    main()
