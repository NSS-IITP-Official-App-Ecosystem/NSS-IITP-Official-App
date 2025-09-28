import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import csv
import time

# Path to your Firebase service account key file
SERVICE_ACCOUNT_KEY = "C:\\Users\\HP\\AndroidStudioProjects\\ChatApp\\chatapp-24fae-4375b955e5f7.json"

# Path to the CSV file
CSV_FILE_PATH = "C:\\Users\\HP\\AndroidStudioProjects\\ChatApp\\Student.csv"

def import_students_to_firestore():
    """Import student data from CSV to Firestore"""
    print("Starting import of student data to Firestore...")
    
    # Initialize Firebase Admin SDK
    try:
        cred = credentials.Certificate(SERVICE_ACCOUNT_KEY)
        firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("Connected to Firestore database")
    except Exception as e:
        print(f"Error connecting to Firestore: {e}")
        return
    
    # Read the CSV file
    try:
        with open(CSV_FILE_PATH, 'r', encoding='utf-8') as csv_file:
            csv_reader = csv.DictReader(csv_file)
            rows = list(csv_reader)
            print(f"Found {len(rows)} students in CSV file")
            
            # Process in batches
            batch_size = 20
            total_batches = (len(rows) + batch_size - 1) // batch_size
            success_count = 0
            
            for batch_index in range(total_batches):
                start_idx = batch_index * batch_size
                end_idx = min(start_idx + batch_size, len(rows))
                batch_rows = rows[start_idx:end_idx]
                
                # Create a batch
                batch = db.batch()
                
                print(f"Processing batch {batch_index + 1}/{total_batches} ({len(batch_rows)} students)")
                
                for row in batch_rows:
                    try:
                        # Clean up data
                        student_data = {}
                        roll_no = row['Roll No.'].strip()
                        
                        # Skip empty roll numbers
                        if not roll_no:
                            print("Skipping row with empty roll number")
                            continue
                        
                        # Add all fields to the document
                        for key, value in row.items():
                            # Clean keys by removing whitespace and dots
                            clean_key = key.strip().replace(".", "_").replace(" ", "_")
                            # Store the value
                            student_data[clean_key] = value.strip() if isinstance(value, str) else value
                        
                        # Create a reference to the student document
                        student_ref = db.collection('Student').document(roll_no)
                        
                        # Add to batch
                        batch.set(student_ref, student_data)
                    except Exception as e:
                        print(f"Error processing student {roll_no}: {e}")
                
                # Commit the batch
                try:
                    batch.commit()
                    success_count += len(batch_rows)
                    print(f"Successfully committed batch {batch_index + 1}")
                    # Small delay to avoid hitting Firestore limits
                    time.sleep(1)
                except Exception as e:
                    print(f"Error committing batch {batch_index + 1}: {e}")
            
            print(f"Import completed. Successfully imported {success_count} out of {len(rows)} students.")
    
    except Exception as e:
        print(f"Error reading CSV file: {e}")

if __name__ == "__main__":
    import_students_to_firestore() 