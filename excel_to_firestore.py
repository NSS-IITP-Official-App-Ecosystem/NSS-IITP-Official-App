import pandas as pd
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from datetime import datetime

# Initialize Firebase Admin SDK
cred = credentials.Certificate('path/to/your/serviceAccountKey.json')  # Replace with your service account key path
firebase_admin.initialize_app(cred)
db = firestore.client()

def transfer_students_to_firestore(excel_file_path):
    # Read Excel file
    df = pd.read_excel(excel_file_path)
    
    # Process each row
    for index, row in df.iterrows():
        # Create student document
        student_data = {
            'name': str(row.get('name', '')),
            'email': str(row.get('email', '')),
            'roll_number': str(row.get('roll_number', '')),
            'user_type': 'student',
            'description': str(row.get('branch', '')),
            'contact_number': str(row.get('contact_number', '')),
            'year': str(row.get('year', '0')),
            'profile_image_url': None
        }
        
        # Add to Firestore
        db.collection('users').document(student_data['roll_number']).set(student_data)
        print(f"Added student: {student_data['name']}")

def transfer_admins_to_firestore(excel_file_path):
    # Read Excel file
    df = pd.read_excel(excel_file_path)
    
    # Process each row
    for index, row in df.iterrows():
        # Create admin document
        admin_data = {
            'name': str(row.get('name', '')),
            'email': str(row.get('email', '')),
            'roll_number': str(row.get('roll_number', '')),
            'user_type': 'admin',
            'description': str(row.get('role', '')),
            'contact_number': str(row.get('contact_number', '')),
            'year': '0',
            'profile_image_url': None
        }
        
        # Add to Firestore
        db.collection('users').document(admin_data['roll_number']).set(admin_data)
        print(f"Added admin: {admin_data['name']}")

if __name__ == "__main__":
    # Replace these paths with your actual Excel file paths
    students_file = "path/to/your/students.xlsx"
    admins_file = "path/to/your/admins.xlsx"
    
    print("Starting data transfer...")
    
    # Transfer students
    print("\nTransferring students...")
    transfer_students_to_firestore(students_file)
    
    # Transfer admins
    print("\nTransferring admins...")
    transfer_admins_to_firestore(admins_file)
    
    print("\nData transfer completed!") 