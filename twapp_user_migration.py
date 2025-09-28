import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os
import time

# Constants for user types
USER_TYPE_STUDENT = "Student"
USER_TYPE_ADMIN1 = "Admin1"
USER_TYPE_ADMIN2 = "Admin2"

# Path to the service account key files
TWAPP_KEY_PATH = 'path_to_twapp_service_account_key.json'
MAIN_APP_KEY_PATH = 'path_to_main_app_service_account_key.json'

def init_twapp_firebase():
    """Initialize the TWApp Firebase Admin SDK"""
    if len(firebase_admin._apps) > 0:
        # If already initialized, get the existing app
        return firebase_admin.get_app(name='twapp')
    else:
        # Initialize with the TWApp credentials
        cred = credentials.Certificate(TWAPP_KEY_PATH)
        return firebase_admin.initialize_app(cred, name='twapp')

def init_main_firebase():
    """Initialize the main app Firebase Admin SDK"""
    if len(firebase_admin._apps) > 1:
        # If already initialized, get the existing app
        return firebase_admin.get_app(name='mainapp')
    else:
        # Initialize with the main app credentials
        cred = credentials.Certificate(MAIN_APP_KEY_PATH)
        return firebase_admin.initialize_app(cred, name='mainapp')

def get_twapp_db():
    """Get a Firestore client for the TWApp database"""
    app = init_twapp_firebase()
    return firestore.client(app)

def get_main_db():
    """Get a Firestore client for the main app database"""
    app = init_main_firebase()
    return firestore.client(app)

def format_student_data(student_data):
    """Format student data to match the users collection schema"""
    formatted_data = {
        'contactNumber': student_data.get('mobile_number', ''),
        'description': 'together we can',
        'email': student_data.get('email', ''),
        'name': student_data.get('name', ''),
        'rollNumber': student_data.get('roll_number', ''),
        'userType': USER_TYPE_STUDENT,
        'year': 1
    }
    return formatted_data

def format_admin1_data(admin_data):
    """Format Admin1 data to match the users collection schema"""
    # Try to get email from either field
    email = admin_data.get('email_id_google', '') or admin_data.get('email_id_college', '')
    
    formatted_data = {
        'contactNumber': admin_data.get('mobile_number', ''),
        'description': 'together we can',
        'email': email,
        'name': admin_data.get('name', ''),
        'rollNumber': admin_data.get('roll_number', ''),
        'userType': USER_TYPE_ADMIN1,
        'year': 2
    }
    return formatted_data

def format_admin2_data(admin_data):
    """Format Admin2 data to match the users collection schema"""
    # Try to get email from either field
    email = admin_data.get('email_id_google', '') or admin_data.get('email_id_college', '')
    
    formatted_data = {
        'contactNumber': admin_data.get('mobile_number', ''),
        'description': 'together we can',
        'email': email,
        'name': admin_data.get('name', ''),
        'rollNumber': admin_data.get('roll_number', ''),
        'userType': USER_TYPE_ADMIN2,
        'year': 3
    }
    return formatted_data

def migrate_users():
    """Fetch users from TWApp database and migrate to main app users collection"""
    # Get database clients
    twapp_db = get_twapp_db()
    main_db = get_main_db()
    
    # Collections to process
    collections = [
        {'name': 'Student', 'formatter': format_student_data},
        {'name': 'Admin1', 'formatter': format_admin1_data},
        {'name': 'Admin2', 'formatter': format_admin2_data}
    ]
    
    total_migrated = 0
    
    # Process each collection
    for collection_info in collections:
        collection_name = collection_info['name']
        formatter = collection_info['formatter']
        
        print(f"Fetching data from {collection_name} collection...")
        
        # Get all documents from the collection
        docs = twapp_db.collection(collection_name).get()
        
        # Process each document
        for doc in docs:
            source_data = doc.to_dict()
            
            # Get the roll number (used as document ID)
            roll_number = source_data.get('roll_number')
            if not roll_number:
                print(f"Skipping record with missing roll number in {collection_name}")
                continue
            
            # Format the data
            user_data = formatter(source_data)
            
            # Write to the main app's users collection
            main_db.collection('users').document(roll_number).set(user_data)
            
            print(f"Migrated {roll_number} from {collection_name} to users collection")
            total_migrated += 1
            
            # Small delay to avoid overwhelming Firestore
            time.sleep(0.1)
    
    print(f"Migration complete! Total records migrated: {total_migrated}")

if __name__ == "__main__":
    # Update these paths with your actual service account key file paths
    TWAPP_KEY_PATH = input("Enter the path to TWApp service account key file: ")
    MAIN_APP_KEY_PATH = input("Enter the path to main app service account key file: ")
    
    # Validate file paths
    if not os.path.exists(TWAPP_KEY_PATH):
        print(f"Error: TWApp service account key file not found at {TWAPP_KEY_PATH}")
        exit(1)
    
    if not os.path.exists(MAIN_APP_KEY_PATH):
        print(f"Error: Main app service account key file not found at {MAIN_APP_KEY_PATH}")
        exit(1)
    
    # Perform the migration
    migrate_users() 