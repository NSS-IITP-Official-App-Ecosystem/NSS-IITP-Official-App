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
        with open(csv_path, 'r', encoding='utf-8-sig') as f: # utf-8-sig handles BOM if present
            reader = csv.reader(f)
            headers = next(reader, None)
            
            if not headers:
                print("Error: CSV file is empty.")
                return

            print(f"Headers found: {headers}")

            # Find columns
            # Updated to match specific headers provided by user
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
            
            # Prepare batch
            batch = db.batch()
            batch_count = 0
            total_updates = 0
            
            for row in reader:
                if len(row) <= max(roll_idx, wing_idx):
                    continue # Skip incomplete rows

                roll_number = row[roll_idx].strip().upper()
                wing_name = row[wing_idx].strip()
                
                if not roll_number or not wing_name:
                    continue

                # Reference to user
                doc_ref = db.collection('users').document(roll_number)
                
                # Update wings field (ArrayUnion ensures it's added to the list without duplicates)
                batch.update(doc_ref, {
                    'wings': firestore.ArrayUnion([wing_name])
                })
                
                batch_count += 1
                total_updates += 1
                
                # Commit if limit reached
                if batch_count >= 400:
                    batch.commit()
                    print(f"  Committed batch of {batch_count} updates...")
                    batch = db.batch()
                    batch_count = 0
            
            # Final commit
            if batch_count > 0:
                batch.commit()
                
            print(f"\nSUCCESS: Updated wings for {total_updates} users.")
            
    except Exception as e:
        print(f"Error processing CSV: {e}")

if __name__ == "__main__":
    main()
