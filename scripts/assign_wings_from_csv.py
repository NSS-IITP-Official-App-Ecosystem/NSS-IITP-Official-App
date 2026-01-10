import os
import sys
import csv

# Try importing firebase_admin
try:
    import firebase_admin
    from firebase_admin import credentials
    from firebase_admin import firestore
except ImportError:
    print("Error: 'firebase-admin' package is missing.")
    print("Please run: pip install firebase-admin")
    sys.exit(1)

def get_column_index(headers, possible_names):
    headers_lower = [h.strip().lower() for h in headers]
    for name in possible_names:
        if name.lower() in headers_lower:
            return headers_lower.index(name.lower())
    return -1

def main():
    print("--- Assign Wings from CSV ---")
    
    # 1. Get Service Account Key Path
    while True:
        key_path = input("Enter the absolute path to your serviceAccountKey.json: ").strip()
        key_path = key_path.strip("'\"")
        
        if not key_path:
            print("Path cannot be empty.")
            continue

        if os.path.exists(key_path):
            if os.path.isfile(key_path) and key_path.endswith('.json'):
                break
            elif os.path.isdir(key_path):
                json_files = [f for f in os.listdir(key_path) if f.endswith('.json')]
                if 'serviceAccountKey.json' in json_files:
                    key_path = os.path.join(key_path, 'serviceAccountKey.json')
                    print(f"Using: {key_path}")
                    break
                elif len(json_files) == 1:
                    key_path = os.path.join(key_path, json_files[0])
                    print(f"Using: {key_path}")
                    break
        print(f"Error: Valid JSON key file not found at {key_path}.")

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

    # 3. Get CSV Path
    while True:
        csv_path = input("Enter the absolute path to your CSV file: ").strip()
        csv_path = csv_path.strip("'\"")
        
        if os.path.exists(csv_path) and csv_path.lower().endswith('.csv'):
            break
        print("Invalid file. Please provide a valid .csv files.")

    # 4. Process CSV
    print("\nProcessing CSV...")
    
    try:
        # Pre-fetch existing user IDs to avoid 404s in batch
        print("Fetching existing user list from Firestore...")
        users_ref = db.collection('users')
        existing_ids = {doc.id for doc in users_ref.stream()}
        print(f"Loaded {len(existing_ids)} existing users.")

        with open(csv_path, 'r', encoding='utf-8-sig') as f:
            reader = csv.reader(f)
            headers = next(reader, None)
            
            if not headers:
                print("Error: CSV file is empty.")
                return

            print(f"Headers found: {headers}")

            # Find columns
            roll_idx = get_column_index(headers, [
                'Roll number (Letters in capital)', 
                'Roll Number', 'rollNumber', 'RollNo', 'Roll'
            ])
            
            wing_idx = get_column_index(headers, [
                'Your Wing', 
                'wings', 'Wing', 'assigned_wing', 'Wing Name'
            ])

            if roll_idx == -1:
                print("Error: Could not find 'Roll number (Letters in capital)' column.")
                return
            if wing_idx == -1:
                print("Error: Could not find 'Your Wing' column.")
                return

            print(f"Mapped columns - Roll Number: Index {roll_idx}, Wing: Index {wing_idx}")
            
            batch = db.batch()
            batch_count = 0
            total_updates = 0
            skipped_users = []
            processed_rolls = []
            
            for row in reader:
                if len(row) <= max(roll_idx, wing_idx):
                    continue

                roll_number = row[roll_idx].strip().upper()
                wing_name = row[wing_idx].strip()
                
                if not roll_number or not wing_name:
                    continue

                # Validation: Check if user exists
                if roll_number not in existing_ids:
                    print(f"Skipping {roll_number} (Not found in DB)")
                    skipped_users.append(roll_number)
                    continue

                doc_ref = db.collection('users').document(roll_number)
                
                batch.update(doc_ref, {
                    'wings': firestore.ArrayUnion([wing_name])
                })
                
                processed_rolls.append(roll_number)
                
                batch_count += 1
                total_updates += 1
                
                if batch_count >= 400:
                    batch.commit()
                    print(f"  Committed batch of {batch_count} updates...")
                    batch = db.batch()
                    batch_count = 0
            
            if batch_count > 0:
                batch.commit()
                
            print(f"\nSUCCESS: Updated wings for {total_updates} users.")
            
            # --- Report Missing Users (Batch 25) ---
            print("\n--- Checking for Batch 25 Missing in CSV ---")
            
            # 1. Fetch full details (Name + Roll) for Batch 25 users in DB
            # We already have existing_ids (set of rolls), but we need names now.
            # Ideally we fetch this at start if we wanted names, but to correct minimal code, 
            # let's just re-fetch or assume efficient lookup if needed. 
            # Actually, let's just do a specific query for batch 25 now.
            
            processed_rolls_set = set(processed_rolls) # We need to track this in the loop
            
            missing_in_csv = []
            
            # Optimization: Filter existing_ids locally first to avoid DB read if we didn't save names
            # But user wants NAMES. existing_ids only has IDs.
            # So let's query DB for users where 'rollNumber' >= '25' and 'rollNumber' < '26' 
            # (Lexicographical check for startsWith '25')
            
            batch_25_users = users_ref.where('rollNumber', '>=', '25').where('rollNumber', '<', '26').stream()
            
            count_25 = 0
            for doc in batch_25_users:
                data = doc.to_dict()
                roll = data.get('rollNumber', doc.id).upper()
                name = data.get('name', 'Unknown')
                
                count_25 += 1
                
                if roll not in processed_rolls_set:
                    missing_in_csv.append({'roll': roll, 'name': name})
            
            if missing_in_csv:
                print(f"\nFound {len(missing_in_csv)} users starting with '25' in DB but NOT in CSV:")
                print(f"{'ROLL NUMBER':<15} | {'NAME'}")
                print("-" * 40)
                for u in sorted(missing_in_csv, key=lambda x: x['roll']):
                    print(f"{u['roll']:<15} | {u['name']}")
            else:
                print("All Batch 25 users in DB were present in the CSV!")

            if skipped_users:
                print(f"\nWARNING: Skipped {len(skipped_users)} users not found in database (from CSV):")
                for s in skipped_users:
                    print(f" - {s}")
            
    except Exception as e:
        print(f"Error processing CSV: {e}")

if __name__ == "__main__":
    main()
