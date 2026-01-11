import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os
import sys

def main():
    print("--- Add iOS User to Allowlist ---")
    
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
        # Remove quotes if user dragged and dropped file
        key_path = key_path.strip("'\"")
        
        if os.path.isdir(key_path):
            # User provided a directory, check for json files inside
            json_files = [f for f in os.listdir(key_path) if f.endswith('.json')]
            if not json_files:
                print(f"Error: No .json files found in directory '{key_path}'. Please provide the full path to the file.")
                key_path = None
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
                key_path = None
                continue

        if os.path.exists(key_path):
            break
        else:
            print(f"Error: File not found at {key_path}. Please try again.")
            key_path = None

    # 2. Initialize Firebase
    try:
        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("Successfully connected to Firestore.")
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

    # 3. Get Roll Numbers
    while True:
        raw_input = input("Enter Roll Numbers separated by space (e.g., 2301CS01 2401CE02): ").strip().upper()
        if raw_input:
            roll_numbers = [r.strip() for r in raw_input.split() if r.strip()]
            
            if not roll_numbers:
                print("No valid roll numbers found.")
                continue

            print(f"Found {len(roll_numbers)} roll numbers: {', '.join(roll_numbers)}")
            confirm = input(f"Are you sure you want to allow these {len(roll_numbers)} users? (y/n): ").lower()
            if confirm == 'y':
                break
        else:
            print("Input cannot be empty.")

    # 4. Add to Firestore
    print(f"\nProcessing {len(roll_numbers)} users...")
    
    batch = db.batch()
    batch_count = 0
    total_added = 0
    
    try:
        for roll in roll_numbers:
            doc_ref = db.collection('allowed_ios_users').document(roll)
            
            # Use 'set' to create or overwrite. 
            # (We skip the existence check for bulk operations to be faster, 
            #  but you can assume this action means 'ensure they are allowed')
            data = {
                'added_at': firestore.SERVER_TIMESTAMP,
                'active': True
            }
            batch.set(doc_ref, data)
            batch_count += 1
            total_added += 1
            
            # Commit processing in chunks of 400 (limit is 500)
            if batch_count >= 400:
                batch.commit()
                print(f"  Committed batch of {batch_count} users...")
                batch = db.batch()
                batch_count = 0
        
        if batch_count > 0:
            batch.commit()
            
        print(f"\nSUCCESS: Processed {total_added} users. They are now in the 'allowed_ios_users' collection.")
        
    except Exception as e:
        print(f"Error adding users: {e}")

if __name__ == "__main__":
    try:
        import firebase_admin
    except ImportError:
        print("Error: 'firebase-admin' package is missing.")
        print("Please run: pip install firebase-admin")
        sys.exit(1)
        
    main()
