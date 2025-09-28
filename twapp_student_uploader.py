import csv
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import datetime

# Path to the CSV file
CSV_FILE_PATH = r"C:\Users\HP\Downloads\Database.TWAPP - Student _ user.csv"

# Path to your service account key file
SERVICE_ACCOUNT_KEY_PATH = r"C:\Users\HP\AndroidStudioProjects\ChatApp\twapp-9bf5f-firebase-adminsdk-fbsvc-cbb13cc3c2.json"

# Initialize Firebase Admin SDK with service account
cred = credentials.Certificate(SERVICE_ACCOUNT_KEY_PATH)
firebase_admin.initialize_app(cred)
db = firestore.client()

def upload_to_firestore():
    """Upload CSV data to Firestore with Student collection structure"""
    print("Starting upload to Firestore...")
    
    # Open and read the CSV file
    with open(CSV_FILE_PATH, mode='r', encoding='utf-8') as file:
        # Create a CSV reader
        csv_reader = csv.DictReader(file)
        
        # Counter for progress tracking
        count = 0
        
        # Process each row
        for row in csv_reader:
            # Use the roll number as document ID
            roll_number = row['Roll No.'].strip()
            
            if not roll_number:
                print("Skipping row with empty roll number")
                continue
            
            # Create a data dictionary from all fields in the row
            data = {}
            for key, value in row.items():
                # Clean the field name (replace spaces and special chars with underscores)
                clean_key = key.lower().replace(' ', '_').replace('.', '').replace('/', '_')
                
                # Add the field to data dictionary
                data[clean_key] = value
            
            # Add metadata
            data['upload_timestamp'] = firestore.SERVER_TIMESTAMP
            data['last_updated'] = firestore.SERVER_TIMESTAMP
            
            # Upload to Firestore
            db.collection('Student').document(roll_number).set(data)
            
            # Increment counter and show progress
            count += 1
            if count % 10 == 0:
                print(f"Uploaded {count} records...")
    
    print(f"Upload completed! Total records: {count}")

if __name__ == "__main__":
    upload_to_firestore() 