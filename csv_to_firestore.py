import csv
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

# Initialize Firebase with your second project's credentials
cred = credentials.Certificate("C:\\Users\\HP\\AndroidStudioProjects\\ChatApp\\twapp-9bf5f-firebase-adminsdk-fbsvc-cbb13cc3c2.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

def upload_csv_to_firestore(csv_file_path):
    # Open and read the CSV file
    with open(csv_file_path, mode='r', encoding='utf-8') as file:
        # Create a CSV reader
        csv_reader = csv.DictReader(file)
        
        # Counter for tracking progress
        count = 0
        
        # Process each row
        for row in csv_reader:
            # Clean the roll number (remove spaces) to use as document ID
            roll_number = row['Roll No.'].strip()
            
            # Skip if roll number is empty
            if not roll_number:
                print(f"Skipping row with empty roll number")
                continue
                
            # Create a dictionary with all fields
            user_data = {
                'name': row['Name'],
                'sex': row['Sex'],
                'nss_group': row['NSS gro'],
                'academic_group': row['Academic Grp.'],
                'gmail_id': row['Gmail ID'],
                'institute_id': row['Institute ID'],
                'course_code': row['Course Code'],
                'interview_score': row['Interview Score'],
                'subject_preference1': row['Subjec Prefrence 1'],
                'subject_preference2': row['Sub. Preference2'],
                'subject_preference3': row['Sub. Preference 3'],
                'mobile_number': row['Mobile no.'],
                'hindi': row['Hindi'],
                'other_interest': row['Other Interest'],
                'selected': row['Selected']
            }
            
            # Add document to Firestore with roll number as document ID
            db.collection('users').document(roll_number).set(user_data)
            
            # Increment counter and print progress
            count += 1
            if count % 10 == 0:
                print(f"Uploaded {count} records to Firestore")
    
    print(f"Successfully uploaded {count} records to Firestore")

if __name__ == "__main__":
    # Replace with your CSV file path
    csv_file_path = "your_student_data.csv"
    
    print("Starting upload to Firestore...")
    upload_csv_to_firestore(csv_file_path)
    print("Upload completed!") 